# DCMaar Bridge Protocol – User Manual

## 1. Audience

This manual is for TypeScript engineers integrating the desktop ↔ extension bridge.

## 2. Basic Usage

1. Add `@dcmaar/bridge-protocol` as a dependency.
2. Use its types in bridge handlers for compile-time safety.
3. Validate messages with the provided Zod schemas before processing.

## 3. Best Practices

- Never send ad-hoc payloads; always conform to declared contracts.

This manual is self-contained and explains how to use `@dcmaar/bridge-protocol`.
