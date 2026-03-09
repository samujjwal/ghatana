# DCMaar – Browser Extension Core – Technical Reference

## 1. Overview

This reference summarizes key technical concepts of `@dcmaar/browser-extension-core`.

## 2. Core Concepts (Conceptual)

- **Sources** – capture events from the browser (tabs, navigation, DOM signals) in a controlled way.
- **Processors** – transform, filter, or enrich events.
- **Sinks** – deliver processed events to downstream consumers (e.g., bridge, logging, storage).

## 3. Intended Consumers

- Concrete browser extension implementations within DCMaar.

This technical reference is self-contained and describes the primary surfaces of `@dcmaar/browser-extension-core`.
