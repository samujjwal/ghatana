package com.ghatana.refactorer.shared.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility to execute external processes with timeout and capture of stdout/stderr as UTF-8.
 *
 * <p>This class is intentionally minimal and dependency-light to comply with workspace rules.
 
 * @doc.type class
 * @doc.purpose Handles process exec operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ProcessExec {
    private static final Logger log = LogManager.getLogger(ProcessExec.class);

    private ProcessExec() {}

    public static Result run(
            final Path cwd,
            final Duration timeout,
            final List<String> cmd,
            final Map<String, String> env) {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cmd, "cmd");

        final ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        if (env != null && !env.isEmpty()) {
            pb.environment().putAll(env);
        }

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream(1024);
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream(1024);
        int exit = -1;

        try {
            log.debug("Executing command: {} (cwd={})", String.join(" ", cmd), cwd);
            final Process p = pb.start();

            // Concurrent readers
            final CountDownLatch latch = new CountDownLatch(2);
            Thread tOut =
                    new Thread(
                            () -> copyToBuffer(p.getInputStream(), outBuf, latch),
                            "proc-out-gobbler");
            Thread tErr =
                    new Thread(
                            () -> copyToBuffer(p.getErrorStream(), errBuf, latch),
                            "proc-err-gobbler");
            tOut.setDaemon(true);
            tErr.setDaemon(true);
            tOut.start();
            tErr.start();

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                latch.await(2, TimeUnit.SECONDS);
                String out = outBuf.toString(UTF_8);
                String err = errBuf.toString(UTF_8);
                log.warn("Process timed out after {} ms: {}", timeout.toMillis(), cmd);
                return new Result(-1, out, err.isEmpty() ? "Timed out" : err);
            }

            exit = p.exitValue();
            latch.await(2, TimeUnit.SECONDS);
            String out = outBuf.toString(UTF_8);
            String err = errBuf.toString(UTF_8);
            log.debug("Command exit={} for: {}", exit, cmd);
            return new Result(exit, out, err);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            String out = outBuf.toString(UTF_8);
            String err = errBuf.toString(UTF_8);
            return new Result(exit, out, (err + "\nInterrupted: " + ie.getMessage()).trim());
        } catch (IOException ioe) {
            String out = outBuf.toString(UTF_8);
            String err = (errBuf.toString(UTF_8) + "\n" + ioe.getMessage()).trim();
            return new Result(exit, out, err);
        }
    }

    private static void copyToBuffer(
            InputStream in, ByteArrayOutputStream buf, CountDownLatch latch) {
        try (in;
                buf) {
            byte[] tmp = new byte[4096];
            int r;
            while ((r = in.read(tmp)) != -1) {
                buf.write(tmp, 0, r);
            }
        } catch (IOException ignored) {
            // swallow
        } finally {
            latch.countDown();
        }
    }

    public static record Result(int exitCode, String out, String err) {}
}
