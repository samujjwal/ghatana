# DCMaar Framework – Browser Extension – Design & Architecture

## 1. Purpose

The DCMaar Browser Extension provides **in-browser capture and control** for supported browsers. It observes allowed domains and events, applies client-side policy, and sends telemetry to the DCMaar platform.

## 2. Responsibilities

- Capture browser activity (navigation, tabs, selected content) according to policy.
- Enforce domain allowlists and redaction rules locally.
- Communicate with the DCMaar Agent/Server over secure channels.
- Provide minimal UI surfaces (popup, options, badges) for status and controls.

## 3. Architectural Position

- Implemented as a **WebExtensions MV3** extension.
- Runs alongside the agent and desktop, contributing browser-side signals.
- Uses background scripts, content scripts, and extension messaging.

## 4. Components (Conceptual)

- Background/service worker: core logic, messaging, policy evaluation hooks.
- Content scripts: page-level instrumentation and event capture.
- Options/popup UI: configuration and status.

## 5. Design Constraints

- Follow privacy-by-default: never capture from disallowed domains; aggressively redact content.
- Keep extension lightweight to minimize browser performance impact.

This document is self-contained and summarizes the architecture and responsibilities of the DCMaar Browser Extension module.
