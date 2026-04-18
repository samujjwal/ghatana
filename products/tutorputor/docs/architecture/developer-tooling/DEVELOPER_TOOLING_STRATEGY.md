# Developer Tooling Strategy

**Last Updated:** 2026-04-17  
**Version:** 1.0  
**Status:** DEFERRED - Not required at current scale

---

## Overview

This document outlines the developer tooling strategy for TutorPutor. Enhanced developer tooling is currently deferred as ESLint, Prettier, and TypeScript provide sufficient code quality coverage. This strategy will be implemented when team size and code governance requirements increase.

---

## Current State

### Code Quality Tools
**Status:** IMPLEMENTED

- ESLint with TypeScript support
- Prettier for code formatting
- Strict TypeScript configuration
- Custom architecture rules
- Dependency policy enforcement

**Location:** `eslint.config.js`, `tsconfig.base.json`

---

## Developer Tooling Evaluation

### When to Enhance Developer Tooling

Enhanced developer tooling should be implemented when:
1. Team size increases significantly (>10 developers)
2. Code quality requires formal governance
3. Dependency management becomes burdensome
4. Pre-commit quality gates are needed
5. Security vulnerability scanning is required

### Current Coverage
- Linting: ESLint configured
- Formatting: Prettier configured
- Type checking: TypeScript strict mode
- Architecture rules: Custom ESLint rules

**Conclusion:** Enhanced developer tooling not required at current scale.

---

## Code Quality Gates

### SonarQube Integration

**Recommended:** SonarQube Community Edition

**Configuration:**
```yaml
# .github/workflows/sonarqube.yml
name: SonarQube Scan

on:
  pull_request:
    types: [opened, synchronize, reopened]

jobs:
  sonarqube:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: SonarQube Scan
        uses: sonarsource/sonarqube-scan-action@master
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
```

**Quality Gates:**
- Code coverage >80%
- No critical vulnerabilities
- No blocker issues
- Code smell density <5%
- Duplicated lines <3%

---

## Dependency Management Automation

### Renovate Bot

**Recommended:** Renovate Bot

**Configuration:**
```json
// renovate.json
{
  "extends": [
    "config:base"
  ],
  "schedule": ["every weekend"],
  "automerge": false,
  "major": {
    "automerge": false
  },
  "minor": {
    "automerge": true
  },
  "patch": {
    "automerge": true
  },
  "lockFileMaintenance": {
    "enabled": true,
    "schedule": ["before 3am on Monday"]
  }
}
```

**Benefits:**
- Automated dependency updates
- Security vulnerability scanning
- Semantic versioning awareness
- Grouped updates by package

---

### Dependabot

**Alternative:** GitHub Dependabot

**Configuration:**
```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "npm"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 10
```

---

## Pre-commit Hooks

### Husky + lint-staged

**Installation:**
```bash
npm install --save-dev husky lint-staged
npx husky install
npx husky add .husky/pre-commit "npx lint-staged"
```

**Configuration:**
```json
// package.json
{
  "lint-staged": {
    "*.{ts,tsx}": [
      "eslint --fix",
      "prettier --write"
    ],
    "*.{json,md,yml,yaml}": [
      "prettier --write"
    ]
  }
}
```

**Benefits:**
- Automatic linting before commit
- Automatic formatting before commit
- Consistent code style across team
- Faster code reviews

---

## Automated Refactoring Tools

### TypeScript ESLint Auto-fix

**Implementation:**
```json
// .eslintrc.json
{
  "rules": {
    "@typescript-eslint/no-unused-vars": "error",
    "@typescript-eslint/explicit-function-return-type": "warn",
    "@typescript-eslint/no-explicit-any": "error"
  }
}
```

**Usage:**
```bash
npx eslint --fix src/
```

---

## Implementation Steps

1. **Phase 1: Pre-commit Hooks**
   - Install Husky
   - Configure lint-staged
   - Set up pre-commit hook
   - Test hook functionality

2. **Phase 2: Dependency Automation**
   - Install Renovate or Dependabot
   - Configure update schedules
   - Set up automerge rules
   - Configure security scanning

3. **Phase 3: Code Quality Gates**
   - Set up SonarQube server
   - Configure quality rules
   - Integrate with CI/CD
   - Set up quality gates

4. **Phase 4: Documentation**
   - Document tooling setup
   - Document quality gate rules
   - Document dependency update process
   - Create developer onboarding guide

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
