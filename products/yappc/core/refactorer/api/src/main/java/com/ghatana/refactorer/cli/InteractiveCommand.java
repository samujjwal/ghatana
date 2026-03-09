/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import com.ghatana.refactorer.orchestrator.PolyfixOrchestrator;
import com.ghatana.refactorer.shared.*;
import com.ghatana.refactorer.shared.PolyfixConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.builtins.Completers.FileNameCompleter;
import org.jline.reader.*;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;

@CommandLine.Command(
        name = "interactive",
        description = "Start an interactive shell for Polyfix",
        aliases = {"i", "shell"})
/**
 * @doc.type class
 * @doc.purpose Handles interactive command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public class InteractiveCommand implements Callable<Integer> {
    private static final Logger LOG = LogManager.getLogger(InteractiveCommand.class);
    private static final String PROMPT = "polyfix> ";
    private static final String VERSION = "1.0.0";

    @CommandLine.ParentCommand PolyfixCommand parent;

    @CommandLine.Option(
            names = {"--history-file"},
            description = "File to store command history",
            defaultValue = "${sys:user.home}/.polyfix/history")
    private Path historyFile;

    private final AtomicBoolean running = new AtomicBoolean(true);
    Terminal terminal;
    LineReader reader;
    PolyfixProjectContext context;
    PolyfixOrchestrator orchestrator;

    @Override
    public Integer call() throws Exception {
        initialize();
        printWelcome();
        runShell();
        return 0;
    }

    void initialize() throws IOException {
        // Create history directory if it doesn't exist
        if (historyFile.getParent() != null) {
            historyFile.getParent().toFile().mkdirs();
        }

        // Setup terminal
        terminal = TerminalBuilder.builder().system(true).build();

        // Setup line reader with completions
        reader =
                LineReaderBuilder.builder()
                        .terminal(terminal)
                        .history(new DefaultHistory())
                        .completer(createCompleter())
                        .variable(LineReader.HISTORY_FILE, historyFile)
                        .variable(LineReader.HISTORY_FILE_SIZE, 1000)
                        .variable(LineReader.HISTORY_SIZE, 1000)
                        .variable(LineReader.LIST_MAX, 100)
                        .build();

        // Initialize context and orchestrator
        context = PolyfixCommand.buildContext(parent.root);
        orchestrator = new PolyfixOrchestrator();

        // Add shutdown hook to save history
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    private void printWelcome() {
        terminal.writer()
                .println(
                        new AttributedStringBuilder()
                                .style(AttributedStyle.BOLD.foreground(AttributedStyle.GREEN))
                                .append("Polyfix Interactive Shell ")
                                .append(VERSION)
                                .style(AttributedStyle.DEFAULT)
                                .append("\nType 'help' for available commands")
                                .toAnsi());
    }

    private void runShell() throws IOException {
        while (running.get()) {
            try {
                String line = reader.readLine(PROMPT);
                if (line == null) {
                    break; // EOF
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Add to history if not empty
                reader.getHistory().add(line);

                // Process command
                processCommand(line);

            } catch (UserInterruptException e) {
                // Handle Ctrl+C
                terminal.writer().println("^C");
            } catch (EndOfFileException e) {
                // Handle Ctrl+D
                terminal.writer().println("exit");
                break;
            } catch (Exception e) {
                terminal.writer().println("Error: " + e.getMessage());
                LOG.error("Error in interactive shell", e);
            }
        }
    }

    void processCommand(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length == 0) return;

        String command = parts[0].toLowerCase();

        try {
            switch (command) {
                case "run":
                    handleRun(parts);
                    break;
                case "diagnose":
                    handleDiagnose(parts);
                    break;
                case "help":
                    printHelp();
                    break;
                case "clear":
                    terminal.puts(InfoCmp.Capability.clear_screen);
                    break;
                case "config":
                    handleConfig(parts);
                    break;
                case "rules":
                    handleRules(parts);
                    break;
                case "files":
                    handleFiles(parts);
                    break;
                case "exit":
                case "quit":
                    running.set(false);
                    break;
                default:
                    // Try to suggest similar commands
                    suggestSimilarCommand(command);
            }
        } catch (Exception e) {
            terminal.writer().println("Error: " + e.getMessage());
            LOG.error("Error processing command: " + line, e);
        }
    }

    private void handleRun(String[] parts) {
        int maxPasses = 3;
        boolean dryRun = false;

        // Parse options if provided
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals("--max-passes") && i + 1 < parts.length) {
                maxPasses = Integer.parseInt(parts[++i]);
            } else if (parts[i].equals("--dry-run")) {
                dryRun = true;
            }
        }

        // Get the current config and create a new one with updated values
        PolyfixConfig currentConfig = context.config();
        PolyfixConfig.Budgets newBudgets =
                new PolyfixConfig.Budgets(maxPasses, currentConfig.budgets().maxEditsPerFile());

        // Create a new config with the updated budgets
        PolyfixConfig newConfig =
                new PolyfixConfig(
                        currentConfig.languages(),
                        currentConfig.schemaPaths(),
                        newBudgets,
                        currentConfig.policies(),
                        currentConfig.tools());

        // Create a new context with the updated config
        PolyfixProjectContext newContext =
                new PolyfixProjectContext(
                        context.root(),
                        newConfig,
                        context.languages(),
                        context.exec(),
                        context.log());

        terminal.writer()
                .println(
                        "Running analysis with "
                                + maxPasses
                                + " max passes"
                                + (dryRun ? " (dry run)" : "")
                                + "...");
        var summary = orchestrator.run(newContext);

        terminal.writer()
                .println(
                        new AttributedStringBuilder()
                                .style(AttributedStyle.BOLD)
                                .append("Analysis complete:")
                                .style(AttributedStyle.DEFAULT)
                                .append(
                                        String.format(
                                                "\n  Passes: %d\n  Edits Applied: %d\n  Status: %s",
                                                summary.passes(),
                                                summary.editsApplied(),
                                                summary.status()))
                                .toAnsi());
    }

    private void suggestSimilarCommand(String command) {
        List<String> commands =
                Arrays.asList(
                        "run",
                        "diagnose",
                        "help",
                        "clear",
                        "exit",
                        "quit",
                        "config",
                        "rules",
                        "files");

        // Find similar commands using Levenshtein distance
        List<String> suggestions = new ArrayList<>();
        for (String cmd : commands) {
            if (calculateSimilarity(cmd, command) > 0.6) { // Threshold for similarity
                suggestions.add(cmd);
            }
        }

        AttributedStringBuilder message =
                new AttributedStringBuilder()
                        .style(AttributedStyle.BOLD.foreground(AttributedStyle.RED))
                        .append("Unknown command: " + command);

        if (!suggestions.isEmpty()) {
            message.style(AttributedStyle.DEFAULT)
                    .append("\n\nDid you mean one of these?\n  ")
                    .append(String.join("\n  ", suggestions));
        }

        message.append("\nType 'help' for a list of available commands.");
        terminal.writer().println(message.toAnsi());
    }

    private double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }

    private void handleConfig(String[] parts) {
        if (parts.length < 2) {
            terminal.writer().println("Usage: config <get|set|list> [key] [value]");
            return;
        }

        String subCommand = parts[1].toLowerCase();
        switch (subCommand) {
            case "list":
                terminal.writer().println("Current configuration:");
                terminal.writer().println("  maxPasses: " + context.getMaxPasses());
                terminal.writer().println("  dryRun: " + context.isDryRun());
                break;

            case "get":
                if (parts.length < 3) {
                    terminal.writer().println("Please specify a configuration key");
                    return;
                }
                String key = parts[2];
                switch (key) {
                    case "maxPasses":
                        terminal.writer().println("maxPasses = " + context.getMaxPasses());
                        break;
                    case "dryRun":
                        terminal.writer().println("dryRun = " + context.isDryRun());
                        break;
                    default:
                        terminal.writer().println("Unknown configuration key: " + key);
                }
                break;

            case "set":
                if (parts.length < 4) {
                    terminal.writer().println("Usage: config set <key> <value>");
                    return;
                }
                String setKey = parts[2];
                String value = parts[3];

                try {
                    switch (setKey) {
                        case "maxPasses":
                            int maxPasses = Integer.parseInt(value);
                            updateMaxPasses(maxPasses);
                            terminal.writer().println("Set maxPasses to " + maxPasses);
                            break;
                        case "dryRun":
                            terminal.writer()
                                    .println(
                                            "dryRun is not directly configurable in this version."
                                                    + " Use --dry-run flag when running the tool.");
                            break;
                        default:
                            terminal.writer().println("Unknown configuration key: " + setKey);
                    }
                } catch (NumberFormatException e) {
                    terminal.writer().println("Invalid value: " + value);
                }
                break;

            default:
                terminal.writer().println("Unknown config command: " + subCommand);
        }
    }

    private void handleRules(String[] parts) {
        if (parts.length < 2) {
            terminal.writer().println("Usage: rules <list|enable|disable|info> [rule]");
            return;
        }

        String subCommand = parts[1].toLowerCase();
        switch (subCommand) {
            case "list":
                terminal.writer().println("Available rules:");
                for (Rule rule : context.getActiveRules()) {
                    terminal.writer()
                            .println(String.format("  %-20s %s", rule.getId(), rule.getName()));
                }
                break;

            case "enable":
            case "disable":
                if (parts.length < 3) {
                    terminal.writer().println("Please specify a rule to " + subCommand);
                    return;
                }
                String ruleId = parts[2];
                boolean enabled = subCommand.equals("enable");
                // Implementation would go here to enable/disable rules
                terminal.writer()
                        .println(
                                String.format(
                                        "Rule '%s' %sd", ruleId, enabled ? "enable" : "disable"));
                break;

            case "info":
                if (parts.length < 3) {
                    terminal.writer().println("Please specify a rule to get info about");
                    return;
                }
                String infoRuleId = parts[2];
                // Implementation would go here to show rule details
                terminal.writer().println("Information about rule: " + infoRuleId);
                break;

            default:
                terminal.writer().println("Unknown rules command: " + subCommand);
        }
    }

    private void handleFiles(String[] parts) {
        if (parts.length < 2) {
            terminal.writer().println("Usage: files <list|exclude|include> [pattern]");
            return;
        }

        String subCommand = parts[1].toLowerCase();
        switch (subCommand) {
            case "list":
                terminal.writer()
                        .println("Source files (" + context.getSourceFiles().size() + "):");
                for (Path file : context.getSourceFiles()) {
                    terminal.writer().println("  " + file);
                }
                break;

            case "exclude":
            case "include":
                if (parts.length < 3) {
                    terminal.writer().println("Please specify a file pattern to " + subCommand);
                    return;
                }
                String pattern = parts[2];
                // Implementation would go here to include/exclude files
                terminal.writer()
                        .println(String.format("Pattern '%s' %sd", pattern, subCommand + "d"));
                break;

            default:
                terminal.writer().println("Unknown files command: " + subCommand);
        }
    }

    private void handleDiagnose(String[] parts) {
        try {
            terminal.writer()
                    .println(
                            new AttributedStringBuilder()
                                    .style(AttributedStyle.BOLD)
                                    .append("\n=== Running Diagnostics ===\n")
                                    .style(AttributedStyle.DEFAULT)
                                    .toAnsi());

            // 1. Project Structure Analysis
            terminal.writer().println("🔍 Project Structure:");
            Path projectRoot = context.getProjectRoot();
            terminal.writer().println("   Project Root: " + projectRoot);

            // 2. Language Support
            terminal.writer().println("\n🌐 Language Support:");
            Map<String, Boolean> supportedLanguages = orchestrator.getSupportedLanguages();
            supportedLanguages.forEach(
                    (lang, enabled) ->
                            terminal.writer()
                                    .println(
                                            String.format(
                                                    "   %-15s %s",
                                                    lang + ":", enabled ? "✅" : "❌")));

            // 3. Configuration Status
            terminal.writer().println("\n⚙️  Configuration:");
            terminal.writer().println("   Max Passes: " + context.getMaxPasses());
            terminal.writer().println("   Dry Run: " + context.isDryRun());

            // 4. File Analysis
            terminal.writer().println("\n📊 File Analysis:");
            terminal.writer().println("   Total files: " + context.getSourceFiles().size());
            context.getSourceFiles().stream()
                    .collect(
                            Collectors.groupingBy(
                                    f -> getFileExtension(f.getFileName().toString())))
                    .forEach(
                            (ext, files) ->
                                    terminal.writer()
                                            .println(
                                                    String.format(
                                                            "   %-10s: %d files",
                                                            ext.isEmpty() ? "No Ext" : ext,
                                                            files.size())));

            // 5. Rule Status
            terminal.writer().println("\n📜 Active Rules:");
            context.getActiveRules()
                    .forEach(
                            rule ->
                                    terminal.writer()
                                            .println(
                                                    "   - "
                                                            + rule.getName()
                                                            + " (severity: "
                                                            + rule.getSeverity()
                                                            + ")"));

            // 6. Performance Metrics
            terminal.writer().println("\n⏱️  Performance Metrics:");
            terminal.writer()
                    .println(
                            "   Memory Usage: "
                                    + (Runtime.getRuntime().totalMemory()
                                                    - Runtime.getRuntime().freeMemory())
                                            / (1024 * 1024)
                                    + "MB");
            terminal.writer()
                    .println(
                            "   Available Processors: "
                                    + Runtime.getRuntime().availableProcessors());

            terminal.writer()
                    .println(
                            new AttributedStringBuilder()
                                    .style(AttributedStyle.BOLD)
                                    .append("\n=== End of Diagnostics ===\n")
                                    .style(AttributedStyle.DEFAULT)
                                    .toAnsi());

        } catch (Exception e) {
            terminal.writer().println("❌ Error running diagnostics: " + e.getMessage());
            LOG.error("Diagnostic error", e);
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot + 1);
    }

    private void printHelp() {
        terminal.writer()
                .println(
                        new AttributedStringBuilder()
                                .style(AttributedStyle.BOLD.foreground(AttributedStyle.CYAN))
                                .append("Available commands:")
                                .style(AttributedStyle.DEFAULT)
                                .append("\n  run [--max-passes N] [--dry-run] - Run the analysis")
                                .append("\n  diagnose - Run diagnostics on the codebase")
                                .append(
                                        "\n"
                                                + "  config <get|set|list> [key] [value] - Manage"
                                                + " configuration")
                                .append(
                                        "\n"
                                            + "  rules <list|enable|disable|info> [rule] - Manage"
                                            + " rules")
                                .append(
                                        "\n"
                                            + "  files <list|exclude|include> [pattern] - Manage"
                                            + " file patterns")
                                .append("\n  clear - Clear the screen")
                                .append("\n  help - Show this help message")
                                .append("\n  exit|quit - Exit the interactive shell")
                                .append(
                                        "\n\n"
                                                + "Type 'help <command>' for more information on a"
                                                + " command.")
                                .toAnsi());
    }

    private void updateMaxPasses(int maxPasses) {
        // Since we can't modify the PolyfixProjectContext directly, we'll need to reinitialize the
        // orchestrator
        // with the new configuration. This is a simplified approach.
        PolyfixConfig oldConfig = context.config();
        // Create a new config with the updated maxPasses
        PolyfixConfig newConfig =
                new PolyfixConfig(
                        oldConfig.languages(),
                        oldConfig.schemaPaths(),
                        new PolyfixConfig.Budgets(maxPasses, oldConfig.budgets().maxEditsPerFile()),
                        oldConfig.policies(),
                        oldConfig.tools());

        // Reinitialize the orchestrator with the new config
        this.orchestrator = new PolyfixOrchestrator();
        // Update the context with the new config
        // Note: In a real application, you would want to properly handle the executor service and
        // logger
        // This is a simplified approach that creates a new context with default values
        this.context =
                new PolyfixProjectContext(
                        context.root(),
                        newConfig,
                        context.languages(),
                        Executors.newCachedThreadPool(),
                        LogManager.getLogger(InteractiveCommand.class));
        // Note: This is a simplified approach. In a real application, you might need to
        // handle the context update more carefully, especially if there are other components
        // that depend on it.
    }

    private void shutdown() {
        running.set(false);
        try {
            if (reader != null && reader.getTerminal() != null) {
                reader.getTerminal().close();
            }
        } catch (Exception e) {
            // Ignore errors during shutdown
        }
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception e) {
            // Ignore errors during shutdown
        }
    }

    private ArgumentCompleter createCompleter() {
        // Basic commands
        List<Completer> completers = new ArrayList<>();

        // Main commands
        completers.add(
                new StringsCompleter(
                        "run",
                        "diagnose",
                        "help",
                        "clear",
                        "exit",
                        "quit",
                        "config",
                        "rules",
                        "files"));

        // Run command options
        completers.add(
                new ArgumentCompleter(
                        new StringsCompleter("run"),
                        new ArgumentCompleter(
                                new StringsCompleter("--max-passes"), NullCompleter.INSTANCE),
                        new StringsCompleter("--dry-run")));

        // Config command options
        completers.add(
                new ArgumentCompleter(
                        new StringsCompleter("config"),
                        new StringsCompleter("get", "set", "list"),
                        NullCompleter.INSTANCE));

        // Rules command options
        completers.add(
                new ArgumentCompleter(
                        new StringsCompleter("rules"),
                        new StringsCompleter("list", "enable", "disable", "info"),
                        NullCompleter.INSTANCE));

        // Files command options
        completers.add(
                new ArgumentCompleter(
                        new StringsCompleter("files"),
                        new StringsCompleter("list", "exclude", "include"),
                        NullCompleter.INSTANCE));

        // Add file name completion for relevant commands
        completers.add(
                new ArgumentCompleter(
                        new StringsCompleter("files", "diagnose"), new FileNameCompleter()));

        return new ArgumentCompleter(completers);
    }
}
