# PHR Platform — UI End-to-End Test Cases

**Version:** 2.0  
**Date:** 2026-03-17  
**Updated:** 2026-01-19

| Field          | Value                                                                                                                                                                                                                                                                       |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner          | QA Lead                                                                                                                                                                                                                                                                     |
| Classification | C2 — Internal                                                                                                                                                                                                                                                               |
| Review cadence | Per sprint                                                                                                                                                                                                                                                                  |
| Companion docs | [Frontend route map](../04_design_and_workflows/phr_frontend_route_and_component_map.md), [Screen matrix](../04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md), [Design system](../04_design_and_workflows/ghatana_design_system_complete_spec.md) |

> 📌 **What changed in v2.0:** Emergency QR UI tests, FCHV flow tests, offline mode tests, Nepali locale tests, WCAG 2.2 AA audit cases, browser compatibility matrix.

This document defines user-visible end-to-end scenarios for web, mobile, and desktop-delivered flows.

---

## 1. Core MVP UI scenarios

| Test ID  | Phase | Surface                  | Scenario                                     | Expected result                                            |
| -------- | ----- | ------------------------ | -------------------------------------------- | ---------------------------------------------------------- |
| `UI-001` | MVP   | sign in                  | valid sign in and session restore            | user lands in role-appropriate shell                       |
| `UI-002` | MVP   | registration             | complete patient registration                | patient created and redirected to dashboard/profile        |
| `UI-003` | MVP   | registration             | duplicate identifier on registration         | inline error shown, no duplicate patient created           |
| `UI-004` | MVP   | patient profile          | edit allowed profile fields                  | saved values re-render correctly                           |
| `UI-005` | MVP   | timeline                 | load timeline with filters                   | filtered record set appears correctly                      |
| `UI-006` | MVP   | observations             | change metric/date range in trends view      | chart updates without losing context                       |
| `UI-007` | MVP   | provider patient summary | provider views patient summary in scope      | summary loads with correct permission handling             |
| `UI-008` | MVP   | appointments             | patient books appointment                    | confirmation appears and list updates                      |
| `UI-009` | MVP   | documents                | upload and list document                     | progress, success, and list refresh work                   |
| `UI-010` | MVP   | access grants            | create and revoke access grant               | grant state changes immediately in UI                      |
| `UI-011` | MVP   | insurance                | run eligibility check                        | result state renders with success/failure messaging        |
| `UI-012` | MVP   | patient voice input      | record, review, confirm transcription        | accepted entities become created records                   |
| `UI-017` | MVP   | OCR review               | review OCR fields and confirm                | accepted edits produce persisted records                   |
| `UI-021` | MVP   | provider voice dictation | dictate, review, and apply structured output | accepted entities become encounter-linked clinical records |

---

## 2. Additional MVP and Phase 2 UI scenarios

| Test ID  | Phase   | Surface              | Scenario                                    | Expected result                                     |
| -------- | ------- | -------------------- | ------------------------------------------- | --------------------------------------------------- |
| `UI-013` | Phase 2 | telemedicine room    | create/join/end consult                     | session state changes are visible and stable        |
| `UI-014` | MVP     | caregiver dependents | caregiver opens delegated dependent summary | only granted data is visible                        |
| `UI-015` | MVP     | offline patient app  | queue approved write while offline          | pending-sync state, conflict messaging, and replay behavior appear |
| `UI-016` | MVP     | referrals            | provider creates referral and patient tracks it | referral list and status detail remain coherent   |

---

## 3. Accessibility UI scenarios

| Test ID  | Phase   | Surface                   | Scenario                                 | Expected result                               |
| -------- | ------- | ------------------------- | ---------------------------------------- | --------------------------------------------- |
| `UI-018` | MVP     | shipped Core MVP surfaces | keyboard-only navigation and focus order | user can complete task without pointer        |
| `UI-019` | MVP     | patient/provider screens  | screen reader labels and landmarks       | critical controls are announced correctly     |
| `UI-020` | Phase 2 | telemedicine room         | captions/transcript access               | captions/transcripts are visible and operable |

---

## 4. Test environment notes

- run on web app, mobile app, and desktop app shell for each committed MVP surface
- run on desktop-class provider/admin flows and patient-assisted registration flows
- run on mobile viewport and native-capable patient shell
- include Nepali and English locale runs for key patient flows

---

## 5. Emergency QR UI Tests (Added in v2.0)

| Test ID  | Phase | Surface      | Scenario                               | Expected result                                   |
| -------- | ----- | ------------ | -------------------------------------- | ------------------------------------------------- |
| `UI-022` | MVP   | Emergency QR | View QR card with complete profile     | QR image + data summary displayed                 |
| `UI-023` | MVP   | Emergency QR | View QR card with incomplete profile   | Warning shows missing fields with link to profile |
| `UI-024` | MVP   | Emergency QR | Print QR card                          | PDF generated with credit-card-size layout        |
| `UI-025` | MVP   | Emergency QR | QR auto-refreshes on medication change | Updated QR displayed after medication edit        |

