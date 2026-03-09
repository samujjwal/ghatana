# DCMaar Bridge Protocol – Operations Guide

## 1. Overview

`@dcmaar/bridge-protocol` is a library; operational impact comes from how it is versioned and rolled out to desktop and extension builds.

## 2. Versioning

- Coordinate protocol version changes across all consumers (desktop, extension, middleware).
- Treat breaking changes as **major version bumps**.

## 3. Monitoring

- When updating, monitor bridge error logs for validation or schema mismatches.

This guide is self-contained and documents operational considerations for `@dcmaar/bridge-protocol`.
