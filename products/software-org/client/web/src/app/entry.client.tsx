/**
 * Client Entry Point - React Router v7 Framework Mode
 *
 * This file handles client-side hydration/rendering for Framework Mode.
 * It initializes MSW for API mocking before hydrating the application.
 *
 * @doc.type module
 * @doc.purpose Client-side application entry point
 * @doc.layer product
 * @doc.pattern Entry
 */
import { StrictMode, startTransition } from "react";
import { hydrateRoot } from "react-dom/client";
import { HydratedRouter } from "react-router/dom";
import { setupMocks, waitForWorkerReady } from "@/mocks/browser";
import type { Persona } from "@/state/atoms/persona.atoms";
import "@/index.css";
import "@/styles/tokens.css";

/**
 * Demo persona for development
 */
const DEMO_PERSONA: Persona = {
    id: 'user-demo-owner',
    type: 'owner',
    name: 'Demo Owner',
    email: 'demo@ghatana.local',
    permissions: [
        'view_org',
        'manage_users',
        'restructure_org',
        'approve_restructure',
        'manage_budgets',
        'approve_budgets',
    ],
    avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=demo-owner',
};

/**
 * Suppress noisy console warnings
 */
const originalWarn = console.warn;
const originalError = console.error;

console.warn = (...args: unknown[]) => {
    const message = String(args[0] || '');
    // Suppress React Router v7 migration warnings
    if (message.includes('React Router')) {
        return;
    }
    originalWarn.apply(console, args as Parameters<typeof console.warn>);
};

console.error = (...args: unknown[]) => {
    const message = String(args[0] || '');
    if (message === 'API Error:') {
        return;
    }
    originalError.apply(console, args as Parameters<typeof console.error>);
};

window.addEventListener('unhandledrejection', (event) => {
    console.error('[Boot] Unhandled rejection:', event.reason);
});

window.addEventListener('error', (event) => {
    console.error('[Boot] Uncaught error:', event.error || event.message);
});

/**
 * Initialize application
 */
async function boot() {
    try {
        const shouldMock = import.meta.env.VITE_USE_MOCKS === 'true';
        console.log('[Boot] Initializing with VITE_USE_MOCKS=', shouldMock);

        // Initialize demo persona for development BEFORE rendering
        if (shouldMock && !localStorage.getItem('current-persona')) {
            localStorage.setItem('current-persona', JSON.stringify(DEMO_PERSONA));
            console.log('[Boot] ✅ Demo persona seeded for development');
        }

        await setupMocks();

        const ready = await waitForWorkerReady(10000);
        if (ready) {
            console.log('[Boot] ✅ MSW ready - intercepting API calls');
        } else {
            console.warn('[Boot] ⚠️ MSW did not report ready within timeout');
        }
    } catch (error) {
        console.error("[Boot] ❌ setupMocks() threw error:", error);
    }

    // Hydrate the application
    startTransition(() => {
        hydrateRoot(
            document,
            <StrictMode>
                <HydratedRouter />
            </StrictMode>
        );
    });

    console.log('[Boot] ✅ React hydration started');
}

boot().catch((error) => {
    console.error('[Main] Fatal error:', error);
    const rootElement = document.getElementById('root');
    if (rootElement) {
        rootElement.innerHTML = `
            <div style="padding: 2rem; color: red;">
                <h1>Application failed to load</h1>
                <p>${(error as Error).message}</p>
            </div>
        `;
    }
});
