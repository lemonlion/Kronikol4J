package io.kronikol.mongodb;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;

/**
 * A MongoDB driver {@link CommandListener} that auto-captures each command as a tracked request/response
 * pair — the Java analog of the .NET {@code MongoDbTrackingSubscriber}. Register it on the client:
 * {@code MongoClientSettings.builder().addCommandListener(new KronikolMongoCommandListener(options))}.
 *
 * <p>This is a thin adapter mapping driver events to {@link MongoInteractionRecorder}, which holds the
 * two-phase logic and is unit-testable without constructing driver events.
 */
public final class KronikolMongoCommandListener implements CommandListener {

    private final MongoInteractionRecorder recorder;

    public KronikolMongoCommandListener(MongoDbTrackingOptions options) {
        this.recorder = new MongoInteractionRecorder(options);
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        recorder.logStarted(event.getRequestId(), event.getCommandName(),
            event.getDatabaseName(), event.getCommand());
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        recorder.logSucceeded(event.getRequestId(), event.getResponse());
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        recorder.logFailed(event.getRequestId(), event.getThrowable());
    }
}
