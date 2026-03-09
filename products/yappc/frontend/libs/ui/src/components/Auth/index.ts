/**
 * Authentication Components
 * 
 * Production-grade authentication UI components
 * 
 * @module ui/components/Auth
 * @doc.type module
 * @doc.purpose Authentication forms and UI
 * @doc.layer ui
 */

// Login
export { LoginForm } from './LoginForm';
export type { LoginFormProps, LoginFormData } from './LoginForm';

// Register
export { RegisterForm } from './RegisterForm';
export type { RegisterFormProps, RegisterFormData } from './RegisterForm';

// Password Reset
export { PasswordResetRequest, PasswordResetConfirm } from './PasswordResetForm';
export type {
  PasswordResetRequestProps,
  PasswordResetConfirmProps,
} from './PasswordResetForm';

// Protected Routes
export { ProtectedRoute, withProtectedRoute, useRouteAccess } from './ProtectedRoute';
export type { ProtectedRouteProps } from './ProtectedRoute';

// Examples (for development and documentation)
export * as AuthExamples from './examples';
export { LoginPage, RegisterPage, DashboardPage } from './examples';
export type { LoginPageProps, RegisterPageProps, DashboardPageProps } from './examples';

// ============================================================================
// Usage Examples
// ============================================================================

/**
 * @example Login Page
 * 
 * import { LoginForm } from '@ghatana/yappc-ui';
 * 
 * function LoginPage() {
 *   const navigate = useNavigate();
 *   
 *   return (
 *     <div className="auth-page">
 *       <LoginForm
 *         onSuccess={() => navigate('/dashboard')}
 *         showRememberMe
 *         showForgotPassword
 *       />
 *     </div>
 *   );
 * }
 */

/**
 * @example Register Page
 * 
 * import { RegisterForm } from '@ghatana/yappc-ui';
 * 
 * function RegisterPage() {
 *   const navigate = useNavigate();
 *   
 *   return (
 *     <div className="auth-page">
 *       <RegisterForm
 *         onSuccess={() => navigate('/onboarding')}
 *         showTerms
 *         minPasswordLength={8}
 *       />
 *     </div>
 *   );
 * }
 */

/**
 * @example Password Reset Flow
 * 
 * import { PasswordResetRequest, PasswordResetConfirm } from '@ghatana/yappc-ui';
 * 
 * function ResetPasswordPage() {
 *   const [searchParams] = useSearchParams();
 *   const token = searchParams.get('token');
 *   
 *   if (token) {
 *     return (
 *       <PasswordResetConfirm
 *         token={token}
 *         onSuccess={() => navigate('/login')}
 *       />
 *     );
 *   }
 *   
 *   return (
 *     <PasswordResetRequest
 *       onSuccess={() => setStep('check-email')}
 *     />
 *   );
 * }
 */
