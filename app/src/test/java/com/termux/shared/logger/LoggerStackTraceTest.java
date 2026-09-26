package com.termux.shared.logger;

import com.termux.shared.errors.Error;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class LoggerStackTraceTest {
    @Test public void preservesOrdinaryCausesAndSuppressedExceptions() {
        IOException failure = new IOException("cleanup failed", new IOException("cause"));
        failure.addSuppressed(new IOException("permission denied"));
        StringWriter expected = new StringWriter();
        failure.printStackTrace(new PrintWriter(expected));
        assertEquals(expected.toString(), Logger.getStackTraceString(failure));
    }

    @Test public void boundsLargeSuppressedExceptionTrees() {
        String trace = Logger.getStackTraceString(cleanupFailure());
        assertTrue(trace.startsWith("java.io.IOException: cleanup failed"));
        assertTrue(trace.contains("Suppressed: java.io.IOException: permission denied: 0"));
        assertTrue(trace.endsWith("[Stack trace truncated]\n"));
        assertTrue(trace.length() < Logger.MAX_STACK_TRACE_CHARACTERS + 100);
    }

    @Test public void boundsOversizedExceptionMessagesBeforeCopyingThem() {
        char[] message = new char[Logger.MAX_STACK_TRACE_CHARACTERS * 4];
        Arrays.fill(message, 'x');
        String trace = Logger.getStackTraceString(new IOException(new String(message)));
        assertTrue(trace.endsWith("[Stack trace truncated]\n"));
        assertTrue(trace.length() < Logger.MAX_STACK_TRACE_CHARACTERS + 100);
    }

    @Test public void stopsRenderingAfterTheLimitIsReached() {
        Throwable failure = new Throwable() {
            @Override public void printStackTrace(PrintWriter writer) {
                char[] chunk = new char[1024];
                Arrays.fill(chunk, 'x');
                for (int i = 0; i <= Logger.MAX_STACK_TRACE_CHARACTERS / chunk.length; i++)
                    writer.write(chunk);
                fail("Rendering must stop rather than discard an unbounded stream");
            }
        };
        assertTrue(Logger.getStackTraceString(failure).endsWith("[Stack trace truncated]\n"));
    }

    @Test public void boundsTheNumberOfSeparatelyRenderedExceptions() {
        String[] traces = Logger.getStackTracesStringArray(
            Collections.nCopies(1000000, new IOException("permission denied")));
        assertEquals(Logger.MAX_STACK_TRACES + 1, traces.length);
        assertEquals("[Additional stack traces omitted]", traces[Logger.MAX_STACK_TRACES]);
    }

    @Test public void cleanupErrorLogsAndCrashReportsRemainBounded() {
        Error failure = new Error("Failed to clear termux $TMPDIR", cleanupFailure());
        String log = failure.toString();
        String markdown = failure.getErrorMarkdownString();
        assertTrue(log.contains("Failed to clear termux $TMPDIR"));
        assertTrue(log.contains("[Stack trace truncated]"));
        assertTrue(log.length() < Logger.MAX_STACK_TRACE_CHARACTERS + 1000);
        assertTrue(markdown.contains("[Stack trace truncated]"));
        assertTrue(markdown.length() < Logger.MAX_STACK_TRACE_CHARACTERS + 1000);
    }

    @Test public void preservesNullAndEmptyTraceBehavior() {
        assertNull(Logger.getStackTraceString(null));
        assertNull(Logger.getStackTracesStringArray((java.util.List<Throwable>) null));
        assertArrayEquals(new String[0], Logger.getStackTracesStringArray(Collections.emptyList()));
        assertArrayEquals(new String[]{null}, Logger.getStackTracesStringArray((Throwable) null));
    }

    private IOException cleanupFailure() {
        IOException failure = new IOException("cleanup failed");
        for (int i = 0; i < 2000; i++)
            failure.addSuppressed(new IOException("permission denied: " + i));
        return failure;
    }
}
