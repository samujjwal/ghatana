# Guardian – Backend API – User Manual

## 1. Audience

This manual is for engineers and operators working with the Guardian Backend API.

## 2. Basic Usage

1. Configure environment variables (database, Redis, auth secrets, telemetry exporters).
2. Run `npm run dev` for local development.
3. Run `npm run build && npm start` (or equivalent pnpm commands) for production.
4. Use `db:migrate` and `db:seed` to manage schema and initial data.

## 3. Best Practices

- Keep secrets in environment/config, not in code.
- Ensure observability (metrics, traces, logs) is enabled in non-local environments.

This manual is self-contained and explains how to use the Guardian Backend API in typical workflows.
