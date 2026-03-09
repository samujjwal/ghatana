/**
 * Pages Index
 *
 * @description Central export for all page components.
 */

// Auth pages
export * from './auth';

// Dashboard pages
export * from './dashboard';

// Bootstrapping pages
export * from './bootstrapping';

// Initialization pages
export * from './initialization';

// Development pages
export * from './development';

// Operations pages
export * from './operations';

// Collaboration pages
export * from './collaboration';

// Security pages
export * from './security';

// Admin pages
export * from './admin';

// Settings pages
export * from './settings';

// Error pages
export * from './errors';

// Landing page
export const LandingPage = () => import('./LandingPage');
