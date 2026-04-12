# Ghatana Web Renderer - Vanilla Example

This directory demonstrates how to use `@ghatana/ui-builder/web` in a non-React context.

## Usage

The vanilla web renderer provides two main functions:

### `serializeToHtml(document, config)`

Converts a `BuilderDocument` to an HTML string. Useful for:
- Server-side rendering
- Email templates
- Static site generation
- Export functionality

```typescript
import { serializeToHtml } from '@ghatana/ui-builder/web';

const html = serializeToHtml(document, {
  includeDebugAttributes: false,
  escapeContent: true,
});
```

### `mountToDOM(document, container, config)`

Mounts a `BuilderDocument` directly to a DOM element. Useful for:
- Vanilla JavaScript applications
- Legacy integrations
- Non-React frameworks

```typescript
import { mountToDOM } from '@ghatana/ui-builder/web';

const cleanup = mountToDOM(document, document.getElementById('root'), {
  includeDebugAttributes: true,
});

// Call cleanup when done
cleanup();
```

## When to Use Vanilla Web Target

The vanilla web target is justified for non-React product surfaces such as:

1. **Email templates** - Render BuilderDocument as static HTML for email campaigns
2. **Static documentation** - Generate HTML docs from BuilderDocument without React runtime
3. **Legacy applications** - Integrate UI Builder output into existing vanilla JS apps
4. **Server-side rendering** - Generate HTML on the server for better performance
5. **Export functionality** - Allow users to export their designs as standalone HTML

## See Also

- `@ghatana/ui-builder/web` package documentation
- `@ghatana/ui-builder/react` for React-based rendering
