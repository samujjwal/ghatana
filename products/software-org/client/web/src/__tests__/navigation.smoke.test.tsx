/**
 * Navigation Smoke Tests
 *
 * Validates that all primary navigation destinations render without errors
 * and that persona workspaces are accessible and correctly configured.
 *
 * These tests are designed to catch:
 * - Broken imports or missing components
 * - Route configuration errors
 * - Basic rendering issues
 * - Persona context propagation
 *
 * @doc.type test
 * @doc.purpose Smoke tests for primary navigation and persona workspaces
 * @doc.layer product
 * @doc.pattern Smoke Test
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Provider as JotaiProvider } from 'jotai';

// Mock localStorage
const localStorageMock = (() => {
    let store: Record<string, string> = {};
    return {
        getItem: (key: string) => store[key] || null,
        setItem: (key: string, value: string) => { store[key] = value; },
        removeItem: (key: string) => { delete store[key]; },
        clear: () => { store = {}; },
    };
})();
Object.defineProperty(window, 'localStorage', { value: localStorageMock });

// Mock hooks that make API calls
vi.mock('@/hooks', () => ({
    usePendingTasks: () => ({ tasks: { hitlApprovals: 0, securityAlerts: 0, failedWorkflows: 0, modelAlerts: 0 }, isLoading: false, error: null }),
    useRecentActivities: () => ({ activities: [], isLoading: false, error: null }),
    useMetrics: () => ({ metrics: {}, isLoading: false, error: null }),
    usePinnedFeatures: () => ({ features: [], unpin: vi.fn(), isMutating: false }),
}));

vi.mock('@/hooks/useMyWorkItems', () => ({
    useAllWorkItems: () => ({ workItems: [], isLoading: false }),
    useMyWorkItems: () => ({ workItems: [], isLoading: false }),
}));

vi.mock('@/hooks/usePersonaComposition', () => ({
    usePersonaComposition: () => ({
        merged: { quickActions: [], metrics: [], permissions: [], widgets: [] },
        roles: ['engineer'],
        hasPermission: () => true,
    }),
}));

vi.mock('@/lib/hooks/usePersonaQueries', () => ({
    useRoleDefinitions: () => ({ data: [], isLoading: false }),
    usePersonaPreference: () => ({ data: null, isLoading: false }),
    useUpdatePersonaPreference: () => ({ mutateAsync: vi.fn() }),
}));

vi.mock('@/lib/hooks/usePersonaSync', () => ({
    usePersonaSync: () => ({ isConnected: false, error: null }),
}));

vi.mock('@/lib/toast', () => ({
    useToast: () => ({
        showSuccess: vi.fn(),
        showError: vi.fn(),
        showWarning: vi.fn(),
    }),
}));

vi.mock('@/hooks/useAudit', () => ({
    useAuditLog: () => ({ logEvent: vi.fn() }),
}));

vi.mock('@/features/departments/hooks/useDepartments', () => ({
    useDepartments: () => ({ data: [], isLoading: false, error: null }),
}));

vi.mock('@/features/dashboard/hooks/useOrgKpis', () => ({
    useOrgKpis: () => ({ data: null, isLoading: false, error: null }),
}));

// Test wrapper with providers
function TestWrapper({ children, initialEntries = ['/'] }: { children: React.ReactNode; initialEntries?: string[] }) {
    const queryClient = new QueryClient({
        defaultOptions: {
            queries: { retry: false },
            mutations: { retry: false },
        },
    });

    return (
        <QueryClientProvider client={queryClient}>
            <JotaiProvider>
                <MemoryRouter initialEntries={initialEntries}>
                    {children}
                </MemoryRouter>
            </JotaiProvider>
        </QueryClientProvider>
    );
}

describe('Primary Navigation Smoke Tests', () => {
    beforeEach(() => {
        localStorageMock.clear();
        vi.clearAllMocks();
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    describe('Home Page', () => {
        it('renders HomePage without crashing', async () => {
            const HomePage = (await import('@/pages/HomePage')).default;

            render(
                <TestWrapper>
                    <HomePage />
                </TestWrapper>
            );

            // Should render without throwing
            await waitFor(() => {
                // Look for any content that indicates the page rendered
                expect(document.body.textContent).toBeTruthy();
            });
        });
    });

    describe('Control Tower (Dashboard)', () => {
        it('renders Dashboard without crashing', async () => {
            const { Dashboard } = await import('@/features/dashboard/Dashboard');

            render(
                <TestWrapper>
                    <Dashboard />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Control Tower/i)).toBeInTheDocument();
            });
        });
    });

    describe('Departments', () => {
        it('renders DepartmentList without crashing', async () => {
            const { DepartmentList } = await import('@/features/departments/DepartmentList');

            render(
                <TestWrapper>
                    <DepartmentList />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Departments/i)).toBeInTheDocument();
            });
        });
    });

    describe('Workflows', () => {
        it('renders WorkflowExplorer without crashing', async () => {
            const { WorkflowExplorer } = await import('@/features/workflows/WorkflowExplorer');

            render(
                <TestWrapper>
                    <WorkflowExplorer />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Workflow Explorer/i)).toBeInTheDocument();
            });
        });
    });

    describe('Reports', () => {
        it('renders ReportingDashboard without crashing', async () => {
            const { ReportingDashboard } = await import('@/features/reporting/ReportingDashboard');

            render(
                <TestWrapper>
                    <ReportingDashboard />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Reports/i)).toBeInTheDocument();
            });
        });
    });

    describe('Security', () => {
        it('renders SecurityCenter without crashing', async () => {
            const { SecurityCenter } = await import('@/features/security/SecurityCenter');

            render(
                <TestWrapper>
                    <SecurityCenter />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Security Center/i)).toBeInTheDocument();
            });
        });
    });

    describe('Real-Time Monitor', () => {
        it('renders RealTimeMonitor without crashing', async () => {
            const { RealTimeMonitor } = await import('@/features/monitoring/RealTimeMonitor');

            render(
                <TestWrapper>
                    <RealTimeMonitor />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Real-Time Monitor/i)).toBeInTheDocument();
            });
        });
    });

    describe('Org Builder', () => {
        it('renders OrgBuilderPage without crashing', async () => {
            const { OrgBuilderPage } = await import('@/features/org/OrgBuilderPage');

            render(
                <TestWrapper>
                    <OrgBuilderPage />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Org Builder/i)).toBeInTheDocument();
            });
        });
    });

    describe('Personas', () => {
        it('renders PersonasPage without crashing', async () => {
            const { PersonasPage } = await import('@/pages/PersonasPage');

            render(
                <TestWrapper initialEntries={['/personas/default']}>
                    <Routes>
                        <Route path="/personas/:workspaceId" element={<PersonasPage />} />
                    </Routes>
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Persona Preferences/i)).toBeInTheDocument();
            });
        });
    });

    describe('Settings', () => {
        it('renders SettingsPage without crashing', async () => {
            const { SettingsPage } = await import('@/features/settings/SettingsPage');

            render(
                <TestWrapper>
                    <SettingsPage />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Settings/i)).toBeInTheDocument();
            });
        });
    });
});

describe('Persona Workspace Smoke Tests', () => {
    beforeEach(() => {
        localStorageMock.clear();
        vi.clearAllMocks();
    });

    const personas = ['engineer', 'lead', 'sre', 'security', 'admin'] as const;

    personas.forEach((personaId) => {
        describe(`${personaId} workspace`, () => {
            it(`renders /workspace/${personaId} without crashing`, async () => {
                const HomePage = (await import('@/pages/HomePage')).default;

                render(
                    <TestWrapper initialEntries={[`/workspace/${personaId}`]}>
                        <Routes>
                            <Route path="/workspace/:personaId" element={<HomePage />} />
                        </Routes>
                    </TestWrapper>
                );

                await waitFor(() => {
                    // Should render without throwing
                    expect(document.body.textContent).toBeTruthy();
                });
            });
        });
    });
});

describe('Shared Component Smoke Tests', () => {
    beforeEach(() => {
        localStorageMock.clear();
        vi.clearAllMocks();
    });

    describe('PersonaFlowStrip', () => {
        it('renders PersonaFlowStrip without crashing', async () => {
            const { PersonaFlowStrip } = await import('@/shared/components/PersonaFlowStrip');

            render(
                <TestWrapper>
                    <PersonaFlowStrip personaId="engineer" />
                </TestWrapper>
            );

            await waitFor(() => {
                // Should render phase labels
                expect(screen.getByText(/Intake/i)).toBeInTheDocument();
            });
        });
    });

    describe('PersonaFlowSidebar', () => {
        it('renders PersonaFlowSidebar without crashing', async () => {
            const { PersonaFlowSidebar } = await import('@/shared/components/PersonaFlowSidebar');

            render(
                <TestWrapper>
                    <PersonaFlowSidebar personaId="engineer" />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Engineer Flow/i)).toBeInTheDocument();
            });
        });
    });

    describe('PersonaWorkspaceCard', () => {
        it('renders PersonaWorkspaceCard without crashing', async () => {
            const { PersonaWorkspaceCard } = await import('@/shared/components/PersonaWorkspaceCard');

            render(
                <TestWrapper>
                    <PersonaWorkspaceCard personaId="engineer" />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Engineer Workspace/i)).toBeInTheDocument();
            });
        });
    });

    describe('OrgGraphCanvas', () => {
        it('renders OrgGraphCanvas without crashing', async () => {
            const { OrgGraphCanvas } = await import('@/shared/components/org/OrgGraphCanvas');
            const { getMockOrgGraphData } = await import('@/features/org/mockOrgData');

            render(
                <TestWrapper>
                    <OrgGraphCanvas data={getMockOrgGraphData()} />
                </TestWrapper>
            );

            await waitFor(() => {
                // Should render department nodes
                expect(screen.getByText(/Platform Engineering/i)).toBeInTheDocument();
            });
        });
    });

    describe('GlobalFilterBar', () => {
        it('renders GlobalFilterBar without crashing', async () => {
            const { GlobalFilterBar } = await import('@/shared/components/GlobalFilterBar');

            render(
                <TestWrapper>
                    <GlobalFilterBar showPersonaFilter showDepartmentFilter />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Persona/i)).toBeInTheDocument();
            });
        });
    });

    describe('ContextualHints', () => {
        it('renders ContextualHints without crashing', async () => {
            const { ContextualHints } = await import('@/shared/components/NavigationHint');

            render(
                <TestWrapper>
                    <ContextualHints context="home" />
                </TestWrapper>
            );

            await waitFor(() => {
                expect(screen.getByText(/Related/i)).toBeInTheDocument();
            });
        });
    });
});
