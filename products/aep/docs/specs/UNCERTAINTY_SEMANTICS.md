# Uncertainty Semantics

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contracts:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/model/UncertaintyContext.java`, `UncertaintyPropagator.java`

AEP inherits uncertainty handling from the adaptive ESP foundation and modernizes it for probabilistic inference, retrieval-grounded agents, online learning, and model calibration.

## Required Dimensions

```text
eventDetectionConfidence
attributeConfidence
temporalConfidence
sourceReliability
patternConfidence
modelConfidence
retrievalConfidence
inputCompleteness
calibrationScore
```

## Operator Requirements

```text
Every EventOperator must define how it propagates uncertainty.
Every AgentOperator must emit model/retrieval/evidence confidence.
Every PatternMatch must include confidence and evidence.
```

## Propagation Rules

| Operator | Required uncertainty behavior |
| --- | --- |
| `AND` | Combine operand confidence and penalize missing, duplicated, or low-reliability evidence. |
| `OR` | Use the best satisfying branch while preserving alternative evidence and branch confidence. |
| `SEQ` | Combine operand confidence with temporal ordering confidence. |
| `NOT` | Emit absence confidence based on source reliability, watermark progress, and allowed lateness. |
| `WITHIN` | Adjust confidence based on interval fit, late events, and temporal completeness. |
| `TIMES` | Compute confidence from count satisfaction, duplicate handling, and event quality. |
| `WINDOW` | Include window completeness, watermark state, and late-event policy. |
| `AGENT_*` | Include model confidence, retrieval confidence, evidence confidence, calibration, guardrail outcome, and input completeness. |

## Thresholds

Production patterns must declare thresholds for:

- minimum event confidence,
- minimum attribute confidence where attributes drive decisions,
- minimum pattern confidence,
- minimum model confidence for agent decisions,
- minimum retrieval confidence when RAG evidence is required.

## Late, Missing, and Duplicated Events

Late events, missing events, and duplicated events must be reflected in confidence. A pattern may incorporate, compensate, degrade, or reject late events according to its time and replay policy.
