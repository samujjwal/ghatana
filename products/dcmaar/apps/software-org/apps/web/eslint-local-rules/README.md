# ESLint Local Rules

Custom ESLint rules for enforcing software-org code conventions.

## Rules

### `prefer-ghatana-ui`

Enforces importing @ghatana/ui components through the `@/components/ui` barrel export instead of directly from `@ghatana/ui`.

**Rationale:**
- Single source of truth for UI imports
- Easier to update if @ghatana/ui APIs change
- Consistent import patterns across codebase
- Hides implementation details (@ghatana/* packages internal)

**Auto-fix:** ✅ Yes - automatically rewrites imports

#### ❌ Incorrect

```typescript
import { Badge, Button, Card } from '@ghatana/ui';
```

#### ✅ Correct

```typescript
import { Badge, Button, Card } from '@/components/ui';
```

---

## Setup

### 1. Enable Rule in ESLint Config

Add to root `eslint.config.cjs`:

```javascript
import localRules from './products/dcmaar/apps/software-org/apps/web/eslint-local-rules';

export default [
  // ... existing config
  {
    files: ['products/dcmaar/apps/software-org/apps/web/**/*.{ts,tsx}'],
    plugins: {
      'local': localRules
    },
    rules: {
      'local/prefer-ghatana-ui': 'error'
    }
  }
];
```

### 2. Run ESLint

```bash
# Check for violations
cd products/dcmaar/apps/software-org/apps/web
npx eslint src/

# Auto-fix violations
npx eslint src/ --fix
```

---

## Tracked Components

The rule tracks 50+ components across atoms, molecules, and organisms:

**Atoms:**
- Badge, Button, Checkbox, Icon, Input, Label, Radio, Select, Switch, Textarea, Toggle

**Molecules:**
- Card, Dropdown, Modal, Popover, Tooltip, Tabs, Accordion, Alert, Breadcrumb, Pagination

**Organisms:**
- Table, DataTable, Form, Navigation, Sidebar, Header, Footer, KpiCard

**Charts:**
- LineChart, AreaChart, BarChart, PieChart, ScatterChart

**See `rules/prefer-ghatana-ui.ts` for full list.**

---

## Adding New Rules

1. Create rule file: `rules/my-rule.ts`
2. Export rule with ESLint rule format:
   ```typescript
   export default {
     meta: { /* metadata */ },
     create(context) { /* rule logic */ }
   };
   ```
3. Export from `index.ts`:
   ```typescript
   import myRule from './rules/my-rule';
   
   export default {
     'my-rule': myRule
   };
   ```
4. Update this README with rule documentation

---

## Testing Rules

Create test file: `__tests__/my-rule.test.ts`

```typescript
import { RuleTester } from 'eslint';
import myRule from '../rules/my-rule';

const ruleTester = new RuleTester({
  parser: require.resolve('@typescript-eslint/parser'),
  parserOptions: { ecmaVersion: 2020, sourceType: 'module' }
});

ruleTester.run('my-rule', myRule, {
  valid: [
    { code: '/* valid code */' }
  ],
  invalid: [
    {
      code: '/* invalid code */',
      errors: [{ message: 'Expected error message' }]
    }
  ]
});
```

Run tests:
```bash
npm test -- eslint-local-rules
```

---

## Resources

- [ESLint Rule Documentation](https://eslint.org/docs/latest/developer-guide/working-with-rules)
- [AST Explorer](https://astexplorer.net/) - Explore JavaScript AST
- [@typescript-eslint/parser](https://typescript-eslint.io/packages/parser/) - TypeScript parser
