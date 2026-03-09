# Contributing to Ghatana

Thank you for your interest in contributing to Ghatana! This document outlines the process for contributing to our project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Release Process](#release-process)
- [Reporting Issues](#reporting-issues)

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## Getting Started

1. Fork the repository
2. Clone your fork locally
3. Set up the development environment (see below)
4. Create a new branch for your changes
5. Make your changes
6. Run tests and verify everything works
7. Submit a pull request

## Development Workflow

### Prerequisites

- Java 17 or later
- Gradle 7.6.1 or later
- Node.js 18 or later
- pnpm 8 or later
- Docker (for integration tests)

### Building the Project

```bash
# Build all Gradle modules
./gradlew build

# Build a specific module
./gradlew :module:path:build

# Build Node.js packages
pnpm install
pnpm build
```

### Code Style

We use Checkstyle, PMD, and SpotBugs to maintain code quality. Run these checks with:

```bash
./gradlew check
```

Format your code using Spotless:

```bash
./gradlew spotlessApply
```

## Testing

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :module:path:test

# Run a specific test class
./gradlew :module:path:test --tests com.example.TestClass

# Run tests with coverage
./gradlew jacocoTestReport
```

### Integration Tests

Some modules may include integration tests that require additional setup (like Docker containers). Check the module's README for specific instructions.

## Pull Request Process

1. Ensure any install or build dependencies are removed before the end of the layer when doing a build
2. Update the README.md with details of changes to the interface, including new environment variables, exposed ports, useful file locations, and container parameters
3. Increase the version numbers in any example files and the README.md to the new version that this Pull Request would represent
4. Your pull request will be reviewed by the maintainers
5. Once approved, it will be merged into the appropriate branch

## Release Process

Releases are managed through GitHub Actions. To create a new release:

1. Go to the Actions tab
2. Select the "Release" workflow
3. Click "Run workflow"
4. Enter the version number (e.g., 1.0.0)
5. Enter the module path (e.g., :products:agentic-event-processor:core)
6. Click "Run workflow"

The workflow will:

- Update the version in the module's build.gradle
- Create a git tag
- Publish to Maven Central
- Create a GitHub release

## Reporting Issues

When reporting issues, please include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Any relevant logs or screenshots
- Version information (Java, Gradle, Node.js, pnpm, OS, etc.)

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
