# 12. Help Center – Deep-Dive Spec

Related inventory entry: [WEB_PAGE_FEATURE_INVENTORY.md – 12. `/help` – Help Center](../WEB_PAGE_FEATURE_INVENTORY.md#12-help--help-center)

**Code file:**

- `src/features/help/pages/HelpCenter.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide a central, self-service portal for documentation, FAQs, guides, and support contact so users can solve most problems without leaving the product.

**Primary goals:**

- Offer **search** across FAQs/guides.
- Organize **guides and tutorials** by topic.
- Present **FAQs** with categories and helpfulness feedback.
- Provide **support contact** and key external resources.

**Non-goals:**

- Deep product analytics.
- Full CMS management UI.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **New users** onboarding.
- **Engineers** debugging specific features.
- **PMs/Leads** wanting to understand capabilities.

**Scenarios:**

1. **Onboarding to model deployment**
   - GIVEN: New user wants to deploy their first model.
   - WHEN: They search or click `Getting Started with Model Deployment` guide.
   - THEN: They follow a step-by-step tutorial.

2. **Troubleshooting high latency**
   - GIVEN: User sees high latency in Dashboard.
   - WHEN: They search for `latency` or open `How to debug model latency`.
   - THEN: They read concrete troubleshooting steps.

3. **Finding API documentation**
   - GIVEN: Developer wants to integrate with the API.
   - WHEN: They click `API Reference` in Resources.
   - THEN: They land on external or internal API docs.

---

## 3. Content & Layout Overview

From `HelpCenter.tsx`:

- **Hero section:**
  - Big title: `How can we help?`.
  - Search bar (`Search documentation, FAQs, guides...`).

- **Quick links (4 cards):**
  - Documentation, Tutorials, Community, Support.

- **Main grid:**
  - Left (2/3): Guides & FAQs.
  - Right (1/3): Sidebar (Still need help, Trending, Resources).

- **Guides section:**
  - List of guides with icon, title, description, read time.

- **FAQs section:**
  - Category filter chips (All, Getting Started, Troubleshooting, API, Settings).
  - Expandable FAQ items with question, answer, category, views.
  - Feedback buttons: `Helpful` / `Not helpful`.

- **Sidebar:**
  - Still need help (Contact Support button).
  - Trending articles.
  - Resources (API Reference, GitHub Examples, Status Page, Changelog).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Search that feels real:**
  - Typing should at least filter the FAQ/guides list (future: search backend).
- **Categories intuitive:**
  - Clear category names; highlight active chip.
- **Expandable FAQ answers:**
  - Smooth expand/collapse; only one or multiple open depending on preference.
- **Feedback loop:**
  - Helpful/Not helpful buttons should feel easy and safe to click.

---

## 5. Completeness and Real-World Coverage

Help Center should support:

- Getting started (deployment, monitoring, automation).
- Troubleshooting (errors, performance, failed training, etc.).
- API integration.
- Settings, security, compliance topics.

---

## 6. Modern UI/UX Nuances and Features

- Visual hierarchy between hero, guides, FAQs.
- Card-based quick links with subtle hover.
- Responsive layout (grid becomes stacked on mobile).
- Clear CTAs for contacting support.

---

## 7. Coherence and Consistency Across the App

- Should be reachable from a `?` icon in header/shell.
- Articles should reference the same route names and terminology used in the product.
- Trending/resources should highlight docs relevant to commonly used features.

---

## 8. Links to More Detail & Working Entry Points

- Inventory overview: `../WEB_PAGE_FEATURE_INVENTORY.md#12-help--help-center`
- Implementation: `src/features/help/pages/HelpCenter.tsx`

---

## 9. Open Gaps & Enhancement Plan

- Wire search to a real documentation index.
- Personalize suggestions based on current page/role.
- Add deep links from pages (e.g., Dashboard → relevant help anchors).

---

## 10. Mockup / Expected Layout & Content (With Sample Values)

### 10.1 Desktop Layout Sketch

```text
H1: How can we help?
[ Search documentation, FAQs, guides...              ( 🔍 ) ]

[Quick Links]
[ Documentation ]  [ Tutorials ]  [ Community ]  [ Support ]

+---------------------------------------------+-------------------------+
| Guides & FAQs (left, 2/3)                  | Sidebar (right, 1/3)   |
+---------------------------------------------+-------------------------+
```

### 10.2 Sample Guides

1. **Getting Started with Model Deployment**
   - Description: `Deploy your first model in under 10 minutes.`
   - Read time: `10 min`

2. **Understanding Model Metrics**
   - Description: `Accuracy, precision, recall, F1 – explained with examples.`
   - Read time: `8 min`

3. **Automating Incident Response**
   - Description: `How to connect Real-Time Monitor to Automation Engine.`
   - Read time: `12 min`

### 10.3 Sample FAQs

1. **Q:** `Why are my dashboard metrics not updating?`
   - Category: `Troubleshooting`
   - Answer (summary): `Check that data sources are connected and real-time streaming is enabled. Verify Event Simulator or connectors.`
   - Views: `234`

2. **Q:** `How do I roll back a failed deployment?`
   - Category: `Getting Started`
   - Answer (summary): `Use Automation Engine's 'Auto-Rollback Failed Deployment' workflow or manually trigger rollback from Workflows.`
   - Views: `178`

3. **Q:** `Where can I find the API access token?`
   - Category: `API`
   - Answer (summary): `Go to Security → Manage API Keys. You can create, revoke, and rotate tokens there.`
   - Views: `412`

Feedback buttons under each answer:

- `Helpful (32)` `Not helpful (3)`

### 10.4 Sidebar Example Content

- **Still need help?**
  - Text: `Can’t find what you’re looking for?`
  - Button: `[ Contact Support ]` (opens mailto or in-app form).

- **Trending Articles**
  - `Interpreting Drift Scores in ML Observatory`
  - `Setting Up PagerDuty Integration`
  - `Best Practices for HITL Workflows`

- **Resources**
  - `API Reference` → `/docs/api`
  - `GitHub Examples` → `https://github.com/ghatana/examples`
  - `Status Page` → `https://status.ghatana.dev`
  - `Changelog` → `/docs/changelog`

This mockup anchors Help Center content and layout with realistic examples and link targets.
