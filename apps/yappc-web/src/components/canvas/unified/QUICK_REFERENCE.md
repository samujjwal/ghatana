# Quick Reference: Adding a New Panel

## 1. Create Panel Component

```typescript
// panels/MyNewPanel.tsx
import { Box, Typography } from '@yappc/ui';
import type { RailPanelProps } from '../UnifiedLeftRail.types';

export function MyNewPanel({ context, nodes, onInsertNode }: RailPanelProps) {
  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="subtitle2">My New Panel</Typography>
      <Typography variant="body2">Content goes here</Typography>
    </Box>
  );
}
```

## 2. Export from index

```typescript
// panels/index.ts
export { MyNewPanel } from './MyNewPanel';
```

## 3. Register in panel-registry.ts

```typescript
import { MyNewPanel } from './panels';

// In registerDefaultPanels():
this.register({
  id: 'mynewpanel',
  label: 'My Panel',
  icon: '🎯',
  category: 'utility',
  order: 95,
  component: MyNewPanel,
  description: 'My custom panel',
  visibility: {
    modes: ['design', 'code'], // When to show
    roles: ['Developer'], // Optional: role filter
    phases: ['BUILD'], // Optional: phase filter
  },
});
```

## 4. Add to RailTabId type

```typescript
// UnifiedLeftRail.types.ts
export type RailTabId =
  | 'assets'
  | 'layers'
  | 'components'
  | 'infrastructure'
  | 'history'
  | 'files'
  | 'data'
  | 'ai'
  | 'favorites'
  | 'mynewpanel'; // ← Add here
```

## 5. Add visibility rule (optional)

```typescript
// rail-config.ts
export const PANEL_VISIBILITY_RULES: Record<RailTabId, VisibilityRule> = {
  // ... existing rules
  mynewpanel: {
    modes: ['design', 'code'],
    condition: (context) => {
      // Custom logic
      return context.role === 'Developer';
    },
  },
};
```

## Done!

Your panel will now:

- Appear in the left rail when conditions match
- Receive context, nodes, and event handlers
- Auto-hide/show based on mode/role/phase
- Be included in panel registry

---

# Quick Reference: Context Structure

## RailContext

```typescript
{
  mode: 'design' | 'code' | 'architecture' | ...,  // Canvas mode
  role: 'Designer' | 'Developer' | ...,            // User role
  phase: 'IDEATE' | 'BUILD' | 'DEPLOY' | ...       // Lifecycle phase
}
```

## RailPanelProps

```typescript
{
  context: RailContext,           // Current mode/role/phase
  nodes?: any[],                  // Canvas nodes (for LayersPanel)
  selectedNodeIds?: string[],     // Selected nodes
  onInsertNode?: (...) => void,   // Insert asset/node
  onSelectNode?: (...) => void,   // Select a node
  onUpdateNode?: (...) => void,   // Update node data
  onDeleteNode?: (...) => void,   // Delete a node
  onToggleVisibility?: (...) => void, // Toggle visibility
  onToggleLock?: (...) => void,   // Toggle lock state
}
```

---

# Quick Reference: Adding Assets

## 1. Define Asset Template

```typescript
// In panels/AssetsPanel.tsx (or separate file)
const myAssets: AssetTemplate[] = [
  {
    id: 'custom-shape',
    name: 'Custom Shape',
    icon: '🟦',
    type: 'rectangle',
    category: 'basic',
    defaultSize: { width: 100, height: 100 },
    defaultData: { color: '#0088ff' },
    tags: ['custom', 'shape'],
  },
];
```

## 2. Add to Category Metadata

```typescript
// rail-config.ts
export const ASSET_CATEGORY_META: Record<AssetCategory, CategoryMetadata> = {
  // ... existing categories
  mycategory: {
    id: 'mycategory',
    label: 'My Category',
    icon: '🎨',
    color: '#ff6b6b',
    order: 50,
    visibility: {
      modes: ['design'],
      condition: (context) => context.role === 'Designer',
    },
  },
};
```

## 3. Update AssetCategory Type

```typescript
// UnifiedLeftRail.types.ts
export type AssetCategory =
  | 'basic'
  | 'flowchart'
  // ...
  | 'mycategory'; // ← Add here
```

---

# Quick Reference: Visibility Rules

## Simple Mode Filter

```typescript
visibility: {
  modes: ['design', 'code'], // Show only in these modes
}
```

## Role + Phase Filter

```typescript
visibility: {
  modes: ['architecture'],
  roles: ['Architect', 'DevOps'],
  phases: ['BUILD', 'DEPLOY'],
}
```

## Custom Condition

```typescript
visibility: {
  condition: (context) => {
    // Complex logic
    return (
      context.mode === 'code' &&
      context.role?.includes('Developer') &&
      context.phase === 'BUILD'
    );
  },
}
```

## All Modes

```typescript
visibility: {
  modes: ['all'], // Shows in every mode
}
```

---

# Quick Reference: Plugin Development

```typescript
import { createPlugin, panelRegistry } from './panel-registry';

const myPlugin = createPlugin({
  id: 'my-plugin',
  name: 'My Plugin',
  version: '1.0.0',
  panels: [
    {
      id: 'mypanel' as RailTabId,
      label: 'My Panel',
      icon: '🔌',
      component: MyPanelComponent,
      visibility: { modes: ['code'] },
      order: 100,
    },
  ],
  initialize: (registry) => {
    console.log('Plugin loaded!');
    // Setup code
  },
  cleanup: () => {
    console.log('Plugin unloaded');
    // Teardown code
  },
});

// Install
panelRegistry.installPlugin(myPlugin);

// Uninstall
panelRegistry.uninstallPlugin('my-plugin');
```

---

# Quick Reference: Common Patterns

## Search in Panel

```typescript
const [searchQuery, setSearchQuery] = useState('');

const filteredItems = useMemo(() => {
  if (!searchQuery) return allItems;
  return allItems.filter(item =>
    item.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
}, [allItems, searchQuery]);

return (
  <TextField
    placeholder="Search..."
    value={searchQuery}
    onChange={(e) => setSearchQuery(e.target.value)}
  />
);
```

## Context-Aware Content

```typescript
export function MyPanel({ context }: RailPanelProps) {
  const content = useMemo(() => {
    if (context.mode === 'design') {
      return <DesignTools />;
    } else if (context.mode === 'code') {
      return <CodeTools />;
    }
    return <DefaultTools />;
  }, [context.mode]);

  return <Box>{content}</Box>;
}
```

## Insert Asset on Click

```typescript
const handleAssetClick = (asset: AssetTemplate) => {
  if (onInsertNode) {
    onInsertNode(
      {
        type: asset.type,
        ...asset.defaultData,
      },
      { x: 100, y: 100 } // Optional position
    );
  }
};
```

---

# Quick Reference: Debugging

## Check Visible Panels

```typescript
import { panelRegistry } from './panel-registry';

const context = { mode: 'design', role: 'Designer', phase: 'IDEATE' };
const visible = panelRegistry.getVisiblePanels(context);
console.log(
  'Visible panels:',
  visible.map((p) => p.label)
);
```

## Check Asset Filtering

```typescript
import { getVisibleAssetCategories } from './rail-config';

const context = { mode: 'architecture', role: 'Architect', phase: 'BUILD' };
const categories = getVisibleAssetCategories(context);
console.log('Visible categories:', categories);
```

## Check Panel Registration

```typescript
console.log('Registered panels:', panelRegistry.getAllPanelIds());
console.log('Installed plugins:', panelRegistry.getInstalledPlugins());
```
