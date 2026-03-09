/**
 * @doc.type service
 * @doc.purpose Automated threat analysis using STRIDE methodology (Journey 11.2)
 * @doc.layer product
 * @doc.pattern Security Service
 */

import type { SecurityFinding } from '../views/SecurityLens';

/**
 * Node interface for analysis
 */
interface AnalysisNode {
    id: string;
    type: string;
    label: string;
    data: Record<string, unknown>;
}

/**
 * ThreatAnalyzer Service
 * 
 * Automated security analysis with:
 * - SQL injection detection
 * - Missing rate limiting checks
 * - PII data logging detection
 * - STRIDE threat modeling
 * - PCI-DSS compliance validation
 */
export class ThreatAnalyzer {
    /**
     * Analyze nodes for security threats
     */
    analyze(nodes: AnalysisNode[]): SecurityFinding[] {
        const findings: SecurityFinding[] = [];

        findings.push(...this.detectSQLInjection(nodes));
        findings.push(...this.checkRateLimiting(nodes));
        findings.push(...this.detectPIILogging(nodes));
        findings.push(...this.validatePCIDSS(nodes));

        return findings;
    }

    /**
     * Detect potential SQL injection vulnerabilities
     */
    private detectSQLInjection(nodes: AnalysisNode[]): SecurityFinding[] {
        const findings: SecurityFinding[] = [];

        nodes.forEach((node) => {
            if (node.type === 'database' || node.type === 'api') {
                const hasParameterization = node.data.parameterized === true;
                const hasInputValidation = node.data.validated === true;

                if (!hasParameterization) {
                    findings.push({
                        id: `sqli-${node.id}`,
                        type: 'vulnerability',
                        severity: 'high',
                        title: 'Potential SQL Injection',
                        description: `Node "${node.label}" may be vulnerable to SQL injection attacks`,
                        nodeId: node.id,
                        recommendation: 'Use parameterized queries or ORM with prepared statements',
                    });
                }

                if (!hasInputValidation) {
                    findings.push({
                        id: `validation-${node.id}`,
                        type: 'vulnerability',
                        severity: 'medium',
                        title: 'Missing Input Validation',
                        description: `Node "${node.label}" lacks input validation`,
                        nodeId: node.id,
                        recommendation: 'Implement input validation and sanitization',
                    });
                }
            }
        });

        return findings;
    }

    /**
     * Check for missing rate limiting
     */
    private checkRateLimiting(nodes: AnalysisNode[]): SecurityFinding[] {
        const findings: SecurityFinding[] = [];

        nodes.forEach((node) => {
            if (node.type === 'api' || node.type === 'endpoint') {
                const hasRateLimit = node.data.rateLimited === true;

                if (!hasRateLimit) {
                    findings.push({
                        id: `ratelimit-${node.id}`,
                        type: 'missing_control',
                        severity: 'medium',
                        title: 'Missing Rate Limiting',
                        description: `API endpoint "${node.label}" lacks rate limiting`,
                        nodeId: node.id,
                        recommendation: 'Implement rate limiting to prevent abuse and DoS attacks',
                    });
                }
            }
        });

        return findings;
    }

    /**
     * Detect PII data logging
     */
    private detectPIILogging(nodes: AnalysisNode[]): SecurityFinding[] {
        const findings: SecurityFinding[] = [];
        const piiKeywords = ['email', 'password', 'ssn', 'credit', 'card', 'phone', 'address'];

        nodes.forEach((node) => {
            if (node.type === 'logging' || node.type === 'analytics') {
                const logFields = (node.data.fields as string[]) || [];
                const piiFields = logFields.filter((field) =>
                    piiKeywords.some((keyword) => field.toLowerCase().includes(keyword))
                );

                if (piiFields.length > 0) {
                    findings.push({
                        id: `pii-${node.id}`,
                        type: 'compliance',
                        severity: 'critical',
                        title: 'PII Data Logging Detected',
                        description: `Node "${node.label}" logs PII data: ${piiFields.join(', ')}`,
                        nodeId: node.id,
                        recommendation: 'Remove PII from logs or implement data masking/encryption',
                    });
                }
            }
        });

        return findings;
    }

