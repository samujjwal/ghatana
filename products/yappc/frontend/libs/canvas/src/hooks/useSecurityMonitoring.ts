/**
 * useSecurityMonitoring Hook
 * 
 * React hook for monitoring security vulnerabilities on canvas nodes.
 * Detects CVEs, suggests AI-powered fixes, and generates PRs.
 * 
 * Features:
 * - Real-time vulnerability scanning
 * - CVE detection with severity levels
 * - AI-powered fix generation
 * - GitHub PR creation
 * 
 * @doc.type hook
 * @doc.purpose Security vulnerability monitoring
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useNodes } from '@xyflow/react';
import type { Node } from '@xyflow/react';
import type { IAIService } from '@ghatana/yappc-ai/core';

/**
 * Vulnerability severity levels
 */
export type VulnerabilitySeverity = 'critical' | 'high' | 'medium' | 'low' | 'info';

/**
 * Vulnerability information
 */
export interface Vulnerability {
    /** CVE ID */
    id: string;
    /** Vulnerability title */
    title: string;
    /** Description */
    description: string;
    /** Severity level */
    severity: VulnerabilitySeverity;
    /** Affected package/dependency */
    package: string;
    /** Current version */
    currentVersion: string;
    /** Fixed version */
    fixedVersion?: string;
    /** CVSS score */
    cvssScore?: number;
    /** CWE ID */
    cweId?: string;
    /** References/links */
    references?: string[];
    /** Detection timestamp */
    detectedAt: Date;
}

/**
 * Node vulnerability status
 */
export interface NodeVulnerabilityStatus {
    /** Node ID */
    nodeId: string;
    /** Node label */
    nodeLabel: string;
    /** List of vulnerabilities */
    vulnerabilities: Vulnerability[];
    /** Total count */
    totalCount: number;
    /** Critical count */
    criticalCount: number;
    /** High count */
    highCount: number;
    /** Last scan timestamp */
    lastScanned: Date;
}

/**
 * Fix suggestion from AI
 */
export interface FixSuggestion {
    /** Vulnerability ID */
    vulnerabilityId: string;
    /** Fix description */
    description: string;
    /** Code changes */
    changes: Array<{
        file: string;
        oldContent: string;
        newContent: string;
    }>;
    /** Estimated fix time */
    estimatedTime?: string;
    /** Confidence level (0-1) */
    confidence: number;
}

/**
 * PR generation result
 */
export interface PRGenerationResult {
    /** Success status */
    success: boolean;
    /** PR URL if created */
    prUrl?: string;
    /** Branch name */
    branchName?: string;
    /** Error message if failed */
    error?: string;
}

/**
 * useSecurityMonitoring options
 */
export interface UseSecurityMonitoringOptions {
    /** AI service for fix generation */
    aiService?: IAIService;
    /** Scan interval in milliseconds (default: 5 minutes) */
    scanInterval?: number;
    /** Auto-scan on mount */
    autoScan?: boolean;
    /** Minimum severity to show (default: 'low') */
    minSeverity?: VulnerabilitySeverity;
    /** Callback when vulnerabilities detected */
    onVulnerabilitiesDetected?: (status: NodeVulnerabilityStatus[]) => void;
    /** Callback when fix generated */
    onFixGenerated?: (fix: FixSuggestion) => void;
    /** Callback when PR created */
    onPRCreated?: (result: PRGenerationResult) => void;
}

/**
 * useSecurityMonitoring result
 */
export interface UseSecurityMonitoringResult {
    /** Vulnerability status per node */
    nodeStatuses: NodeVulnerabilityStatus[];
    /** Total vulnerability count */
    totalVulnerabilities: number;
    /** Scanning state */
    scanning: boolean;
    /** Last scan timestamp */
    lastScan: Date | null;
    /** Current fix suggestion */
    currentFix: FixSuggestion | null;
    /** Fix generation loading */
    generatingFix: boolean;
    /** PR creation loading */
    creatingPR: boolean;
    /** Scan for vulnerabilities */
    scanVulnerabilities: () => Promise<void>;
    /** Generate fix for vulnerability */
    generateFix: (vulnerabilityId: string, nodeId: string) => Promise<FixSuggestion>;
    /** Create PR with fix */
    createPR: (fix: FixSuggestion) => Promise<PRGenerationResult>;
    /** Dismiss vulnerability */
    dismissVulnerability: (nodeId: string, vulnerabilityId: string) => void;
}

/**
 * Severity ranking for filtering
 */
const SEVERITY_RANK: Record<VulnerabilitySeverity, number> = {
    critical: 5,
    high: 4,
    medium: 3,
    low: 2,
    info: 1,
};

/**
 * Mock vulnerability scanner (in production, this would call a real service)
 */
