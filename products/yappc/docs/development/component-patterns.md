# Component Patterns and Best Practices

## Component Structure

### File Organization

Each component should have its own directory with the following structure:

```
Component/
├── Component.tsx              # Main component
├── Component.types.ts         # TypeScript types
├── Component.styles.ts        # Styled components (if using)
├── Component.stories.tsx      # Storybook stories
├── index.ts                   # Barrel export
└── __tests__/
    ├── Component.test.tsx     # Unit tests
    ├── Component.a11y.test.tsx # Accessibility tests
    └── Component.perf.test.tsx # Performance tests (if needed)
```

### Component Template

```typescript
// Button.tsx
import React from 'react';
import { cn } from '@yappc/ui/utils';
import type { ButtonProps } from './Button.types';

/**
 * Button component with multiple variants and sizes.
 * 
 * @example
 * <Button variant="primary" size="md" onClick={handleClick}>
 *   Click me
 * </Button>
 */
export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      variant = 'primary',
      size = 'md',
      disabled = false,
      loading = false,
      onClick,
      className,
      ...props
    },
    ref
  ) => {
    return (
      <button
        ref={ref}
        className={cn(
          'button',
          `button--${variant}`,
          `button--${size}`,
          {
            'button--disabled': disabled,
            'button--loading': loading,
          },
          className
        )}
        disabled={disabled || loading}
        onClick={onClick}
        {...props}
      >
        {loading ? <Spinner /> : children}
      </button>
    );
  }
);

Button.displayName = 'Button';
```

### Type Definitions

```typescript
// Button.types.ts
import type { ButtonHTMLAttributes } from 'react';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Button variant */
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
  
  /** Button size */
  size?: 'sm' | 'md' | 'lg';
  
  /** Disabled state */
  disabled?: boolean;
  
  /** Loading state */
  loading?: boolean;
  
  /** Click handler */
  onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
  
  /** Additional CSS classes */
  className?: string;
  
  /** Button content */
  children: React.ReactNode;
}
```

### Index Export

```typescript
// index.ts
export { Button } from './Button';
export type { ButtonProps } from './Button.types';
```

## Component Patterns

### 1. Controlled vs Uncontrolled

#### Controlled Component

```typescript
interface TextFieldProps {
  value: string;
  onChange: (value: string) => void;
}

export const TextField: React.FC<TextFieldProps> = ({ value, onChange }) => {
  return (
    <input
      value={value}
      onChange={(e) => onChange(e.target.value)}
    />
  );
};

// Usage
function Form() {
  const [name, setName] = useState('');
  return <TextField value={name} onChange={setName} />;
}
```

#### Uncontrolled Component

```typescript
interface TextFieldProps {
  defaultValue?: string;
  onValueChange?: (value: string) => void;
}

export const TextField: React.FC<TextFieldProps> = ({ 
  defaultValue, 
  onValueChange 
}) => {
  const ref = useRef<HTMLInputElement>(null);
  
  return (
    <input
      ref={ref}
      defaultValue={defaultValue}
      onChange={(e) => onValueChange?.(e.target.value)}
    />
  );
};

// Usage
function Form() {
  return <TextField defaultValue="Initial" onValueChange={console.log} />;
}
```

### 2. Compound Components

