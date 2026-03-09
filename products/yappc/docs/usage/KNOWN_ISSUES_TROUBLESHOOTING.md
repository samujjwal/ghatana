# YAPPC – Known Issues & Troubleshooting

## 1. Symptoms: Backend/Frontend Mismatch

**Symptom:** Frontend features fail due to missing backend support.

**Troubleshooting:**

- Confirm compatible versions of backend and frontend.
- Verify feature flags and environment configuration.

## 2. Symptoms: State Inconsistency

**Symptom:** App-creator state behaves inconsistently.

**Troubleshooting:**

- Review state management usage; ensure StateManager patterns are applied consistently.

## 3. Symptoms: Missing Metrics or Observability

**Symptom:** YAPPC services appear healthy but you lack visibility into behavior.

**Troubleshooting:**

- Confirm services emit metrics through shared observability abstractions.
- Check that dashboards and alerts are configured for key flows.

This guide is self-contained and highlights likely categories of YAPPC issues.
