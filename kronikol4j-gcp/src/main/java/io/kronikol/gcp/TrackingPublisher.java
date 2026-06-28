package io.kronikol.gcp;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;

/**
 * Wraps a Pub/Sub {@link Publisher} so each publish is auto-captured as a tracked interaction via
 * {@link PubSubInteractionRecorder} — the Java analog of the .NET {@code TrackingPublisherClient}. Pub/Sub is
 * gRPC and the Java {@link Publisher} is a concrete class, so this is an explicit decorator; {@link #inner()}
 * exposes the underlying client. The Pub/Sub SDK is {@code compileOnly}.
 */
public final class TrackingPublisher {

    private final Publisher inner;
    private final PubSubInteractionRecorder recorder;

    public TrackingPublisher(Publisher inner, PubSubTrackerOptions options) {
        this.inner = inner;
        this.recorder = new PubSubInteractionRecorder(options);
    }

    /** The underlying real publisher (for operations this wrapper does not track). */
    public Publisher inner() {
        return inner;
    }

    /** Publishes a message, tracking it as a {@code Publish} once the broker returns the id (or the error). */
    public ApiFuture<String> publish(PubsubMessage message) {
        PubSubOperationInfo op = PubSubOperationClassifier.classify(
            "PublishAsync", inner.getTopicNameString(), null, 1);
        String content = bodyOf(message);
        ApiFuture<String> future = inner.publish(message);
        ApiFutures.addCallback(future, new ApiFutureCallback<>() {
            @Override
            public void onSuccess(String messageId) {
                recorder.record(op, content, messageId);
            }

            @Override
            public void onFailure(Throwable t) {
                recorder.record(op, content, t.getMessage());
            }
        }, MoreExecutors.directExecutor());
        return future;
    }

    private static String bodyOf(PubsubMessage message) {
        try {
            return message.getData() == null ? null : message.getData().toStringUtf8();
        } catch (Exception e) {
            return null;
        }
    }
}
