# DCMaar Agent Common – Operations Guide

## 1. Overview

`dcmaar-agent-common` is a library crate, not a standalone service. Operational concerns relate to how it is used within agent binaries.

## 2. Versioning

- Treat changes to shared types as **API changes** for the agent ecosystem.
- Coordinate version bumps with dependent crates and binaries.

## 3. Monitoring Impact

- When updating this crate, ensure downstream binaries still meet resource and behavioral budgets.

This guide is self-contained and documents operational considerations for `dcmaar-agent-common`.
