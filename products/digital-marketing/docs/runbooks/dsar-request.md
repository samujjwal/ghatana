# DSAR Request Runbook

## Trigger

- A data subject requests export, delete, anonymize, correct, restrict, or marketing opt-out handling.
- A privacy operator creates a DSAR case for a contact or identifier.

## Immediate Containment

1. Validate requester identity and tenant/workspace scope.
2. Record request type, received date, jurisdiction, legal basis, and due date.
3. Apply suppression immediately for unsubscribe/do-not-contact requests.
4. Preserve consent proof and audit evidence.

## Investigation

1. Locate contact identifiers across contact, lead, opportunity, consent, suppression, AI artifact, audit, and connector records.
2. Confirm legal hold or contractual exceptions.
3. Determine whether data must be exported, deleted, anonymized, restricted, or corrected.
4. Identify downstream connectors that require deletion or suppression propagation.

## Recovery

1. Export permitted records with redacted third-party data for access requests.
2. Delete or anonymize records where legally allowed.
3. Revoke or suppress contact processing for marketing opt-out.
4. Propagate deletion/suppression to configured connectors where required.

## Verification

- DSAR case records action, actor, due date, completion timestamp, and evidence.
- Suppression checks block future contact/audience/export actions.
- Export/delete/anonymize results are reproducible from audit evidence.
- Completion happens within the documented SLA target.