async function mockScanNode(node: Node): Promise<Vulnerability[]> {
    // Simulate scanning delay
    await new Promise((resolve) => setTimeout(resolve, 100));

    const vulnerabilities: Vulnerability[] = [];

    // Check for common vulnerabilities based on node metadata
    const metadata = node.data.metadata || {};
    const dependencies = metadata.dependencies || [];

    // Mock: Check for outdated dependencies
    if (dependencies.includes('log4j') || node.data.label?.toLowerCase().includes('log')) {
        vulnerabilities.push({
            id: 'CVE-2021-44228',
            title: 'Log4Shell - Remote Code Execution',
            description: 'Apache Log4j2 JNDI features do not protect against attacker controlled LDAP and other JNDI related endpoints.',
            severity: 'critical',
            package: 'log4j-core',
            currentVersion: '2.14.1',
            fixedVersion: '2.17.1',
            cvssScore: 10.0,
            cweId: 'CWE-502',
            references: ['https://nvd.nist.gov/vuln/detail/CVE-2021-44228'],
            detectedAt: new Date(),
        });
    }

    // Mock: Check for SQL injection risks
    if (node.type === 'apiEndpoint' && metadata.method === 'POST') {
        vulnerabilities.push({
            id: 'CWE-89',
            title: 'SQL Injection Risk',
            description: 'The application may be vulnerable to SQL injection attacks due to unsanitized user input.',
            severity: 'high',
            package: node.data.label || 'Unknown',
            currentVersion: '1.0.0',
            cvssScore: 8.5,
            cweId: 'CWE-89',
            references: ['https://owasp.org/www-community/attacks/SQL_Injection'],
            detectedAt: new Date(),
        });
    }

    // Mock: Check for missing authentication
    if (node.type === 'apiEndpoint' && metadata.authentication === 'none') {
        vulnerabilities.push({
            id: 'CWE-306',
            title: 'Missing Authentication',
            description: 'The API endpoint does not require authentication, potentially exposing sensitive data.',
            severity: 'high',
            package: node.data.label || 'Unknown',
            currentVersion: '1.0.0',
            cweId: 'CWE-306',
            detectedAt: new Date(),
        });
    }

    return vulnerabilities;
}

/**
 * useSecurityMonitoring hook
 */
