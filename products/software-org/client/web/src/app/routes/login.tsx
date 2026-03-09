/**
 * Login Route Module
 * 
 * Placeholder login page that redirects to home once persona is loaded.
 * In demo mode (VITE_USE_MOCKS=true), the persona is auto-seeded in localStorage.
 */
import { useEffect } from 'react';
import { useNavigate } from 'react-router';

export default function Login() {
    const navigate = useNavigate();

    useEffect(() => {
        // Check if persona exists in localStorage
        const stored = localStorage.getItem('current-persona');
        if (stored) {
            console.log('[Login] Persona found, redirecting to home');
            navigate('/', { replace: true });
            return;
        }

        // In demo mode, seed the persona and redirect
        const isDemoMode = import.meta.env.VITE_USE_MOCKS === 'true';
        if (isDemoMode) {
            const demoPersona = {
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
            localStorage.setItem('current-persona', JSON.stringify(demoPersona));
            console.log('[Login] Demo persona seeded, redirecting to home');
            navigate('/', { replace: true });
        }
    }, [navigate]);

    return (
        <div className="flex items-center justify-center h-screen bg-slate-50 dark:bg-slate-900">
            <div className="text-center max-w-md p-8">
                <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-4">
                    Software Org
                </h1>
                <p className="text-slate-600 dark:text-neutral-400 mb-6">
                    Loading your workspace...
                </p>
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto"></div>
            </div>
        </div>
    );
}
