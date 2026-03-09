# Guardian – Parent Dashboard – Design & Architecture

## 1. Purpose

`@guardian/parent-dashboard` is the **web dashboard** for parents to view and manage Guardian parental-control data. It surfaces activity, alerts, and configuration in a browser UI.

## 2. Responsibilities

- Display real-time and historical data from the Guardian backend.
- Provide configuration flows (rules, schedules, notifications) for Guardian policies.
- Offer reporting/export capabilities for audits and reviews.

## 3. Architectural Position

- React + TypeScript SPA built with Vite.
- Uses TailwindCSS, React Router, Jotai, Zod, and Axios.
- Integrates with backend via HTTP/WebSocket (`socket.io-client`).

This document is self-contained and summarizes the architecture and role of the Guardian Parent Dashboard.