```typescript
// Dialog.tsx
interface DialogContextValue {
  open: boolean;
  onClose: () => void;
}

const DialogContext = React.createContext<DialogContextValue | null>(null);

export const Dialog: React.FC<DialogProps> & {
  Trigger: typeof DialogTrigger;
  Content: typeof DialogContent;
  Title: typeof DialogTitle;
  Description: typeof DialogDescription;
  Footer: typeof DialogFooter;
} = ({ children, open, onOpenChange }) => {
  return (
    <DialogContext.Provider value={{ open, onClose: () => onOpenChange(false) }}>
      {children}
    </DialogContext.Provider>
  );
};

const DialogTrigger: React.FC = ({ children }) => {
  const context = useContext(DialogContext);
  return <button onClick={() => context?.onClose()}>{children}</button>;
};

const DialogContent: React.FC = ({ children }) => {
  const context = useContext(DialogContext);
  if (!context?.open) return null;
  return <div className="dialog-content">{children}</div>;
};

// Usage
<Dialog open={open} onOpenChange={setOpen}>
  <Dialog.Trigger>Open</Dialog.Trigger>
  <Dialog.Content>
    <Dialog.Title>Title</Dialog.Title>
    <Dialog.Description>Description</Dialog.Description>
    <Dialog.Footer>
      <Button>Close</Button>
    </Dialog.Footer>
  </Dialog.Content>
</Dialog>
```

### 3. Render Props

```typescript
interface DataFetcherProps<T> {
  url: string;
  children: (data: T | null, loading: boolean, error: Error | null) => React.ReactNode;
}

export function DataFetcher<T>({ url, children }: DataFetcherProps<T>) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  
  useEffect(() => {
    fetch(url)
      .then(res => res.json())
      .then(setData)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [url]);
  
  return <>{children(data, loading, error)}</>;
}

// Usage
<DataFetcher<User> url="/api/user">
  {(user, loading, error) => {
    if (loading) return <Loading />;
    if (error) return <Error message={error.message} />;
    return <div>{user?.name}</div>;
  }}
</DataFetcher>
```

### 4. Custom Hooks

```typescript
// useToggle.ts
export function useToggle(initialValue = false): [boolean, {
  toggle: () => void;
  setTrue: () => void;
  setFalse: () => void;
  setValue: (value: boolean) => void;
}] {
  const [value, setValue] = useState(initialValue);
  
  const toggle = useCallback(() => setValue(v => !v), []);
  const setTrue = useCallback(() => setValue(true), []);
  const setFalse = useCallback(() => setValue(false), []);
  
  return [value, { toggle, setTrue, setFalse, setValue }];
}

// Usage
function Component() {
  const [isOpen, { toggle, setTrue, setFalse }] = useToggle();
  
  return (
    <>
      <button onClick={toggle}>Toggle</button>
      <button onClick={setTrue}>Open</button>
      <button onClick={setFalse}>Close</button>
    </>
  );
}
```

### 5. Polymorphic Components

```typescript
interface BoxProps<T extends React.ElementType = 'div'> {
  as?: T;
  children: React.ReactNode;
  className?: string;
}

type PolymorphicBoxProps<T extends React.ElementType> = BoxProps<T> &
  Omit<React.ComponentPropsWithoutRef<T>, keyof BoxProps<T>>;

export function Box<T extends React.ElementType = 'div'>({
  as,
  children,
  className,
  ...props
}: PolymorphicBoxProps<T>) {
  const Component = as || 'div';
  
  return (
    <Component className={cn('box', className)} {...props}>
      {children}
    </Component>
  );
}

// Usage
<Box>Default div</Box>
<Box as="section">Section</Box>
<Box as="button" onClick={handleClick}>Button</Box>
<Box as={Link} href="/home">Link</Box>
```

## Performance Optimization

### 1. React.memo

```typescript
// ✅ GOOD: Memoize expensive components
export const ExpensiveComponent = React.memo<ExpensiveComponentProps>(
  ({ data }) => {
    const processedData = expensiveCalculation(data);
    return <div>{processedData}</div>;
  },
  (prevProps, nextProps) => {
    // Custom comparison
    return prevProps.data.id === nextProps.data.id;
  }
);

// ❌ BAD: Don't memoize simple components
export const SimpleComponent = React.memo(({ text }: { text: string }) => {
  return <div>{text}</div>;  // Too simple to memoize
});
```

### 2. useCallback and useMemo