    /**
     * Validate PCI-DSS compliance
     */
    private validatePCIDSS(nodes: AnalysisNode[]): SecurityFinding[] {
        const findings: SecurityFinding[] = [];

        const paymentNodes = nodes.filter((n) => n.type === 'payment' || n.label.toLowerCase().includes('payment'));

        paymentNodes.forEach((node) => {
            const hasEncryption = node.data.encrypted === true;
            const hasTokenization = node.data.tokenized === true;
            const hasAuditLog = node.data.auditLogged === true;

            if (!hasEncryption) {
                findings.push({
                    id: `pci-encrypt-${node.id}`,
                    type: 'compliance',
                    severity: 'critical',
                    title: 'PCI-DSS: Missing Encryption',
                    description: `Payment node "${node.label}" lacks encryption`,
                    nodeId: node.id,
                    recommendation: 'Implement TLS 1.2+ encryption for all payment data transmission',
                });
            }

            if (!hasTokenization) {
                findings.push({
                    id: `pci-token-${node.id}`,
                    type: 'compliance',
                    severity: 'high',
                    title: 'PCI-DSS: Missing Tokenization',
                    description: `Payment node "${node.label}" should use tokenization`,
                    nodeId: node.id,
                    recommendation: 'Implement tokenization to avoid storing sensitive card data',
                });
            }

            if (!hasAuditLog) {
                findings.push({
                    id: `pci-audit-${node.id}`,
                    type: 'compliance',
                    severity: 'medium',
                    title: 'PCI-DSS: Missing Audit Logging',
                    description: `Payment node "${node.label}" lacks audit logging`,
                    nodeId: node.id,
                    recommendation: 'Implement comprehensive audit logging for all payment transactions',
                });
            }
        });

        return findings;
    }

    /**
     * Run STRIDE threat analysis
     */
    runSTRIDEAnalysis(nodes: AnalysisNode[]): Record<string, SecurityFinding[]> {
        return {
            spoofing: this.checkSpoofing(nodes),
            tampering: this.checkTampering(nodes),
            repudiation: this.checkRepudiation(nodes),
            information_disclosure: this.checkInformationDisclosure(nodes),
            denial_of_service: this.checkDenialOfService(nodes),
            elevation_of_privilege: this.checkElevationOfPrivilege(nodes),
        };
    }

    private checkSpoofing(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => n.type === 'api' && !n.data.authenticated)
            .map((n) => ({
                id: `stride-spoof-${n.id}`,
                type: 'vulnerability' as const,
                severity: 'high' as const,
                title: 'STRIDE: Spoofing Threat',
                description: `Node "${n.label}" lacks authentication`,
                nodeId: n.id,
                recommendation: 'Implement authentication (OAuth2, JWT, API keys)',
            }));
    }

    private checkTampering(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => (n.type === 'data' || n.type === 'database') && !n.data.integrity)
            .map((n) => ({
                id: `stride-tamper-${n.id}`,
                type: 'vulnerability' as const,
                severity: 'high' as const,
                title: 'STRIDE: Tampering Threat',
                description: `Node "${n.label}" lacks integrity checks`,
                nodeId: n.id,
                recommendation: 'Implement checksums, signatures, or HMAC',
            }));
    }

    private checkRepudiation(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => (n.type === 'transaction' || n.type === 'payment') && !n.data.auditLogged)
            .map((n) => ({
                id: `stride-repud-${n.id}`,
                type: 'compliance' as const,
                severity: 'medium' as const,
                title: 'STRIDE: Repudiation Threat',
                description: `Node "${n.label}" lacks audit logging`,
                nodeId: n.id,
                recommendation: 'Implement comprehensive audit trails with timestamps',
            }));
    }

    private checkInformationDisclosure(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => n.type === 'data' && !n.data.encrypted)
            .map((n) => ({
                id: `stride-info-${n.id}`,
                type: 'vulnerability' as const,
                severity: 'critical' as const,
                title: 'STRIDE: Information Disclosure',
                description: `Node "${n.label}" stores unencrypted sensitive data`,
                nodeId: n.id,
                recommendation: 'Implement encryption at rest (AES-256)',
            }));
    }

    private checkDenialOfService(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => n.type === 'api' && !n.data.rateLimited)
            .map((n) => ({
                id: `stride-dos-${n.id}`,
                type: 'missing_control' as const,
                severity: 'medium' as const,
                title: 'STRIDE: Denial of Service',
                description: `Node "${n.label}" vulnerable to DoS attacks`,
                nodeId: n.id,
                recommendation: 'Implement rate limiting and request throttling',
            }));
    }

    private checkElevationOfPrivilege(nodes: AnalysisNode[]): SecurityFinding[] {
        return nodes
            .filter((n) => n.type === 'admin' && !n.data.authorized)
            .map((n) => ({
                id: `stride-privesc-${n.id}`,
                type: 'vulnerability' as const,
                severity: 'critical' as const,
                title: 'STRIDE: Elevation of Privilege',
                description: `Admin node "${n.label}" lacks authorization checks`,
                nodeId: n.id,
                recommendation: 'Implement role-based access control (RBAC)',
            }));
    }
}
