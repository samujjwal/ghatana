# DCMaar – Browser Extension Core – Design & Architecture

## 1. Purpose

`@dcmaar/browser-extension-core` provides the **core framework for building browser extensions** using a Source–Processor–Sink pipeline architecture.

## 2. Responsibilities

- Offer composable primitives for capturing browser events (sources).
- Apply processing stages (filters, transformers) to captured data.
- Deliver processed events to sinks (bridge, storage, messaging) in a structured way.
 - Provide a small, domain-neutral plugin host and connector bridge so
   extensions can install DCMAAR plugins and route their telemetry via
   shared connectors.

## 3. Architectural Position

- Framework-agnostic core library for MV3/WebExtensions extensions.
- Used by concrete browser extension UIs and bundles.

This document is self-contained and summarizes the architecture and role of `@dcmaar/browser-extension-core`.
