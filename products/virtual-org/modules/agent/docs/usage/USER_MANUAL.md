# Virtual-Org – Java VirtualOrg-Agent Module – User Manual

## 1. Audience

This manual is for backend engineers implementing or using Java Virtual-Org agents.

## 2. Basic Usage

1. Add the `virtualorg-agent` module as a dependency.
2. Implement or extend agent interfaces for your domain-specific agents.
3. Wire agents into Virtual-Org organizations and workflows.
4. Ensure agents emit and react to events according to the framework’s event model.

## 3. Best Practices

- Keep product-specific logic in separate modules built on top of these agents.
- Prefer composition of capabilities over large monolithic agents.

This manual is self-contained and explains how to adopt the Java `virtualorg-agent` module.
