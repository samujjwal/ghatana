# PHR Workflow — Appointments

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                            |
| **Classification** | Internal                                                                                                                     |
| **Last Review**    | 2026-01-19                                                                                                                   |
| **Companion Docs** | [Sequence Diagrams](../03_architecture/phr_core_sequence_diagrams.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Added double-booking conflict resolution, offline appointment viewing, appointment→telemedicine session linkage (Phase 2), and timezone handling for NRN caregiver notifications.
> **Phase:** Core MVP

---

## 1. Goal

Allow patients or authorized staff to book and view appointments while protecting against slot conflicts and ensuring reminders can be planned.

---

## 2. Primary actors

- patient
- provider staff
- caregiver in later delegated scope

---

## 3. Entry points

- patient appointment list
- patient booking screen
- provider calendar

APIs:

- `GET /api/v1/patients/:id/appointments`
- `GET /api/v1/appointments/:id`
- `GET /api/v1/providers/:id/availability`
- `POST /api/v1/appointments`
- `POST /api/v1/appointments/:id/cancel`

---

## 4. Preconditions

- provider availability exists
- patient context is valid
- booking actor is authenticated and authorized

---

## 5. Data touched

- `Appointment`
- later `Schedule`
- later `Slot`
- `ReminderPlan`
- `AuditLog`

---

## 6. Happy path

1. actor selects provider/date
2. availability is fetched
3. actor selects slot and enters reason for visit
4. API validates slot and patient context
5. transaction checks conflict and creates appointment
6. reminder planning event/table entry is created
7. audit record is written
8. confirmed appointment is returned

---

## 7. Alternate and failure paths

- selected slot already booked -> conflict
- provider not available -> validation error
- reminder planning fails after appointment create -> retryable async recovery path, not duplicate booking
- patient tries to cancel outside allowed policy -> forbidden

---

## 8. UX requirements

- upcoming/past views
- clear conflict messaging
- stable slot-loading state
- successful booking confirmation with next steps

---

## 9. Acceptance criteria

- double-book prevention works under concurrent requests
- reminder planning is triggered for confirmed bookings
- patient and provider list views stay consistent
- cancellation path is audited and permissioned

---

## 10. Double-Booking Resolution (Added in v2.0)

**Concurrency control:** Optimistic locking on appointment slot. If two actors attempt to book the same slot:

1. Both read available slot
2. First `POST` succeeds → creates appointment, marks slot taken
3. Second `POST` detects version conflict → returns `409 Conflict`
4. UI shows: "This slot was just booked. Please select another."
5. Available slots auto-refresh in the background (5-second polling or WebSocket)

**Provider-side:** Providers see real-time slot status. Booked slots grayed out immediately via WebSocket push.

---

## 11. Offline Appointment Viewing (Added in v2.0)

Mobile app caches upcoming appointments for offline access:

- Next 30 days of appointments cached locally (encrypted)
- Cache refreshes on each successful sync
- Offline display shows: appointment date, time, provider name, facility, reason
- **No offline booking allowed** (requires real-time slot validation)
- If appointment passes while offline → shown as "Past" with "Sync to update" note

---

## 12. Telemedicine Session Linkage — Phase 2 (Added in v2.0)

When an appointment has `type: telemedicine`:

1. 15 minutes before scheduled time → system creates Jitsi session room
2. Patient and provider receive "Join consultation" button in their appointment view
3. On join → redirect to telemedicine session (see telemedicine workflow doc)
4. On session end → encounter outcome linked back to appointment record
5. Appointment status changes: `scheduled` → `in-progress` → `completed`

---

## 13. NRN Timezone Handling (Added in v2.0)

Non-Resident Nepalese (NRN) patients may book appointments from different timezones:

- All appointment times stored in UTC
- Patient UI shows times in **patient's local timezone** (detected or set in profile)
- Provider UI shows times in **Nepal Standard Time (NST, UTC+5:45)** always
- Booking confirmation displays: "Your appointment: {local time} ({NST time} Nepal time)"
- Reminder notifications respect patient's timezone

**Edge case:** Nepal uses UTC+5:45 (non-standard offset). All timezone libraries must support 15-minute offsets.
