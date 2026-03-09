# 11. Settings Page – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 11. `/settings` – Settings & Preferences](../WEB_PAGE_FEATURE_INVENTORY.md#11-settings--settings--preferences)

**Code file:**

- `src/features/settings/pages/SettingsPage.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a centralized place for users to configure personal preferences, notifications, integrations, and account/security settings.

**Primary goals:**

- Let users choose **theme, timezone, date format, and display options**.
- Configure **notification channels and alert types**.
- Manage **integrations** (Slack, GitHub, etc.).
- Manage **account info**, sessions, and password.

**Non-goals:**

- Organization-wide security policies (those live elsewhere).
- Billing/subscription settings.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Any end user** of the platform.
- **On-call engineers** adjusting alerts.
- **Security-conscious users** reviewing sessions.

**Scenarios:**

1. **Setting dark mode and timezone**
   - GIVEN: User prefers dark theme and IST.
   - WHEN: They go to Settings → General.
   - THEN: They select `Dark` theme and `IST` timezone, updating their experience.

2. **Adjusting notifications**
   - GIVEN: On-call engineer wants Slack alerts but not email.
   - WHEN: They go to Settings → Notifications and toggle channels.
   - THEN: Alerts are delivered via Slack only (once wired).

3. **Managing integrations**
   - GIVEN: Team uses GitHub and Datadog.
   - WHEN: User connects these integrations.
   - THEN: CI/CD and monitoring features work seamlessly.

4. **Checking active sessions**
   - GIVEN: User suspects a stale/mobile session.
   - WHEN: They check Settings → Account → Active Sessions.
   - THEN: They can sign out of older sessions.

---

## 3. Content & Layout Overview

From `SettingsPage.tsx`:

- **Header:**
  - Title: `Settings`.
  - Subtitle: `Manage your preferences and account settings`.

- **Layout:**
  - Sidebar (left): tabs.
  - Main panel (right): active tab content.

- **Sidebar tabs:**
  - `⚙️ General`.
  - `🔔 Notifications`.
  - `🔗 Integrations`.
  - `👤 Account`.

- **General tab:**
  - Theme radio buttons: Light/Dark/Auto.
  - Timezone select.
  - Date format select.
  - Display checkboxes: Compact mode, Show grid lines.

- **Notifications tab:**
  - Toggles for Email, Desktop, Slack.
  - Alert types list (High Latency, Model Training Failed, SLA Breach, Security Alert).

- **Integrations tab:**
  - Cards for Slack, GitHub, PagerDuty, Datadog.
  - Shows connection status and Connect/Disconnect button.

- **Account tab:**
  - Email & Full Name inputs.
  - Active Sessions list.
  - Change Password button.
  - Delete Account button.

- **Footer section:**
  - `Save Changes` and `Cancel` buttons.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Immediate clarity:**
  - Each tab should clearly explain what settings it controls.
- **Non-destructive defaults:**
  - Dangerous actions (Delete Account) must be visually marked and require confirmation.
- **Save behavior:**
  - Save button clearly indicates when changes are persisted; optionally show a brief “Saved” state.

---

## 5. Completeness and Real-World Coverage

Settings must support:

- Personalizing visual and time settings.
- Controlling notification noise.
- Managing third-party tool integrations.
- Reviewing and protecting account access.

---

## 6. Modern UI/UX Nuances and Features

- Sticky sidebar on large screens.
- Smooth transitions between tabs.
- Accessible labels on all inputs.
- Inline help text (e.g., for alert types or integration scopes).

---

## 7. Coherence and Consistency Across the App

- Theme setting must align with global ThemeProvider & Layout.
- Timezone/date formats affect timestamps in Dashboard, Reports, and Logs.
- Integrations must line up with usage in Workflows, Alerts, etc.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#11-settings--settings--preferences`
- Implementation: `src/features/settings/pages/SettingsPage.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Wire settings to backend API and persist per user.
- Add preview of how date/time changes affect sample timestamps.
- Provide more granular notification controls (per-alert-type channels).

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch (General Tab)

```text
H1: Settings
Subtitle: Manage your preferences and account settings

[Sidebar]
⚙️ General
🔔 Notifications
🔗 Integrations
👤 Account

[General Tab Content]
- Theme: ( ) Light   (•) Dark   ( ) Auto
- Timezone: [ IST ▼ ]
- Date Format: [ YYYY-MM-DD HH:mm:ss ▼ ]
- [ ] Compact mode
- [x] Show grid lines in charts

[ ✓ Save Changes ]  [ Cancel ]
```

### 10.2 Example Notification Settings

**Email Notifications:** ON  
**Desktop Notifications:** ON  
**Slack Integration:** OFF

Alert Types (all default ON):

- High Latency Detected.
- Model Training Failed.
- SLA Breach.
- Security Alert.

### 10.3 Example Integrations

- **Slack** – disconnected  
  Button: `[ Connect ]`

- **GitHub** – connected  
  Button: `[ Disconnect ]`

- **PagerDuty** – disconnected

- **Datadog** – connected

### 10.4 Example Account & Sessions

- Email: `user@example.com`
- Full Name: `Jane Doe`

Active Sessions:

- `Current Session (Chrome, macOS)` – `Active`.
- `Mobile App (iPhone)` – `[ Sign out ]`.

Buttons:

- `[ 🔑 Change Password ]`
- `[ 🗑 Delete Account ]` (dangerous, requires confirmation dialog).

This mockup anchors Settings in concrete values and flows consistent with the rest of the product.
