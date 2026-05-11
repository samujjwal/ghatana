# Data Cloud User Manual

**Status:** Canonical
**Owner:** Product Team
**Last reviewed:** 2026-05-10
**Supersedes:** `docs/usage/USER_MANUAL.md`
**Superseded by:** N/A

## Overview

This manual provides end-user documentation for the Data Cloud platform.

## Getting Started

### Accessing Data Cloud

Data Cloud is accessed through the web UI at the configured deployment URL. Contact your administrator for access credentials.

### Authentication

Data Cloud uses tenant-based authentication. Log in with your organization credentials and select your tenant if multiple are available.

## Core Features

### Entity Management

- **Browse Entities:** View and search entities across collections
- **Create Entities:** Add new entities to collections
- **Edit Entities:** Update entity metadata and content
- **Delete Entities:** Remove entities (with confirmation)

### Event Timeline

- **View Events:** Browse the event timeline for audit trail
- **Filter Events:** Filter by type, time range, and source

### Connectors

- **Register Connectors:** Add new data source connectors
- **Test Connections:** Validate connector connectivity
- **Sync Data:** Trigger data synchronization from sources
- **Manage Credentials:** Securely manage connector credentials

### Pipelines

- **Create Pipelines:** Define data processing pipelines
- **Execute Pipelines:** Run pipeline jobs
- **Monitor Executions:** View pipeline execution history and status
- **Debug Failures:** Review execution logs and error details

### Governance

- **Policies:** Create and manage governance policies
- **Retention:** Configure data retention rules
- **Privacy:** Set up data redaction and privacy controls
- **Compliance:** Monitor compliance status and reports

### AI/ML

- **AI Assist:** Use AI-powered assistance for data operations
- **Suggestions:** Review and apply AI-generated suggestions
- **Models:** Manage AI models and deployments

## Keyboard Shortcuts

- `Ctrl/Cmd + K`: Quick search
- `Ctrl/Cmd + /`: Command palette
- `Escape`: Close modals/dropdowns

## Troubleshooting

### Common Issues

**Cannot access a feature:** Check if the required surface is enabled in Runtime Truth. Contact your administrator.

**Connector sync failed:** Verify connector credentials and network connectivity. Check connector status in the Connectors page.

**Pipeline execution failed:** Review execution logs for error details. Check if required surfaces are available.

## Support

For additional support, contact your Data Cloud administrator or refer to the internal documentation.