```typescript
function Component({ items }: { items: Item[] }) {
  // ✅ GOOD: Memoize callback passed to children
  const handleClick = useCallback((id: string) => {
    console.log('Clicked:', id);
  }, []);
  
  // ✅ GOOD: Memoize expensive calculations
  const sortedItems = useMemo(() => {
    return items.sort((a, b) => a.name.localeCompare(b.name));
  }, [items]);
  
  // ❌ BAD: Don't memoize simple values
  const doubledCount = useMemo(() => count * 2, [count]);  // Too simple
  
  return (
    <div>
      {sortedItems.map(item => (
        <ItemComponent key={item.id} item={item} onClick={handleClick} />
      ))}
    </div>
  );
}
```

### 3. Code Splitting

```typescript
// Lazy load components
const HeavyComponent = React.lazy(() => import('./HeavyComponent'));

function App() {
  return (
    <Suspense fallback={<Loading />}>
      <HeavyComponent />
    </Suspense>
  );
}

// Lazy load with named export
const HeavyComponent = React.lazy(() =>
  import('./HeavyComponent').then(module => ({ default: module.HeavyComponent }))
);
```

### 4. Virtualization

```typescript
import { FixedSizeList } from 'react-window';

function VirtualizedList({ items }: { items: Item[] }) {
  const Row = ({ index, style }: { index: number; style: React.CSSProperties }) => (
    <div style={style}>
      {items[index].name}
    </div>
  );
  
  return (
    <FixedSizeList
      height={600}
      itemCount={items.length}
      itemSize={50}
      width="100%"
    >
      {Row}
    </FixedSizeList>
  );
}
```

## Error Boundaries

```typescript
// ErrorBoundary.tsx
interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }
  
  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }
  
  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('Error caught by boundary:', error, errorInfo);
    this.props.onError?.(error, errorInfo);
  }
  
  render() {
    if (this.state.hasError) {
      return this.props.fallback || (
        <div className="error-boundary">
          <h2>Something went wrong</h2>
          <pre>{this.state.error?.message}</pre>
        </div>
      );
    }
    
    return this.props.children;
  }
}

// Usage
<ErrorBoundary fallback={<ErrorFallback />} onError={logError}>
  <App />
</ErrorBoundary>
```

## Accessibility

### 1. Semantic HTML

```typescript
// ✅ GOOD: Semantic elements
export const Article = ({ title, content }: ArticleProps) => (
  <article>
    <header>
      <h1>{title}</h1>
    </header>
    <section>{content}</section>
  </article>
);

// ❌ BAD: Div soup
export const Article = ({ title, content }: ArticleProps) => (
  <div>
    <div>
      <div>{title}</div>
    </div>
    <div>{content}</div>
  </div>
);
```

### 2. ARIA Attributes

```typescript
export const Dialog: React.FC<DialogProps> = ({ title, children, open, onClose }) => {
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="dialog-title"
      aria-describedby="dialog-description"
    >
      <h2 id="dialog-title">{title}</h2>
      <div id="dialog-description">{children}</div>
      <button onClick={onClose} aria-label="Close dialog">
        ×
      </button>
    </div>
  );
};
```

### 3. Keyboard Navigation

```typescript
export const Menu: React.FC<MenuProps> = ({ items }) => {
  const [selectedIndex, setSelectedIndex] = useState(0);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);
  
  const handleKeyDown = (e: React.KeyboardEvent) => {
    switch (e.key) {
      case 'ArrowDown':
        e.preventDefault();
        setSelectedIndex((i) => Math.min(i + 1, items.length - 1));
        break;
      case 'ArrowUp':
        e.preventDefault();
        setSelectedIndex((i) => Math.max(i - 1, 0));
        break;
      case 'Enter':
      case ' ':
        e.preventDefault();
        itemRefs.current[selectedIndex]?.click();
        break;
    }
  };
  
  useEffect(() => {
    itemRefs.current[selectedIndex]?.focus();
  }, [selectedIndex]);
  
  return (
    <div role="menu" onKeyDown={handleKeyDown}>
      {items.map((item, index) => (
        <button
          key={item.id}
          ref={(el) => (itemRefs.current[index] = el)}
          role="menuitem"
          tabIndex={index === selectedIndex ? 0 : -1}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
};
```

