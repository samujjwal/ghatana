# @yappc/initialization-ui

YAPPC domain UI library for project initialization flows.

## Purpose

Provides typed React components and domain types for the YAPPC project-initialization wizard (`InitializationWizardPage`) and progress tracker (`InitializationProgressPage`).

## Exports

### Types

| Type | Description |
|---|---|
| `CloudProvider` | Cloud deployment provider identifier |
| `Provider` | Service provider descriptor (id, name, features) |
| `ProgressStep` | A single step in an initialization sequence |
| `LogEntry` | Structured log entry produced during initialization |
| `Resource` | A cloud resource created during initialization |
| `WizardStep` | Step navigation item for wizard progress indicator |
| `InfrastructureValues` | Database/cache/storage configuration |
| `CostEstimates` | Monthly/annual cost breakdown |
| `Environment` | Per-environment variable configuration |
| `StepValidation` | Validation result for a wizard step |
| `WizardStepDefinition` | Step definition for `ConfigurationWizard` |

### Components

| Component | Description |
|---|---|
| `LiveProgressViewer` | Real-time step progress + log output |
| `ResourcesList` | List of created cloud resources with status |
| `StepProgress` | Horizontal/vertical wizard progress indicator |
| `ProviderSelector` | Provider selection widget (cards or list) |
| `InfrastructureForm` | Infrastructure configuration form |
| `CostEstimator` | Estimated cost display |
| `EnvironmentTabs` | Per-environment variable configuration tabs |
| `ConfigurationWizard` | Multi-step configuration wizard shell |

## Usage

```tsx
import {
  ConfigurationWizard,
  ProgressStep,
  WizardStepDefinition,
} from '@yappc/initialization-ui';
```

## Notes

- All components are product-specific and not suitable for the `@ghatana/*` platform scope.
- Alias resolved by Vite/Vitest to `libs/yappc-initialization-ui/src`.
- This package is internal to `products/yappc/frontend` and is not published.
