# platform:java:ds-cli

Canonical package: `com.ghatana.platform.dscli.*`

## Purpose

`platform:java:ds-cli` is the picocli-based command-line entry point for design-system token operations, validation, auditing, and governance workflows.

## Dependencies

- `platform:java:core` for shared platform utilities
- Picocli for command parsing and generation
- Jackson YAML/JSON support for token and configuration materialization

## Usage

Run the CLI with Gradle:

```bash
./gradlew :platform:java:ds-cli:run --args="--help"
```

Add the module as an application dependency only when embedding CLI behavior is explicitly required; most usage is through the packaged executable.

## Public API Surface

- CLI entry point `com.ghatana.platform.dscli.DesignSystemCLI`
- Command implementations under `com.ghatana.platform.dscli.*`
- Token validation and governance-oriented command flows