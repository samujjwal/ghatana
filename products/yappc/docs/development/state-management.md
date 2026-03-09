# State Management Guide

## Overview

YAPPC uses **Jotai** for global state management with a focus on atomic, composable state. This guide covers when to use different state management approaches and best practices.

## State Management Philosophy

- ✅ **Local state first:** Use React's `useState` for component-local state
- ✅ **Jotai atoms for global state:** Use atoms for state shared across components
- ✅ **Derived state:** Use Jotai's derived atoms for computed values
- ❌ **Avoid Context overuse:** Use Context only for theme, i18n, and auth
- ❌ **No Redux:** Redux is deprecated in YAPPC

## When to Use Each Approach

### 1. Local State (useState, useReducer)

Use for state that's only needed within a single component:

```typescript
import { useState } from 'react';

function Counter() {
  const [count, setCount] = useState(0);
  
  return (
    <button onClick={() => setCount(c => c + 1)}>
      Count: {count}
    </button>
  );
}
```

**Use cases:**
- Form input values
- UI toggle states (modals, dropdowns)
- Component-specific loading/error states
- Temporary data that doesn't need to persist

### 2. Jotai Atoms (Global State)

Use for state that needs to be shared across multiple components:

```typescript
import { atom, useAtom } from 'jotai';

// Define atom
export const projectIdAtom = atom<string | null>(null);

// Use in components
function ProjectSelector() {
  const [projectId, setProjectId] = useAtom(projectIdAtom);
  
  return (
    <select value={projectId ?? ''} onChange={e => setProjectId(e.target.value)}>
      {/* options */}
    </select>
  );
}

function ProjectDisplay() {
  const [projectId] = useAtom(projectIdAtom);
  return <div>Project: {projectId}</div>;
}
```

**Use cases:**
- User authentication state
- Current project/workspace
- Canvas document state
- UI preferences (theme, sidebar state)
- Collaboration presence

### 3. Context (Limited Use)

Use **only** for cross-cutting concerns that need to be available everywhere:

```typescript
import { createContext, useContext } from 'react';

const ThemeContext = createContext<Theme>(defaultTheme);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  return (
    <ThemeContext.Provider value={theme}>
      {children}
    </ThemeContext.Provider>
  );
}

export const useTheme = () => useContext(ThemeContext);
```

**Use cases (ONLY):**
- Theme/styling system
- Internationalization (i18n)
- Authentication provider
- Feature flags

## Jotai Atom Patterns

### 1. Primitive Atoms

Simple value atoms:

```typescript
import { atom } from 'jotai';

// String atom
export const projectIdAtom = atom<string | null>(null);

// Number atom
export const zoomLevelAtom = atom<number>(1);

// Boolean atom
export const isSidebarOpenAtom = atom<boolean>(true);

// Object atom
export const userAtom = atom<User | null>(null);

// Array atom
export const selectedNodesAtom = atom<string[]>([]);
```

### 2. Derived Atoms (Read-Only)

Compute values from other atoms:

```typescript
import { atom } from 'jotai';

// Base atoms
const projectIdAtom = atom<string | null>(null);
const projectsAtom = atom<Project[]>([]);

// Derived atom (read-only)
export const currentProjectAtom = atom((get) => {
  const id = get(projectIdAtom);
  const projects = get(projectsAtom);
  return projects.find(p => p.id === id) ?? null;
});

// Derived atom with computation
export const projectNameAtom = atom((get) => {
  const project = get(currentProjectAtom);
  return project?.name ?? 'No Project';
});

// Derived atom with filtering
export const completedTasksAtom = atom((get) => {
  const tasks = get(tasksAtom);
  return tasks.filter(t => t.status === 'completed');
});
```

### 3. Write-Only Atoms (Actions)

Atoms that perform actions without storing state:

```typescript
import { atom } from 'jotai';

// Action atom to add a task
export const addTaskAtom = atom(
  null,  // No read value
  (get, set, task: Task) => {
    const tasks = get(tasksAtom);
    set(tasksAtom, [...tasks, task]);
  }
);

// Usage
function AddTaskButton() {
  const [, addTask] = useAtom(addTaskAtom);
  
  return (
    <button onClick={() => addTask({ id: '1', title: 'New Task' })}>
      Add Task
    </button>
  );
}
```

### 4. Read-Write Atoms (Computed with Setter)

Atoms with custom read and write logic:

```typescript
import { atom } from 'jotai';

// Base atom
const countAtom = atom(0);

// Computed atom with custom setter
export const doubledCountAtom = atom(
  (get) => get(countAtom) * 2,  // Read: return doubled value
  (get, set, newValue: number) => {
    set(countAtom, newValue / 2);  // Write: divide by 2
  }
);
```

### 5. Async Atoms

Handle asynchronous data fetching:

