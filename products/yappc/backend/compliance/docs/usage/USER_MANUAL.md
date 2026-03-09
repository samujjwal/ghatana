# YAPPC Backend – Compliance Module – User Manual

## 1. Audience

This manual is for backend engineers and product engineers who use the Compliance module to implement compliance features in their services and UIs.

## 2. Typical Flows

- **Manage policies**
  - Create new policies with initial status and version.
  - Update existing policies and automatically bump versions.
  - Deprecate or archive obsolete policies.
- **Track acknowledgments**
  - Record user acknowledgments for specific policy versions.
  - Query which policies a given user still needs to acknowledge.
- **Monitor coverage**
  - Compute acknowledgment coverage for each policy against a set of users.
  - Identify users or groups that have not accepted required policies.
- **Review and retention**
  - Identify policies due for review based on last update time.
  - Periodically clean up archived policies beyond retention.

## 3. Integration Pattern

1. Use Compliance domain types in your service layer.
2. Persist policies and acknowledgments using your service’s storage.
3. Expose APIs and UIs backed by the Compliance module’s operations.
4. Use search and export helpers to drive reporting and dashboards.

## 4. Best Practices

- Treat Compliance as the single source of truth for policy logic; avoid duplicating rules.
- Keep audit logs and metrics for policy changes and acknowledgments at the service level.

This manual is self-contained and explains how to use the Compliance module in common scenarios.
