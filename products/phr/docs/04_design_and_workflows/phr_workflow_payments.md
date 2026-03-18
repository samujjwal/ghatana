# PHR Workflow — Billing and Payments

**Version:** 2.0  
**Date:** 2026-03-17
**Phase:** Core MVP

| Field              | Value                                                                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                     |
| **Classification** | Internal — Restricted                                                                                                                 |
| **Last Review**    | 2026-03-17                                                                                                                            |
| **Companion Docs** | [Insurance workflow](phr_workflow_insurance_baseline.md), [Route Contract Pack](phr_mvp_route_contract_pack.md), [Screen Matrix](phr_screen_by_screen_mvp_implementation_matrix.md) |

> **📌 What changed in v2.0:** Added patient bill presentation, Nepal wallet payment initiation and confirmation, receipt generation, idempotent payment handling, and caregiver-scoped billing actions for MVP.

---

## 1. Goal

Allow patients and authorized caregivers or staff to view bills, initiate approved payments, confirm settlement, and retrieve receipts without exposing claim-management behavior that remains Phase 2.

---

## 2. Primary actors

- patient
- caregiver with billing scope
- provider or facility billing staff
- payment gateway callback actor

---

## 3. Entry points

- patient payments page
- caregiver dependent summary billing card
- staff-assisted billing desk workflow

APIs:

- `GET /api/v1/patients/:id/bills`
- `POST /api/v1/payments`
- `GET /api/v1/payments/:id`
- `POST /api/v1/payments/:id/confirm`

---

## 4. Preconditions

- target invoice exists and is payable
- actor is allowed to see and act on that invoice
- payment method is enabled for the tenant and facility
- idempotency key is present for payment initiation
- gateway callback validation secrets and redirect URLs are configured

---

## 5. Data touched

- `Invoice`
- `PaymentNotice`
- `PaymentReconciliation`
- `AuditLog`

---

## 6. Happy path

### 6.1 View bills

1. actor opens billing surface
2. bill list returns open, partially paid, paid, or void invoices allowed by policy
3. outstanding amount, due date, and receipt availability are displayed

### 6.2 Initiate payment

1. actor selects an unpaid invoice
2. client submits payment request with amount, method, and idempotency key
3. platform validates invoice state, outstanding balance, actor permissions, and method availability
4. payment intent is created and persisted as `PENDING_REDIRECT` or `PENDING_CONFIRMATION`
5. gateway redirect URL or desk-confirmation path is returned
6. audit record is written for payment initiation

### 6.3 Confirm payment

1. gateway callback, patient redirect, or staff verification reaches confirmation endpoint
2. platform validates signature or confirmation metadata
3. payment state transitions to `CONFIRMED` or `SETTLED`
4. invoice outstanding balance is updated through reconciliation logic
5. receipt artifact is generated and linked to the payment record
6. user can poll status or open the receipt link

---

## 7. Alternate and failure paths

- invoice already settled -> idempotent success or conflict according to policy
- amount exceeds outstanding balance -> validation error
- duplicate payment initiation with same idempotency key -> original payment response returned
- invalid wallet callback signature -> forbidden and audited
- reconciliation lag after gateway success -> payment remains `CONFIRMED` until settlement completes
- caregiver without billing scope -> forbidden without exposing financial details

---

## 8. UX requirements

- outstanding balance and payment status must be visually distinct
- wallet redirect flows must explain when the user is leaving the PHR surface
- receipt availability should appear only after confirmation or settlement
- failure states must clearly separate payment initiation failure from settlement delay
- all billing surfaces must show NPR currency formatting consistently

---

## 9. Acceptance criteria

- bill list reflects current invoice and payment state accurately
- payment initiation is idempotent and safe to retry
- payment confirmation accepts both redirect and webhook-style completion paths
- settled payments provide receipt metadata and auditable evidence
- caregiver and staff actions respect delegated or tenant billing policy

---

## 10. Operational and reconciliation behavior

- `PaymentNotice` stores gateway-facing transaction status
- `PaymentReconciliation` resolves gateway success, invoice state, and receipt generation
- gateway outages do not duplicate invoices or create duplicate successful payments
- monitoring must alert on high callback-failure rates and reconciliation backlog growth

---

## 11. Security and compliance notes

- billing responses must avoid exposing unrelated invoice history outside the patient or dependent scope
- payment callback signatures and return URLs must be validated server-side
- no card or wallet secret material is stored in patient-facing logs
- payment actions are auditable and retained per the active finance and privacy policy
- claim submission and adjudication remain out of scope for this workflow and belong to Phase 2