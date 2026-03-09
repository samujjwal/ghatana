package com.ghatana.refactorer.debug;

import java.util.List;
import java.util.Objects;

/**
 * Immutable allowlist policy for SafeExec. Backed by config/debug/safe-commands.allowlist.json 
 * @doc.type class
 * @doc.purpose Handles allowlist policy operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class AllowlistPolicy {
    private final List<AllowedCommand> commands;

    public AllowlistPolicy(List<AllowedCommand> commands) {
        this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));
    }

    public List<AllowedCommand> commands() {
        return commands;
    }

    public record AllowedCommand(String name, List<String> cmd, List<String> allowArgs) {
        public AllowedCommand {
            cmd = cmd == null ? List.of() : List.copyOf(cmd);
            allowArgs = allowArgs == null ? List.of() : List.copyOf(allowArgs);
        }
    }
}
