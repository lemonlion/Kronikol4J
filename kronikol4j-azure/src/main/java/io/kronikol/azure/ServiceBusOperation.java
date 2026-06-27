package io.kronikol.azure;

/**
 * Classified Azure Service Bus operation types. Java port of the .NET {@code ServiceBusOperation} enum. Each
 * constant carries the .NET {@code ToString()} (PascalCase) display name used as the Raw diagram label.
 */
public enum ServiceBusOperation {

    SEND("Send"),
    SEND_BATCH("SendBatch"),
    SCHEDULE("Schedule"),
    CANCEL_SCHEDULE("CancelSchedule"),
    RECEIVE("Receive"),
    RECEIVE_BATCH("ReceiveBatch"),
    PEEK("Peek"),
    COMPLETE("Complete"),
    ABANDON("Abandon"),
    DEAD_LETTER("DeadLetter"),
    DEFER("Defer"),
    RENEW_MESSAGE_LOCK("RenewMessageLock"),
    RENEW_SESSION_LOCK("RenewSessionLock"),
    GET_SESSION_STATE("GetSessionState"),
    SET_SESSION_STATE("SetSessionState"),
    START_PROCESSING("StartProcessing"),
    STOP_PROCESSING("StopProcessing"),
    OTHER("Other");

    private final String displayName;

    ServiceBusOperation(String displayName) {
        this.displayName = displayName;
    }

    /** The .NET {@code ToString()} (PascalCase) form used as the Raw diagram label. */
    public String displayName() {
        return displayName;
    }
}
