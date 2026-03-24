/**
 * Tests for ThreatAnalyzer service (Journey 11.2)
 */

import { describe, it, expect } from 'vitest';
import { ThreatAnalyzer } from '../services/ThreatAnalyzer';

describe('ThreatAnalyzer', () => {
    const analyzer = new ThreatAnalyzer();

    describe('detectSQLInjection', () => {
        it('detects missing parameterized queries', () => {
            const nodes = [
                { id: '1', type: 'database', data: { label: 'User Query', parameterized: false } },
            ];

            const findings = analyzer.detectSQLInjection(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].type).toBe('SQL Injection');
            expect(findings[0].severity).toBe('high');
            expect(findings[0].title).toContain('parameterized');
        });

        it('detects missing input validation', () => {
            const nodes = [
                { id: '1', type: 'database', data: { label: 'User Query', inputValidation: false } },
            ];

            const findings = analyzer.detectSQLInjection(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].severity).toBe('medium');
            expect(findings[0].title).toContain('input validation');
        });

        it('returns empty for safe queries', () => {
            const nodes = [
                { id: '1', type: 'database', data: { label: 'Safe Query', parameterized: true, inputValidation: true } },
            ];

            const findings = analyzer.detectSQLInjection(nodes);

            expect(findings).toHaveLength(0);
        });
    });

    describe('checkRateLimiting', () => {
        it('detects missing rate limiting on API', () => {
            const nodes = [
                { id: '1', type: 'api', data: { label: 'API Endpoint', rateLimited: false } },
            ];

            const findings = analyzer.checkRateLimiting(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].type).toBe('Denial of Service');
            expect(findings[0].severity).toBe('medium');
            expect(findings[0].title).toContain('rate limiting');
        });

        it('returns empty for rate-limited endpoints', () => {
            const nodes = [
                { id: '1', type: 'api', data: { label: 'Protected API', rateLimited: true } },
            ];

            const findings = analyzer.checkRateLimiting(nodes);

            expect(findings).toHaveLength(0);
        });
    });

    describe('detectPIILogging', () => {
        it('detects email in logs', () => {
            const nodes = [
                { id: '1', type: 'logging', data: { label: 'Log email address' } },
            ];

            const findings = analyzer.detectPIILogging(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].type).toBe('Information Disclosure');
            expect(findings[0].severity).toBe('critical');
            expect(findings[0].title).toContain('email');
        });

        it('detects password in logs', () => {
            const nodes = [
                { id: '1', type: 'logging', data: { label: 'Log user password' } },
            ];

            const findings = analyzer.detectPIILogging(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].title).toContain('password');
        });

        it('detects SSN in logs', () => {
            const nodes = [
                { id: '1', type: 'logging', data: { label: 'Log SSN number' } },
            ];

            const findings = analyzer.detectPIILogging(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].title).toContain('ssn');
        });

        it('detects credit card in logs', () => {
            const nodes = [
                { id: '1', type: 'logging', data: { label: 'Log credit card data' } },
            ];

            const findings = analyzer.detectPIILogging(nodes);

            expect(findings).toHaveLength(1);
            expect(findings[0].title).toContain('credit');
        });

        it('returns empty for safe logging', () => {
            const nodes = [
                { id: '1', type: 'logging', data: { label: 'Log request timestamp' } },
            ];

            const findings = analyzer.detectPIILogging(nodes);

            expect(findings).toHaveLength(0);
        });
    });

    describe('validatePCIDSS', () => {
        it('detects missing encryption', () => {
            const nodes = [
                { id: '1', type: 'payment', data: { label: 'Payment', encrypted: false } },
            ];

            const findings = analyzer.validatePCIDSS(nodes);

            const encryptionFinding = findings.find(f => f.title.includes('encryption'));
            expect(encryptionFinding).toBeDefined();
            expect(encryptionFinding?.severity).toBe('critical');
        });

        it('detects missing tokenization', () => {
            const nodes = [
                { id: '1', type: 'payment', data: { label: 'Payment', tokenized: false } },
            ];

            const findings = analyzer.validatePCIDSS(nodes);

            const tokenFinding = findings.find(f => f.title.includes('tokenization'));
            expect(tokenFinding).toBeDefined();
            expect(tokenFinding?.severity).toBe('high');
        });

        it('detects missing audit logging', () => {
            const nodes = [
                { id: '1', type: 'payment', data: { label: 'Payment', auditLogged: false } },
            ];

            const findings = analyzer.validatePCIDSS(nodes);

            const auditFinding = findings.find(f => f.title.includes('audit'));
            expect(auditFinding).toBeDefined();
            expect(auditFinding?.severity).toBe('medium');
        });

        it('returns empty for compliant payment nodes', () => {
            const nodes = [
                { id: '1', type: 'payment', data: { label: 'Payment', encrypted: true, tokenized: true, auditLogged: true } },
            ];

            const findings = analyzer.validatePCIDSS(nodes);

            expect(findings).toHaveLength(0);
        });
    });

    describe('runSTRIDEAnalysis', () => {
        it('detects all STRIDE threats', () => {
            const nodes = [
                { id: '1', type: 'api', data: { label: 'API', authenticated: false } },
                { id: '2', type: 'data', data: { label: 'Data', integrityCheck: false } },
                { id: '3', type: 'transaction', data: { label: 'Trans', auditLogged: false } },
                { id: '4', type: 'database', data: { label: 'DB', encryptedAtRest: false } },
                { id: '5', type: 'api', data: { label: 'API2', rateLimited: false } },
                { id: '6', type: 'admin', data: { label: 'Admin', authorized: false } },
            ];

            const findings = analyzer.runSTRIDEAnalysis(nodes);

            const spoofing = findings.find(f => f.type === 'Spoofing');
            const tampering = findings.find(f => f.type === 'Tampering');
            const repudiation = findings.find(f => f.type === 'Repudiation');
            const infoDisclosure = findings.find(f => f.type === 'Information Disclosure');
            const dos = findings.find(f => f.type === 'Denial of Service');
            const privilege = findings.find(f => f.type === 'Elevation of Privilege');

            expect(spoofing).toBeDefined();
            expect(tampering).toBeDefined();
            expect(repudiation).toBeDefined();
            expect(infoDisclosure).toBeDefined();
            expect(dos).toBeDefined();
            expect(privilege).toBeDefined();
        });

        it('assigns correct severities', () => {
            const nodes = [
                { id: '1', type: 'api', data: { label: 'API', authenticated: false } },
                { id: '2', type: 'database', data: { label: 'DB', encryptedAtRest: false } },
                { id: '3', type: 'admin', data: { label: 'Admin', authorized: false } },
            ];

            const findings = analyzer.runSTRIDEAnalysis(nodes);

            const spoofing = findings.find(f => f.type === 'Spoofing');
            const infoDisclosure = findings.find(f => f.type === 'Information Disclosure');
            const privilege = findings.find(f => f.type === 'Elevation of Privilege');

            expect(spoofing?.severity).toBe('high');
            expect(infoDisclosure?.severity).toBe('critical');
            expect(privilege?.severity).toBe('critical');
        });
    });

    describe('analyze', () => {
        it('combines all analysis results', () => {
            const nodes = [
                { id: '1', type: 'api', data: { label: 'API', authenticated: false, rateLimited: false } },
                { id: '2', type: 'database', data: { label: 'DB', parameterized: false } },
                { id: '3', type: 'logging', data: { label: 'Log password' } },
                { id: '4', type: 'payment', data: { label: 'Payment', encrypted: false } },
            ];

            const findings = analyzer.analyze(nodes);

            // Should have findings from multiple methods
            expect(findings.length).toBeGreaterThan(0);

            const sqlInjection = findings.find(f => f.type === 'SQL Injection');
            const piiLogging = findings.find(f => f.title.includes('password'));
            const strideFindings = findings.filter(f => ['Spoofing', 'Denial of Service'].includes(f.type));

            expect(sqlInjection).toBeDefined();
            expect(piiLogging).toBeDefined();
            expect(strideFindings.length).toBeGreaterThan(0);
        });
    });
});
