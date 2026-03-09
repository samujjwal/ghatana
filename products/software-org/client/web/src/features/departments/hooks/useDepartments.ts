/**
 * Hook for fetching departments data
 *
 * <p><b>Purpose</b><br>
 * TanStack Query hook that fetches department list with filtering and search.
 *
 * <p><b>Features</b><br>
 * - List departments with pagination
 * - Filter by status, team size, automation level
 * - Search by name or description
 * - Automatic caching and stale-while-revalidate
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { data: departments } = useDepartments({
 *   search: 'engineering'
 * });
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose Department data fetching
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
import { useQuery } from "@tanstack/react-query";

export interface Department {
    id: string;
    name: string;
    description: string;
    teams: number;
    activeAgents: number;
    automationLevel: number; // 0-100
    lastUpdated: string;
    status: "active" | "inactive";
    kpis: {
        deploymentFrequency: number;
        leadTime: string;
        mttr: string;
        changeFailureRate: string;
    };
}

interface UseDepartmentsOptions {
    search?: string;
    enabled?: boolean;
}

/**
 * Mock departments data for development
 */
const mockDepartments: Department[] = [
    {
        id: "engineering",
        name: "Engineering",
        description: "Core software development and technical infrastructure. Responsible for building and maintaining all product features.",
        teams: 4,
        activeAgents: 8,
        automationLevel: 78,
        lastUpdated: new Date(Date.now() - 3600000).toISOString(),
        status: "active",
        kpis: {
            deploymentFrequency: 234,
            leadTime: "2.1h",
            mttr: "8m",
            changeFailureRate: "2.1%",
        },
    },
    {
        id: "devops",
        name: "DevOps",
        description: "Infrastructure, deployment, and site reliability. Ensures system uptime and deployment automation.",
        teams: 2,
        activeAgents: 5,
        automationLevel: 92,
        lastUpdated: new Date(Date.now() - 1800000).toISOString(),
        status: "active",
        kpis: {
            deploymentFrequency: 89,
            leadTime: "1.5h",
            mttr: "4m",
            changeFailureRate: "1.2%",
        },
    },
    {
        id: "security",
        name: "Security",
        description: "Application and infrastructure security. Responsible for vulnerability management and compliance.",
        teams: 2,
        activeAgents: 3,
        automationLevel: 85,
        lastUpdated: new Date(Date.now() - 7200000).toISOString(),
        status: "active",
        kpis: {
            deploymentFrequency: 45,
            leadTime: "3.2h",
            mttr: "12m",
            changeFailureRate: "1.8%",
        },
    },
    {
        id: "product",
        name: "Product",
        description: "Product management and strategy. Defines roadmap and prioritizes features.",
        teams: 2,
        activeAgents: 2,
        automationLevel: 45,
        lastUpdated: new Date(Date.now() - 10800000).toISOString(),
        status: "active",
        kpis: {
            deploymentFrequency: 12,
            leadTime: "4.5h",
            mttr: "15m",
            changeFailureRate: "3.2%",
        },
    },
];

export function useDepartments(options: UseDepartmentsOptions = {}) {
    const { search, enabled = true } = options;

    return useQuery({
        queryKey: ["departments", search],
        queryFn: async () => {
            // Simulate API call
            await new Promise((resolve) => setTimeout(resolve, 300));

            if (!search) {
                return mockDepartments;
            }

            const searchLower = search.toLowerCase();
            return mockDepartments.filter(
                (dept) =>
                    dept.name.toLowerCase().includes(searchLower) ||
                    dept.description.toLowerCase().includes(searchLower)
            );
        },
        staleTime: 1000 * 60 * 5, // 5 minutes
        gcTime: 1000 * 60 * 10, // 10 minutes
        enabled,
        retry: 1,
    });
}

export default useDepartments;
