# YAPPC Stub Pages — Replacement Tracker

> **Created:** 2025-01-19 | **Total Stubs:** 57 | **Replaced:** 5

## Priority Tiers

### P0 — Critical Path (Auth + Entry)
| Page | Status | Notes |
|---|---|---|
| `LoginPage` | ✅ Replaced | Full email/password form + SSO link |
| `LandingPage` | ✅ Replaced | Marketing hero + feature highlights |
| `ProjectsPage` | ✅ Replaced | Grid listing with TanStack Query |
| `RegisterPage` | ✅ Replaced | Full sign-up form with validation + POST /api/auth/register |
| `ForgotPasswordPage` | ✅ Replaced | Email form + POST /api/auth/forgot-password + success state |
| `ResetPasswordPage` | ❌ Stub | Token-based password reset |
| `SSOCallbackPage` | ❌ Stub | OAuth2 callback handler |
| `ProfilePage` | ❌ Stub | User profile/settings |
| `SettingsPage` | ❌ Stub | Workspace settings |

### P1 — Core Workflow
| Page | Status |
|---|---|
| `IncidentsPage` | ❌ Stub |
| `SecurityAlertsPage` | ❌ Stub |
| `SecurityScansPage` | ❌ Stub |
| `VulnerabilityDetailPage` | ❌ Stub |
| `ScanResultsPage` | ❌ Stub |
| `DashboardsPage` | ❌ Stub |
| `DashboardEditorPage` | ❌ Stub |
| `AdminDashboardPage` | ❌ Stub |
| `TeamsPage` | ❌ Stub |
| `UsersPage` | ❌ Stub |

### P2 — DevSecOps
| Page | Status |
|---|---|
| `ComplianceFrameworkPage` | ❌ Stub |
| `PoliciesPage` | ❌ Stub |
| `PolicyDetailPage` | ❌ Stub |
| `SecretsPage` | ❌ Stub |
| `ThreatModelPage` | ❌ Stub |
| `PostmortemsPage` | ❌ Stub |
| `RunbooksPage` | ❌ Stub |
| `RunbookDetailPage` | ❌ Stub |
| `OnCallPage` | ❌ Stub |
| `WarRoomPage` | ❌ Stub |
| `ServiceMapPage` | ❌ Stub |

### P3 — Collaboration
| Page | Status |
|---|---|
| `MessagesPage` | ❌ Stub |
| `ChannelPage` | ❌ Stub |
| `DirectMessagePage` | ❌ Stub |
| `ActivityFeedPage` | ❌ Stub |
| `TeamHubPage` | ❌ Stub |
| `CalendarPage` | ❌ Stub |
| `GoalsPage` | ❌ Stub |
| `StandupsPage` | ❌ Stub |
| `RetrosPage` | ❌ Stub |
| `ArticlePage` | ❌ Stub |
| `ArticleEditorPage` | ❌ Stub |

### P4 — Development
| Page | Status |
|---|---|
| `CodeReviewPage` | ❌ Stub |
| `PullRequestsPage` | ❌ Stub |
| `PullRequestDetailPage` | ❌ Stub |
| `EpicsPage` | ❌ Stub |
| `VelocityPage` | ❌ Stub |

### P5 — Admin/Setup
| Page | Status |
|---|---|
| `AuditPage` | ❌ Stub |
| `BillingPage` | ❌ Stub |
| `IntegrationsPage` | ❌ Stub |
| `EnvironmentSetupPage` | ❌ Stub |
| `InfrastructureConfigPage` | ❌ Stub |
| `SetupProgressPage` | ❌ Stub |
| `SetupWizardPage` | ❌ Stub |
| `TeamInvitePage` | ❌ Stub |
| `ProjectPreviewPage` | ❌ Stub |
| `TemplateGalleryPage` | ❌ Stub |
| `PricingPage` | ❌ Stub |

## Pattern for Replacing Stubs

```tsx
// 1. Remove StubPage import
// 2. Add real imports (react-router, @tanstack/react-query, etc.)
// 3. Implement with Tailwind CSS dark theme (zinc palette)
// 4. Wire API calls through fetch + Bearer token
// 5. Use TanStack Query for server state
```
