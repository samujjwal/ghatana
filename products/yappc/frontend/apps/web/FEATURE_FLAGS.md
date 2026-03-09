# Feature Flag System

YAPPC uses [GrowthBook](https://www.growthbook.io/) for centralized feature flag management, replacing ad-hoc `VITE_FEATURE_*` environment variables.

## Overview

Feature flags enable:
- **Gradual rollouts** — Release features to a percentage of users
- **A/B testing** — Test multiple variants of a feature
- **Kill switches** — Quickly disable problematic features
- **Targeting** — Enable features for specific users, tenants, or roles
- **Environment control** — Different flags for dev/staging/prod

## Setup

### 1. Install Dependencies

```bash
pnpm install @growthbook/growthbook-react
```

### 2. Configure Environment Variables

Create `.env.local`:

```bash
# GrowthBook Configuration
VITE_GROWTHBOOK_API_HOST=https://cdn.growthbook.io
VITE_GROWTHBOOK_CLIENT_KEY=sdk-your-client-key-here
```

**Note:** If `VITE_GROWTHBOOK_CLIENT_KEY` is not set, the app will use default feature values defined in `FeatureFlagProvider.tsx`.

### 3. GrowthBook Dashboard Setup

1. Sign up at [app.growthbook.io](https://app.growthbook.io)
2. Create a new project for YAPPC
3. Copy the SDK Client Key
4. Define features in the GrowthBook dashboard

## Usage

### Checking Feature Flags

```tsx
import { useFeatureFlag, FeatureFlag } from '../providers/FeatureFlagProvider';

function MyComponent() {
  const { isFeatureEnabled } = useFeatureFlag();

  if (!isFeatureEnabled(FeatureFlag.AI_SUGGESTIONS)) {
    return null; // Feature disabled
  }

  return <AISuggestionsPanel />;
}
```

### Getting Feature Values

Feature flags can have values beyond boolean:

```tsx
import { useFeatureFlag, FeatureFlag } from '../providers/FeatureFlagProvider';

function MyComponent() {
  const { getFeatureValue } = useFeatureFlag();

  const maxSuggestions = getFeatureValue(FeatureFlag.AI_SUGGESTIONS, 5);
  const theme = getFeatureValue('ui-theme', 'light');

  return <div>Showing {maxSuggestions} suggestions</div>;
}
```

### Using GrowthBook Hooks Directly

For advanced use cases, use GrowthBook hooks directly:

```tsx
import { useFeature } from '@growthbook/growthbook-react';

function MyComponent() {
  const feature = useFeature('ai-suggestions');

  if (!feature.on) {
    return null;
  }

  return <div>Value: {feature.value}</div>;
}
```

## Available Feature Flags

All feature flags are defined in the `FeatureFlag` enum in `FeatureFlagProvider.tsx`:

### Onboarding & UX
- `onboarding` — User onboarding flow
- `canvas-calm-mode` — Minimal UI mode for canvas
- `command-palette` — Keyboard-driven command palette

### AI Features
- `ai-suggestions` — AI-powered suggestions
- `ai-canvas-assistant` — AI assistant for canvas
- `ai-code-review` — AI code review integration

### Collaboration
- `real-time-collaboration` — Real-time collaborative editing
- `canvas-comments` — Comments on canvas elements

### Advanced Features
- `approval-workflows` — Multi-stage approval workflows
- `agent-orchestration` — AI agent orchestration
- `canvas-versioning` — Canvas version control

### Experimental
- `canvas-3d-mode` — 3D canvas rendering (experimental)
- `voice-commands` — Voice command interface (experimental)

## Default Values

When GrowthBook is not configured, the app uses default values defined in `DEFAULT_FEATURES`:

```typescript
const DEFAULT_FEATURES: Record<FeatureFlag, boolean> = {
  [FeatureFlag.ONBOARDING]: true,
  [FeatureFlag.AI_SUGGESTIONS]: import.meta.env.DEV, // Enabled in dev only
  [FeatureFlag.REAL_TIME_COLLAB]: true,
  // ... etc
};
```

## Adding New Feature Flags

### 1. Add to FeatureFlag Enum

```typescript
// src/providers/FeatureFlagProvider.tsx
export enum FeatureFlag {
  // ... existing flags
  MY_NEW_FEATURE = 'my-new-feature',
}
```

### 2. Add Default Value

```typescript
const DEFAULT_FEATURES: Record<FeatureFlag, boolean> = {
  // ... existing defaults
  [FeatureFlag.MY_NEW_FEATURE]: false,
};
```

### 3. Create Feature in GrowthBook Dashboard

1. Go to Features → Create Feature
2. Key: `my-new-feature`
3. Type: Boolean (or JSON for complex values)
4. Default Value: `false`
5. Add targeting rules as needed

### 4. Use in Code

```tsx
import { useFeatureFlag, FeatureFlag } from '../providers/FeatureFlagProvider';

function MyComponent() {
  const { isFeatureEnabled } = useFeatureFlag();

  if (isFeatureEnabled(FeatureFlag.MY_NEW_FEATURE)) {
    return <NewFeature />;
  }

  return <OldFeature />;
}
```

## Targeting & Rollouts

### Percentage Rollout

Enable a feature for 25% of users:

```javascript
// In GrowthBook dashboard
{
  "condition": {
    "hashAttribute": "id",
    "hashVersion": 2,
    "ranges": [[0, 0.25]]
  },
  "force": true
}
```

### User Targeting

Enable for specific users:

```javascript
{
  "condition": {
    "id": {
      "$in": ["user-123", "user-456"]
    }
  },
  "force": true
}
```

### Role-Based Targeting

Enable for admins only:

```javascript
{
  "condition": {
    "role": "admin"
  },
  "force": true
}
```

## Migration from Environment Variables

### Before (Environment Variables)

```tsx
// ❌ Old way - hardcoded env vars
if (import.meta.env.VITE_FEATURE_ONBOARDING !== 'false') {
  return <OnboardingFlow />;
}
```

### After (Feature Flags)

```tsx
// ✅ New way - centralized feature flags
import { useFeatureFlag, FeatureFlag } from '../providers/FeatureFlagProvider';

function MyComponent() {
  const { isFeatureEnabled } = useFeatureFlag();

  if (isFeatureEnabled(FeatureFlag.ONBOARDING)) {
    return <OnboardingFlow />;
  }
}
```

### Backward Compatibility

For gradual migration, legacy env vars are still available:

```tsx
import { LEGACY_ENV_FLAGS } from '../providers/FeatureFlagProvider';

// ⚠️ Deprecated - migrate to FeatureFlag enum
if (LEGACY_ENV_FLAGS.ONBOARDING) {
  return <OnboardingFlow />;
}
```

## Best Practices

### ✅ DO

- **Use the FeatureFlag enum** — Type-safe, prevents typos
- **Set sensible defaults** — App should work without GrowthBook
- **Document new flags** — Update this README when adding flags
- **Clean up old flags** — Remove flags after full rollout
- **Use targeting wisely** — Start with small percentages
- **Test both states** — Ensure feature works when on AND off

### ❌ DON'T

- **Don't hardcode flag keys** — Use the enum
- **Don't nest flags deeply** — Keep flag checks at component boundaries
- **Don't use flags for config** — Use for feature toggles only
- **Don't forget fallbacks** — Always handle the "off" state
- **Don't leave experimental flags forever** — Clean up after decisions

## Monitoring

### Feature Usage Tracking

Feature flag evaluations are automatically tracked:

```typescript
// In FeatureFlagProvider.tsx
trackingCallback: (experiment, result) => {
  console.log('Feature viewed:', {
    experimentId: experiment.key,
    variationId: result.variationId,
    value: result.value,
  });
  
  // TODO: Send to analytics service
  // analytics.track('Feature Flag Viewed', { ... });
}
```

### GrowthBook Dashboard

Monitor feature usage in the GrowthBook dashboard:
- Feature evaluation counts
- User distribution across variants
- Experiment results

## Troubleshooting

### Features Not Loading

**Symptom:** All features use default values

**Solutions:**
1. Check `VITE_GROWTHBOOK_CLIENT_KEY` is set
2. Verify GrowthBook API is reachable
3. Check browser console for errors
4. Verify features are published in GrowthBook dashboard

### Feature Always Off

**Symptom:** Feature is enabled in dashboard but off in app

**Solutions:**
1. Check targeting rules in GrowthBook
2. Verify user attributes are set correctly
3. Check feature key matches enum value
4. Clear browser cache and reload

### TypeScript Errors

**Symptom:** `FeatureFlag.X does not exist`

**Solution:** Add the flag to the `FeatureFlag` enum in `FeatureFlagProvider.tsx`

## References

- [GrowthBook Documentation](https://docs.growthbook.io/)
- [GrowthBook React SDK](https://docs.growthbook.io/lib/react)
- [Feature Flag Best Practices](https://docs.growthbook.io/guide/feature-flags)
