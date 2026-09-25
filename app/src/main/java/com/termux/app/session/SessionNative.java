package com.termux.app.session;

/** Loaded explicitly because a Shizuku UserService does not have an app's native search path. */
@androidx.annotation.Keep
final class SessionNative {
    static synchronized void load(String directory) { System.load(directory + "/libaether-session.so"); }
    static int[] start(String packageName, String script, String[] environment,
                       int rows, int columns, int cellWidth, int cellHeight) {
        byte[][] encoded = new byte[environment.length][];
        for (int i = 0; i < environment.length; i++)
            encoded[i] = environment[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return startUtf8(packageName.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            script.getBytes(java.nio.charset.StandardCharsets.UTF_8), encoded, rows, columns, cellWidth, cellHeight);
    }
    private static native int[] startUtf8(byte[] packageName, byte[] script, byte[][] environment,
                                         int rows, int columns, int cellWidth, int cellHeight);
    /** Wait without reaping: the PID stays reserved until finish(), serialized with stop(). */
    static native void awaitExit(int pid);
    static native int finish(int pid);
    static native int signal(int pid, boolean terminate);
}
