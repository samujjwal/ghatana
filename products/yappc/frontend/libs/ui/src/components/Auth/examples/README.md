# Authentication System - Examples & Integration Guide

Complete production-ready authentication examples demonstrating best practices, security patterns, and integration workflows.

## 📋 Table of Contents

- [Quick Start](#quick-start)
- [Examples Overview](#examples-overview)
- [Integration Patterns](#integration-patterns)
- [Component Reference](#component-reference)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## 🚀 Quick Start

### 1. Install Dependencies

```bash
pnpm add @yappc/ui @yappc/state @yappc/api jotai react-router-dom zod
```

### 2. Configure Environment

Create `.env` file:

```bash
VITE_API_BASE_URL=http://localhost:3000
```

### 3. Setup Application

**main.tsx:**

```tsx
import { createRoot } from 'react-dom/client';
import { App, initializeApp } from '@yappc/ui/examples';
import './index.css';

// Initialize environment
initializeApp();

// Render app
const root = createRoot(document.getElementById('root')!);
root.render(<App />);
```

### 4. Start Development Server

```bash
pnpm dev
```

That's it! Your app now has complete authentication with login, registration, and protected routes.

## 📦 Examples Overview

### Page Examples

1. **LoginPage** - Complete login page with validation
2. **RegisterPage** - Registration with password strength
3. **DashboardPage** - Protected dashboard with user info
4. **RouterExample** - Complete router configuration
5. **AppExample** - Full application setup

### File Structure

```
examples/
├── LoginPage.tsx          # Login page with routing
├── RegisterPage.tsx       # Registration with validation
├── DashboardPage.tsx      # Protected dashboard
├── RouterExample.tsx      # Complete router setup
├── AppExample.tsx         # Application initialization
└── index.ts              # Export all examples
```

## 🔧 Integration Patterns

### Pattern 1: Component-Based Protection

```tsx
import { ProtectedRoute } from '@yappc/ui';
import { useAuth } from '@yappc/state';

function Dashboard() {
  const { user, isAuthenticated, isLoading } = useAuth();
  
  return (
    <ProtectedRoute
      isAuthenticated={isAuthenticated}
      isLoading={isLoading}
      requiredRoles={['user']}
      userRoles={user?.roles}
    >
      <DashboardContent />
    </ProtectedRoute>
  );
}
```

### Pattern 2: HOC Protection

```tsx
import { withProtectedRoute } from '@yappc/ui';

const Dashboard = () => <div>Dashboard Content</div>;

// Protect with authentication
export const ProtectedDashboard = withProtectedRoute(Dashboard, {
  requiredRoles: ['user'],
});

// Protect with admin role
export const AdminDashboard = withProtectedRoute(Dashboard, {
  requiredRoles: ['admin'],
  unauthorizedRedirectTo: '/403',
});
```

### Pattern 3: Hook-Based Access Check

```tsx
import { useRouteAccess } from '@yappc/ui';
import { useAuth } from '@yappc/state';

function FeatureButton() {
  const { user, isAuthenticated } = useAuth();
  const { hasAccess, reason } = useRouteAccess({
    isAuthenticated,
    requiredRoles: ['premium'],
    userRoles: user?.roles,
  });
  
  if (!hasAccess) {
    return <div>Upgrade to access this feature</div>;
  }
  
  return <button>Premium Feature</button>;
}
```

### Pattern 4: Custom Router Configuration

```tsx
import { createBrowserRouter } from 'react-router-dom';
import { LoginPage, DashboardPage } from '@yappc/ui/examples';

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage redirectTo="/app/dashboard" />,
  },
  {
    path: '/app/dashboard',
    element: <DashboardPage requiredRoles={['user']} />,
  },
]);
```

## 📖 Component Reference

### LoginPage

Complete login page with form validation and routing.

**Props:**

```typescript
interface LoginPageProps {
  redirectTo?: string;              // Default: '/dashboard'
  showRememberMe?: boolean;         // Default: true
  showForgotPassword?: boolean;     // Default: true
  showSignUp?: boolean;             // Default: true
  logo?: React.ReactNode;
  title?: string;                   // Default: 'Welcome Back'
  subtitle?: string;
}
```

**Example:**

```tsx
<LoginPage
  redirectTo="/admin"
  logo={<MyLogo />}
  title="Admin Portal"
  showRememberMe={false}
/>
```

### RegisterPage

Registration page with password strength and validation.

**Props:**

```typescript
interface RegisterPageProps {
  redirectTo?: string;              // Default: '/dashboard'
  showTerms?: boolean;              // Default: true
  showSignIn?: boolean;             // Default: true
  minPasswordLength?: number;       // Default: 8
  logo?: React.ReactNode;
  title?: string;
  subtitle?: string;
  autoLogin?: boolean;              // Default: true
}
```

**Example:**

```tsx
<RegisterPage
  minPasswordLength={12}
  autoLogin={false}
  title="Create Your Account"
/>
```

### DashboardPage

Protected dashboard demonstrating authentication state.

**Props:**

```typescript
interface DashboardPageProps {
  requiredRoles?: string[];         // Default: ['user']
  requiredPermissions?: string[];
  title?: string;                   // Default: 'Dashboard'
  showWelcome?: boolean;            // Default: true
}
```

**Example:**

```tsx
<DashboardPage
  requiredRoles={['admin', 'moderator']}
  title="Admin Dashboard"
  showWelcome={true}
/>
```

### ProtectedRoute

Route-level authentication and authorization.

**Props:**

```typescript
interface ProtectedRouteProps {
  isAuthenticated: boolean;
  isLoading?: boolean;
  requiredRoles?: string[];
  requiredPermissions?: string[];
  userRoles?: string[];
  userPermissions?: string[];
  redirectTo?: string;              // Default: '/login'
  unauthorizedRedirectTo?: string;  // Default: '/unauthorized'
  loadingFallback?: ReactNode;
  unauthorizedFallback?: ReactNode;
  children: ReactNode;
}
```

## ✅ Best Practices

### 1. State Management

```tsx
// ✅ Good: Use useAuth hook
const { login, user, isAuthenticated } = useAuth();

// ❌ Bad: Direct atom access in components
import { userAtom } from '@yappc/state';
const [user] = useAtom(userAtom);
```

### 2. Route Protection

```tsx
// ✅ Good: Protect sensitive routes
<ProtectedRoute isAuthenticated={true} requiredRoles={['admin']}>
  <AdminPanel />
</ProtectedRoute>

// ❌ Bad: Conditional rendering only
{isAuthenticated && <AdminPanel />}
```

### 3. Error Handling

```tsx
// ✅ Good: Show user-friendly errors
const handleError = (error: string) => {
  toast.error(error, { title: 'Login Failed' });
};

// ❌ Bad: Generic error messages
const handleError = () => {
  alert('Error');
};
```

### 4. Loading States

```tsx
// ✅ Good: Show loading feedback
{isLoading && <Spinner />}
<button disabled={isLoading}>
  {isLoading ? 'Logging in...' : 'Login'}
</button>

// ❌ Bad: No loading indication
<button>Login</button>
```

### 5. Security

```tsx
// ✅ Good: Validate on both client and server
const result = validate(loginSchema, formData);
if (result.success) {
  await apiLogin(result.data); // Server validates again
}

// ❌ Bad: Client-side only validation
if (email && password) {
  await apiLogin({ email, password });
}
```

## 🐛 Troubleshooting

### Issue: "Cannot find module '@yappc/state'"

**Solution:** Ensure dependencies are installed and TypeScript paths are configured:

```json
// tsconfig.json
{
  "compilerOptions": {
    "paths": {
      "@yappc/state": ["../../libs/state/src"]
    }
  }
}
```

### Issue: Protected routes not working

**Solution:** Verify authentication state is initialized:

```tsx
// Check if auth is loading
const { isLoading, isAuthenticated } = useAuth();

console.log({ isLoading, isAuthenticated });

// Wait for auth to load
if (isLoading) return <Spinner />;
```

### Issue: Redirects not working after login

**Solution:** Ensure location state is preserved:

```tsx
// In LoginPage
const location = useLocation();
const from = location.state?.from?.pathname || '/dashboard';

// Navigate to return path
navigate(from, { replace: true });
```

### Issue: Toast notifications not showing

**Solution:** Verify ToastProvider is at the root:

```tsx
// ✅ Correct
<JotaiProvider>
  <ToastProvider>
    <RouterProvider router={router} />
  </ToastProvider>
</JotaiProvider>

// ❌ Wrong - ToastProvider not at root
<RouterProvider router={router}>
  <ToastProvider>...</ToastProvider>
</RouterProvider>
```

## 📚 Additional Resources

- [Complete Integration Guide](../../docs/AUTHENTICATION_INTEGRATION_GUIDE.md)
- [API Reference](../../docs/API_REFERENCE.md)
- [Security Best Practices](../../docs/SECURITY.md)
- [Testing Guide](../../docs/TESTING.md)

## 🤝 Contributing

Found an issue or want to improve examples? Please see our [Contributing Guide](../../../CONTRIBUTING.md).

## 📄 License

MIT © YAPPC Team
