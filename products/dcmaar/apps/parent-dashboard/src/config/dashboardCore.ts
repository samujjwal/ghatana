import { setDashboardFetch } from '@ghatana/dcmaar-dashboard-core';

export function configureDashboardCore(): void {
    setDashboardFetch(async (path: string) => {
        const token = localStorage.getItem('guardian_token') ?? localStorage.getItem('token');

        const response = await fetch(path, {
            headers: {
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
        });

        if (!response.ok) {
            throw new Error(`Dashboard fetch failed: ${response.status} ${response.statusText}`);
        }

        return response.json();
    });
}
