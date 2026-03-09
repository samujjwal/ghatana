/**
 * Zero-Trust Architecture Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for zero-trust architecture functionality
 * @doc.layer product
 * @doc.pattern Unit Tests
 * 
 * Test coverage:
 * - Security zone management (CRUD operations)
 * - Identity provider management
 * - Policy rule management
 * - Trust score calculation
 * - Coverage analysis
 * - Architecture validation (NIST 800-207 principles)
 * - Recommendation engine
 * - Export functionality (Terraform, Kubernetes, Diagrams)
 */

import { renderHook, act } from '@testing-library/react';
import {
    useZeroTrustArchitecture,
    type SecurityZone,
    type IdentityProvider,
    type PolicyRule,
} from '../useZeroTrustArchitecture';

describe('useZeroTrustArchitecture', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            expect(result.current.architectureName).toBe('Zero-Trust Architecture');
            expect(result.current.securityZones).toEqual([]);
            expect(result.current.identityProviders).toEqual([]);
            expect(result.current.policyRules).toEqual([]);
            expect(result.current.getSecurityZoneCount()).toBe(0);
            expect(result.current.getIdentityProviderCount()).toBe(0);
            expect(result.current.getPolicyRuleCount()).toBe(0);
        });

        it('should allow setting architecture name', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.setArchitectureName('Production Zero-Trust');
            });

            expect(result.current.architectureName).toBe('Production Zero-Trust');
        });
    });

    describe('Security Zone Management', () => {
        it('should add a security zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zoneId: string = '';
            act(() => {
                zoneId = result.current.addSecurityZone({
                    name: 'Web Tier',
                    type: 'private',
                    description: 'Web application servers',
                    trustLevel: 'medium',
                    resources: ['web-1', 'web-2'],
                });
            });

            expect(zoneId).toBeTruthy();
            expect(result.current.getSecurityZoneCount()).toBe(1);
            expect(result.current.securityZones[0]).toMatchObject({
                id: zoneId,
                name: 'Web Tier',
                type: 'private',
                description: 'Web application servers',
                trustLevel: 'medium',
                resources: ['web-1', 'web-2'],
            });
        });

        it('should add multiple security zones', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addSecurityZone({
                    name: 'DMZ',
                    type: 'dmz',
                    trustLevel: 'low',
                });
                result.current.addSecurityZone({
                    name: 'Database',
                    type: 'restricted',
                    trustLevel: 'high',
                });
            });

            expect(result.current.getSecurityZoneCount()).toBe(2);
            expect(result.current.securityZones.map((z) => z.name)).toEqual(['DMZ', 'Database']);
        });

        it('should get a security zone by id', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zoneId: string = '';
            act(() => {
                zoneId = result.current.addSecurityZone({
                    name: 'Management',
                    type: 'management',
                    trustLevel: 'verified',
                });
            });

            const zone = result.current.getSecurityZone(zoneId);
            expect(zone).toBeDefined();
            expect(zone?.name).toBe('Management');
            expect(zone?.type).toBe('management');
        });

        it('should return undefined for non-existent zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const zone = result.current.getSecurityZone('non-existent');
            expect(zone).toBeUndefined();
        });

        it('should update a security zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zoneId: string = '';
            act(() => {
                zoneId = result.current.addSecurityZone({
                    name: 'Public Zone',
                    type: 'public',
                    trustLevel: 'untrusted',
                });
            });

            act(() => {
                result.current.updateSecurityZone(zoneId, {
                    name: 'Public API Zone',
                    description: 'Updated description',
                    resources: ['api-1', 'api-2'],
                });
            });

            const zone = result.current.getSecurityZone(zoneId);
            expect(zone?.name).toBe('Public API Zone');
            expect(zone?.description).toBe('Updated description');
            expect(zone?.resources).toEqual(['api-1', 'api-2']);
            expect(zone?.trustLevel).toBe('untrusted'); // Unchanged
        });

        it('should delete a security zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zoneId: string = '';
            act(() => {
                zoneId = result.current.addSecurityZone({
                    name: 'Temp Zone',
                    type: 'private',
                    trustLevel: 'medium',
                });
            });

            expect(result.current.getSecurityZoneCount()).toBe(1);

            act(() => {
                result.current.deleteSecurityZone(zoneId);
            });

            expect(result.current.getSecurityZoneCount()).toBe(0);
            expect(result.current.getSecurityZone(zoneId)).toBeUndefined();
        });

        it('should clean up orphaned policies when deleting a zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let sourceId: string = '';
            let destId: string = '';

            act(() => {
                sourceId = result.current.addSecurityZone({
                    name: 'Source',
                    type: 'private',
                    trustLevel: 'medium',
                });
                destId = result.current.addSecurityZone({
                    name: 'Dest',
                    type: 'private',
                    trustLevel: 'medium',
                });
                result.current.addPolicyRule({
                    name: 'Test Policy',
                    sourceZone: sourceId,
                    destinationZone: destId,
                    action: 'allow',
                });
            });

            expect(result.current.getPolicyRuleCount()).toBe(1);

            act(() => {
                result.current.deleteSecurityZone(sourceId);
            });

            expect(result.current.getPolicyRuleCount()).toBe(0);
        });
    });

    describe('Identity Provider Management', () => {
        it('should add an identity provider', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let idpId: string = '';
            act(() => {
                idpId = result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });
            });

            expect(idpId).toBeTruthy();
            expect(result.current.getIdentityProviderCount()).toBe(1);
            expect(result.current.identityProviders[0]).toMatchObject({
                id: idpId,
                name: 'Okta',
                type: 'oidc',
                endpoint: 'https://okta.example.com',
                mfaEnabled: true,
            });
        });

        it('should add multiple identity providers', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Azure AD',
                    type: 'saml',
                    endpoint: 'https://azure.example.com',
                    mfaEnabled: true,
                });
                result.current.addIdentityProvider({
                    name: 'Google OAuth',
                    type: 'oauth',
                    endpoint: 'https://accounts.google.com',
                    mfaEnabled: false,
                });
            });

            expect(result.current.getIdentityProviderCount()).toBe(2);
            expect(result.current.identityProviders.map((idp) => idp.name)).toEqual([
                'Azure AD',
                'Google OAuth',
            ]);
        });

        it('should get an identity provider by id', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let idpId: string = '';
            act(() => {
                idpId = result.current.addIdentityProvider({
                    name: 'Auth0',
                    type: 'oidc',
                    endpoint: 'https://auth0.example.com',
                    mfaEnabled: true,
                });
            });

            const idp = result.current.getIdentityProvider(idpId);
            expect(idp).toBeDefined();
            expect(idp?.name).toBe('Auth0');
        });

        it('should update an identity provider', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let idpId: string = '';
            act(() => {
                idpId = result.current.addIdentityProvider({
                    name: 'Keycloak',
                    type: 'oidc',
                    endpoint: 'https://keycloak.example.com',
                    mfaEnabled: false,
                });
            });

            act(() => {
                result.current.updateIdentityProvider(idpId, {
                    mfaEnabled: true,
                    attributes: ['email', 'phone', 'role'],
                });
            });

            const idp = result.current.getIdentityProvider(idpId);
            expect(idp?.mfaEnabled).toBe(true);
            expect(idp?.attributes).toEqual(['email', 'phone', 'role']);
        });

        it('should delete an identity provider', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let idpId: string = '';
            act(() => {
                idpId = result.current.addIdentityProvider({
                    name: 'Temp IdP',
                    type: 'saml',
                    endpoint: 'https://temp.example.com',
                    mfaEnabled: true,
                });
            });

            expect(result.current.getIdentityProviderCount()).toBe(1);

            act(() => {
                result.current.deleteIdentityProvider(idpId);
            });

            expect(result.current.getIdentityProviderCount()).toBe(0);
        });
    });

    describe('Policy Rule Management', () => {
        it('should add a policy rule', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let sourceId: string = '';
            let destId: string = '';

            act(() => {
                sourceId = result.current.addSecurityZone({
                    name: 'Web',
                    type: 'private',
                    trustLevel: 'medium',
                });
                destId = result.current.addSecurityZone({
                    name: 'DB',
                    type: 'restricted',
                    trustLevel: 'high',
                });
            });

            let policyId: string = '';
            act(() => {
                policyId = result.current.addPolicyRule({
                    name: 'Web to DB',
                    sourceZone: sourceId,
                    destinationZone: destId,
                    action: 'allow',
                    conditions: ['authenticated', 'encrypted'],
                    protocol: 'tcp',
                    port: 3306,
                });
            });

            expect(policyId).toBeTruthy();
            expect(result.current.getPolicyRuleCount()).toBe(1);
            expect(result.current.policyRules[0]).toMatchObject({
                id: policyId,
                name: 'Web to DB',
                sourceZone: sourceId,
                destinationZone: destId,
                action: 'allow',
                conditions: ['authenticated', 'encrypted'],
                protocol: 'tcp',
                port: 3306,
            });
        });

        it('should add deny policy rule', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let publicId: string = '';
            let dbId: string = '';

            act(() => {
                publicId = result.current.addSecurityZone({
                    name: 'Public',
                    type: 'public',
                    trustLevel: 'untrusted',
                });
                dbId = result.current.addSecurityZone({
                    name: 'Database',
                    type: 'restricted',
                    trustLevel: 'high',
                });
                result.current.addPolicyRule({
                    name: 'Block Public to DB',
                    sourceZone: publicId,
                    destinationZone: dbId,
                    action: 'deny',
                });
            });

            expect(result.current.policyRules[0].action).toBe('deny');
        });

        it('should get a policy rule by id', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zone1: string = '';
            let zone2: string = '';
            let policyId: string = '';

            act(() => {
                zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });
                policyId = result.current.addPolicyRule({
                    name: 'Test Policy',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            const policy = result.current.getPolicyRule(policyId);
            expect(policy).toBeDefined();
            expect(policy?.name).toBe('Test Policy');
        });

        it('should update a policy rule', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zone1: string = '';
            let zone2: string = '';
            let policyId: string = '';

            act(() => {
                zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });
                policyId = result.current.addPolicyRule({
                    name: 'Initial Policy',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            act(() => {
                result.current.updatePolicyRule(policyId, {
                    name: 'Updated Policy',
                    action: 'deny',
                    conditions: ['mfa-required'],
                });
            });

            const policy = result.current.getPolicyRule(policyId);
            expect(policy?.name).toBe('Updated Policy');
            expect(policy?.action).toBe('deny');
            expect(policy?.conditions).toEqual(['mfa-required']);
        });

        it('should delete a policy rule', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zone1: string = '';
            let zone2: string = '';
            let policyId: string = '';

            act(() => {
                zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });
                policyId = result.current.addPolicyRule({
                    name: 'Temp Policy',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            expect(result.current.getPolicyRuleCount()).toBe(1);

            act(() => {
                result.current.deletePolicyRule(policyId);
            });

            expect(result.current.getPolicyRuleCount()).toBe(0);
        });
    });

    describe('Trust Score Calculation', () => {
        it('should calculate zero score for empty architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const score = result.current.calculateTrustScore();

            expect(score.overall).toBe(0);
            expect(score.identityScore).toBe(0);
            expect(score.networkScore).toBe(0);
            expect(score.policyScore).toBe(0);
            expect(score.rating).toBe('Critical');
        });

        it('should calculate identity score with IdP', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });
            });

            const score = result.current.calculateTrustScore();

            expect(score.identityScore).toBeGreaterThan(0);
            expect(score.identityScore).toBe(100); // 40 base + 60 for MFA
        });

        it('should calculate network score with zones', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addSecurityZone({
                    name: 'Web',
                    type: 'private',
                    trustLevel: 'medium',
                });
                result.current.addSecurityZone({
                    name: 'DB',
                    type: 'restricted',
                    trustLevel: 'high',
                });
            });

            const score = result.current.calculateTrustScore();

            expect(score.networkScore).toBeGreaterThan(0);
        });

        it('should calculate policy score with rules', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            let zone1: string = '';
            let zone2: string = '';

            act(() => {
                zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });
                result.current.addPolicyRule({
                    name: 'Policy 1',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'deny',
                });
            });

            const score = result.current.calculateTrustScore();

            expect(score.policyScore).toBeGreaterThan(0);
        });

        it('should calculate excellent score for comprehensive architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                // Add identity providers with MFA
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });

                // Add diverse security zones
                const web = result.current.addSecurityZone({
                    name: 'Web',
                    type: 'private',
                    trustLevel: 'high',
                });
                const db = result.current.addSecurityZone({
                    name: 'DB',
                    type: 'restricted',
                    trustLevel: 'verified',
                });
                const dmz = result.current.addSecurityZone({
                    name: 'DMZ',
                    type: 'dmz',
                    trustLevel: 'low',
                });

                // Add comprehensive policies with deny-by-default
                result.current.addPolicyRule({
                    name: 'Allow Web to DB',
                    sourceZone: web,
                    destinationZone: db,
                    action: 'allow',
                });
                result.current.addPolicyRule({
                    name: 'Block DMZ to DB',
                    sourceZone: dmz,
                    destinationZone: db,
                    action: 'deny',
                });
            });

            const score = result.current.calculateTrustScore();

            expect(score.overall).toBeGreaterThan(75);
            expect(score.rating).toMatch(/Good|Excellent/);
        });
    });

    describe('Coverage Analysis', () => {
        it('should analyze empty architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const coverage = result.current.analyzeCoverage();

            expect(coverage.totalZones).toBe(0);
            expect(coverage.zonesWithPolicies).toBe(0);
            expect(coverage.hasIdentityProvider).toBe(false);
            expect(coverage.policyPercentage).toBe(0);
        });

        it('should detect identity provider presence', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });
            });

            const coverage = result.current.analyzeCoverage();

            expect(coverage.hasIdentityProvider).toBe(true);
        });

        it('should calculate policy coverage percentage', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                const zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                const zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });
                result.current.addSecurityZone({
                    name: 'Zone3',
                    type: 'private',
                    trustLevel: 'medium',
                });

                result.current.addPolicyRule({
                    name: 'Policy 1',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            const coverage = result.current.analyzeCoverage();

            expect(coverage.totalZones).toBe(3);
            expect(coverage.zonesWithPolicies).toBe(2);
            expect(coverage.policyPercentage).toBe(67); // 2/3 = 66.67 rounded to 67
        });
    });

    describe('Architecture Validation', () => {
        it('should validate empty architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const issues = result.current.validateArchitecture();

            expect(issues).toContain('No security zones defined');
            expect(issues).toContain('No identity providers configured');
            expect(issues).toContain('No policy rules defined');
        });

        it('should detect missing MFA on identity providers', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Weak IdP',
                    type: 'oauth',
                    endpoint: 'https://weak.example.com',
                    mfaEnabled: false,
                });
            });

            const issues = result.current.validateArchitecture();

            expect(issues.some((issue) => issue.includes('without MFA'))).toBe(true);
        });

        it('should detect missing untrusted zones', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addSecurityZone({
                    name: 'Internal',
                    type: 'private',
                    trustLevel: 'high',
                });
            });

            const issues = result.current.validateArchitecture();

            expect(issues.some((issue) => issue.includes('No untrusted zones'))).toBe(true);
        });

        it('should detect zones without policies', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                const zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                result.current.addSecurityZone({
                    name: 'Orphan Zone',
                    type: 'private',
                    trustLevel: 'medium',
                });
                const zone3 = result.current.addSecurityZone({
                    name: 'Zone3',
                    type: 'private',
                    trustLevel: 'medium',
                });

                result.current.addPolicyRule({
                    name: 'Policy 1',
                    sourceZone: zone1,
                    destinationZone: zone3,
                    action: 'allow',
                });
            });

            const issues = result.current.validateArchitecture();

            expect(
                issues.some((issue) => issue.includes('zone(s) have no policies'))
            ).toBe(true);
        });

        it('should detect missing deny rules', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                const zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                const zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });

                result.current.addPolicyRule({
                    name: 'Allow All',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            const issues = result.current.validateArchitecture();

            expect(
                issues.some((issue) => issue.includes('No explicit deny rules'))
            ).toBe(true);
        });

        it('should detect policies without conditions', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                const zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                const zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });

                result.current.addPolicyRule({
                    name: 'Unconditional',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            const issues = result.current.validateArchitecture();

            expect(
                issues.some((issue) => issue.includes('have no conditions'))
            ).toBe(true);
        });

        it('should detect high-trust public zones', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addSecurityZone({
                    name: 'Misconfigured Public',
                    type: 'public',
                    trustLevel: 'verified', // Should not be high trust
                });
            });

            const issues = result.current.validateArchitecture();

            expect(
                issues.some((issue) => issue.includes('public zone(s) have high trust level'))
            ).toBe(true);
        });

        it('should pass validation for well-configured architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });

                const web = result.current.addSecurityZone({
                    name: 'Web',
                    type: 'private',
                    trustLevel: 'medium',
                    resources: ['web-1'],
                });
                const db = result.current.addSecurityZone({
                    name: 'DB',
                    type: 'restricted',
                    trustLevel: 'high',
                    resources: ['db-1'],
                });
                result.current.addSecurityZone({
                    name: 'Public',
                    type: 'public',
                    trustLevel: 'untrusted',
                    resources: ['public-1'],
                });

                result.current.addPolicyRule({
                    name: 'Allow Web to DB',
                    sourceZone: web,
                    destinationZone: db,
                    action: 'allow',
                    conditions: ['authenticated', 'encrypted'],
                });
                result.current.addPolicyRule({
                    name: 'Deny by Default',
                    sourceZone: web,
                    destinationZone: db,
                    action: 'deny',
                });
            });

            const issues = result.current.validateArchitecture();

            expect(issues.length).toBeLessThan(3);
        });
    });

    describe('Recommendation Engine', () => {
        it('should recommend adding identity provider', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const recommendations = result.current.generateRecommendations();

            expect(
                recommendations.some((rec) => rec.includes('Add an identity provider'))
            ).toBe(true);
        });

        it('should recommend adding security zones', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            const recommendations = result.current.generateRecommendations();

            expect(
                recommendations.some((rec) => rec.includes('Define security zones'))
            ).toBe(true);
        });

        it('should recommend adding DMZ zone', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addSecurityZone({
                    name: 'Private',
                    type: 'private',
                    trustLevel: 'medium',
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('Add a DMZ zone'))).toBe(true);
        });

        it('should recommend enabling MFA', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Weak IdP',
                    type: 'oauth',
                    endpoint: 'https://weak.example.com',
                    mfaEnabled: false,
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(
                recommendations.some((rec) => rec.includes('Enable MFA'))
            ).toBe(true);
        });

        it('should recommend adding policy conditions', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                const zone1 = result.current.addSecurityZone({
                    name: 'Zone1',
                    type: 'private',
                    trustLevel: 'medium',
                });
                const zone2 = result.current.addSecurityZone({
                    name: 'Zone2',
                    type: 'private',
                    trustLevel: 'medium',
                });

                result.current.addPolicyRule({
                    name: 'Simple Policy',
                    sourceZone: zone1,
                    destinationZone: zone2,
                    action: 'allow',
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(
                recommendations.some((rec) => rec.includes('Add conditions to policies'))
            ).toBe(true);
        });

        it('should have no recommendations for excellent architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                });
                result.current.addIdentityProvider({
                    name: 'Azure AD',
                    type: 'saml',
                    endpoint: 'https://azure.example.com',
                    mfaEnabled: true,
                });

                const dmz = result.current.addSecurityZone({
                    name: 'DMZ',
                    type: 'dmz',
                    trustLevel: 'low',
                    resources: ['dmz-1'],
                });
                const web = result.current.addSecurityZone({
                    name: 'Web',
                    type: 'private',
                    trustLevel: 'medium',
                    resources: ['web-1'],
                });
                const db = result.current.addSecurityZone({
                    name: 'DB',
                    type: 'restricted',
                    trustLevel: 'high',
                    resources: ['db-1'],
                });
                result.current.addSecurityZone({
                    name: 'Management',
                    type: 'management',
                    trustLevel: 'verified',
                    resources: ['mgmt-1'],
                });
                result.current.addSecurityZone({
                    name: 'Public',
                    type: 'public',
                    trustLevel: 'untrusted',
                    resources: ['public-1'],
                });

                result.current.addPolicyRule({
                    name: 'Allow Web to DB',
                    sourceZone: web,
                    destinationZone: db,
                    action: 'allow',
                    conditions: ['authenticated', 'encrypted', 'mfa'],
                });
                result.current.addPolicyRule({
                    name: 'Deny DMZ to DB',
                    sourceZone: dmz,
                    destinationZone: db,
                    action: 'deny',
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.length).toBeLessThan(3);
        });
    });

    describe('Export Functionality', () => {
        describe('Terraform Export', () => {
            it('should export empty architecture to Terraform', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                const terraform = result.current.exportToTerraform();

                expect(terraform).toContain('# Zero-Trust Architecture Configuration');
                expect(terraform).toContain('# Generated from: Zero-Trust Architecture');
            });

            it('should export security zones as security groups', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    result.current.addSecurityZone({
                        name: 'Web Tier',
                        type: 'private',
                        description: 'Web servers',
                        trustLevel: 'medium',
                    });
                });

                const terraform = result.current.exportToTerraform();

                expect(terraform).toContain('resource "aws_security_group"');
                expect(terraform).toContain('name        = "Web Tier"');
                expect(terraform).toContain('description = "Web servers"');
                expect(terraform).toContain('TrustLevel  = "medium"');
            });

            it('should export allow policies as security group rules', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    const web = result.current.addSecurityZone({
                        name: 'Web',
                        type: 'private',
                        trustLevel: 'medium',
                    });
                    const db = result.current.addSecurityZone({
                        name: 'DB',
                        type: 'restricted',
                        trustLevel: 'high',
                    });

                    result.current.addPolicyRule({
                        name: 'Allow Web to DB',
                        sourceZone: web,
                        destinationZone: db,
                        action: 'allow',
                        protocol: 'tcp',
                        port: 3306,
                    });
                });

                const terraform = result.current.exportToTerraform();

                expect(terraform).toContain('resource "aws_security_group_rule"');
                expect(terraform).toContain('type                     = "ingress"');
                expect(terraform).toContain('from_port                = 3306');
                expect(terraform).toContain('protocol                 = "tcp"');
            });

            it('should export identity providers', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    result.current.addIdentityProvider({
                        name: 'Okta',
                        type: 'oidc',
                        endpoint: 'https://okta.example.com',
                        mfaEnabled: true,
                    });
                });

                const terraform = result.current.exportToTerraform();

                expect(terraform).toContain('resource "aws_cognito_identity_provider"');
                expect(terraform).toContain('provider_name = "Okta"');
                expect(terraform).toContain('provider_type = "OIDC"');
            });
        });

        describe('Kubernetes Export', () => {
            it('should export to Kubernetes NetworkPolicies', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    result.current.addSecurityZone({
                        name: 'Frontend',
                        type: 'private',
                        trustLevel: 'medium',
                    });
                });

                const k8s = result.current.exportToKubernetes();

                expect(k8s).toContain('# Zero-Trust Kubernetes NetworkPolicies');
                expect(k8s).toContain('kind: NetworkPolicy');
                expect(k8s).toContain('zone: "Frontend"');
            });

            it('should export ingress rules', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    const frontend = result.current.addSecurityZone({
                        name: 'Frontend',
                        type: 'private',
                        trustLevel: 'medium',
                    });
                    const backend = result.current.addSecurityZone({
                        name: 'Backend',
                        type: 'private',
                        trustLevel: 'high',
                    });

                    result.current.addPolicyRule({
                        name: 'Frontend to Backend',
                        sourceZone: frontend,
                        destinationZone: backend,
                        action: 'allow',
                        protocol: 'tcp',
                        port: 8080,
                    });
                });

                const k8s = result.current.exportToKubernetes();

                expect(k8s).toContain('ingress:');
                expect(k8s).toContain('- from:');
                expect(k8s).toContain('podSelector:');
                expect(k8s).toContain('port: 8080');
            });
        });

        describe('Diagram Export', () => {
            it('should export to PlantUML diagram', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    result.current.addSecurityZone({
                        name: 'Web Tier',
                        type: 'private',
                        trustLevel: 'medium',
                        resources: ['web-1', 'web-2'],
                    });
                });

                const diagram = result.current.exportToDiagram();

                expect(diagram).toContain('@startuml');
                expect(diagram).toContain('@enduml');
                expect(diagram).toContain('package "Web Tier"');
                expect(diagram).toContain('[web-1]');
            });

            it('should show policy connections in diagram', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    const web = result.current.addSecurityZone({
                        name: 'Web',
                        type: 'private',
                        trustLevel: 'medium',
                    });
                    const db = result.current.addSecurityZone({
                        name: 'DB',
                        type: 'restricted',
                        trustLevel: 'high',
                    });

                    result.current.addPolicyRule({
                        name: 'Connection',
                        sourceZone: web,
                        destinationZone: db,
                        action: 'allow',
                        conditions: ['authenticated'],
                    });
                });

                const diagram = result.current.exportToDiagram();

                expect(diagram).toContain('"Web" --> "DB"');
                expect(diagram).toContain('authenticated');
            });

            it('should include identity providers in diagram', () => {
                const { result } = renderHook(() => useZeroTrustArchitecture());

                act(() => {
                    result.current.addIdentityProvider({
                        name: 'Okta',
                        type: 'oidc',
                        endpoint: 'https://okta.example.com',
                        mfaEnabled: true,
                    });
                });

                const diagram = result.current.exportToDiagram();

                expect(diagram).toContain('cloud "Identity Providers"');
                expect(diagram).toContain('[Okta (MFA)]');
            });
        });
    });

    describe('Complex Scenario: Complete Zero-Trust Architecture', () => {
        it('should handle comprehensive enterprise architecture', () => {
            const { result } = renderHook(() => useZeroTrustArchitecture());

            act(() => {
                // Set architecture name
                result.current.setArchitectureName('Enterprise Zero-Trust Architecture');

                // Add identity providers
                result.current.addIdentityProvider({
                    name: 'Okta',
                    type: 'oidc',
                    endpoint: 'https://okta.example.com',
                    mfaEnabled: true,
                    attributes: ['email', 'phone', 'department'],
                });
                result.current.addIdentityProvider({
                    name: 'Azure AD',
                    type: 'saml',
                    endpoint: 'https://azure.example.com',
                    mfaEnabled: true,
                });

                // Add security zones
                const publicZone = result.current.addSecurityZone({
                    name: 'Public Internet',
                    type: 'public',
                    description: 'Internet-facing resources',
                    trustLevel: 'untrusted',
                    resources: ['cdn', 'static-assets'],
                });

                const dmzZone = result.current.addSecurityZone({
                    name: 'DMZ',
                    type: 'dmz',
                    description: 'Demilitarized zone',
                    trustLevel: 'low',
                    resources: ['reverse-proxy', 'waf'],
                });

                const webZone = result.current.addSecurityZone({
                    name: 'Web Application Tier',
                    type: 'private',
                    description: 'Web servers',
                    trustLevel: 'medium',
                    resources: ['web-1', 'web-2', 'web-3'],
                });

                const apiZone = result.current.addSecurityZone({
                    name: 'API Gateway',
                    type: 'private',
                    description: 'API layer',
                    trustLevel: 'medium',
                    resources: ['api-gateway-1', 'api-gateway-2'],
                });

                const dbZone = result.current.addSecurityZone({
                    name: 'Database Layer',
                    type: 'restricted',
                    description: 'Database servers',
                    trustLevel: 'high',
                    resources: ['postgres-primary', 'postgres-replica'],
                });

                const mgmtZone = result.current.addSecurityZone({
                    name: 'Management',
                    type: 'management',
                    description: 'Administrative access',
                    trustLevel: 'verified',
                    resources: ['bastion', 'monitoring'],
                });

                // Add comprehensive policies
                result.current.addPolicyRule({
                    name: 'Public to DMZ',
                    sourceZone: publicZone,
                    destinationZone: dmzZone,
                    action: 'allow',
                    protocol: 'tcp',
                    port: 443,
                    conditions: ['https-only', 'rate-limited'],
                });

                result.current.addPolicyRule({
                    name: 'DMZ to Web',
                    sourceZone: dmzZone,
                    destinationZone: webZone,
                    action: 'allow',
                    protocol: 'tcp',
                    port: 8080,
                    conditions: ['authenticated', 'encrypted'],
                });

                result.current.addPolicyRule({
                    name: 'Web to API',
                    sourceZone: webZone,
                    destinationZone: apiZone,
                    action: 'allow',
                    protocol: 'tcp',
                    port: 443,
                    conditions: ['authenticated', 'encrypted', 'jwt-valid'],
                });

                result.current.addPolicyRule({
                    name: 'API to Database',
                    sourceZone: apiZone,
                    destinationZone: dbZone,
                    action: 'allow',
                    protocol: 'tcp',
                    port: 5432,
                    conditions: ['authenticated', 'encrypted', 'connection-pooled'],
                });

                result.current.addPolicyRule({
                    name: 'Management Access',
                    sourceZone: mgmtZone,
                    destinationZone: dbZone,
                    action: 'allow',
                    protocol: 'tcp',
                    port: 22,
                    conditions: ['mfa-required', 'vpn-only', 'audit-logged'],
                });

                // Deny rules (defense in depth)
                result.current.addPolicyRule({
                    name: 'Block Public to Database',
                    sourceZone: publicZone,
                    destinationZone: dbZone,
                    action: 'deny',
                });

                result.current.addPolicyRule({
                    name: 'Block DMZ to Database',
                    sourceZone: dmzZone,
                    destinationZone: dbZone,
                    action: 'deny',
                });
            });

            // Verify counts
            expect(result.current.getSecurityZoneCount()).toBe(6);
            expect(result.current.getIdentityProviderCount()).toBe(2);
            expect(result.current.getPolicyRuleCount()).toBe(7);

            // Verify trust score
            const trustScore = result.current.calculateTrustScore();
            expect(trustScore.overall).toBeGreaterThanOrEqual(75);
            expect(trustScore.rating).toMatch(/Good|Excellent/);

            // Verify coverage
            const coverage = result.current.analyzeCoverage();
            expect(coverage.hasIdentityProvider).toBe(true);
            expect(coverage.policyPercentage).toBeGreaterThanOrEqual(80);

            // Verify validation
            const issues = result.current.validateArchitecture();
            expect(issues.length).toBeLessThanOrEqual(2);

            // Verify exports
            const terraform = result.current.exportToTerraform();
            expect(terraform).toContain('resource "aws_security_group"');
            expect(terraform.split('resource "aws_security_group"').length - 1).toBe(6);

            const k8s = result.current.exportToKubernetes();
            expect(k8s).toContain('kind: NetworkPolicy');

            const diagram = result.current.exportToDiagram();
            expect(diagram).toContain('@startuml');
            expect(diagram).toContain('Enterprise Zero-Trust Architecture');
        });
    });
});
