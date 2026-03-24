# Scaffolding System Documentation

## Overview

YAPPC's scaffolding system generates project structures and boilerplate code.

## Architecture

```
scaffold/
├── api/        # Public API
├── engine/     # Core orchestration
├── generators/ # Language-specific generators
└── templates/  # Template management
```

## Features

- Multi-framework support (React, Java, Python)
- Template-based generation
- AI-powered customization
- Incremental scaffolding

## Usage

```java
ScaffoldEngine engine = new ScaffoldEngine();

ScaffoldRequest request = ScaffoldRequest.builder()
    .framework("react")
    .template("webapp")
    .projectName("my-app")
    .build();

Promise<ScaffoldResult> result = engine.scaffold(request);
```

## Templates

Templates are defined in YAML:

```yaml
name: react-webapp
framework: react
files:
  - path: src/App.tsx
    template: templates/react/App.tsx.hbs
  - path: package.json
    template: templates/react/package.json.hbs
```

---

See [Scaffold API](../api/scaffold-api.md) for complete API reference.
