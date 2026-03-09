package com.ghatana.refactorer.orchestrator.debug;

import com.ghatana.refactorer.debug.DebugController;
import com.ghatana.refactorer.debug.DebugControllerFactory;
import com.ghatana.refactorer.debug.StackTraceParser;
import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entry-point utility for running a safe debug session from the orchestrator. 
 * @doc.type class
 * @doc.purpose Handles debug entry operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class DebugEntry {
    private DebugEntry() {}

    /**
     * Runs a debug command in the given project root and parses any stack traces from its output.
     * This is a minimal local implementation to avoid cross-module API drift.
     *
     * @param ctx Project context (used for repo root)
     * @param command Full command (executable + args)
     * @param timeout Timeout for the process
     * @return ParseResult with parsed frames and raw output
     */
    /**
     * Runs a command in the specified directory and parses any stack traces from its output.
     *
     * @param ctx Project context (used for working directory)
     * @param command Command to execute (executable + arguments)
     * @param timeout Maximum time to wait for command completion
     * @return ParseResult with parsed frames and raw output
     * @throws NullPointerException if command is null
     * @throws IndexOutOfBoundsException if command is empty
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the current thread is interrupted
     * @throws RuntimeException if the command times out or fails to execute
     */
    public static DebugController.ParseResult run(
            PolyfixProjectContext ctx, List<String> command, Duration timeout)
            throws IOException, InterruptedException {
        // Validate command parameter
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        if (command.isEmpty()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        Path root = ctx != null ? ctx.root() : Path.of(".");
        DebugController controller = DebugControllerFactory.create();
        Process proc = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);

            proc = pb.start();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            // Create a final reference to the process for the lambda
            final Process process = proc;

            // Read output in a separate thread to prevent deadlocks
            Thread outputReader =
                    new Thread(
                            () -> {
                                try (InputStream in = process.getInputStream()) {
                                    in.transferTo(outputStream);
                                } catch (IOException e) {
                                    // Ignore if the stream was closed
                                }
                            });
            outputReader.setDaemon(true);
            outputReader.start();

            // Wait for completion with timeout
            long timeoutMs = timeout != null ? timeout.toMillis() : 60000L;
            boolean finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                proc.destroyForcibly().waitFor(1, TimeUnit.SECONDS);
                throw new RuntimeException("Command timed out after " + timeout);
            }

            // Wait for output reader to finish (with a reasonable timeout)
            outputReader.join(1000);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            return controller.parse(output);

        } finally {
            if (proc != null) {
                // Ensure all streams are closed
                try {
                    proc.getInputStream().close();
                } catch (IOException ignored) {
                }
                try {
                    proc.getErrorStream().close();
                } catch (IOException ignored) {
                }
                try {
                    proc.getOutputStream().close();
                } catch (IOException ignored) {
                }

                // Ensure process is terminated
                if (proc.isAlive()) {
                    proc.destroyForcibly();
                }
            }
        }
    }

    /**
 * Convenience helper returning only frames. */
    public static List<StackTraceParser.TraceFrame> frames(
            PolyfixProjectContext ctx, List<String> command, Duration timeout) throws Exception {
        return run(ctx, command, timeout).frames();
    }
}
