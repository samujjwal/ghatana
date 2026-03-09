# YAPPC Pack Authoring Guide

> **Version:** 1.0.0  
> **Last Updated:** 2025-12-06

## Table of Contents

1. [Introduction](#introduction)
2. [Pack Structure](#pack-structure)
3. [Pack Metadata](#pack-metadata)
4. [Template Syntax](#template-syntax)
5. [Variables](#variables)
6. [Advanced Features](#advanced-features)
7. [Testing Packs](#testing-packs)
8. [Best Practices](#best-practices)
9. [Examples](#examples)

---

## Introduction

YAPPC packs are self-contained project templates that generate complete, production-ready projects. This guide explains how to create custom packs for your organization or the community.

### Why Create a Pack?

- **Standardization:** Enforce consistent project structure across teams
- **Productivity:** Reduce boilerplate and setup time
- **Best Practices:** Embed security, testing, and observability patterns
- **Customization:** Tailor to your technology stack

---

## Pack Structure

```
packs/
└── my-custom-pack/
    ├── pack.json           # Pack metadata and configuration
    ├── templates/          # Handlebars template files
    │   ├── src/
    │   │   └── main.ts.hbs
    │   ├── package.json.hbs
    │   ├── Dockerfile.hbs
    │   └── README.md.hbs
    └── hooks/              # Optional: pre/post generation hooks
        ├── pre-generate.js
        └── post-generate.js
```

### Required Files

| File | Description |
|------|-------------|
| `pack.json` | Pack metadata, templates list, variables |
| `templates/*.hbs` | Handlebars template files |

### Optional Files

| File | Description |
|------|-------------|
| `hooks/pre-generate.js` | Run before generation |
| `hooks/post-generate.js` | Run after generation |
| `README.md` | Pack documentation |
| `examples/` | Example configurations |

---

## Pack Metadata

The `pack.json` file defines everything about your pack.

### Basic Structure

```json
{
  "name": "my-custom-pack",
  "version": "1.0.0",
  "description": "A custom project template",
  "category": "service",
  "language": "typescript",
  "runtime": "node",
  "buildTool": "pnpm",
  "templates": [...],
  "variables": {...},
  "dependencies": {...},
  "postCreate": [...],
  "devCommand": "npm run dev",
  "buildCommand": "npm run build",
  "testCommand": "npm test"
}
```

### Field Reference

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | ✅ | Unique pack identifier |
| `version` | string | ✅ | Semantic version |
| `description` | string | ✅ | Brief description |
| `category` | string | ✅ | Pack category (see below) |
| `language` | string | ✅ | Primary language |
| `runtime` | string | | Runtime environment |
| `buildTool` | string | | Primary build tool |
| `templates` | array | ✅ | Template file mappings |
| `variables` | object | | Configurable variables |
| `dependencies` | object | | Dependencies by build system |
| `postCreate` | array | | Post-creation commands |
| `devCommand` | string | | Development command |
| `buildCommand` | string | | Build command |
| `testCommand` | string | | Test command |

### Categories

| Category | Description |
|----------|-------------|
| `service` | Backend services/APIs |
| `frontend` | Frontend applications |
| `library` | Reusable libraries |
| `fullstack` | Full-stack applications |
| `middleware` | API gateways, proxies |
| `platform` | Desktop/mobile apps |
| `feature` | Feature add-on packs |

### Template Mappings

```json
{
  "templates": [
    {
      "source": "src/index.ts.hbs",
      "target": "src/index.ts"
    },
    {
      "source": "package.json.hbs",
      "target": "package.json"
    },
    {
      "source": "src/{{moduleName}}/main.ts.hbs",
      "target": "src/{{moduleName}}/main.ts"
    }
  ]
}
```

- `source`: Path relative to `templates/` directory
- `target`: Output path (supports variable interpolation)

---

## Template Syntax

Templates use [Handlebars](https://handlebarsjs.com/) syntax.

### Basic Interpolation

```handlebars
{
  "name": "{{projectName}}",
  "version": "{{version}}"
}
```

### Conditionals

```handlebars
{{#if enableDocker}}
FROM node:{{nodeVersion}}-alpine

WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .

EXPOSE {{port}}
CMD ["npm", "start"]
{{/if}}
```

### Loops

```handlebars
{
  "dependencies": {
    {{#each dependencies}}
    "{{@key}}": "{{this}}"{{#unless @last}},{{/unless}}
    {{/each}}
  }
}
```

### Comments

```handlebars
{{!-- This is a comment that won't appear in output --}}
```

### Raw Blocks (Escape Handlebars)

```handlebars
\{{rawMustache}}
{{{{raw}}}}
  This {{won't}} be processed
{{{{/raw}}}}
```

### Built-in Helpers

| Helper | Description | Example |
|--------|-------------|---------|
| `if` | Conditional | `{{#if enabled}}...{{/if}}` |
| `unless` | Negative conditional | `{{#unless disabled}}...{{/unless}}` |
| `each` | Loop over array/object | `{{#each items}}...{{/each}}` |
| `with` | Change context | `{{#with config}}...{{/with}}` |
| `eq` | Equality check | `{{#if (eq type "java")}}` |
| `ne` | Not equal | `{{#if (ne type "java")}}` |
| `and` | Logical AND | `{{#if (and a b)}}` |
| `or` | Logical OR | `{{#if (or a b)}}` |

### Custom Helpers

YAPPC includes additional helpers:

```handlebars
{{!-- String manipulation --}}
{{lowercase projectName}}    {{!-- myproject --}}
{{uppercase projectName}}    {{!-- MYPROJECT --}}
{{capitalize projectName}}   {{!-- Myproject --}}
{{camelCase projectName}}    {{!-- myProject --}}
{{pascalCase projectName}}   {{!-- MyProject --}}
{{kebabCase projectName}}    {{!-- my-project --}}
{{snakeCase projectName}}    {{!-- my_project --}}

{{!-- Path manipulation --}}
{{packagePath}}              {{!-- com/example/project --}}
{{modulePath}}               {{!-- Converts dots to slashes --}}

{{!-- Timestamps --}}
{{now}}                      {{!-- 2025-12-06T10:30:00Z --}}
{{year}}                     {{!-- 2025 --}}
```

---

## Variables

Variables allow users to customize generated projects.

### Variable Definition

```json
{
  "variables": {
    "projectName": {
      "type": "string",
      "description": "Project name",
      "required": true
    },
    "port": {
      "type": "number",
      "description": "Server port",
      "default": 3000
    },
    "enableDocker": {
      "type": "boolean",
      "description": "Include Docker configuration",
      "default": true
    },
    "database": {
      "type": "string",
      "description": "Database type",
      "enum": ["postgresql", "mysql", "sqlite"],
      "default": "postgresql"
    },
    "features": {
      "type": "array",
      "description": "Optional features",
      "items": {
        "type": "string",
        "enum": ["auth", "cache", "queue"]
      },
      "default": []
    }
  }
}
```

### Variable Types

| Type | Description | Example |
|------|-------------|---------|
| `string` | Text value | `"my-project"` |
| `number` | Numeric value | `8080` |
| `boolean` | True/false | `true` |
| `array` | List of values | `["auth", "cache"]` |

### Variable Properties

| Property | Type | Description |
|----------|------|-------------|
| `type` | string | Data type (required) |
| `description` | string | User-facing description |
| `required` | boolean | Must be provided |
| `default` | any | Default value if not provided |
| `enum` | array | Allowed values |
| `pattern` | string | Regex validation (strings) |
| `min`/`max` | number | Range validation (numbers) |
| `items` | object | Array item schema |

### Computed Variables

Some variables are automatically computed:

| Variable | Description |
|----------|-------------|
| `packagePath` | Java package as path (dots → slashes) |
| `packageName` | Normalized package name |
| `moduleName` | Module/package identifier |

---

## Advanced Features

### Conditional Templates

Include or exclude entire files based on variables:

```json
{
  "templates": [
    {
      "source": "Dockerfile.hbs",
      "target": "Dockerfile",
      "condition": "enableDocker"
    },
    {
      "source": "docker-compose.yml.hbs",
      "target": "docker-compose.yml",
      "condition": "enableDocker && enableDatabase"
    }
  ]
}
```

### Multi-Language Feature Packs

Feature packs support multiple languages:

```json
{
  "name": "feature-auth",
  "category": "feature",
  "templates": {
    "java": [
      { "source": "java/SecurityConfig.java.hbs", "target": "..." }
    ],
    "typescript": [
      { "source": "typescript/auth.ts.hbs", "target": "..." }
    ],
    "rust": [
      { "source": "rust/auth.rs.hbs", "target": "..." }
    ],
    "go": [
      { "source": "go/auth.go.hbs", "target": "..." }
    ]
  }
}
```

### Dependencies

Declare dependencies that should be added:

```json
{
  "dependencies": {
    "java-gradle": {
      "implementation": [
        "org.springframework.boot:spring-boot-starter-web"
      ],
      "testImplementation": [
        "org.springframework.boot:spring-boot-starter-test"
      ]
    },
    "typescript-pnpm": {
      "dependencies": {
        "fastify": "^4.28.0"
      },
      "devDependencies": {
        "@types/node": "^22.0.0"
      }
    },
    "rust-cargo": {
      "dependencies": {
        "axum": "0.7",
        "tokio": { "version": "1", "features": ["full"] }
      }
    },
    "go-modules": {
      "require": [
        "github.com/go-chi/chi/v5 v5.1.0"
      ]
    }
  }
}
```

### Post-Create Hooks

Commands to run after project creation:

```json
{
  "postCreate": [
    "pnpm install",
    "pnpm prisma generate",
    "git init"
  ]
}
```

### Custom Hooks

Create JavaScript hooks for complex logic:

**hooks/post-generate.js:**
```javascript
module.exports = async function(context) {
  const { projectPath, variables, logger } = context;
  
  // Custom post-generation logic
  if (variables.initGit) {
    await exec('git init', { cwd: projectPath });
    logger.info('Initialized git repository');
  }
};
```

---

## Testing Packs

### Manual Testing

```bash
# Generate in dry-run mode
yappc create --pack ./packs/my-custom-pack --name test --dry-run

# Generate to temp directory
yappc create --pack ./packs/my-custom-pack --name test --output /tmp/test-output

# Verify generated project
cd /tmp/test-output
npm install  # or appropriate command
npm test
```

### Automated Testing

Create test specifications in `tests/`:

```yaml
# tests/my-pack.test.yaml
pack: my-custom-pack
variables:
  projectName: test-project
  port: 8080
  enableDocker: true

assertions:
  - file: package.json
    contains:
      - '"name": "test-project"'
  - file: Dockerfile
    exists: true
  - file: src/index.ts
    matches: 'const PORT = 8080'
```

Run tests:

```bash
yappc test --pack my-custom-pack
```

---

## Best Practices

### 1. Use Semantic Naming

```
✅ ts-node-fastify
✅ java-service-spring-gradle
✅ fullstack-go-react

❌ my-pack
❌ template1
❌ new_project
```

### 2. Provide Sensible Defaults

```json
{
  "variables": {
    "port": {
      "type": "number",
      "default": 3000  // Sensible default
    }
  }
}
```

### 3. Include Documentation

Every pack should have:
- Clear description in `pack.json`
- Generated README.md explaining the project
- Comments in complex templates

### 4. Make Templates Idiomatic

```handlebars
{{!-- Java: Use package naming conventions --}}
package {{packageName}};

{{!-- Go: Use module naming conventions --}}
module {{moduleName}}
```

### 5. Include Essential Files

Every project pack should generate:
- README.md
- .gitignore
- Dockerfile (optional but recommended)
- Basic test structure
- CI/CD configuration

### 6. Validate Variables

```json
{
  "variables": {
    "packageName": {
      "type": "string",
      "pattern": "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$",
      "description": "Java package name (e.g., com.example.myapp)"
    }
  }
}
```

### 7. Keep Templates DRY

Use partials for repeated content:

```handlebars
{{!-- templates/partials/license-header.hbs --}}
/**
 * Copyright (c) {{year}} {{author}}
 * Licensed under {{license}}
 */

{{!-- templates/src/main.ts.hbs --}}
{{> license-header}}
import { App } from './app';
...
```

---

## Examples

### Minimal Service Pack

```json
{
  "name": "minimal-node-service",
  "version": "1.0.0",
  "description": "Minimal Node.js service",
  "category": "service",
  "language": "typescript",
  "buildTool": "pnpm",
  "templates": [
    { "source": "package.json.hbs", "target": "package.json" },
    { "source": "src/index.ts.hbs", "target": "src/index.ts" },
    { "source": "tsconfig.json.hbs", "target": "tsconfig.json" }
  ],
  "variables": {
    "projectName": { "type": "string", "required": true },
    "port": { "type": "number", "default": 3000 }
  },
  "postCreate": ["pnpm install"],
  "devCommand": "pnpm dev",
  "buildCommand": "pnpm build"
}
```

### Full Feature Pack

See `packs/feature-database/pack.json` for a complete feature pack example with multi-language support.

### Full-Stack Pack

See `packs/fullstack-java-react/pack.json` for a full-stack monorepo pack example.

---

## Resources

- [Handlebars Documentation](https://handlebarsjs.com/guide/)
- [YAPPC User Guide](./USER_GUIDE.md)
- [Existing Packs](../packs/) - Reference implementations
- [GitHub Issues](https://github.com/ghatana/yappc/issues) - Questions and feedback
