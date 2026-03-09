export type DashboardFetch = (input: string) => Promise<any>;

let dashboardFetch: DashboardFetch | null = null;

export function setDashboardFetch(fn: DashboardFetch): void {
    dashboardFetch = fn;
}

export function getDashboardFetch(): DashboardFetch {
    if (dashboardFetch) {
        return dashboardFetch;
    }
    return async (input: string) => {
        const response = await fetch(input);
        return response.json();
    };
}
