import { authService } from '../services/auth.service';

const DEMO_USER = {
  email: 'demo@example.com',
  password: 'demo123',
  name: 'Demo Parent User'
};

async function isBackendAvailable() {
  try {
    const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined) || 'http://localhost:3001/api';
    const backendBase = apiBase.replace(/\/api\/?$/, '');
    const response = await fetch(`${backendBase}/health`);
    return response.ok;
  } catch (error: unknown) {
    console.warn('[DEV] Backend health check failed:', error instanceof Error ? error.message : 'Unknown error');
    return false;
  }
}

export async function initializeDevUser() {
  // Only run in development
  if (import.meta.env.MODE !== 'development') {
    return;
  }

  // Require explicit opt-in for demo user initialization
  if (import.meta.env.VITE_ENABLE_DEV_DEMO !== 'true') {
    return;
  }

  // Check if backend is available
  const backendAvailable = await isBackendAvailable();
  if (!backendAvailable) {
    console.warn('[DEV] Backend not available. Skipping demo user initialization.');
    return;
  }

  try {
    // Check if we're already logged in
    if (authService.isAuthenticated()) {
      console.log('[DEV] Already logged in as demo user');
      return;
    }

    console.log('[DEV] Initializing demo user...');

    try {
      // Try to log in with demo credentials
      const loginResponse = await authService.login({
        email: DEMO_USER.email,
        password: DEMO_USER.password
      });

      console.log('[DEV] Logged in as demo user:', loginResponse.user.email);
    } catch (error: unknown) {
      // If login fails, try to register the demo user
      console.log('[DEV] Login failed, creating demo user account...', error instanceof Error ? error.message : 'Unknown error');
      try {
        await authService.register(DEMO_USER);
        console.log('[DEV] Demo user created successfully');

        // Log in with the new demo user
        await authService.login({
          email: DEMO_USER.email,
          password: DEMO_USER.password
        });

        console.log('[DEV] Logged in as demo user');
      } catch (registerError: unknown) {
        console.error('[DEV] Failed to create demo user:', registerError instanceof Error ? registerError.message : 'Unknown error');
      }
    }
  } catch (error: unknown) {
    console.error('[DEV] Error initializing demo user:', error instanceof Error ? error.message : 'Unknown error');
  }
}

// Add a global function for manual initialization in the browser console
declare global {
  interface Window {
    initDemoUser: () => Promise<void>;
  }
}

// Expose the function globally for manual initialization
window.initDemoUser = initializeDevUser;
