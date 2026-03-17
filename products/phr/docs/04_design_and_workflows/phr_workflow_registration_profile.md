# PHR Workflow — Registration and Profile

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                   |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                       |
| **Classification** | Internal                                                                                                                |
| **Last Review**    | 2026-01-19                                                                                                              |
| **Companion Docs** | [Activation Plan](../03_architecture/phr_mvp_activation_plan.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Added FCHV-initiated registration flow, emergency QR card generation on profile complete, NID verification error handling, and multi-tenancy context during registration.
> **Phase:** Core MVP

---

## 1. Goal

Enable a patient or authorized staff member to create and maintain the patient profile root used by all later record, consent, and scheduling workflows.

---

## 2. Primary actors

- patient
- admin
- provider performing assisted registration

---

## 3. Entry points

- patient registration screen
- patient profile screen

APIs:

- `POST /api/v1/patients`
- `GET /api/v1/patients/:id`
- `PATCH /api/v1/patients/:id`

---

## 4. Preconditions

- actor is authenticated unless public self-registration is enabled by policy
- tenant/facility context is known
- duplicate identifier policy is configured

---

## 5. Data touched

- `Patient`
- `Identifier`
- `HumanName`
- `Address`
- `ContactPoint`
- `ConsentGrant` baseline if auto-created by policy
- `AuditLog`

---

## 6. Happy path

### 6.1 Registration

1. actor opens registration flow
2. demographic and contact fields are entered
3. payload is validated
4. duplicate identifiers are checked
5. patient profile root is created
6. audit event is written
7. response returns patient id and canonical profile

### 6.2 Profile update

1. actor opens profile
2. editable fields are changed
3. field-level validation runs
4. only permitted fields are persisted
5. audit event is written for sensitive changes
6. updated profile is returned

---

## 7. Alternate and failure paths

- duplicate identifier -> block create with conflict
- invalid DOB/phone/address -> validation error
- provider tries to edit restricted profile fields -> forbidden
- consent baseline creation fails after patient create -> transaction rollback or compensating action required

---

## 8. Audit requirements

- patient create
- patient profile update
- restricted update attempt

---

## 9. Notifications

Optional for MVP:

- registration confirmation
- profile completion reminder if registration is partial

---

## 10. Acceptance criteria

- patient can be created with required fields only
- duplicate identifier handling is deterministic
- provider-assisted registration respects policy
- profile update only changes allowed fields
- all create/update paths emit audit records

---

## 11. Open design decisions

- whether self-registration is public or authenticated
- whether baseline consent defaults are created immediately or lazily
- whether draft registration is stored separately from final patient create

---

## 12. FCHV-Initiated Registration Flow (Added in v2.0)

**Actors:** FCHV (Female Community Health Volunteer) during home visit

**Entry point:** FCHV mobile app → "Register new patient" (icon-based)

**Steps:**

1. FCHV opens simplified registration flow (icon-based navigation, large touch targets)
2. FCHV scans patient's NID QR code (or enters name + DOB manually)
3. FCHV captures basic health data: blood type, known allergies (voice or tap)
4. System creates patient record in **pending** state (requires patient confirmation)
5. Patient receives SMS with confirmation link + OTP
6. Patient confirms registration and sets password/PIN
7. System upgrades patient status from **pending** to **active**
8. System auto-generates Emergency QR card data

**Offline behavior:** If FCHV has no connectivity, registration data is stored locally (encrypted SQLite) and synced when connectivity returns. Patient confirmation SMS is queued.

**Access control:** FCHV can only see patients they registered. FCHV cannot view full medical history.

**Global precedent:** Rwanda CHW tablet registration program, India ASHA worker digital tools.

---

## 13. Emergency QR Auto-Generation on Profile Complete (Added in v2.0)

After a patient completes their profile (blood type, allergies, emergency contacts, active medications), the system automatically:

1. Generates an Emergency QR payload (minimal JSON: blood type, allergies, active meds, emergency contacts)
2. Creates a printable QR card (credit-card size PDF template)
3. Stores the QR code in the patient's profile for mobile display
4. Alerts the patient: "Your Emergency QR card is ready. Print it for your wallet."

**Auto-refresh trigger:** Any change to blood type, allergy, active medication, or emergency contact regenerates the QR.

**Privacy note:** QR payload does NOT contain full name or NID number. Only initials + emergency data.

---

## 14. Security Considerations for Registration (Added in v2.0)

| Concern                        | Control                                                             |
| ------------------------------ | ------------------------------------------------------------------- |
| Bot registration abuse         | CAPTCHA or rate limit on registration endpoint (10 per IP per hour) |
| Duplicate patient records      | NID-based deduplication check before create                         |
| NID spoofing                   | NID verification against national registry (when API available)     |
| Registration data in transit   | TLS 1.3 mandatory                                                   |
| FCHV device loss               | Encrypted local storage + remote wipe capability                    |
| Under-age patient registration | Guardian consent required for patients under 18                     |