---

## 6. FCHV Flow UI Tests (Added in v2.0)

| Test ID  | Phase | Surface           | Scenario                                | Expected result                                       |
| -------- | ----- | ----------------- | --------------------------------------- | ----------------------------------------------------- |
| `UI-026` | MVP   | FCHV dashboard    | FCHV opens dashboard                    | Icon-based navigation grid with 48×48px touch targets |
| `UI-027` | MVP   | FCHV registration | FCHV scans NID QR code                  | Patient data pre-populated from QR                    |
| `UI-028` | MVP   | FCHV registration | FCHV manual registration entry          | Form accepts Nepali and English input                 |
| `UI-029` | MVP   | FCHV vitals       | FCHV records vitals offline             | Data stored locally, sync indicator shows pending     |
| `UI-030` | MVP   | FCHV sync         | FCHV syncs after regaining connectivity | Pending data uploaded, sync badge clears              |

---

## 7. Offline Mode UI Tests (Added in v2.0)

| Test ID  | Phase | Surface     | Scenario                                  | Expected result                                      |
| -------- | ----- | ----------- | ----------------------------------------- | ---------------------------------------------------- |
| `UI-031` | MVP   | patient app | Lose connectivity on timeline screen      | Amber offline banner appears, cached timeline shown  |
| `UI-032` | MVP   | patient app | Attempt to book appointment while offline | Button disabled with "Requires connectivity" tooltip |
| `UI-033` | MVP   | patient app | Regain connectivity                       | Banner disappears, data auto-refreshes               |
| `UI-034` | MVP   | patient app | View medication reminders offline         | Local notifications fire on schedule                 |

---

## 8. Nepali Locale UI Tests (Added in v2.0)

| Test ID  | Phase | Surface             | Scenario                        | Expected result                                           |
| -------- | ----- | ------------------- | ------------------------------- | --------------------------------------------------------- |
| `UI-035` | MVP   | all patient screens | Switch to Nepali locale         | All labels, dates, and numbers rendered in Devanagari     |
| `UI-036` | MVP   | registration        | Enter name in Devanagari        | Accepted and displayed correctly                          |
| `UI-037` | MVP   | observations        | Vital signs displayed in Nepali | Numbers use Devanagari numerals per user preference       |
| `UI-038` | MVP   | appointments        | Bikram Sambat date display      | Dates shown in BS calendar when user preference is Nepali |

---

## 9. WCAG 2.2 AA Audit Cases (Added in v2.0)

| Test ID  | Phase | Surface           | Scenario                        | Expected result                              |
| -------- | ----- | ----------------- | ------------------------------- | -------------------------------------------- |
| `UI-039` | MVP   | all screens       | axe-core automated scan         | Zero critical/serious violations             |
| `UI-040` | MVP   | all screens       | Tab order audit                 | Logical focus order, no focus traps          |
| `UI-041` | MVP   | all forms         | Error states with screen reader | Errors announced and linked to fields        |
| `UI-042` | MVP   | all screens       | Color contrast check            | 4.5:1 text, 3:1 UI components                |
| `UI-043` | MVP   | all interactive   | Touch target size audit         | Minimum 44×44px (48×48px for FCHV)           |
| `UI-044` | MVP   | patient dashboard | VoiceOver/NVDA full workflow    | Patient can navigate dashboard without sight |
| `UI-045` | MVP   | payments          | patient pays outstanding bill   | pending, success, and receipt states render correctly |
| `UI-046` | MVP   | imaging viewer    | patient opens imaging study     | viewer, report panel, and secure download controls load |
| `UI-047` | Phase 2 | claims           | create claim and view status    | status list and detail remain coherent              |

---

## 10. Browser Compatibility Matrix (Added in v2.0)

| Browser           | Version  | Priority | Test Frequency  |
| ----------------- | -------- | -------- | --------------- |
| Chrome (desktop)  | Latest 2 | P0       | Every sprint    |
| Chrome (Android)  | Latest 2 | P0       | Every sprint    |
| Safari (iOS)      | Latest 2 | P0       | Every sprint    |
| Firefox (desktop) | Latest 2 | P1       | Every 2 sprints |
| Safari (macOS)    | Latest 2 | P1       | Every 2 sprints |
| Edge (desktop)    | Latest 2 | P2       | Monthly         |

**Mobile viewports:** 320px (min), 375px (target), 414px (large phone), 768px (tablet).
**Desktop viewports:** 1024px (min), 1280px (target), 1920px (wide).
