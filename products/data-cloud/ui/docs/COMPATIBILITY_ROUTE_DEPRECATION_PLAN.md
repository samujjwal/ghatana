# Compatibility Route Deprecation Plan

Purpose: track legacy deep-link aliases and retire them with a fix-forward, measurable path.
Owner: Data Cloud UI
Status: active migration

## Scope

This plan covers compatibility aliases declared in src/routes.tsx under the compatibility section.

## Canonicalization Matrix

| Alias path | Canonical route | Current handling |
|---|---|---|
| /dashboard | / | alias renders canonical page |
| /hub | / | alias renders canonical page |
| /collections | /data | alias renders canonical page |
| /collections/new | /data/new | alias renders canonical page |
| /collections/:id | /data/:id | alias renders canonical page |
| /collections/:id/edit | /data/:id/edit | alias renders canonical page |
| /datasets | /data | alias renders canonical page |
| /lineage | /data?view=lineage | redirect |
| /quality | /data?view=quality | redirect |
| /workflows | /pipelines | alias renders canonical page |
| /workflows/new | /pipelines/new | alias renders canonical page |
| /workflows/:id | /pipelines/:id | alias renders canonical page |
| /sql | /query | alias renders canonical page |
| /governance | /trust | alias renders canonical page |
| /brain | /insights | alias renders canonical page |
| /dashboards | /insights | alias renders canonical page |
| /cost | /insights | alias renders canonical page |

## Retirement Gates

1. Publish deprecation notice in user-facing docs with canonical replacements.
2. Capture route hit telemetry for each alias path (rolling 30-day window).
3. Require alias traffic to be near-zero before removal.
4. Convert aliases from page aliases to explicit redirects before full removal.
5. Remove aliases in a single migration release and update tests/docs.

## Success Criteria

- Compatibility aliases are inventoried and reviewable in source control.
- Removal decisions are backed by route usage metrics, not assumptions.
- Deprecated aliases can be switched to explicit 410 responses after migration window.
