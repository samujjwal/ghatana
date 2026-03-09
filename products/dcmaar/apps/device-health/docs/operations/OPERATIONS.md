# DCMaar – Device Health Extension – Operations Guide

## 1. Overview

The Device Health extension is deployed via browser stores or internal distribution. Operations focus on rollout, configuration, and monitoring.

## 2. Build & Packaging

- Use `build`, `build:chrome`, `build:firefox`, `build:edge` scripts for targeted builds.
- Use `package:*` scripts to create distributable archives.

## 3. Configuration

- Manage configuration via presets and environment variables used by Vite.
- Control plugin and connector behavior centrally via config.

## 4. Monitoring

- Monitor extension metrics via plugin outputs and backend/agent telemetry.
- Track performance and errors using existing observability hooks.

This guide is self-contained and documents operational considerations for the Device Health extension.
