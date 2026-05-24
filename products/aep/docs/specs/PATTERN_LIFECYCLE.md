# Pattern Lifecycle

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contracts:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/lifecycle`
**Current code contracts:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/pattern/lifecycle`

## Lifecycle States

```text
DRAFT
CANDIDATE
VALIDATED
SHADOW
RECOMMENDED
APPROVED
ACTIVE
DEGRADED
RETIRED
```

## Lifecycle Events

```text
pattern.created
pattern.validated
pattern.compiled
pattern.shadow_deployed
pattern.shadow_evaluated
pattern.recommended
pattern.review_requested
pattern.review_completed
pattern.approved
pattern.promoted
pattern.degraded
pattern.retired
pattern.rollback_requested
pattern.rollback_completed
```

## Promotion Rules

- Recommended patterns never become active automatically unless policy explicitly allows auto-promotion.
- Human, expert, and agent review is represented by events.
- Promotion emits auditable lifecycle events.
- Active patterns must have rollback records.
- Shadow patterns must not trigger production side effects.
- Recommended patterns require review unless tenant policy allows auto-promotion.

## Review Model

Review packets include:

- candidate PatternSpec,
- validation result,
- shadow metrics,
- false positive and false negative analysis,
- uncertainty evidence,
- agent review output when present,
- human or expert decision,
- promotion or rejection rationale.
