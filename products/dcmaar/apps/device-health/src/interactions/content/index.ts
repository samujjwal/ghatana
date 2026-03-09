export * from './dom-bridge';
export * from './event-tracking';
export * from './core-web-vitals';
export * from './networkInterceptor';

// Initialize network interception
import { initializeNetworkInterception } from './networkInterceptor';
initializeNetworkInterception();
