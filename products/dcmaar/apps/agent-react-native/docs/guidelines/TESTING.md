# Guardian – Agent React Native – Testing Guidelines

## 1. Goals

- Ensure agent flows, navigation, and background behaviors are correct on mobile.

## 2. Tests

- Use Jest + React Native Testing Library for component and hook tests.
- Mock `@guardian/agent-core` and gRPC calls when testing UI.

This document is self-contained and explains how to test `@guardian/agent-react-native`.
