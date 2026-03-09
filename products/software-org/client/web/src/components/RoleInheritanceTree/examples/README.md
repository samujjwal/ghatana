# RoleInheritanceTree Interactive Demo

This directory contains interactive examples demonstrating RoleInheritanceTree usage patterns.

## Available Demos

### 1. Basic Usage (`BasicDemo.tsx`)
Simple role hierarchy with 3 levels, demonstrating:
- Automatic hierarchical layout
- Node selection
- Permission display

### 2. Permission Explorer (`PermissionExplorerDemo.tsx`)
Interactive permission highlighting, demonstrating:
- Search permissions by name
- Highlight roles with specific permissions
- Filter roles by permission type
- Permission inheritance visualization

### 3. Role Editor (`RoleEditorDemo.tsx`)
Full CRUD operations, demonstrating:
- Create new roles
- Edit existing roles
- Delete roles
- Add/remove parent relationships
- Real-time tree updates

### 4. Export/Import (`ExportImportDemo.tsx`)
Data portability, demonstrating:
- Export hierarchy to JSON/CSV
- Import from external sources
- Validate hierarchy structure
- Preview before import

### 5. Performance Benchmark (`PerformanceBenchmarkDemo.tsx`)
Test with large hierarchies, demonstrating:
- Generate hierarchies of various sizes
- Measure render performance
- Compare optimization strategies
- Memory usage tracking

## Running the Demos

### Development

```bash
cd products/software-org/apps/web
pnpm dev
```

Then navigate to: http://localhost:5173/demos/role-inheritance-tree

### Storybook

```bash
pnpm storybook
```

Then navigate to: Component > RoleInheritanceTree > Stories

## File Structure

```
examples/
├── README.md                           # This file
├── BasicDemo.tsx                       # Simple usage
├── PermissionExplorerDemo.tsx          # Permission highlighting
├── RoleEditorDemo.tsx                  # CRUD operations
├── ExportImportDemo.tsx                # Import/export
├── PerformanceBenchmarkDemo.tsx        # Performance testing
└── shared/
    ├── mockData.ts                     # Sample role hierarchies
    ├── DemoContainer.tsx               # Demo wrapper component
    └── DemoControls.tsx                # Shared control panel
```

## Integration Examples

### With React Query

```tsx
import { useQuery } from '@tanstack/react-query';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function RoleTreeWithQuery({ personaId }: { personaId: string }) {
    const { data: roles, isLoading } = useQuery({
        queryKey: ['roles', personaId],
        queryFn: () => fetchRoles(personaId),
    });

    if (isLoading) return <Spinner />;

    return <RoleInheritanceTree personaId={personaId} />;
}
```

### With State Management

```tsx
import { useAtom } from 'jotai';
import { selectedRoleAtom } from '@/stores/roleStore';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function RoleTreeWithState({ personaId }: { personaId: string }) {
    const [selectedRole, setSelectedRole] = useAtom(selectedRoleAtom);

    return (
        <RoleInheritanceTree
            personaId={personaId}
            onNodeClick={(role) => setSelectedRole(role)}
        />
    );
}
```

### With Search/Filter

```tsx
import { useState } from 'react';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function RoleTreeWithSearch({ personaId }: { personaId: string }) {
    const [searchQuery, setSearchQuery] = useState('');
    const [highlightPermission, setHighlightPermission] = useState<string>();

    return (
        <div>
            <input
                type="text"
                placeholder="Search permissions..."
                value={searchQuery}
                onChange={(e) => {
                    setSearchQuery(e.target.value);
                    setHighlightPermission(e.target.value || undefined);
                }}
            />
            <RoleInheritanceTree
                personaId={personaId}
                highlightPermission={highlightPermission}
            />
        </div>
    );
}
```

## Common Patterns

### 1. Lazy Loading

For large hierarchies, load roles on demand:

```tsx
import { useState, useCallback } from 'react';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function LazyRoleTree({ personaId }: { personaId: string }) {
    const [expandedRoles, setExpandedRoles] = useState<Set<string>>(new Set());

    const handleNodeClick = useCallback(async (role: RoleDefinition) => {
        if (!expandedRoles.has(role.id)) {
            // Load children on first expand
            await fetchRoleChildren(role.id);
            setExpandedRoles(prev => new Set(prev).add(role.id));
        }
    }, [expandedRoles]);

    return (
        <RoleInheritanceTree
            personaId={personaId}
            onNodeClick={handleNodeClick}
        />
    );
}
```

### 2. Real-Time Updates

Subscribe to role changes via WebSocket:

```tsx
import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function RealTimeRoleTree({ personaId }: { personaId: string }) {
    const queryClient = useQueryClient();

    useEffect(() => {
        const socket = io('/roles');

        socket.on('role:updated', (updatedRole) => {
            queryClient.invalidateQueries(['roles', personaId]);
        });

        return () => socket.disconnect();
    }, [personaId, queryClient]);

    return <RoleInheritanceTree personaId={personaId} />;
}
```

### 3. Custom Styling

Override default styles with Tailwind classes:

```tsx
import { RoleInheritanceTree } from '@/components/RoleInheritanceTree';

function StyledRoleTree({ personaId }: { personaId: string }) {
    return (
        <div className="custom-role-tree">
            <style>{`
                .custom-role-tree .react-flow__node {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    border-radius: 12px;
                    padding: 16px;
                }
                
                .custom-role-tree .react-flow__node.highlighted {
                    box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.5);
                }
            `}</style>
            <RoleInheritanceTree personaId={personaId} />
        </div>
    );
}
```

## Performance Tips

1. **Memoize callbacks**: Use `useCallback` for event handlers
2. **Optimize re-renders**: Only update when necessary
3. **Virtualize large trees**: Consider windowing for 100+ nodes
4. **Debounce search**: Wait 300ms before updating highlights
5. **Lazy load images**: If roles have avatars, load on demand

## Accessibility

All demos follow WCAG 2.1 AA guidelines:

- **Keyboard navigation**: Tab through nodes, Enter to select
- **Screen readers**: ARIA labels on all interactive elements
- **Focus management**: Visible focus indicators
- **Color contrast**: 4.5:1 minimum ratio
- **Zoom support**: Scales to 200% without loss of functionality

## Testing

Each demo includes:
- Unit tests for component logic
- Integration tests for user flows
- Visual regression tests (Chromatic)
- Accessibility tests (axe-core)

Run tests:

```bash
# Unit tests
pnpm test examples/RoleInheritanceTree

# Integration tests
pnpm test:integration examples/RoleInheritanceTree

# Visual tests
pnpm chromatic
```

## Contributing

To add a new demo:

1. Create `YourDemo.tsx` in this directory
2. Add entry to `index.ts`
3. Update this README
4. Add tests in `__tests__/YourDemo.test.tsx`
5. Submit PR with demo screenshot

## Resources

- [Component API Documentation](../README.md)
- [Testing Guide](../../../docs/TESTING_GUIDE.md)
- [Performance Guide](../../../docs/PERFORMANCE_GUIDE.md)
- [React Flow Documentation](https://reactflow.dev/)

---

**Last Updated**: November 25, 2025  
**Version**: 1.0.0
