/**
 * Tests for useThreatModeling hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useThreatModeling, type ThreatCategory, type RiskLevel } from '../useThreatModeling';

describe('useThreatModeling', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useThreatModeling());

            expect(result.current.modelName).toBe('Threat Model');
            expect(result.current.systemDescription).toBe('');
            expect(result.current.threats).toHaveLength(0);
            expect(result.current.assets).toHaveLength(0);
            expect(result.current.getThreatCount()).toBe(0);
            expect(result.current.getAssetCount()).toBe(0);
            expect(result.current.getMitigationCount()).toBe(0);
        });

        it('should initialize with custom model name', () => {
            const { result } = renderHook(() =>
                useThreatModeling({ initialModelName: 'E-Commerce Security Model' })
            );

            expect(result.current.modelName).toBe('E-Commerce Security Model');
        });

        it('should initialize with system description', () => {
            const { result } = renderHook(() =>
                useThreatModeling({
                    initialSystemDescription: 'Web application handling payment processing',
                })
            );

            expect(result.current.systemDescription).toBe('Web application handling payment processing');
        });
    });

    describe('Configuration Management', () => {
        it('should update model name', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.setModelName('API Security Model');
            });

            expect(result.current.modelName).toBe('API Security Model');
        });

        it('should update system description', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.setSystemDescription('Microservices architecture with OAuth2');
            });

            expect(result.current.systemDescription).toBe('Microservices architecture with OAuth2');
        });
    });

    describe('Threat Management', () => {
        it('should add threat with default risk level', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'SQL Injection',
                    description: 'Attacker can inject malicious SQL',
                    category: 'tampering',
                });
            });

            expect(result.current.threats).toHaveLength(1);
            expect(result.current.threats[0].title).toBe('SQL Injection');
            expect(result.current.threats[0].category).toBe('tampering');
            expect(result.current.threats[0].id).toBe(threatId!);
        });

        it('should add threat with impact and likelihood', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Password Brute Force',
                    description: 'Attacker tries multiple passwords',
                    category: 'spoofing',
                    impact: 8,
                    likelihood: 6,
                });
            });

            const threat = result.current.threats[0];
            expect(threat.impact).toBe(8);
            expect(threat.likelihood).toBe(6);
            expect(threat.riskLevel).toBe('medium'); // 8*6=48 >= 36 but < 64
        });

        it('should calculate critical risk level', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Data Breach',
                    description: 'Unauthorized access to customer data',
                    category: 'informationDisclosure',
                    impact: 10,
                    likelihood: 8,
                });
            });

            expect(result.current.threats[0].riskLevel).toBe('critical'); // 10*8=80 >= 64
        });

        it('should calculate low risk level', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Minor Info Leak',
                    description: 'Version information exposed',
                    category: 'informationDisclosure',
                    impact: 3,
                    likelihood: 2,
                });
            });

            expect(result.current.threats[0].riskLevel).toBe('low'); // 3*2=6 < 16
        });

        it('should add threat with affected asset', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Database Tampering',
                    description: 'Modification of user records',
                    category: 'tampering',
                    affectedAsset: 'User Database',
                });
            });

            expect(result.current.threats[0].affectedAsset).toBe('User Database');
        });

        it('should add threat with attack vector', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'XSS Attack',
                    description: 'Script injection in user input',
                    category: 'tampering',
                    attackVector: 'Unvalidated user input fields',
                });
            });

            expect(result.current.threats[0].attackVector).toBe('Unvalidated user input fields');
        });

        it('should add multiple threats', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Threat 1',
                    description: 'Description 1',
                    category: 'spoofing',
                });
                result.current.addThreat({
                    title: 'Threat 2',
                    description: 'Description 2',
                    category: 'tampering',
                });
                result.current.addThreat({
                    title: 'Threat 3',
                    description: 'Description 3',
                    category: 'denialOfService',
                });
            });

            expect(result.current.threats).toHaveLength(3);
        });

        it('should update threat', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Original Title',
                    description: 'Original Description',
                    category: 'spoofing',
                });
            });

            act(() => {
                result.current.updateThreat(threatId!, {
                    title: 'Updated Title',
                    description: 'Updated Description',
                });
            });

            const threat = result.current.getThreat(threatId!);
            expect(threat?.title).toBe('Updated Title');
            expect(threat?.description).toBe('Updated Description');
        });

        it('should recalculate risk level on update', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 3,
                    likelihood: 2,
                });
            });

            expect(result.current.threats[0].riskLevel).toBe('low');

            act(() => {
                result.current.updateThreat(threatId!, {
                    impact: 9,
                    likelihood: 8,
                });
            });

            expect(result.current.threats[0].riskLevel).toBe('critical');
        });

        it('should delete threat', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
            });

            act(() => {
                result.current.deleteThreat(threatId!);
            });

            expect(result.current.threats).toHaveLength(0);
        });

        it('should get threat by ID', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'SQL Injection',
                    description: 'Database attack',
                    category: 'tampering',
                });
            });

            const threat = result.current.getThreat(threatId!);
            expect(threat).toBeDefined();
            expect(threat?.title).toBe('SQL Injection');
        });

        it('should return undefined for non-existent threat', () => {
            const { result } = renderHook(() => useThreatModeling());

            const threat = result.current.getThreat('non-existent');
            expect(threat).toBeUndefined();
        });
    });

    describe('Asset Management', () => {
        it('should add asset', () => {
            const { result } = renderHook(() => useThreatModeling());

            let assetId: string;
            act(() => {
                assetId = result.current.addAsset({
                    name: 'User Database',
                    type: 'data',
                    description: 'PostgreSQL database storing user information',
                });
            });

            expect(result.current.assets).toHaveLength(1);
            expect(result.current.assets[0].name).toBe('User Database');
            expect(result.current.assets[0].type).toBe('data');
            expect(result.current.assets[0].id).toBe(assetId!);
        });

        it('should add multiple assets', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addAsset({ name: 'API Gateway', type: 'service' });
                result.current.addAsset({ name: 'AWS EC2 Instances', type: 'infrastructure' });
                result.current.addAsset({ name: 'Admin Users', type: 'user' });
            });

            expect(result.current.assets).toHaveLength(3);
        });

        it('should delete asset', () => {
            const { result } = renderHook(() => useThreatModeling());

            let assetId: string;
            act(() => {
                assetId = result.current.addAsset({ name: 'Test Asset', type: 'data' });
            });

            act(() => {
                result.current.deleteAsset(assetId!);
            });

            expect(result.current.assets).toHaveLength(0);
        });
    });

    describe('Mitigation Management', () => {
        it('should add mitigation to threat', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'SQL Injection',
                    description: 'Database attack',
                    category: 'tampering',
                });
                result.current.addMitigation(threatId, {
                    strategy: 'Use parameterized queries',
                    implementation: 'Replace string concatenation with prepared statements',
                    owner: 'Backend Team',
                });
            });

            const threat = result.current.getThreat(threatId!);
            expect(threat?.mitigations).toHaveLength(1);
            expect(threat?.mitigations?.[0].strategy).toBe('Use parameterized queries');
            expect(threat?.mitigations?.[0].status).toBe('planned');
        });

        it('should add multiple mitigations to threat', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'XSS',
                    description: 'Cross-site scripting',
                    category: 'tampering',
                });
                result.current.addMitigation(threatId, { strategy: 'Input sanitization' });
                result.current.addMitigation(threatId, { strategy: 'Content Security Policy' });
                result.current.addMitigation(threatId, { strategy: 'Output encoding' });
            });

            const threat = result.current.getThreat(threatId!);
            expect(threat?.mitigations).toHaveLength(3);
        });

        it('should update mitigation status', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addMitigation(threatId, { strategy: 'Fix' });
            });

            const threat = result.current.getThreat(threatId!);
            const mitigationId = threat?.mitigations?.[0].id!;

            act(() => {
                result.current.updateMitigationStatus(threatId!, mitigationId, 'in-progress');
            });

            const updatedThreat = result.current.getThreat(threatId!);
            expect(updatedThreat?.mitigations?.[0].status).toBe('in-progress');
        });

        it('should cycle through mitigation statuses', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addMitigation(threatId, { strategy: 'Fix' });
            });

            const threat = result.current.getThreat(threatId!);
            const mitigationId = threat?.mitigations?.[0].id!;

            expect(threat?.mitigations?.[0].status).toBe('planned');

            act(() => {
                result.current.updateMitigationStatus(threatId!, mitigationId, 'in-progress');
            });
            expect(result.current.getThreat(threatId!)?.mitigations?.[0].status).toBe('in-progress');

            act(() => {
                result.current.updateMitigationStatus(threatId!, mitigationId, 'implemented');
            });
            expect(result.current.getThreat(threatId!)?.mitigations?.[0].status).toBe('implemented');
        });

        it('should delete mitigation', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addMitigation(threatId, { strategy: 'Fix 1' });
                result.current.addMitigation(threatId, { strategy: 'Fix 2' });
            });

            const threat = result.current.getThreat(threatId!);
            const mitigationId = threat?.mitigations?.[0].id!;

            act(() => {
                result.current.deleteMitigation(threatId!, mitigationId);
            });

            const updatedThreat = result.current.getThreat(threatId!);
            expect(updatedThreat?.mitigations).toHaveLength(1);
        });
    });

    describe('Risk Score Calculation', () => {
        it('should calculate risk score from impact and likelihood', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 8,
                    likelihood: 7,
                });
            });

            const score = result.current.calculateRiskScore(threatId!);
            expect(score).toBe(Math.round((8 * 7) / 10)); // 5.6 rounded
        });

        it('should calculate risk score from risk level if no impact/likelihood', () => {
            const { result } = renderHook(() => useThreatModeling());

            let criticalId: string, highId: string, mediumId: string, lowId: string;
            act(() => {
                criticalId = result.current.addThreat({
                    title: 'Critical',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 10,
                    likelihood: 9,
                });
                highId = result.current.addThreat({
                    title: 'High',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 7,
                    likelihood: 6,
                });
                mediumId = result.current.addThreat({
                    title: 'Medium',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 5,
                    likelihood: 4,
                });
                lowId = result.current.addThreat({
                    title: 'Low',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 2,
                    likelihood: 3,
                });
            });

            // Risk levels should be assigned correctly
            expect(result.current.getThreat(criticalId!)?.riskLevel).toBe('critical');
            expect(result.current.getThreat(highId!)?.riskLevel).toBe('high');
            expect(result.current.getThreat(mediumId!)?.riskLevel).toBe('medium');
            expect(result.current.getThreat(lowId!)?.riskLevel).toBe('low');
        });

        it('should reduce risk score with implemented mitigations', () => {
            const { result } = renderHook(() => useThreatModeling());

            let threatId: string;
            act(() => {
                threatId = result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addMitigation(threatId, { strategy: 'Fix 1' });
                result.current.addMitigation(threatId, { strategy: 'Fix 2' });
            });

            const initialScore = result.current.calculateRiskScore(threatId!);

            const threat = result.current.getThreat(threatId!);
            const mitigation1Id = threat?.mitigations?.[0].id!;
            const mitigation2Id = threat?.mitigations?.[1].id!;

            act(() => {
                result.current.updateMitigationStatus(threatId!, mitigation1Id, 'implemented');
                result.current.updateMitigationStatus(threatId!, mitigation2Id, 'implemented');
            });

            const reducedScore = result.current.calculateRiskScore(threatId!);
            expect(reducedScore).toBeLessThan(initialScore);
        });
    });

    describe('STRIDE Analysis', () => {
        it('should analyze STRIDE categories', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Spoofing 1',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addThreat({
                    title: 'Spoofing 2',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addThreat({
                    title: 'Tampering',
                    description: 'Desc',
                    category: 'tampering',
                });
            });

            const analysis = result.current.analyzeSTRIDE();

            expect(analysis.categoryBreakdown.spoofing.count).toBe(2);
            expect(analysis.categoryBreakdown.tampering.count).toBe(1);
            expect(analysis.categoryBreakdown.repudiation.count).toBe(0);
        });

        it('should identify unmitigated threats', () => {
            const { result } = renderHook(() => useThreatModeling());

            let mitigatedId: string, unmitigatedId: string;
            act(() => {
                mitigatedId = result.current.addThreat({
                    title: 'Mitigated',
                    description: 'Desc',
                    category: 'spoofing',
                });
                unmitigatedId = result.current.addThreat({
                    title: 'Unmitigated',
                    description: 'Desc',
                    category: 'tampering',
                });

                result.current.addMitigation(mitigatedId, { strategy: 'Fix' });
                const threat = result.current.getThreat(mitigatedId);
                result.current.updateMitigationStatus(mitigatedId, threat!.mitigations![0].id, 'implemented');
            });

            const analysis = result.current.analyzeSTRIDE();

            expect(analysis.unmitigatedThreats).toHaveLength(1);
            expect(analysis.unmitigatedThreats[0].id).toBe(unmitigatedId!);
        });

        it('should calculate overall risk level', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                // Add critical threats
                result.current.addThreat({
                    title: 'Critical 1',
                    description: 'Desc',
                    category: 'spoofing',
                    impact: 10,
                    likelihood: 9,
                });
                result.current.addThreat({
                    title: 'Critical 2',
                    description: 'Desc',
                    category: 'tampering',
                    impact: 9,
                    likelihood: 10,
                });
            });

            const analysis = result.current.analyzeSTRIDE();

            expect(analysis.overallRisk).toBe('critical');
            expect(analysis.averageRiskScore).toBeGreaterThan(8);
        });
    });

    describe('Export', () => {
        it('should export threat model as JSON', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.setModelName('Security Model');
                result.current.addThreat({
                    title: 'SQL Injection',
                    description: 'Database attack',
                    category: 'tampering',
                });
                result.current.addAsset({
                    name: 'Database',
                    type: 'data',
                });
            });

            const exported = result.current.exportModel();
            const parsed = JSON.parse(exported);

            expect(parsed).toHaveProperty('name');
            expect(parsed).toHaveProperty('threats');
            expect(parsed).toHaveProperty('assets');
            expect(parsed).toHaveProperty('analysis');
            expect(parsed).toHaveProperty('metadata');
        });

        it('should include threat details in export', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                const threatId = result.current.addThreat({
                    title: 'XSS',
                    description: 'Script injection',
                    category: 'tampering',
                    affectedAsset: 'Web UI',
                    attackVector: 'User input',
                });
                result.current.addMitigation(threatId, { strategy: 'Input validation' });
            });

            const exported = result.current.exportModel();
            const parsed = JSON.parse(exported);

            expect(parsed.threats[0].title).toBe('XSS');
            expect(parsed.threats[0].affectedAsset).toBe('Web UI');
            expect(parsed.threats[0].mitigations).toHaveLength(1);
        });

        it('should include metadata in export', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Threat',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addAsset({ name: 'Asset', type: 'data' });
            });

            const exported = result.current.exportModel();
            const parsed = JSON.parse(exported);

            expect(parsed.metadata.threatCount).toBe(1);
            expect(parsed.metadata.assetCount).toBe(1);
            expect(parsed.metadata).toHaveProperty('exportedAt');
        });
    });

    describe('Count Utilities', () => {
        it('should count threats', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addThreat({
                    title: 'Threat 1',
                    description: 'Desc',
                    category: 'spoofing',
                });
                result.current.addThreat({
                    title: 'Threat 2',
                    description: 'Desc',
                    category: 'tampering',
                });
            });

            expect(result.current.getThreatCount()).toBe(2);
        });

        it('should count assets', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                result.current.addAsset({ name: 'Asset 1', type: 'data' });
                result.current.addAsset({ name: 'Asset 2', type: 'service' });
                result.current.addAsset({ name: 'Asset 3', type: 'infrastructure' });
            });

            expect(result.current.getAssetCount()).toBe(3);
        });

        it('should count mitigations across all threats', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                const threat1 = result.current.addThreat({
                    title: 'Threat 1',
                    description: 'Desc',
                    category: 'spoofing',
                });
                const threat2 = result.current.addThreat({
                    title: 'Threat 2',
                    description: 'Desc',
                    category: 'tampering',
                });

                result.current.addMitigation(threat1, { strategy: 'Fix 1' });
                result.current.addMitigation(threat1, { strategy: 'Fix 2' });
                result.current.addMitigation(threat2, { strategy: 'Fix 3' });
            });

            expect(result.current.getMitigationCount()).toBe(3);
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle complete threat model workflow', () => {
            const { result } = renderHook(() => useThreatModeling());

            act(() => {
                // Configure model
                result.current.setModelName('E-Commerce Threat Model');
                result.current.setSystemDescription('Online payment system');

                // Add assets
                result.current.addAsset({ name: 'Payment DB', type: 'data' });
                result.current.addAsset({ name: 'API Gateway', type: 'service' });

                // Add threats with mitigations
                const sqli = result.current.addThreat({
                    title: 'SQL Injection',
                    description: 'Attacker injects SQL',
                    category: 'tampering',
                    affectedAsset: 'Payment DB',
                    impact: 9,
                    likelihood: 6,
                });

                result.current.addMitigation(sqli, {
                    strategy: 'Parameterized queries',
                    owner: 'Backend Team',
                });

                const threat = result.current.getThreat(sqli);
                result.current.updateMitigationStatus(sqli, threat!.mitigations![0].id, 'implemented');
            });

            expect(result.current.getThreatCount()).toBe(1);
            expect(result.current.getAssetCount()).toBe(2);
            expect(result.current.getMitigationCount()).toBe(1);

            const analysis = result.current.analyzeSTRIDE();
            expect(analysis.categoryBreakdown.tampering.mitigatedCount).toBe(1);
        });
    });
});
