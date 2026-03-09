# Guardian – Agent React Native – Design & Architecture

## 1. Purpose

`@guardian/agent-react-native` is the **mobile agent app** for Guardian built on React Native and Expo. It captures device and usage data and communicates with DCMAAR/Guardian services.

## 2. Responsibilities

- Collect mobile usage and device signals (subject to platform permissions).
- Integrate with `@guardian/agent-core` for domain logic and gRPC communication.
- Provide basic UI where required to expose status and consent.

## 3. Architectural Position

- React Native + Expo app targeting Android/iOS.
- Depends on `@guardian/agent-core`, React Navigation, Jotai, React Query/Zustand for state.
- Uses gRPC libraries for backend communication.

This document is self-contained and summarizes the architecture and role of `@guardian/agent-react-native`.
