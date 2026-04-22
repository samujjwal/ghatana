package com.ghatana.refactorer.diagnostics.python;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import com.ghatana.refactorer.shared.process.ProcessResult;
import com.ghatana.refactorer.shared.process.ProcessRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Assertions;

/**

 * @doc.type class

 * @doc.purpose Handles fake process runner operations

 * @doc.layer core

 * @doc.pattern ValueObject

 */

final class FakeProcessRunner extends ProcessRunner {

    private final Map<CommandKey, Deque<CommandResponse>> scriptedResponses = new HashMap<>(); // GH-90000

    FakeProcessRunner(PolyfixProjectContext context) { // GH-90000
        super(context); // GH-90000
    }

    static CommandResponse response(ProcessResult result) { // GH-90000
        return new CommandResponse(result, null); // GH-90000
    }

    static CommandResponse response(ProcessResult result, CommandEffect effect) { // GH-90000
        return new CommandResponse(result, effect); // GH-90000
    }

    void when(String command, List<String> args, CommandResponse... responses) { // GH-90000
        Objects.requireNonNull(command, "command"); // GH-90000
        Objects.requireNonNull(args, "args"); // GH-90000
        Objects.requireNonNull(responses, "responses"); // GH-90000
        scriptedResponses
                .computeIfAbsent( // GH-90000
                        new CommandKey(command, List.copyOf(args)), k -> new ArrayDeque<>()) // GH-90000
                .addAll(Arrays.asList(responses)); // GH-90000
    }

    @Override
    public ProcessResult execute( // GH-90000
            String command, List<String> args, Path workingDir, boolean captureOutput) {
        return invoke(command, args, workingDir); // GH-90000
    }

    @Override
    public ProcessResult execute( // GH-90000
            Map<String, String> env,
            String command,
            List<String> args,
            Path workingDir,
            boolean captureOutput) {
        return invoke(command, args, workingDir); // GH-90000
    }

    @Override
    public String executeAndGetOutput(String[] command, Path workingDir) // GH-90000
            throws IOException, InterruptedException {
        ProcessResult result =
                invoke( // GH-90000
                        command[0],
                        Arrays.asList(Arrays.copyOfRange(command, 1, command.length)), // GH-90000
                        workingDir);
        if (!result.isSuccess()) { // GH-90000
            throw new IOException( // GH-90000
                    "Fake process returned non-zero exit code "
                            + result.exitCode() // GH-90000
                            + ": "
                            + result.error()); // GH-90000
        }
        return result.output(); // GH-90000
    }

    private ProcessResult invoke(String command, List<String> args, Path workingDir) { // GH-90000
        CommandKey key = new CommandKey(command, List.copyOf(args)); // GH-90000
        Deque<CommandResponse> queue = scriptedResponses.get(key); // GH-90000
        if (queue == null || queue.isEmpty()) { // GH-90000
            Assertions.fail( // GH-90000
                    "No fake process response configured for command: "
                            + commandLine(command, args)); // GH-90000
        }
        CommandResponse response = queue.removeFirst(); // GH-90000
        if (response.effect() != null) { // GH-90000
            response.effect().accept(workingDir, fullCommand(command, args)); // GH-90000
        }
        return response.result(); // GH-90000
    }

    private static List<String> fullCommand(String command, List<String> args) { // GH-90000
        List<String> full = new ArrayList<>(args.size() + 1); // GH-90000
        full.add(command); // GH-90000
        full.addAll(args); // GH-90000
        return full;
    }

    private static String commandLine(String command, List<String> args) { // GH-90000
        return String.join(" ", fullCommand(command, args)); // GH-90000
    }

    record CommandResponse(ProcessResult result, CommandEffect effect) { // GH-90000
        CommandResponse {
            Objects.requireNonNull(result, "result"); // GH-90000
        }
    }

    record CommandKey(String command, List<String> args) {} // GH-90000

    @FunctionalInterface
    interface CommandEffect extends BiConsumer<Path, List<String>> {}
}