## Testing Patterns

### 1. Unit Tests

```typescript
// Button.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { Button } from './Button';

describe('Button', () => {
  it('renders children', () => {
    render(<Button>Click me</Button>);
    expect(screen.getByText('Click me')).toBeInTheDocument();
  });
  
  it('calls onClick when clicked', () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Click</Button>);
    
    fireEvent.click(screen.getByText('Click'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
  
  it('is disabled when disabled prop is true', () => {
    render(<Button disabled>Click</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });
  
  it('shows loading spinner when loading', () => {
    render(<Button loading>Click</Button>);
    expect(screen.getByTestId('spinner')).toBeInTheDocument();
  });
});
```

### 2. Integration Tests

```typescript
// UserProfile.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import { UserProfile } from './UserProfile';
import { server } from '@yappc/testing/mocks';

describe('UserProfile', () => {
  it('fetches and displays user data', async () => {
    render(<UserProfile userId="123" />);
    
    // Loading state
    expect(screen.getByText('Loading...')).toBeInTheDocument();
    
    // Loaded state
    await waitFor(() => {
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });
  });
  
  it('handles fetch error', async () => {
    server.use(
      rest.get('/api/users/:id', (req, res, ctx) => {
        return res(ctx.status(500));
      })
    );
    
    render(<UserProfile userId="123" />);
    
    await waitFor(() => {
      expect(screen.getByText('Error loading user')).toBeInTheDocument();
    });
  });
});
```

### 3. Accessibility Tests

```typescript
// Button.a11y.test.tsx
import { render } from '@testing-library/react';
import { axe, toHaveNoViolations } from 'jest-axe';
import { Button } from './Button';

expect.extend(toHaveNoViolations);

describe('Button accessibility', () => {
  it('should not have accessibility violations', async () => {
    const { container } = render(<Button>Click me</Button>);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  
  it('has proper aria-label when no text', async () => {
    const { container } = render(<Button aria-label="Close"><X /></Button>);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
});
```

## Best Practices Summary

1. ✅ Use TypeScript for all components
2. ✅ Export types alongside components
3. ✅ Use `forwardRef` for components that wrap HTML elements
4. ✅ Add `displayName` to memoized and forwarded ref components
5. ✅ Document props with JSDoc comments
6. ✅ Use semantic HTML elements
7. ✅ Implement keyboard navigation
8. ✅ Add ARIA attributes for accessibility
9. ✅ Memoize expensive components and callbacks
10. ✅ Use error boundaries for error handling
11. ✅ Write comprehensive tests (unit, integration, a11y)
12. ❌ Don't use default exports (except for pages)
13. ❌ Don't over-memoize simple components
14. ❌ Don't forget to handle loading and error states
15. ❌ Don't ignore accessibility requirements

## Storybook Stories

```typescript
// Button.stories.tsx
import type { Meta, StoryObj } from '@storybook/react';
import { Button } from './Button';

const meta: Meta<typeof Button> = {
  title: 'Components/Button',
  component: Button,
  argTypes: {
    variant: {
      control: 'select',
      options: ['primary', 'secondary', 'outline', 'ghost', 'danger'],
    },
    size: {
      control: 'select',
      options: ['sm', 'md', 'lg'],
    },
  },
};

export default meta;
type Story = StoryObj<typeof Button>;

export const Primary: Story = {
  args: {
    children: 'Primary Button',
    variant: 'primary',
  },
};

export const Secondary: Story = {
  args: {
    children: 'Secondary Button',
    variant: 'secondary',
  },
};

export const Loading: Story = {
  args: {
    children: 'Loading Button',
    loading: true,
  },
};

export const Disabled: Story = {
  args: {
    children: 'Disabled Button',
    disabled: true,
  },
};
```
