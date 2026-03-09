# Guardian – Parent Mobile – Design & Architecture

## 1. Purpose

`@guardian/parent-mobile` is the **Guardian Parent Mobile App** built with React Native. It lets parents view activity, receive notifications, and manage policies on mobile devices.

## 2. Responsibilities

- Display Guardian data and alerts from backend APIs.
- Provide configuration flows (rules, schedules) suitable for mobile devices.
- Integrate with push notifications for critical alerts.

## 3. Architectural Position

- React Native app using React Navigation, React Query, Axios, and charting.
- Communicates with Guardian backend over HTTP and uses Zod for validation.

This document is self-contained and summarizes the architecture and role of `@guardian/parent-mobile`.
