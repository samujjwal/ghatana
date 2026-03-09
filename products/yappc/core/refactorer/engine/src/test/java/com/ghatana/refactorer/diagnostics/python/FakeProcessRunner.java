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

    private final Map<CommandKey, Deque<CommandResponse>> scriptedResponses = new HashMap<>();

    FakeProcessRunner(PolyfixProjectContext context) {
        super(context);
    }

    static CommandResponse response(ProcessResult result) {
        return new CommandResponse(result, null);
    }

    static CommandResponse response(ProcessResult result, CommandEffect effect) {
        return new CommandResponse(result, effect);
    }

    void when(String command, List<String> args, CommandResponse... responses) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(responses, "responses");
        scriptedResponses
                .computeIfAbsent(
                        new CommandKey(command, List.copyOf(args)), k -> new ArrayDeque<>())
                .addAll(Arrays.asList(responses));
    }

    @Override
    public ProcessResult execute(
            String command, List<String> args, Path workingDir, boolean captureOutput) {
        return invoke(command, args, workingDir);
    }

    @Override
    public ProcessResult execute(
            Map<String, String> env,
            String command,
            List<String> args,
            Path workingDir,
            boolean captureOutput) {
        return invoke(command, args, workingDir);
    }

    @Override
    public String executeAndGetOutput(String[] command, Path workingDir)
            throws IOException, InterruptedException {
        ProcessResult result =
                invoke(
                        command[0],
                        Arrays.asList(Arrays.copyOfRange(command, 1, command.length)),
                        workingDir);
        if (!result.isSuccess()) {
            throw new IOException(
                    "Fake process returned non-zero exit code "
                            + result.exitCode()
                            + ": "
                            + result.error());
        }
        return result.output();
    }

    private ProcessResult invoke(String command, List<String> args, Path workingDir) {
        CommandKey key = new CommandKey(command, List.copyOf(args));
        Deque<CommandResponse> queue = scriptedResponses.get(key);
        if (queue == null || queue.isEmpty()) {
            Assertions.fail(
                    "No fake process response configured for command: "
                            + commandLine(command, args));
        }
        CommandResponse response = queue.removeFirst();
        if (response.effect() != null) {
            response.effect().accept(workingDir, fullCommand(command, args));
        }
        return response.result();
    }

    private static List<String> fullCommand(String command, List<String> args) {
        List<String> full = new ArrayList<>(args.size() + 1);
        full.add(command);
        full.addAll(args);
        return full;
    }

    private static String commandLine(String command, List<String> args) {
        return String.join(" ", fullCommand(command, args));
    }

    record CommandResponse(ProcessResult result, CommandEffect effect) {
        CommandResponse {
            Objects.requireNonNull(result, "result");
        }
    }

    record CommandKey(String command, List<String> args) {}

    @FunctionalInterface
    interface CommandEffect extends BiConsumer<Path, List<String>> {}
}
