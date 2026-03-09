# Error Boundary Framework Guide

**Author:** GitHub Copilot  
**Date:** 2026-01-31  
**Version:** 1.0.0  
**Status:** Production Ready

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Components](#components)
4. [Error Reporting](#error-reporting)
5. [Usage Patterns](#usage-patterns)
6. [Best Practices](#best-practices)
7. [Testing](#testing)
8. [Production Configuration](#production-configuration)

---

## Overview

### Purpose

The Error Boundary Framework provides production-grade error handling for React applications with:

- **Graceful Error Recovery**: Catch and handle React errors without app crashes
- **Flexible Fallback UI**: Pre-built and custom error displays
- **Error Reporting**: Integrated logging to console and remote services
- **Developer Experience**: Hooks for programmatic error handling
- **Type Safety**: Full TypeScript support

### Features

✅ **Multiple Error Boundaries**: Component-level, page-level, and app-level  
✅ **Pre-built Fallbacks**: Minimal, card, full-page, network, 404  
✅ **Error Reporting**: Console, remote, and composite reporters  
✅ **Automatic Recovery**: Reset on props change, timer-based reset  
✅ **Developer Tools**: Error severity classification, context tracking  
✅ **Testing Support**: Comprehensive test utilities

---

## Architecture

### Component Hierarchy

```
ErrorBoundary (Root)
├── ErrorFallback Components
│   ├── MinimalErrorFallback
│   ├── CardErrorFallback
│   ├── FullPageErrorFallback
│   ├── NetworkErrorFallback
│   └── NotFoundErrorFallback
├── Error Reporting
│   ├── ConsoleErrorReporter
│   ├── RemoteErrorReporter
│   └── CompositeErrorReporter
└── Error Hooks
    ├── useErrorHandler
    ├── useErrorReset
    └── useAsyncError
```

### Module Structure

```
libs/ui/src/error/
├── ErrorBoundary.tsx         # Main error boundary component
├── ErrorFallback.tsx         # Pre-built fallback components
├── errorReporter.ts          # Error reporting utilities
├── hooks.ts                  # Error handling hooks
├── index.ts                  # Module exports
└── __tests__/
    └── ErrorBoundary.test.tsx
```

---

## Components

### ErrorBoundary

Main error boundary component that catches errors in child component tree.

#### Props

```typescript
interface ErrorBoundaryProps {
  children: ReactNode;
  fallback?: ReactNode | ((error: Error, errorInfo: ErrorInfo, retry: () => void) => ReactNode);
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
  onReset?: () => void;
  showDetails?: boolean;
  boundaryName?: string;
  resetAfter?: number;
  resetOnPropsChange?: boolean;
  resetKeys?: unknown[];
}
```

#### Basic Usage

```tsx
import { ErrorBoundary } from '@yappc/ui/error';

function App() {
  return (
    <ErrorBoundary>
      <MyComponent />
    </ErrorBoundary>
  );
}
```

#### With Custom Fallback

```tsx
<ErrorBoundary
  fallback={<div>Something went wrong. Please refresh.</div>}
>
  <MyComponent />
</ErrorBoundary>
```

#### With Error Callback

```tsx
<ErrorBoundary
  onError={(error, errorInfo) => {
    console.error('Error caught:', error, errorInfo);
    // Send to error tracking service
  }}
>
  <MyComponent />
</ErrorBoundary>
```

#### With Auto-Reset

```tsx
<ErrorBoundary
  resetAfter={5000} // Reset after 5 seconds
  onReset={() => console.log('Boundary reset')}
>
  <MyComponent />
</ErrorBoundary>
```

#### With Reset Keys

```tsx
function ParentComponent() {
  const [userId, setUserId] = useState('user-1');
  
  return (
    <ErrorBoundary
      resetOnPropsChange
      resetKeys={[userId]}
      boundaryName="UserBoundary"
    >
      <UserProfile userId={userId} />
    </ErrorBoundary>
  );
}
```

---

### Error Fallback Components

Pre-built fallback UI components for common error scenarios.

#### MinimalErrorFallback

Compact inline error display.

```tsx
import { ErrorBoundary, MinimalErrorFallback } from '@yappc/ui/error';

<ErrorBoundary fallback={<MinimalErrorFallback />}>
  <SmallComponent />
</ErrorBoundary>
```

**Props:**
- `message?: string` - Custom error message
- `onReset?: () => void` - Reset callback

#### CardErrorFallback

Card-style error display for panels/cards.

```tsx
import { ErrorBoundary, CardErrorFallback } from '@yappc/ui/error';

<ErrorBoundary fallback={(error) => <CardErrorFallback error={error} />}>
  <DashboardCard />
</ErrorBoundary>
```

**Props:**
- `error: Error` - Error object
- `onReset?: () => void` - Reset callback
- `title?: string` - Custom title
- `message?: string` - Custom message
- `actions?: ReactNode` - Additional action buttons

#### FullPageErrorFallback

Full-page error display for critical errors.

```tsx
import { ErrorBoundary, FullPageErrorFallback } from '@yappc/ui/error';

<ErrorBoundary fallback={(error) => <FullPageErrorFallback error={error} />}>
  <App />
</ErrorBoundary>
```

**Props:**
- `error: Error` - Error object
- `onReset?: () => void` - Reset callback
- `title?: string` - Custom title
- `message?: string` - Custom message
- `showSupport?: boolean` - Show support contact
- `supportEmail?: string` - Support email address
- `actions?: ReactNode` - Additional actions

#### NetworkErrorFallback

Network/connectivity error display.

```tsx
import { ErrorBoundary, NetworkErrorFallback } from '@yappc/ui/error';

<ErrorBoundary fallback={(error) => <NetworkErrorFallback error={error} />}>
  <DataComponent />
</ErrorBoundary>
```

**Props:**
- `error: Error` - Error object
- `onReset?: () => void` - Reset callback
- `title?: string` - Custom title
- `message?: string` - Custom message

#### NotFoundErrorFallback

404/resource not found display.

```tsx
import { NotFoundErrorFallback } from '@yappc/ui/error';

function NotFoundPage() {
  return <NotFoundErrorFallback />;
}
```

**Props:**
- `title?: string` - Custom title
- `message?: string` - Custom message

---

## Error Reporting

### Error Reporter Interface

```typescript
interface ErrorReporter {
  report: (error: Error, errorInfo?: ErrorInfo, context?: ErrorContext) => void;
  setContext: (context: Partial<ErrorContext>) => void;
  setUser: (userId: string) => void;
}
```

### Error Context

```typescript
interface ErrorContext {
  userId?: string;
  route?: string;
  component?: string;
  boundaryName?: string;
  [key: string]: unknown;
}
```

### Error Severity

```typescript
enum ErrorSeverity {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical',
}
```

### ConsoleErrorReporter

Logs errors to console (development).

```tsx
import { ConsoleErrorReporter, setErrorReporter } from '@yappc/ui/error';

const reporter = new ConsoleErrorReporter();
reporter.setContext({ version: '1.0.0' });
reporter.setUser('user-123');

setErrorReporter(reporter);
```

### RemoteErrorReporter

Sends errors to remote logging service (production).

```tsx
import { RemoteErrorReporter, setErrorReporter } from '@yappc/ui/error';

const reporter = new RemoteErrorReporter(
  'https://api.example.com/errors',
  'api-key-here'
);

setErrorReporter(reporter);
```

### CompositeErrorReporter

Combines multiple reporters.

```tsx
import {
  CompositeErrorReporter,
  ConsoleErrorReporter,
  RemoteErrorReporter,
  setErrorReporter,
} from '@yappc/ui/error';

const reporter = new CompositeErrorReporter([
  new ConsoleErrorReporter(),
  new RemoteErrorReporter(
    process.env.VITE_ERROR_REPORTING_ENDPOINT!,
    process.env.VITE_ERROR_REPORTING_API_KEY
  ),
]);

reporter.setContext({
  environment: process.env.NODE_ENV,
  version: process.env.VITE_APP_VERSION,
});

setErrorReporter(reporter);
```

### Reporting Errors

```tsx
import { reportError } from '@yappc/ui/error';

try {
  riskyOperation();
} catch (error) {
  reportError(
    error as Error,
    undefined,
    {
      component: 'UserProfile',
      action: 'updateProfile',
      userId: currentUser.id,
    }
  );
}
```

---

## Usage Patterns

### Pattern 1: Nested Boundaries

Wrap different levels of your app with appropriate boundaries.

```tsx
function App() {
  return (
    <ErrorBoundary
      boundaryName="AppBoundary"
      fallback={(error) => <FullPageErrorFallback error={error} />}
    >
      <Router>
        <Routes>
          <Route
            path="/dashboard"
            element={
              <ErrorBoundary
                boundaryName="DashboardBoundary"
                fallback={(error) => <CardErrorFallback error={error} />}
              >
                <Dashboard />
              </ErrorBoundary>
            }
          />
        </Routes>
      </Router>
    </ErrorBoundary>
  );
}
```

### Pattern 2: Feature Boundaries

Wrap individual features with error boundaries.

```tsx
function Dashboard() {
  return (
    <div className="dashboard">
      <ErrorBoundary
        boundaryName="UserProfileBoundary"
        fallback={<CardErrorFallback />}
      >
        <UserProfile />
      </ErrorBoundary>
      
      <ErrorBoundary
        boundaryName="ActivityFeedBoundary"
        fallback={<CardErrorFallback />}
      >
        <ActivityFeed />
      </ErrorBoundary>
      
      <ErrorBoundary
        boundaryName="ChartsBoundary"
        fallback={<CardErrorFallback />}
      >
        <Charts />
      </ErrorBoundary>
    </div>
  );
}
```

### Pattern 3: Programmatic Error Handling

Use hooks for errors in event handlers and async code.

```tsx
import { useErrorHandler } from '@yappc/ui/error';

function DataComponent() {
  const { error, clearError, tryCatchAsync } = useErrorHandler();
  const [data, setData] = useState(null);
  
  const loadData = async () => {
    const result = await tryCatchAsync(async () => {
      const response = await fetch('/api/data');
      return response.json();
    });
    
    if (result) {
      setData(result);
    }
  };
  
  if (error) {
    return (
      <CardErrorFallback
        error={error}
        onReset={() => {
          clearError();
          loadData();
        }}
      />
    );
  }
  
  return <button onClick={loadData}>Load Data</button>;
}
```

### Pattern 4: Async Error Propagation

Throw async errors to nearest error boundary.

```tsx
import { useAsyncError } from '@yappc/ui/error';

function AsyncComponent() {
  const throwError = useAsyncError();
  const [data, setData] = useState(null);
  
  useEffect(() => {
    fetch('/api/data')
      .then(res => res.json())
      .then(setData)
      .catch(throwError); // Caught by error boundary
  }, [throwError]);
  
  return <div>{data?.title}</div>;
}
```

### Pattern 5: Error Reset Control

Control error boundary reset externally.

```tsx
import { useErrorReset } from '@yappc/ui/error';

function ParentComponent() {
  const { resetKey, reset } = useErrorReset();
  
  return (
    <div>
      <button onClick={reset}>Reset All Errors</button>
      
      <ErrorBoundary resetKeys={[resetKey]}>
        <FeatureA />
      </ErrorBoundary>
      
      <ErrorBoundary resetKeys={[resetKey]}>
        <FeatureB />
      </ErrorBoundary>
    </div>
  );
}
```

---

## Best Practices

### ✅ DO

1. **Wrap at Multiple Levels**
   ```tsx
   // App level
   <ErrorBoundary fallback={<FullPageErrorFallback />}>
     <App />
   </ErrorBoundary>
   
   // Feature level
   <ErrorBoundary fallback={<CardErrorFallback />}>
     <Feature />
   </ErrorBoundary>
   ```

2. **Report Errors**
   ```tsx
   <ErrorBoundary
     onError={(error, errorInfo) => {
       reportError(error, errorInfo, { component: 'Feature' });
     }}
   >
     <Feature />
   </ErrorBoundary>
   ```

3. **Provide Context**
   ```tsx
   <ErrorBoundary
     boundaryName="UserDashboard"
     onError={(error, errorInfo) => {
       reportError(error, errorInfo, {
         userId: currentUser.id,
         route: location.pathname,
       });
     }}
   >
     <Dashboard />
   </ErrorBoundary>
   ```

4. **Use Appropriate Fallbacks**
   ```tsx
   // Small components
   <ErrorBoundary fallback={<MinimalErrorFallback />}>
     <Badge />
   </ErrorBoundary>
   
   // Cards/panels
   <ErrorBoundary fallback={<CardErrorFallback />}>
     <DashboardCard />
   </ErrorBoundary>
   
   // Full page
   <ErrorBoundary fallback={<FullPageErrorFallback />}>
     <App />
   </ErrorBoundary>
   ```

5. **Handle Async Errors**
   ```tsx
   const throwError = useAsyncError();
   
   useEffect(() => {
     loadData().catch(throwError);
   }, []);
   ```

### ❌ DON'T

1. **Don't Wrap Everything in One Boundary**
   ```tsx
   // Bad - entire app crashes on any error
   <ErrorBoundary>
     <Header />
     <Sidebar />
     <Content />
     <Footer />
   </ErrorBoundary>
   ```

2. **Don't Ignore Errors**
   ```tsx
   // Bad - error thrown but not handled
   try {
     riskyOperation();
   } catch (error) {
     // Silent failure
   }
   ```

3. **Don't Use Error Boundaries for Flow Control**
   ```tsx
   // Bad - don't use errors for expected conditions
   function Component() {
     if (!data) {
       throw new Error('No data'); // Use conditional rendering instead
     }
   }
   ```

4. **Don't Mix Error Patterns**
   ```tsx
   // Bad - inconsistent error handling
   <ErrorBoundary>
     <Component1 /> {/* Uses error boundary */}
   </ErrorBoundary>
   <Component2 /> {/* No error handling */}
   ```

---

## Testing

### Testing Error Boundaries

```tsx
import { render, screen } from '@testing-library/react';
import { ErrorBoundary } from '@yappc/ui/error';
import { vi } from 'vitest';

function ThrowError() {
  throw new Error('Test error');
}

test('catches and displays error', () => {
  const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
  
  render(
    <ErrorBoundary>
      <ThrowError />
    </ErrorBoundary>
  );
  
  expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
  
  consoleSpy.mockRestore();
});
```

### Testing Error Hooks

```tsx
import { renderHook, act } from '@testing-library/react';
import { useErrorHandler } from '@yappc/ui/error';

test('handles errors with useErrorHandler', () => {
  const { result } = renderHook(() => useErrorHandler());
  
  act(() => {
    result.current.tryCatch(() => {
      throw new Error('Test error');
    });
  });
  
  expect(result.current.hasError).toBe(true);
  expect(result.current.error?.message).toBe('Test error');
  
  act(() => {
    result.current.clearError();
  });
  
  expect(result.current.hasError).toBe(false);
});
```

---

## Production Configuration

### Setup Error Reporting

```tsx
// src/main.tsx
import {
  CompositeErrorReporter,
  ConsoleErrorReporter,
  RemoteErrorReporter,
  setErrorReporter,
} from '@yappc/ui/error';

// Configure error reporter
const reporters = [new ConsoleErrorReporter()];

if (import.meta.env.PROD) {
  reporters.push(
    new RemoteErrorReporter(
      import.meta.env.VITE_ERROR_REPORTING_ENDPOINT,
      import.meta.env.VITE_ERROR_REPORTING_API_KEY
    )
  );
}

const errorReporter = new CompositeErrorReporter(reporters);

errorReporter.setContext({
  environment: import.meta.env.MODE,
  version: import.meta.env.VITE_APP_VERSION,
});

setErrorReporter(errorReporter);
```

### Wrap App

```tsx
// src/App.tsx
import { ErrorBoundary, FullPageErrorFallback, reportError } from '@yappc/ui/error';

function App() {
  return (
    <ErrorBoundary
      boundaryName="AppRoot"
      fallback={(error) => (
        <FullPageErrorFallback
          error={error}
          showSupport={import.meta.env.PROD}
          supportEmail="support@yappc.app"
        />
      )}
      onError={(error, errorInfo) => {
        reportError(error, errorInfo, {
          boundaryName: 'AppRoot',
          route: window.location.pathname,
        });
      }}
    >
      <Router>
        <Routes>{/* routes */}</Routes>
      </Router>
    </ErrorBoundary>
  );
}
```

### Environment Variables

```bash
# .env
VITE_ERROR_REPORTING_ENDPOINT=https://api.example.com/errors
VITE_ERROR_REPORTING_API_KEY=your-api-key
VITE_APP_VERSION=1.0.0
```

---

## Summary

The Error Boundary Framework provides comprehensive error handling with:

✅ **Production-Ready**: Battle-tested error boundaries with fallbacks  
✅ **Flexible**: Pre-built and custom fallback components  
✅ **Observable**: Integrated error reporting and logging  
✅ **Developer-Friendly**: Hooks for programmatic error handling  
✅ **Type-Safe**: Full TypeScript support  
✅ **Testable**: Comprehensive test utilities

**Next Steps:**
1. Wrap app with root error boundary
2. Add feature-level boundaries
3. Configure error reporting
4. Test error scenarios
5. Monitor production errors
