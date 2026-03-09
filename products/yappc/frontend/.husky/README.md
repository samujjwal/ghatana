# Git Hooks

This directory contains Git hooks managed by Husky. These hooks help maintain code quality and consistency across the project.

## Available Hooks

### pre-commit
- Runs lint-staged to check and fix code style issues
- Performance metrics are logged to `.husky/perf-logs/`

### commit-msg
- Validates commit messages using commitlint
- Ensures consistent commit message format
- Performance metrics are logged to `.husky/perf-logs/`

### pre-push
- Runs type checking and tests before allowing a push
- Performance metrics are logged to `.husky/perf-logs/`

## Performance Monitoring

Performance data for each hook run is logged to `.husky/perf-logs/` with timestamps. This data includes:
- Execution time
- Memory usage
- CPU time

To enable debug output, set the `HUSKY_DEBUG` environment variable:

```bash
HUSKY_DEBUG=1 git commit -m "Your commit message"
```

## Versioning

The hooks are versioned using the `.husky/version` file. This helps ensure that all developers are using compatible hook versions.

## Dependencies

Each hook checks for required dependencies before running. If a required dependency is missing, the hook will fail with a descriptive error message.

## Adding a New Hook

1. Create a new file in the `.husky` directory
2. Make it executable (`chmod +x`)
3. Add the Husky shebang and performance tracking:

```bash
#!/bin/sh
. "$(dirname -- "$0")/_/husky.sh"
. "$(dirname -- "$0")/_/performance.sh"

# Your hook code here
```

## Troubleshooting

If a hook is failing:
1. Check the error message for missing dependencies
2. Run with `HUSKY_DEBUG=1` for more detailed output
3. Check the performance logs for timing information