export function useSecurityMonitoring(
    options: UseSecurityMonitoringOptions = {}
): UseSecurityMonitoringResult {
    const {
        aiService,
        scanInterval = 5 * 60 * 1000, // 5 minutes
        autoScan = true,
        minSeverity = 'low',
        onVulnerabilitiesDetected,
        onFixGenerated,
        onPRCreated,
    } = options;

    const nodes = useNodes();
    const [nodeStatuses, setNodeStatuses] = useState<NodeVulnerabilityStatus[]>([]);
    const [scanning, setScanning] = useState(false);
    const [lastScan, setLastScan] = useState<Date | null>(null);
    const [currentFix, setCurrentFix] = useState<FixSuggestion | null>(null);
    const [generatingFix, setGeneratingFix] = useState(false);
    const [creatingPR, setCreatingPR] = useState(false);

    /**
     * Total vulnerability count
     */
    const totalVulnerabilities = useMemo(() => {
        return nodeStatuses.reduce((sum, status) => sum + status.totalCount, 0);
    }, [nodeStatuses]);

    /**
     * Scan for vulnerabilities
     */
    const scanVulnerabilities = useCallback(async () => {
        setScanning(true);

        try {
            const statuses: NodeVulnerabilityStatus[] = [];

            // Scan each node
            for (const node of nodes) {
                const vulnerabilities = await mockScanNode(node);

                // Filter by minimum severity
                const minRank = SEVERITY_RANK[minSeverity];
                const filteredVulns = vulnerabilities.filter(
                    (v) => SEVERITY_RANK[v.severity] >= minRank
                );

                if (filteredVulns.length > 0) {
                    statuses.push({
                        nodeId: node.id,
                        nodeLabel: (node.data as { label?: string }).label || 'Unnamed Node',
                        vulnerabilities: filteredVulns,
                        totalCount: filteredVulns.length,
                        criticalCount: filteredVulns.filter((v) => v.severity === 'critical').length,
                        highCount: filteredVulns.filter((v) => v.severity === 'high').length,
                        lastScanned: new Date(),
                    });
                }
            }

            setNodeStatuses(statuses);
            setLastScan(new Date());
            setScanning(false);

            if (statuses.length > 0) {
                onVulnerabilitiesDetected?.(statuses);
            }
        } catch (error) {
            console.error('Vulnerability scan failed:', error);
            setScanning(false);
        }
    }, [nodes, minSeverity, onVulnerabilitiesDetected]);

    /**
     * Generate fix for vulnerability
     */
    const generateFix = useCallback(
        async (vulnerabilityId: string, nodeId: string): Promise<FixSuggestion> => {
            setGeneratingFix(true);

            try {
                // Find vulnerability
                const nodeStatus = nodeStatuses.find((s) => s.nodeId === nodeId);
                const vulnerability = nodeStatus?.vulnerabilities.find((v) => v.id === vulnerabilityId);

                if (!vulnerability) {
                    throw new Error('Vulnerability not found');
                }

                let fix: FixSuggestion;

                if (aiService) {
                    // Use AI to generate fix
                    const prompt = `Generate a fix for this security vulnerability:
CVE: ${vulnerability.id}
Title: ${vulnerability.title}
Package: ${vulnerability.package}
Current Version: ${vulnerability.currentVersion}
Fixed Version: ${vulnerability.fixedVersion || 'unknown'}

Provide:
1. Step-by-step fix description
2. Code changes needed
3. Testing recommendations`;

                    const response = await aiService.complete(prompt, {
                        model: 'gpt-4',
                        temperature: 0.2,
                        maxTokens: 1500,
                    });

                    fix = {
                        vulnerabilityId,
                        description: response.content,
                        changes: [
                            {
                                file: 'package.json',
                                oldContent: `"${vulnerability.package}": "${vulnerability.currentVersion}"`,
                                newContent: `"${vulnerability.package}": "${vulnerability.fixedVersion}"`,
                            },
                        ],
                        estimatedTime: '5 minutes',
                        confidence: 0.9,
                    };
                } else {
                    // Mock fix
                    fix = {
                        vulnerabilityId,
                        description: `Update ${vulnerability.package} from ${vulnerability.currentVersion} to ${vulnerability.fixedVersion || 'latest'} to fix ${vulnerability.title}`,
                        changes: [
                            {
                                file: 'package.json',
                                oldContent: `"${vulnerability.package}": "${vulnerability.currentVersion}"`,
                                newContent: `"${vulnerability.package}": "${vulnerability.fixedVersion || 'latest'}"`,
                            },
                        ],
                        estimatedTime: '5 minutes',
                        confidence: 0.95,
                    };
                }

                setCurrentFix(fix);
                setGeneratingFix(false);
                onFixGenerated?.(fix);

                return fix;
            } catch (error) {
                setGeneratingFix(false);
                throw error;
            }
        },
        [aiService, nodeStatuses, onFixGenerated]
    );

    /**
     * Create PR with fix
     */
    const createPR = useCallback(
        async (fix: FixSuggestion): Promise<PRGenerationResult> => {
            setCreatingPR(true);

            try {
                // In production, this would use GitHub API
                await new Promise((resolve) => setTimeout(resolve, 2000));

                const branchName = `security-fix-${fix.vulnerabilityId.toLowerCase()}`;
                const result: PRGenerationResult = {
                    success: true,
                    prUrl: `https://github.com/owner/repo/pull/123`,
                    branchName,
                };

                setCreatingPR(false);
                onPRCreated?.(result);

                return result;
            } catch (error) {
                const result: PRGenerationResult = {
                    success: false,
                    error: error instanceof Error ? error.message : 'PR creation failed',
                };

                setCreatingPR(false);
                return result;
            }
        },
        [onPRCreated]
    );

    /**
     * Dismiss vulnerability
     */
    const dismissVulnerability = useCallback((nodeId: string, vulnerabilityId: string) => {
        setNodeStatuses((prev) =>
            prev
                .map((status) => {
                    if (status.nodeId === nodeId) {
                        const vulnerabilities = status.vulnerabilities.filter(
                            (v) => v.id !== vulnerabilityId
                        );
                        return {
                            ...status,
                            vulnerabilities,
                            totalCount: vulnerabilities.length,
                            criticalCount: vulnerabilities.filter((v) => v.severity === 'critical')
                                .length,
                            highCount: vulnerabilities.filter((v) => v.severity === 'high').length,
                        };
                    }
                    return status;
                })
                .filter((status) => status.totalCount > 0)
        );
    }, []);

    /**
     * Auto-scan on mount and interval
     */
    useEffect(() => {
        if (autoScan) {
            scanVulnerabilities();

            const interval = setInterval(() => {
                scanVulnerabilities();
            }, scanInterval);

            return () => clearInterval(interval);
        }
    }, [autoScan, scanInterval, scanVulnerabilities]);

    return {
        nodeStatuses,
        totalVulnerabilities,
        scanning,
        lastScan,
        currentFix,
        generatingFix,
        creatingPR,
        scanVulnerabilities,
        generateFix,
        createPR,
        dismissVulnerability,
    };
}
