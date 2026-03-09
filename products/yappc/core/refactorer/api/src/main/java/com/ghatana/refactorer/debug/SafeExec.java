package com.ghatana.refactorer.debug;

import com.ghatana.refactorer.shared.util.ProcessExec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Safe wrapper over ProcessExec that enforces an allowlist of commands. 
 * @doc.type class
 * @doc.purpose Handles safe exec operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class SafeExec {
    private volatile AllowlistPolicy policy;

    public SafeExec() {}

    public void setPolicy(AllowlistPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public ProcessExec.Result run(Path cwd, List<String> command, long timeoutMillis)
            throws java.io.IOException {
        Objects.requireNonNull(cwd, "cwd");
        Objects.requireNonNull(command, "command");
        if (policy == null) {
            throw new IllegalStateException("AllowlistPolicy not set for SafeExec");
        }
        if (!isAllowed(command)) {
            String joined = String.join(" ", command);
            throw new SecurityException("Command not allowed by policy: " + joined);
        }
        ProcessExec.Result result =
                ProcessExec.run(cwd, Duration.ofMillis(timeoutMillis), command, Map.of());
        if (result.exitCode() != 0) {
            throw new java.io.IOException(
                    result.err().isEmpty()
                            ? "Command failed with exit code " + result.exitCode()
                            : result.err());
        }
        return result;
    }

    private boolean isAllowed(List<String> command) {
        if (command.isEmpty()) return false;

        for (AllowlistPolicy.AllowedCommand ac : policy.commands()) {
            List<String> base = ac.cmd();
            if (base.isEmpty() || base.size() > command.size()) {
                continue;
            }

            // Check prefix match for base command (case-insensitive)
            boolean matches = true;
            for (int i = 0; i < base.size(); i++) {
                if (!base.get(i).equalsIgnoreCase(command.get(i))) {
                    matches = false;
                    break;
                }
            }

            if (matches) {
                // If no additional arguments are required, we have a match
                if (command.size() == base.size()) {
                    return true;
                }

                // If there are allowed args, check them, otherwise allow any args
                if (!ac.allowArgs().isEmpty()) {
                    List<String> rest = command.subList(base.size(), command.size());
                    return ac.allowArgs().containsAll(rest);
                }
                return true;
            }
        }
        return false;
    }
}
