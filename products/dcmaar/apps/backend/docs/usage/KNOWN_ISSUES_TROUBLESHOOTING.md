# Guardian – Backend API – Known Issues & Troubleshooting

## 1. Known Issues

- Complex auth flows and DB migrations can be sources of subtle bugs.
- Misconfigured observability or rate limiting can hide or amplify issues.

## 2. Troubleshooting

- When API issues occur, check:
  - Logs and traces for failing routes.
  - DB and Redis connectivity and health.
  - Auth and rate-limiting configuration.

This document is self-contained and lists common issues and mitigations for the Guardian Backend API.
