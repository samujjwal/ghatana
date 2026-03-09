# Shared TypeScript Tokens – Testing Guidelines

## 1. Goals

- Ensure tokens are correctly defined, exported, and consumed by UI/theme layers.

## 2. Tests

- Use lightweight tests to verify token shape and presence (e.g., snapshot tests of token objects).
- Validate that consuming libraries (ui, theme) compile and render correctly with the tokens.

This document is self-contained and explains how to test the Shared TypeScript Tokens library.
