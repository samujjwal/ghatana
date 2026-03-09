package com.ghatana.yappc.cli.commands;

/**
 * Output format options for CLI commands.
 * Consolidated enum to avoid duplication across command classes.
 *
 * @doc.type enum
 * @doc.purpose Output format options for CLI commands.
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum OutputFormat {
    CONSOLE,
    JSON,
    MARKDOWN,
    HTML
}
