# Data Cloud UI – Known Issues & Troubleshooting

## 1. Known Issues

- API or auth issues may surface as UI errors or missing data.
- Alerts now depend on launcher-backed operator routes for list, acknowledge, resolve, rules, grouping, suggestions, and stream health; older deployments still fall back to the shared unsupported boundary.
- Data Fabric remains preview-only and should not be treated as a fully operational admin surface.
- Smart Workflow Builder captures intent, requests a launcher-backed draft when AI assist is available, and falls back to explicit boundary or degraded guidance when the runtime cannot generate drafts.

## 2. Troubleshooting

- Check browser console and network logs.
- Verify Data Cloud API availability and configuration.
- Confirm the launcher exposes `/api/v1/alerts`, `/api/v1/alerts/groups`, `/api/v1/alerts/suggestions`, and `/api/v1/alerts/rules` if the alerts page falls back to the boundary shell.
- Verify tenant bootstrap in session storage if launcher-backed flows unexpectedly show boundary states.
- Use the shell role switcher in the header when validating operator-only surfaces such as Insights, Trust, Events, or Settings.

This document is self-contained and lists common issues and mitigations for the Data Cloud UI module.
