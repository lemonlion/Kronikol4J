package io.kronikol.gcp;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;

/**
 * A Pub/Sub {@link MessageReceiver} decorator that auto-captures each received message as a tracked
 * interaction via {@link PubSubInteractionRecorder}, then forwards to the delegate — the Java analog of the
 * .NET {@code TrackingSubscriberClient}'s receive path. Pass it to {@code Subscriber.newBuilder(subscription,
 * new TrackingMessageReceiver(yourReceiver, subscription, options))}. The Pub/Sub SDK is {@code compileOnly}.
 */
public final class TrackingMessageReceiver implements MessageReceiver {

    private final MessageReceiver delegate;
    private final String subscriptionName;
    private final PubSubInteractionRecorder recorder;

    public TrackingMessageReceiver(MessageReceiver delegate, String subscriptionName,
                                   PubSubTrackerOptions options) {
        this.delegate = delegate;
        this.subscriptionName = subscriptionName;
        this.recorder = new PubSubInteractionRecorder(options);
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
        PubSubOperationInfo op = PubSubOperationClassifier.classify("Receive", null, subscriptionName, 1);
        recorder.record(op, bodyOf(message), null);
        delegate.receiveMessage(message, consumer);
    }

    private static String bodyOf(PubsubMessage message) {
        try {
            return message.getData() == null ? null : message.getData().toStringUtf8();
        } catch (Exception e) {
            return null;
        }
    }
}
