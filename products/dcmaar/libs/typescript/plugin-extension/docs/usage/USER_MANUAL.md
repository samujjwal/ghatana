# DCMaar – Plugin Extension – User Manual

## 1. Audience

This manual is for TypeScript engineers integrating device monitoring plugins from `@dcmaar/plugin-extension` into DCMaar apps.

## 2. Basic Usage

1. Add `@dcmaar/plugin-extension`, `@dcmaar/plugin-abstractions`, and `@dcmaar/types` as dependencies.
2. Register provided plugins with the host application’s plugin system.
3. Configure sampling intervals and thresholds through the host app’s configuration.

## 3. Best Practices

- Keep plugin configuration declarative and environment-specific.
- Use shared abstractions consistently so plugins remain portable across apps.

This manual is self-contained and explains how to use `@dcmaar/plugin-extension` in typical flows.
