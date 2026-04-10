# Library Ownership Registry

Canonical registry of platform and product library owners in the Ghatana monorepo.

**Updated**: 2026-04-09 | **Process**: See [LIBRARY_GOVERNANCE.md](./LIBRARY_GOVERNANCE.md)

---

## Platform Libraries

| Package | Owner | GitHub | Notes |
|---------|-------|--------|-------|
| `@ghatana/tokens` | Platform Team | `@ghatana/platform-team` | Foundation — rarely changes |
| `@ghatana/theme` | Platform Team | `@ghatana/platform-team` | Foundation |
| `@ghatana/design-system` | Platform Team | `@ghatana/platform-team` | UI primitives |
| `@ghatana/platform-utils` | Platform Team | `@ghatana/platform-team` | Utility functions |
| `@ghatana/api` | Platform Team | `@ghatana/platform-team` | HTTP client |
| `@ghatana/realtime` | Platform Team | `@ghatana/platform-team` | WebSocket/SSE |
| `@ghatana/events` | Platform Team | `@ghatana/platform-team` | Event bus |
| `@ghatana/browser-events` | Platform Team | `@ghatana/platform-team` | Browser events |
| `@ghatana/state` | Platform Team | `@ghatana/platform-team` | State management |
| `@ghatana/config` | Platform Team | `@ghatana/platform-team` | Configuration + feature flags |
| `@ghatana/canvas` | Platform Team | `@ghatana/platform-team` | Canvas/flow UI |
| `@ghatana/charts` | Platform Team | `@ghatana/platform-team` | Chart components |
| `@ghatana/i18n` | Platform Team | `@ghatana/platform-team` | Internationalisation |
| `@ghatana/eslint-plugin` | Platform Team | `@ghatana/platform-team` | Architecture lint rules |

---

## Product Libraries

### YAPPC

| Package | Owner | Notes |
|---------|-------|-------|
| `@yappc/core` | YAPPC Team | Consolidated auth/chat/security/testing |
| `@yappc/api` | YAPPC Team | HTTP layer using `@ghatana/api` |
| `@yappc/state` | YAPPC Team | Uses `@ghatana/state` |
| `@yappc/ui` | YAPPC Team | Uses `@ghatana/design-system` + `@ghatana/tokens` |
| `@yappc/ai` | YAPPC Team | AI integration |

### DCMAAR

| Package | Owner | Notes |
|---------|-------|-------|
| `@dcmaar/types` | DCMAAR Team | Shared type definitions |
| `@dcmaar/ui` | DCMAAR Team | UI components |
| `@dcmaar/browser-extension-core` | DCMAAR Team | Extension core |
| `@dcmaar/bridge-protocol` | DCMAAR Team | Bridge protocol types |
| `@dcmaar/connectors` | DCMAAR Team | External connectors |
| `@dcmaar/config-presets` | DCMAAR Team | Config presets |
| `@dcmaar/plugin-abstractions` | DCMAAR Team | Plugin interfaces |
| `@dcmaar/plugin-extension` | DCMAAR Team | Plugin extension points |

### Data-Cloud

| Package | Owner | Notes |
|---------|-------|-------|
| `@data-cloud/ui-components` | Data-Cloud Team | Reusable presentation components |

### Audio-Video

| Package | Owner | Notes |
|---------|-------|-------|
| `@audio-video/ui` | Audio-Video Team | UI + audio-specific hooks |

### Flashit

| Package | Owner | Notes |
|---------|-------|-------|
| `@flashit/shared` | Flashit Team | Atoms, hooks, types, flashit-specific utils |

---

## Ownership Responsibilities

Every library owner commits to:

1. **Responsiveness** — Reply to issues and PRs within 5 business days
2. **Dependency hygiene** — Monthly dependency reviews, security patches within 48h
3. **Test health** — Maintain ≥80% test coverage; don't merge failing tests
4. **Changelog discipline** — Document breaking changes in the PR description
5. **RFC participation** — Review proposals that affect your library within 7 days

---

## CODEOWNERS Configuration

The `.github/CODEOWNERS` file enforces review requirements. Key entries:

```
platform/typescript/              @ghatana/platform-team
platform/typescript/eslint-plugin/ @ghatana/platform-team
products/yappc/                   @ghatana/yappc-team
products/dcmaar/                  @ghatana/dcmaar-team
products/data-cloud/              @ghatana/data-cloud-team
products/audio-video/             @ghatana/audio-video-team
products/flashit/                 @ghatana/flashit-team
eslint-rules/                     @ghatana/platform-team
docs/                             @ghatana/platform-team
```

---

## Archived (Removed Libraries)

| Package | Removed In | Replaced By |
|---------|-----------|-------------|
| `@ghatana/ui` | V4.1 | `@ghatana/design-system` |
| `@ghatana/audit-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@ghatana/privacy-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@ghatana/security-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@ghatana/voice-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@ghatana/nlp-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@ghatana/selection-ui` | V4.1 Sprint 1 | `@ghatana/design-system` |
| `@yappc/canvas` | Sprint 4 | `@ghatana/canvas` |
| `@yappc/auth` | Library Restructuring | `@yappc/core` |
| `@yappc/chat` | Library Restructuring | `@yappc/core` |
| `@yappc/security` | Library Restructuring | `@yappc/core` |
| `@yappc/testing` | Library Restructuring | `@yappc/core` |
