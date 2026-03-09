# Tutorputor API Gateway – Known Issues & Troubleshooting

## 1. Known Issues

- Upstream service outages or timeouts surface as gateway errors.
- Misconfigured auth or rate-limiting can block valid traffic.

## 2. Troubleshooting

- When clients see frequent errors:
  - Check gateway logs and metrics for upstream failures, auth errors, or rate-limit events.
  - Verify configuration for upstream endpoints, credentials, and policies.

This document is self-contained and lists common issues and mitigations for the Tutorputor API Gateway.
