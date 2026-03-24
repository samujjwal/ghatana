/**
 * SecurityAlerts Tests
 * 
 * Tests for security vulnerability alerts component
 * 
 * @doc.type test
 * @doc.purpose SecurityAlerts component tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SecurityAlerts } from '../SecurityAlerts';
import * as hooks from '../../hooks/useSecurityMonitoring';
import type { UseSecurityMonitoringResult, VulnerabilitySeverity } from '../../hooks/useSecurityMonitoring';

// Mock useSecurityMonitoring hook
vi.mock('../../hooks/useSecurityMonitoring');

describe('SecurityAlerts', () => {
    let mockResult: UseSecurityMonitoringResult;

    beforeEach(() => {
        // Default mock result
        mockResult = {
            nodeStatuses: [],
            totalVulnerabilities: 0,
            scanning: false,
            lastScan: null,
            currentFix: null,
            generatingFix: false,
            creatingPR: false,
            scanVulnerabilities: vi.fn(),
            generateFix: vi.fn(),
            createPR: vi.fn(),
            dismissVulnerability: vi.fn(),
        };

        vi.mocked(hooks.useSecurityMonitoring).mockReturnValue(mockResult);
    });

    describe('Panel Rendering', () => {
        it('should render panel with title and badge', () => {
            mockResult.totalVulnerabilities = 5;

            render(<SecurityAlerts />);

            expect(screen.getByText('Security Alerts')).toBeInTheDocument();
            expect(screen.getByText('5')).toBeInTheDocument();
        });

        it('should not render when showPanel is false', () => {
            render(<SecurityAlerts showPanel={false} />);

            expect(screen.queryByText('Security Alerts')).not.toBeInTheDocument();
        });

        it('should show success message when no vulnerabilities', () => {
            render(<SecurityAlerts />);

            expect(
                screen.getByText(/No vulnerabilities detected/i)
            ).toBeInTheDocument();
        });

        it('should show scanning indicator', () => {
            mockResult.scanning = true;

            render(<SecurityAlerts />);

            expect(
                screen.getByText(/Scanning for vulnerabilities/i)
            ).toBeInTheDocument();
        });

        it('should show last scan time', () => {
            mockResult.lastScan = new Date('2025-01-15T10:30:00Z');

            render(<SecurityAlerts />);

            expect(screen.getByText(/Last scanned:/i)).toBeInTheDocument();
        });
    });

    describe('Vulnerability Display', () => {
        it('should render vulnerability nodes', () => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'User Service',
                    vulnerabilities: [
                        {
                            id: 'CVE-2021-44228',
                            title: 'Log4Shell RCE',
                            description: 'Remote code execution in Log4j',
                            severity: 'critical' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'org.apache.logging.log4j:log4j-core',
                            currentVersion: '2.14.0',
                            fixedVersion: '2.17.1',
                            cvssScore: 10.0,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                        },
                    ],
                    totalCount: 1,
                    criticalCount: 1,
                    highCount: 0,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];
            mockResult.totalVulnerabilities = 1;

            render(<SecurityAlerts />);

            expect(screen.getByText('User Service')).toBeInTheDocument();
            expect(screen.getByText('1 Critical')).toBeInTheDocument();
            expect(screen.getByText('1 Total')).toBeInTheDocument();
        });

        it('should show severity badges correctly', () => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [
                        {
                            id: 'CVE-TEST-1',
                            title: 'Critical Vuln',
                            description: 'Test',
                            severity: 'critical' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'test-package',
                            currentVersion: '1.0.0',
                            cvssScore: 9.5,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                        {
                            id: 'CVE-TEST-2',
                            title: 'High Vuln',
                            description: 'Test',
                            severity: 'high' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'test-package',
                            currentVersion: '1.0.0',
                            cvssScore: 7.5,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                    ],
                    totalCount: 2,
                    criticalCount: 1,
                    highCount: 1,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];

            render(<SecurityAlerts />);

            expect(screen.getByText('1 Critical')).toBeInTheDocument();
            expect(screen.getByText('1 High')).toBeInTheDocument();
        });

        it('should expand/collapse node details on click', async () => {
            const user = userEvent.setup();

            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [
                        {
                            id: 'CVE-TEST',
                            title: 'Test Vulnerability',
                            description: 'Test description',
                            severity: 'high' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'test-package',
                            currentVersion: '1.0.0',
                            cvssScore: 8.0,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                    ],
                    totalCount: 1,
                    criticalCount: 0,
                    highCount: 1,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];

            render(<SecurityAlerts />);

            // Initially collapsed - vulnerability details not visible
            expect(screen.queryByText('Test Vulnerability')).not.toBeInTheDocument();

            // Click to expand
            await user.click(screen.getByText('Test Node'));

            // Now visible
            await waitFor(() => {
                expect(screen.getByText('Test Vulnerability')).toBeInTheDocument();
            });

            // Click to collapse
            await user.click(screen.getByText('Test Node'));

            // Hidden again
            await waitFor(() => {
                expect(screen.queryByText('Test Vulnerability')).not.toBeInTheDocument();
            });
        });
    });

    describe('Vulnerability Details', () => {
        beforeEach(() => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [
                        {
                            id: 'CVE-2021-44228',
                            title: 'Log4Shell',
                            description: 'RCE vulnerability',
                            severity: 'critical' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'log4j-core',
                            currentVersion: '2.14.0',
                            fixedVersion: '2.17.1',
                            cvssScore: 10.0,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                    ],
                    totalCount: 1,
                    criticalCount: 1,
                    highCount: 0,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];
        });

        it('should show vulnerability details when expanded', async () => {
            const user = userEvent.setup();

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));

            await waitFor(() => {
                expect(screen.getByText('Log4Shell')).toBeInTheDocument();
                expect(screen.getByText('RCE vulnerability')).toBeInTheDocument();
                expect(screen.getByText('CVE-2021-44228')).toBeInTheDocument();
                expect(screen.getByText('log4j-core 2.14.0')).toBeInTheDocument();
                expect(screen.getByText('Fix: 2.17.1')).toBeInTheDocument();
                expect(screen.getByText('10.0')).toBeInTheDocument();
            });
        });

        it('should have Fix with AI button', async () => {
            const user = userEvent.setup();

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Fix with AI/i })).toBeInTheDocument();
            });
        });

        it('should have Dismiss button', async () => {
            const user = userEvent.setup();

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Dismiss/i })).toBeInTheDocument();
            });
        });
    });

    describe('Fix Dialog', () => {
        beforeEach(() => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [
                        {
                            id: 'CVE-TEST',
                            title: 'Test Vulnerability',
                            description: 'Test description',
                            severity: 'high' as VulnerabilitySeverity,
                            detectedAt: new Date('2025-01-15T10:00:00Z'),
                            package: 'test-package',
                            currentVersion: '1.0.0',
                            cvssScore: 8.0,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                    ],
                    totalCount: 1,
                    criticalCount: 0,
                    highCount: 1,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];
        });

        it('should open fix dialog on Fix with AI click', async () => {
            const user = userEvent.setup();

            render(<SecurityAlerts />);

            // Expand node
            await user.click(screen.getByText('Test Node'));

            // Click Fix with AI
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            // Dialog should open
            await waitFor(() => {
                expect(screen.getByText('AI-Powered Security Fix')).toBeInTheDocument();
            });
        });

        it('should call generateFix when opening dialog', async () => {
            const user = userEvent.setup();
            const generateFixMock = vi.fn();
            mockResult.generateFix = generateFixMock;

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            await waitFor(() => {
                expect(generateFixMock).toHaveBeenCalledWith('CVE-TEST', 'node-1');
            });
        });

        it('should show generating indicator', async () => {
            const user = userEvent.setup();
            mockResult.generatingFix = true;

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            await waitFor(() => {
                expect(screen.getByText(/Generating AI-powered fix/i)).toBeInTheDocument();
            });
        });

        it('should show fix suggestions', async () => {
            const user = userEvent.setup();

            mockResult.currentFix = {
                vulnerabilityId: 'CVE-TEST',
                description: 'Upgrade to version 2.0.0 to fix vulnerability',
                changes: [
                    {
                        file: 'pom.xml',
                        oldContent: '<version>1.0.0</version>',
                        newContent: '<version>2.0.0</version>',
                    },
                ],
                confidence: 0.95,
                estimatedTime: '5 minutes',
            };

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            await waitFor(() => {
                expect(
                    screen.getByText('Upgrade to version 2.0.0 to fix vulnerability')
                ).toBeInTheDocument();
                expect(screen.getByText('pom.xml')).toBeInTheDocument();
                expect(screen.getByText(/Confidence: 95%/i)).toBeInTheDocument();
                expect(screen.getByText(/Est\. time: 5 minutes/i)).toBeInTheDocument();
            });
        });

        it('should show Create PR button when fix is ready', async () => {
            const user = userEvent.setup();

            mockResult.currentFix = {
                vulnerabilityId: 'CVE-TEST',
                description: 'Fix',
                changes: [],
                confidence: 0.9,
            };

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            await waitFor(() => {
                expect(screen.getByRole('button', { name: /Create PR/i })).toBeInTheDocument();
            });
        });

        it('should call createPR on Create PR click', async () => {
            const user = userEvent.setup();
            const createPRMock = vi.fn().mockResolvedValue({
                success: true,
                prUrl: 'https://github.com/test/repo/pull/123',
            });
            mockResult.createPR = createPRMock;
            mockResult.currentFix = {
                vulnerabilityId: 'CVE-TEST',
                description: 'Fix',
                changes: [],
                confidence: 0.9,
            };

            // Mock window.open
            const openMock = vi.fn();
            vi.stubGlobal('open', openMock);

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Fix with AI/i }));

            const createPRButton = await screen.findByRole('button', { name: /Create PR/i });
            await user.click(createPRButton);

            await waitFor(() => {
                expect(createPRMock).toHaveBeenCalled();
                expect(openMock).toHaveBeenCalledWith(
                    'https://github.com/test/repo/pull/123',
                    '_blank'
                );
            });

            vi.unstubAllGlobals();
        });
    });

    describe('Actions', () => {
        it('should call scanVulnerabilities on refresh click', async () => {
            const user = userEvent.setup();
            const scanMock = vi.fn();
            mockResult.scanVulnerabilities = scanMock;

            render(<SecurityAlerts />);

            const refreshButton = screen.getByRole('button', { name: /Refresh scan/i });
            await user.click(refreshButton);

            expect(scanMock).toHaveBeenCalled();
        });

        it('should disable refresh during scan', () => {
            mockResult.scanning = true;

            render(<SecurityAlerts />);

            const refreshButton = screen.getByRole('button', { name: /Refresh scan/i });
            expect(refreshButton).toBeDisabled();
        });

        it('should call dismissVulnerability on dismiss click', async () => {
            const user = userEvent.setup();
            const dismissMock = vi.fn();
            mockResult.dismissVulnerability = dismissMock;

            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [
                        {
                            id: 'CVE-TEST',
                            title: 'Test Vulnerability',
                            description: 'Test',
                            severity: 'low' as VulnerabilitySeverity,
                            package: 'test',
                            currentVersion: '1.0.0',
                            cvssScore: 3.0,
                            detectedAt: new Date("2025-01-15T10:00:00Z"),
                        },
                    ],
                    totalCount: 1,
                    criticalCount: 0,
                    highCount: 0,
                    mediumCount: 0,
                    lowCount: 1,
                },
            ];

            render(<SecurityAlerts />);

            await user.click(screen.getByText('Test Node'));
            await user.click(screen.getByRole('button', { name: /Dismiss/i }));

            expect(dismissMock).toHaveBeenCalledWith('node-1', 'CVE-TEST');
        });

        it('should call onClose when close button clicked', async () => {
            const user = userEvent.setup();
            const onCloseMock = vi.fn();

            render(<SecurityAlerts onClose={onCloseMock} />);

            const closeButton = screen.getByRole('button', { name: /close/i });
            await user.click(closeButton);

            expect(onCloseMock).toHaveBeenCalled();
        });
    });

    describe('Visual Indicators', () => {
        it('should show critical vulnerabilities with pulsing animation', () => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Critical Node',
                    vulnerabilities: [],
                    totalCount: 1,
                    criticalCount: 1,
                    highCount: 0,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];

            const { container } = render(<SecurityAlerts />);

            // Should have critical border color
            const nodeCard = container.querySelector('[style*="rgb(211, 47, 47)"]');
            expect(nodeCard).toBeInTheDocument();
        });

        it('should show shield icon with correct color', () => {
            mockResult.nodeStatuses = [
                {
                    nodeLabel: 'Test Node',
                    vulnerabilities: [],
                    totalCount: 1,
                    criticalCount: 1,
                    highCount: 0,
                    mediumCount: 0,
                    lowCount: 0,
                },
            ];

            render(<SecurityAlerts />);

            // Check if shield icon exists (Material-UI renders SVGs)
            const shieldIcon = screen.getByText('Test Node').parentElement?.querySelector('svg');
            expect(shieldIcon).toBeInTheDocument();
        });
    });
});
