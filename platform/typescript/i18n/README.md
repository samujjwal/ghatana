# `@ghatana/i18n`

Shared internationalization framework for Ghatana React applications.

## Overview

Provides the `I18nProvider` context wrapper, locale initialization helpers, and pre-bundled locale definitions. Built on top of a lightweight i18n engine (no external runtime dependency bloat).

## Usage

```tsx
import { I18nProvider } from '@ghatana/i18n';
import { initI18n } from '@ghatana/i18n/init';

// Initialize once at app boot
await initI18n('en');

function App() {
  return (
    <I18nProvider>
      <MyApp />
    </I18nProvider>
  );
}
```

## API

| Export | Description |
|--------|-------------|
| `I18nProvider` | React context provider that makes translations available to the component tree |
| `initI18n(locale)` | Loads locale resources and sets the active language |

## Locale Files

Locale definitions live under `src/locales/` in JSON format. Add a new locale by dropping a JSON file there and exporting it from the locale index.

## Installation

```jsonc
// package.json
"dependencies": {
  "@ghatana/i18n": "workspace:*"
}
```
