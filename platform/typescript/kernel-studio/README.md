# @ghatana/kernel-studio

Kernel Studio — UI for managing product lifecycle, deployments, and conformance.

## Purpose

A React application shell providing a visual interface for the Ghatana kernel lifecycle system. Enables platform operators to inspect product lifecycle status, trigger phase runs, review gate results, manage deployments, and audit conformance.

## Key Concepts

- **App** — root application component for Kernel Studio
- **Pages** — views for lifecycle status, gate results, deployment history, and conformance reports

## Usage

Kernel Studio is deployed as a standalone web application. It is not a library; it does not export a public API for consumption by other packages.

To start locally:

```bash
cd platform/typescript/kernel-studio
pnpm dev
```

## Directory Structure

```
src/
  App.tsx     # Root application component
  index.ts    # Entry point
  pages/      # Page components (lifecycle, deployment, conformance)
```

## Ownership

Platform Kernel Engineering / UI. See [platform/typescript/LIBRARY_GOVERNANCE.md](../LIBRARY_GOVERNANCE.md).
