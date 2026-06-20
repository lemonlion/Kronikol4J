package io.kronikol.core.tracking;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The central, thread-safe sink every extension funnels through (plan §1 Seam A). The single most
 * important interface in the system: any extension that can build a {@link RequestResponseLog} can
 * feed the core via {@link #log}, with no other coupling.
 *
 * <p>At end-of-run the runtime reads {@link #getAllLogs()} to emit this JVM's report fragment
 * (plan §5.3); under forked JVMs the fragments are merged.
 */
public final class RequestResponseLogger {

    private static final ConcurrentLinkedQueue<RequestResponseLog> LOGS = new ConcurrentLinkedQueue<>();
    private static volatile Integer maxContentLength; // null = unlimited

    private RequestResponseLogger() {
    }

    /** Records one interaction half. Applies {@link #setMaxContentLength content truncation} if set. */
    public static void log(RequestResponseLog log) {
        if (log == null) {
            return;
        }
        LOGS.add(truncateIfNeeded(log));
    }

    private static RequestResponseLog truncateIfNeeded(RequestResponseLog log) {
        Integer max = maxContentLength;
        if (max == null) {
            return log;
        }
        String content = log.content();
        if (content == null || content.length() <= max) {
            return log;
        }
        return log.toBuilder().content(content.substring(0, max)).build();
    }

    /** A snapshot of all logs recorded so far (insertion order). */
    public static List<RequestResponseLog> getAllLogs() {
        return new ArrayList<>(LOGS);
    }

    public static void clear() {
        LOGS.clear();
    }

    public static Integer getMaxContentLength() {
        return maxContentLength;
    }

    public static void setMaxContentLength(Integer value) {
        maxContentLength = value;
    }
}
