# DCMaar – Browser Extension Core – Operations Guide

## 1. Overview

`@dcmaar/browser-extension-core` is a library; operational impact comes from how it is used in concrete browser extension bundles.

## 2. Configuration

- Configure pipelines (sources/processors/sinks) in the extension code using this core library.
- Keep domain allowlists, redaction rules, and connection settings configurable per environment.

## 3. Monitoring

- Monitor extension behavior through logs and metrics surfaced via the agent/server.
- Watch for pipeline errors and dropped events.

This guide is self-contained and documents operational considerations for `@dcmaar/browser-extension-core`.