```typescript
import { atom } from 'jotai';

// Async data atom
export const projectDataAtom = atom(async (get) => {
  const projectId = get(projectIdAtom);
  if (!projectId) return null;
  
  const response = await fetch(`/api/projects/${projectId}`);
  return response.json();
});

// Usage with Suspense
function ProjectData() {
  const [project] = useAtom(projectDataAtom);
  return <div>{project.name}</div>;
}

// Wrap with Suspense
<Suspense fallback={<Loading />}>
  <ProjectData />
</Suspense>
```

### 6. Atom with Storage

Persist atoms to localStorage:

```typescript
import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';

// Automatically persisted to localStorage
export const themeAtom = atomWithStorage<'light' | 'dark'>(
  'theme',  // localStorage key
  'light'   // default value
);

// Usage (same as regular atom)
const [theme, setTheme] = useAtom(themeAtom);
```

## Atom Organization

### File Structure

```
libs/state/src/
├── atoms/
│   ├── auth.ts           # Authentication atoms
│   ├── canvas.ts         # Canvas document atoms
│   ├── collaboration.ts  # Real-time collab atoms
│   ├── project.ts        # Project management atoms
│   ├── ui.ts             # UI state atoms
│   └── index.ts          # Export all atoms
├── hooks/
│   ├── useAuth.ts        # Custom auth hooks
│   ├── useProject.ts     # Custom project hooks
│   └── index.ts
└── index.ts
```

### Naming Conventions

```typescript
// ✅ GOOD: Descriptive names with "Atom" suffix
export const projectIdAtom = atom<string | null>(null);
export const selectedNodesAtom = atom<string[]>([]);
export const isSidebarOpenAtom = atom<boolean>(true);

// ✅ GOOD: Action atoms with verb prefix
export const addProjectAtom = atom(/* ... */);
export const deleteNodeAtom = atom(/* ... */);
export const updateCanvasAtom = atom(/* ... */);

// ❌ BAD: Generic names without context
export const idAtom = atom<string | null>(null);
export const dataAtom = atom<any>(null);
```

### Atom Documentation

```typescript
/**
 * Current project ID atom.
 * 
 * @example
 * const [projectId, setProjectId] = useAtom(projectIdAtom);
 * setProjectId('project-123');
 */
export const projectIdAtom = atom<string | null>(null);

/**
 * Derived atom that computes the current project from projectIdAtom.
 * Returns null if no project is selected or project not found.
 * 
 * @example
 * const [currentProject] = useAtom(currentProjectAtom);
 * console.log(currentProject?.name);
 */
export const currentProjectAtom = atom((get) => {
  const id = get(projectIdAtom);
  const projects = get(projectsAtom);
  return projects.find(p => p.id === id) ?? null;
});
```

## Custom Hooks

Wrap atoms in custom hooks for better ergonomics:

```typescript
// atoms/auth.ts
export const userAtom = atom<User | null>(null);
export const isAuthenticatedAtom = atom((get) => get(userAtom) !== null);

// hooks/useAuth.ts
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { userAtom, isAuthenticatedAtom } from '../atoms/auth';

export function useAuth() {
  const [user, setUser] = useAtom(userAtom);
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);
  
  const login = useCallback(async (credentials: Credentials) => {
    const user = await authService.login(credentials);
    setUser(user);
  }, [setUser]);
  
  const logout = useCallback(() => {
    setUser(null);
  }, [setUser]);
  
  return {
    user,
    isAuthenticated,
    login,
    logout,
  };
}

// Usage
function Header() {
  const { user, logout } = useAuth();
  return <button onClick={logout}>{user?.name}</button>;
}
```

## Performance Optimization

### 1. Use useAtomValue for Read-Only

```typescript
import { useAtomValue, useSetAtom } from 'jotai';

// ✅ GOOD: Read-only, won't re-render on write
function DisplayName() {
  const user = useAtomValue(userAtom);
  return <div>{user?.name}</div>;
}

// ✅ GOOD: Write-only, won't re-render on read
function UpdateButton() {
  const setUser = useSetAtom(userAtom);
  return <button onClick={() => setUser({ name: 'John' })}>Update</button>;
}

// ❌ BAD: Re-renders on every change even if not using setter
function DisplayNameBad() {
  const [user, setUser] = useAtom(userAtom);  // setUser not used!
  return <div>{user?.name}</div>;
}
```

### 2. Split Large Atoms

```typescript
// ❌ BAD: Monolithic atom
const appStateAtom = atom({
  user: null,
  projects: [],
  selectedProjectId: null,
  theme: 'light',
  sidebarOpen: true,
});

// ✅ GOOD: Separate atoms
const userAtom = atom<User | null>(null);
const projectsAtom = atom<Project[]>([]);
const selectedProjectIdAtom = atom<string | null>(null);
const themeAtom = atom<'light' | 'dark'>('light');
const isSidebarOpenAtom = atom<boolean>(true);
```

### 3. Use Derived Atoms for Computed Values

