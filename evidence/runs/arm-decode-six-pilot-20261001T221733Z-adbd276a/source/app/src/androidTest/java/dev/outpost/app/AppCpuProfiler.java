package dev.outpost.app;

import android.os.SystemClock;


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Test-only CPU sampling of this app. No shell, device settings or stack capture. */
final class AppCpuProfiler implements AutoCloseable {
    private final Process process;
    private boolean closed;

    AppCpuProfiler(File directory, String name) throws Exception {
        if (!name.matches("profile-case[0-9]+")) throw new IllegalArgumentException("Invalid profile name");
        File data = new File(directory, name + ".data");
        File log = new File(directory, name + ".log");
        if (data.exists() || log.exists()) throw new IllegalStateException("Profile already exists");
        List<String> command = List.of("/system/bin/simpleperf", "record", "--in-app",
            "-p", Integer.toString(android.os.Process.myPid()), "-e", "cpu-clock:u",
            "--clockid", "monotonic", "-f", "100", "--duration", "180",
            "--size-limit", "32M", "--no-dump-kernel-symbols", "--stdio-controls-profiling",
            "-o", data.getAbsolutePath());
        process = new ProcessBuilder(command).redirectError(log).start();
        try {
            BufferedReader replies = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            long deadline = SystemClock.elapsedRealtime() + 10000;
            while (!replies.ready() && process.isAlive() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25);
            if (!replies.ready() || !"started".equals(replies.readLine())) throw new IllegalStateException("Profiler did not start; inspect " + log.getName());
        } catch (Exception error) {
            try { close(); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
    }

    @Override public void close() throws Exception {
        if (closed) return;
        closed = true;
        try {
            if (process.isAlive()) process.destroy();
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IllegalStateException("Profiler stop timed out");
            if (process.exitValue() != 0) throw new IllegalStateException("Profiler failed: " + process.exitValue());
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            process.getOutputStream().close();
            process.getInputStream().close();
        }
    }
}
