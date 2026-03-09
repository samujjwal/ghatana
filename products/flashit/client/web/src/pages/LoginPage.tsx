/**
 * Login Page
 * User authentication with email and password
 */

import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useLogin } from '../hooks/use-api';
import { useAtomValue } from 'jotai';
import { isAuthenticatedAtom } from '../store/atoms';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();
  const login = useLogin();

  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await login.mutateAsync({ email, password });
      // Navigation will happen in useEffect when isAuthenticated updates
    } catch (error) {
      console.error('Login failed:', error);
    }
  };

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/');
    }
  }, [isAuthenticated, navigate]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 to-primary-100">
      <div className="max-w-md w-full space-y-8 p-8 bg-white rounded-xl shadow-lg">
        <div className="text-center">
          <h1
            className="text-4xl font-bold text-primary-600"
            role="heading"
            aria-level={1}
          >
            Flashit
          </h1>
          <p className="mt-2 text-gray-600">Capture your moments, reflect on your story</p>
        </div>

        <form
          className="mt-8 space-y-6"
          onSubmit={handleSubmit}
          aria-labelledby="login-heading"
        >
          <h2 id="login-heading" className="sr-only">Sign in to your account</h2>

          {login.isError && (
            <div
              className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg"
              role="alert"
              aria-live="polite"
            >
              <p className="text-sm">Invalid email or password. Please try again.</p>
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-gray-700"
              >
                Email
              </label>
              <input
                id="email"
                type="email"
                required
                autoComplete="email"
                className="input mt-1"
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                disabled={login.isPending}
                aria-describedby="email-hint"
                aria-invalid={login.isError ? 'true' : 'false'}
              />
              <span id="email-hint" className="sr-only">
                Enter your email address to sign in
              </span>
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-700"
              >
                Password
              </label>
              <input
                id="password"
                type="password"
                required
                autoComplete="current-password"
                className="input mt-1"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={login.isPending}
                aria-describedby="password-hint"
                aria-invalid={login.isError ? 'true' : 'false'}
              />
              <span id="password-hint" className="sr-only">
                Enter your password to sign in
              </span>
            </div>
          </div>

          <button
            type="submit"
            className="btn-primary w-full focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2"
            disabled={login.isPending}
            aria-busy={login.isPending}
          >
            {login.isPending ? 'Signing in...' : 'Sign in'}
          </button>

          <div className="text-center text-sm">
            <span className="text-gray-600">Don't have an account? </span>
            <Link
              to="/register"
              className="text-primary-600 hover:text-primary-700 font-medium focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 rounded"
            >
              Sign up
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}

