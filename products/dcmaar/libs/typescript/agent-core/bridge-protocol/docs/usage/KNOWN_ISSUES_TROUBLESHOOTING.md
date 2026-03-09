# DCMaar Bridge Protocol – Known Issues & Troubleshooting

## 1. Known Issues

- Schema drift between desktop and extension builds can cause runtime validation failures.

## 2. Troubleshooting

- When messages are rejected, check:
  - Version alignment across consumers.
  - Zod schema and type definitions.

This document is self-contained and lists common issues and mitigations for `@dcmaar/bridge-protocol`.