```typescript
// ❌ BAD: Computing in component
function TaskStats() {
  const [tasks] = useAtom(tasksAtom);
  const completed = tasks.filter(t => t.status === 'completed').length;
  const total = tasks.length;
  return <div>{completed}/{total}</div>;
}

// ✅ GOOD: Derived atom
const completedTaskCountAtom = atom((get) => {
  const tasks = get(tasksAtom);
  return tasks.filter(t => t.status === 'completed').length;
});

const totalTaskCountAtom = atom((get) => get(tasksAtom).length);

function TaskStats() {
  const completed = useAtomValue(completedTaskCountAtom);
  const total = useAtomValue(totalTaskCountAtom);
  return <div>{completed}/{total}</div>;
}
```

## Testing State

### Testing Atoms

```typescript
import { renderHook } from '@testing-library/react';
import { useAtom } from 'jotai';
import { projectIdAtom } from '../atoms/project';

describe('projectIdAtom', () => {
  it('should have null as default value', () => {
    const { result } = renderHook(() => useAtom(projectIdAtom));
    expect(result.current[0]).toBeNull();
  });
  
  it('should update value', () => {
    const { result } = renderHook(() => useAtom(projectIdAtom));
    const [, setProjectId] = result.current;
    
    act(() => {
      setProjectId('project-123');
    });
    
    expect(result.current[0]).toBe('project-123');
  });
});
```

### Testing Components with Atoms

```typescript
import { render, screen } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { ProjectDisplay } from './ProjectDisplay';

describe('ProjectDisplay', () => {
  it('should display project name', () => {
    render(
      <JotaiProvider>
        <ProjectDisplay />
      </JotaiProvider>
    );
    
    expect(screen.getByText('No Project')).toBeInTheDocument();
  });
});
```

## Migration from Redux/Context

### Redux → Jotai

```typescript
// Before (Redux)
const projectSlice = createSlice({
  name: 'project',
  initialState: { id: null },
  reducers: {
    setProjectId: (state, action) => {
      state.id = action.payload;
    },
  },
});

// After (Jotai)
const projectIdAtom = atom<string | null>(null);
```

### Context → Jotai

```typescript
// Before (Context)
const ProjectContext = createContext<string | null>(null);

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projectId, setProjectId] = useState<string | null>(null);
  
  return (
    <ProjectContext.Provider value={projectId}>
      {children}
    </ProjectContext.Provider>
  );
}

// After (Jotai)
const projectIdAtom = atom<string | null>(null);

// No provider needed (implicit from Jotai root provider)
function App() {
  return <YourComponents />;
}
```

## Best Practices Summary

1. ✅ Use `useState` for local component state
2. ✅ Use Jotai atoms for shared global state
3. ✅ Create derived atoms for computed values
4. ✅ Use `useAtomValue` and `useSetAtom` for performance
5. ✅ Split large atoms into smaller focused atoms
6. ✅ Wrap atoms in custom hooks for better API
7. ✅ Use `atomWithStorage` for persistent state
8. ✅ Document atoms with JSDoc comments
9. ❌ Don't use Context except for theme/i18n/auth
10. ❌ Don't create monolithic state atoms
11. ❌ Don't compute values in components (use derived atoms)
12. ❌ Don't use Redux (deprecated)

## Common Patterns

### Loading States

```typescript
const dataAtom = atom(async () => {
  const response = await fetch('/api/data');
  return response.json();
});

function DataDisplay() {
  const [data] = useAtom(dataAtom);
  return <div>{data.value}</div>;
}

// Wrap with Suspense
<Suspense fallback={<Loading />}>
  <DataDisplay />
</Suspense>
```

### Optimistic Updates

```typescript
const tasksAtom = atom<Task[]>([]);

const addTaskOptimisticAtom = atom(
  null,
  async (get, set, task: Task) => {
    // Optimistically add to UI
    const tasks = get(tasksAtom);
    set(tasksAtom, [...tasks, task]);
    
    try {
      // Send to server
      await api.addTask(task);
    } catch (error) {
      // Rollback on error
      set(tasksAtom, tasks);
      throw error;
    }
  }
);
```

### Form State

```typescript
const formAtom = atom({
  name: '',
  email: '',
  password: '',
});

const formErrorsAtom = atom((get) => {
  const form = get(formAtom);
  const errors: Record<string, string> = {};
  
  if (!form.email.includes('@')) {
    errors.email = 'Invalid email';
  }
  
  if (form.password.length < 8) {
    errors.password = 'Password too short';
  }
  
  return errors;
});

const isFormValidAtom = atom((get) => {
  const errors = get(formErrorsAtom);
  return Object.keys(errors).length === 0;
});
```

## Additional Resources

- [Jotai Documentation](https://jotai.org/)
- [Jotai Examples](https://jotai.org/docs/basics/examples)
- [Jotai Utils](https://jotai.org/docs/utilities/storage)
