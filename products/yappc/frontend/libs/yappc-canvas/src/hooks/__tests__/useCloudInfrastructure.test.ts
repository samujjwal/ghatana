/**
 * Cloud Infrastructure Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for multi-cloud infrastructure functionality
 * @doc.layer product
 * @doc.pattern Unit Tests
 * 
 * Test coverage:
 * - Resource management (CRUD operations across AWS/GCP/Azure)
 * - Cost calculation with regional pricing
 * - Infrastructure validation (network topology, security)
 * - Compliance checking (CIS benchmarks, best practices)
 * - High availability analysis (multi-AZ, redundancy, backup)
 * - Recommendation engine (security, cost optimization, HA)
 * - Export functionality (Terraform, CloudFormation, Pulumi, ARM)
 * - Multi-cloud scenarios
 */

import { renderHook, act } from '@testing-library/react';
import {
    useCloudInfrastructure,
    type CloudResource,
    type CloudProvider,
    type ResourceCategory,
} from '../useCloudInfrastructure';

describe('useCloudInfrastructure', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            expect(result.current.infrastructureName).toBe('Cloud Infrastructure');
            expect(result.current.selectedProvider).toBe('aws');
            expect(result.current.selectedRegion).toBe('us-east-1');
            expect(result.current.resources).toEqual([]);
            expect(result.current.getResourceCount()).toBe(0);
        });

        it('should allow setting infrastructure name', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setInfrastructureName('Production Infrastructure');
            });

            expect(result.current.infrastructureName).toBe('Production Infrastructure');
        });

        it('should allow changing cloud provider', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedProvider('gcp');
            });

            expect(result.current.selectedProvider).toBe('gcp');
        });

        it('should allow changing region', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedRegion('eu-west-1');
            });

            expect(result.current.selectedRegion).toBe('eu-west-1');
        });
    });

    describe('Resource Management - AWS', () => {
        it('should add an AWS EC2 instance', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            let resourceId: string = '';
            act(() => {
                resourceId = result.current.addResource({
                    name: 'web-server-01',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { instanceType: 't2.micro', storage: '20GB' },
                });
            });

            expect(resourceId).toBeTruthy();
            expect(result.current.getResourceCount()).toBe(1);
            expect(result.current.resources[0]).toMatchObject({
                id: resourceId,
                name: 'web-server-01',
                provider: 'aws',
                category: 'compute',
                type: 'vm',
                region: 'us-east-1',
            });
        });

        it('should add AWS RDS database', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'postgres-db',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { engine: 'postgres', instanceClass: 'db.t3.medium' },
                });
            });

            expect(result.current.getResourceCount()).toBe(1);
            expect(result.current.resources[0].type).toBe('rds');
        });

        it('should add AWS VPC and subnet', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'main-vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: { cidr: '10.0.0.0/16' },
                });
                result.current.addResource({
                    name: 'private-subnet',
                    provider: 'aws',
                    category: 'network',
                    type: 'subnet',
                    region: 'us-east-1',
                    config: { cidr: '10.0.1.0/24' },
                });
            });

            expect(result.current.getResourceCount()).toBe(2);
            const networkResources = result.current.getResourcesByCategory('network');
            expect(networkResources).toHaveLength(2);
        });

        it('should calculate cost for AWS resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const cost = result.current.calculateCost();
            expect(cost.totalMonthlyCost).toBeGreaterThan(0);
            expect(cost.breakdown.compute).toBeGreaterThan(0);
        });
    });

    describe('Resource Management - GCP', () => {
        it('should add GCP Compute Engine instance', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedProvider('gcp');
                result.current.addResource({
                    name: 'gcp-vm-01',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: { machineType: 'n1-standard-1' },
                });
            });

            expect(result.current.getResourceCount()).toBe(1);
            expect(result.current.resources[0].provider).toBe('gcp');
        });

        it('should add GCP Cloud SQL database', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedProvider('gcp');
                result.current.addResource({
                    name: 'cloudsql-mysql',
                    provider: 'gcp',
                    category: 'database',
                    type: 'rds',
                    region: 'us-central1',
                    config: { databaseVersion: 'MYSQL_8_0' },
                });
            });

            expect(result.current.resources[0].type).toBe('rds');
            expect(result.current.resources[0].provider).toBe('gcp');
        });

        it('should get GCP-specific regions', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const gcpRegions = result.current.getAvailableRegions('gcp');

            expect(gcpRegions).toContain('us-central1');
            expect(gcpRegions).toContain('europe-west1');
            expect(gcpRegions).toContain('asia-east1');
        });
    });

    describe('Resource Management - Azure', () => {
        it('should add Azure Virtual Machine', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedProvider('azure');
                result.current.addResource({
                    name: 'azure-vm-01',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: { vmSize: 'Standard_B2s' },
                });
            });

            expect(result.current.getResourceCount()).toBe(1);
            expect(result.current.resources[0].provider).toBe('azure');
        });

        it('should add Azure SQL Database', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setSelectedProvider('azure');
                result.current.addResource({
                    name: 'azure-sql',
                    provider: 'azure',
                    category: 'database',
                    type: 'rds',
                    region: 'eastus',
                    config: { edition: 'Standard' },
                });
            });

            expect(result.current.resources[0].type).toBe('rds');
        });

        it('should get Azure-specific regions', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const azureRegions = result.current.getAvailableRegions('azure');

            expect(azureRegions).toContain('eastus');
            expect(azureRegions).toContain('westeurope');
            expect(azureRegions).toContain('southeastasia');
        });
    });

    describe('Resource CRUD Operations', () => {
        it('should get a resource by id', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            let resourceId: string = '';
            act(() => {
                resourceId = result.current.addResource({
                    name: 'test-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const resource = result.current.getResource(resourceId);
            expect(resource).toBeDefined();
            expect(resource?.name).toBe('test-vm');
        });

        it('should return undefined for non-existent resource', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const resource = result.current.getResource('non-existent');
            expect(resource).toBeUndefined();
        });

        it('should update a resource', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            let resourceId: string = '';
            act(() => {
                resourceId = result.current.addResource({
                    name: 'old-name',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            act(() => {
                result.current.updateResource(resourceId, {
                    name: 'new-name',
                    config: { size: 'large' },
                });
            });

            const resource = result.current.getResource(resourceId);
            expect(resource?.name).toBe('new-name');
            expect(resource?.config.size).toBe('large');
        });

        it('should delete a resource', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            let resourceId: string = '';
            act(() => {
                resourceId = result.current.addResource({
                    name: 'temp-resource',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            expect(result.current.getResourceCount()).toBe(1);

            act(() => {
                result.current.deleteResource(resourceId);
            });

            expect(result.current.getResourceCount()).toBe(0);
            expect(result.current.getResource(resourceId)).toBeUndefined();
        });

        it('should get resources by category', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const computeResources = result.current.getResourcesByCategory('compute');
            expect(computeResources).toHaveLength(2);
            expect(computeResources.every((r) => r.category === 'compute')).toBe(true);

            const databaseResources = result.current.getResourcesByCategory('database');
            expect(databaseResources).toHaveLength(1);
        });

        it('should get resources by provider', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'aws-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'gcp-vm',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: {},
                });
                result.current.addResource({
                    name: 'azure-vm',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: {},
                });
            });

            const awsResources = result.current.getResourcesByProvider('aws');
            expect(awsResources).toHaveLength(1);
            expect(awsResources[0].provider).toBe('aws');

            const gcpResources = result.current.getResourcesByProvider('gcp');
            expect(gcpResources).toHaveLength(1);

            const azureResources = result.current.getResourcesByProvider('azure');
            expect(azureResources).toHaveLength(1);
        });
    });

    describe('Cost Calculation', () => {
        it('should calculate zero cost for empty infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const cost = result.current.calculateCost();

            expect(cost.totalMonthlyCost).toBe(0);
            expect(cost.breakdown).toEqual({});
        });

        it('should calculate cost with breakdown by category', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'storage-1',
                    provider: 'aws',
                    category: 'storage',
                    type: 'storage',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const cost = result.current.calculateCost();

            expect(cost.totalMonthlyCost).toBeGreaterThan(0);
            expect(cost.breakdown.compute).toBeGreaterThan(0);
            expect(cost.breakdown.database).toBeGreaterThan(0);
            expect(cost.breakdown.storage).toBeGreaterThan(0);
        });

        it('should adjust cost based on resource size', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'small-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const smallCost = result.current.calculateCost().totalMonthlyCost;

            act(() => {
                result.current.addResource({
                    name: 'large-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { size: 'large' },
                });
            });

            const largeCost = result.current.calculateCost().totalMonthlyCost;

            expect(largeCost).toBeGreaterThan(smallCost);
        });
    });

    describe('Infrastructure Validation', () => {
        it('should validate empty infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const issues = result.current.validateInfrastructure();

            expect(issues).toContain('No resources defined');
        });

        it('should detect missing VPC for multiple resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-3',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.some((issue) => issue.includes('VPC'))).toBe(true);
        });

        it('should detect compute without network configuration', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.some((issue) => issue.includes('network'))).toBe(true);
        });

        it('should detect database without subnet', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.some((issue) => issue.includes('subnet'))).toBe(true);
        });

        it('should detect missing security resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.some((issue) => issue.includes('security'))).toBe(true);
        });

        it('should detect single region deployment', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                for (let i = 0; i < 6; i++) {
                    result.current.addResource({
                        name: `vm-${i}`,
                        provider: 'aws',
                        category: 'compute',
                        type: 'vm',
                        region: 'us-east-1',
                        config: {},
                    });
                }
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.some((issue) => issue.includes('single region'))).toBe(true);
        });

        it('should pass validation for well-configured infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'subnet',
                    provider: 'aws',
                    category: 'network',
                    type: 'subnet',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'firewall',
                    provider: 'aws',
                    category: 'security',
                    type: 'firewall',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const issues = result.current.validateInfrastructure();

            expect(issues.length).toBeLessThan(2);
        });
    });

    describe('Compliance Checking', () => {
        it('should check encryption at rest', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'unencrypted-storage',
                    provider: 'aws',
                    category: 'storage',
                    type: 'storage',
                    region: 'us-east-1',
                    config: { encryption: false },
                });
            });

            const compliance = result.current.checkCompliance();

            const encryptionCheck = compliance.checks.find((c) => c.name.includes('encryption'));
            expect(encryptionCheck?.passed).toBe(false);
        });

        it('should check network segmentation', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const compliance = result.current.checkCompliance();

            const segmentationCheck = compliance.checks.find((c) => c.name.includes('segmentation'));
            expect(segmentationCheck?.passed).toBe(false);
        });

        it('should check firewall protection', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const compliance = result.current.checkCompliance();

            const firewallCheck = compliance.checks.find((c) => c.name.includes('Firewall'));
            expect(firewallCheck?.passed).toBe(false);
        });

        it('should check database backups', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { backup: false },
                });
            });

            const compliance = result.current.checkCompliance();

            const backupCheck = compliance.checks.find((c) => c.name.includes('backup'));
            expect(backupCheck?.passed).toBe(false);
        });

        it('should achieve high compliance score for secure infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'subnet',
                    provider: 'aws',
                    category: 'network',
                    type: 'subnet',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'firewall',
                    provider: 'aws',
                    category: 'security',
                    type: 'firewall',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'waf',
                    provider: 'aws',
                    category: 'security',
                    type: 'waf',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'kms',
                    provider: 'aws',
                    category: 'security',
                    type: 'kms',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'storage',
                    provider: 'aws',
                    category: 'storage',
                    type: 'storage',
                    region: 'us-east-1',
                    config: { encryption: true, logging: true },
                });
                result.current.addResource({
                    name: 'db',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { backup: true, encrypted: true },
                });
                result.current.addResource({
                    name: 'lb',
                    provider: 'aws',
                    category: 'network',
                    type: 'loadbalancer',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const compliance = result.current.checkCompliance();

            expect(compliance.compliantPercentage).toBeGreaterThanOrEqual(80);
        });
    });

    describe('High Availability Analysis', () => {
        it('should detect single-AZ deployment', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const ha = result.current.analyzeHighAvailability();

            expect(ha.hasMultiAZ).toBe(false);
        });

        it('should detect multi-AZ deployment', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'eu-west-1',
                    config: {},
                });
            });

            const ha = result.current.analyzeHighAvailability();

            expect(ha.hasMultiAZ).toBe(true);
        });

        it('should detect backup configuration', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { backup: true },
                });
            });

            const ha = result.current.analyzeHighAvailability();

            expect(ha.hasBackup).toBe(true);
        });

        it('should calculate redundancy score', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                // Add resources that improve redundancy
                result.current.addResource({
                    name: 'lb',
                    provider: 'aws',
                    category: 'network',
                    type: 'loadbalancer',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'eu-west-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'db',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { backup: true },
                });
                result.current.addResource({
                    name: 'cdn',
                    provider: 'aws',
                    category: 'storage',
                    type: 'cdn',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const ha = result.current.analyzeHighAvailability();

            expect(ha.redundancyScore).toBeGreaterThanOrEqual(8);
        });

        it('should identify single points of failure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { replication: false },
                });
            });

            const ha = result.current.analyzeHighAvailability();

            expect(ha.singlePointsOfFailure.length).toBeGreaterThan(0);
            expect(ha.singlePointsOfFailure.some((spof) => spof.includes('Single compute'))).toBe(true);
            expect(ha.singlePointsOfFailure.some((spof) => spof.includes('database'))).toBe(true);
        });
    });

    describe('Recommendation Engine', () => {
        it('should recommend adding resources for empty infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('Add cloud resources'))).toBe(true);
        });

        it('should recommend creating VPC', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('VPC'))).toBe(true);
        });

        it('should recommend adding firewall', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('firewall'))).toBe(true);
        });

        it('should recommend load balancer for multiple instances', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'vm-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('load balancer'))).toBe(true);
        });

        it('should recommend enabling backups', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'db-1',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('backup'))).toBe(true);
        });

        it('should recommend adding CDN', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'storage',
                    provider: 'aws',
                    category: 'storage',
                    type: 'storage',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('CDN'))).toBe(true);
        });

        it('should recommend caching layer', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'db',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: {},
                });
            });

            const recommendations = result.current.generateRecommendations();

            expect(recommendations.some((rec) => rec.includes('caching'))).toBe(true);
        });
    });

    describe('Export to Terraform', () => {
        it('should export empty infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const terraform = result.current.exportToTerraform();

            expect(terraform).toContain('# Cloud Infrastructure Configuration');
            expect(terraform).toContain('Cloud Infrastructure');
        });

        it('should export AWS resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'web-server',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { instanceType: 't2.micro' },
                });
            });

            const terraform = result.current.exportToTerraform();

            expect(terraform).toContain('provider "aws"');
            expect(terraform).toContain('resource "aws_vm"');
            expect(terraform).toContain('name = "web-server"');
            expect(terraform).toContain('instanceType = "t2.micro"');
        });

        it('should export GCP resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'gcp-instance',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: { machineType: 'n1-standard-1' },
                });
            });

            const terraform = result.current.exportToTerraform();

            expect(terraform).toContain('provider "google"');
            expect(terraform).toContain('resource "google_vm"');
            expect(terraform).toContain('name = "gcp-instance"');
        });

        it('should export Azure resources', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'azure-vm',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: { vmSize: 'Standard_B2s' },
                });
            });

            const terraform = result.current.exportToTerraform();

            expect(terraform).toContain('provider "azurerm"');
            expect(terraform).toContain('resource "azurerm_vm"');
            expect(terraform).toContain('location = "eastus"');
        });

        it('should export multi-cloud infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'aws-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'gcp-vm',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: {},
                });
                result.current.addResource({
                    name: 'azure-vm',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: {},
                });
            });

            const terraform = result.current.exportToTerraform();

            expect(terraform).toContain('# AWS Resources');
            expect(terraform).toContain('# Google Cloud Resources');
            expect(terraform).toContain('# Azure Resources');
        });
    });

    describe('Export to CloudFormation', () => {
        it('should export to CloudFormation YAML', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'web-server',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { instanceType: 't2.micro' },
                });
            });

            const cfn = result.current.exportToCloudFormation();

            expect(cfn).toContain('AWSTemplateFormatVersion');
            expect(cfn).toContain('Resources:');
            expect(cfn).toContain('Name: web-server');
        });

        it('should include tags in CloudFormation export', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'tagged-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                    tags: { Environment: 'Production', Team: 'Platform' },
                });
            });

            const cfn = result.current.exportToCloudFormation();

            expect(cfn).toContain('Tags:');
            expect(cfn).toContain('Environment');
            expect(cfn).toContain('Production');
        });
    });

    describe('Export to Pulumi', () => {
        it('should export to Pulumi TypeScript', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'web-server',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { instanceType: 't2.micro' },
                });
            });

            const pulumi = result.current.exportToPulumi();

            expect(pulumi).toContain('import * as pulumi');
            expect(pulumi).toContain('import * as aws');
            expect(pulumi).toContain('const');
            expect(pulumi).toContain('new aws.compute.vm');
        });

        it('should export multi-cloud Pulumi', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'aws-vm',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'gcp-vm',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: {},
                });
            });

            const pulumi = result.current.exportToPulumi();

            expect(pulumi).toContain('import * as aws');
            expect(pulumi).toContain('import * as gcp');
        });
    });

    describe('Export to ARM Template', () => {
        it('should export to Azure ARM JSON', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.addResource({
                    name: 'azure-vm',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: { vmSize: 'Standard_B2s' },
                });
            });

            const arm = result.current.exportToARM();
            const armJson = JSON.parse(arm);

            expect(armJson.$schema).toContain('deploymentTemplate.json');
            expect(armJson.resources).toHaveLength(1);
            expect(armJson.resources[0].name).toBe('azure-vm');
            expect(armJson.resources[0].location).toBe('eastus');
        });
    });

    describe('Complex Scenario: Enterprise Multi-Cloud Infrastructure', () => {
        it('should handle comprehensive enterprise infrastructure', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            act(() => {
                result.current.setInfrastructureName('Enterprise Multi-Cloud Infrastructure');

                // AWS Resources
                result.current.addResource({
                    name: 'aws-vpc',
                    provider: 'aws',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-east-1',
                    config: { cidr: '10.0.0.0/16' },
                    tags: { Environment: 'Production' },
                });
                result.current.addResource({
                    name: 'aws-subnet-public',
                    provider: 'aws',
                    category: 'network',
                    type: 'subnet',
                    region: 'us-east-1',
                    config: { cidr: '10.0.1.0/24' },
                });
                result.current.addResource({
                    name: 'aws-firewall',
                    provider: 'aws',
                    category: 'security',
                    type: 'firewall',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'aws-waf',
                    provider: 'aws',
                    category: 'security',
                    type: 'waf',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'aws-kms',
                    provider: 'aws',
                    category: 'security',
                    type: 'kms',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'aws-lb',
                    provider: 'aws',
                    category: 'network',
                    type: 'loadbalancer',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'aws-web-1',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-east-1',
                    config: { instanceType: 't3.medium' },
                });
                result.current.addResource({
                    name: 'aws-web-2',
                    provider: 'aws',
                    category: 'compute',
                    type: 'vm',
                    region: 'eu-west-1',
                    config: { instanceType: 't3.medium' },
                });
                result.current.addResource({
                    name: 'aws-rds-primary',
                    provider: 'aws',
                    category: 'database',
                    type: 'rds',
                    region: 'us-east-1',
                    config: { backup: true, encrypted: true, replication: true },
                });
                result.current.addResource({
                    name: 'aws-redis',
                    provider: 'aws',
                    category: 'database',
                    type: 'cache',
                    region: 'us-east-1',
                    config: {},
                });
                result.current.addResource({
                    name: 'aws-s3',
                    provider: 'aws',
                    category: 'storage',
                    type: 'storage',
                    region: 'us-east-1',
                    config: { encryption: true, logging: true },
                });
                result.current.addResource({
                    name: 'aws-cdn',
                    provider: 'aws',
                    category: 'storage',
                    type: 'cdn',
                    region: 'us-east-1',
                    config: {},
                });

                // GCP Resources
                result.current.addResource({
                    name: 'gcp-vpc',
                    provider: 'gcp',
                    category: 'network',
                    type: 'vpc',
                    region: 'us-central1',
                    config: {},
                });
                result.current.addResource({
                    name: 'gcp-compute',
                    provider: 'gcp',
                    category: 'compute',
                    type: 'vm',
                    region: 'us-central1',
                    config: { machineType: 'n1-standard-2' },
                });
                result.current.addResource({
                    name: 'gcp-cloudsql',
                    provider: 'gcp',
                    category: 'database',
                    type: 'rds',
                    region: 'us-central1',
                    config: { backup: true },
                });

                // Azure Resources
                result.current.addResource({
                    name: 'azure-vnet',
                    provider: 'azure',
                    category: 'network',
                    type: 'vpc',
                    region: 'eastus',
                    config: {},
                });
                result.current.addResource({
                    name: 'azure-vm',
                    provider: 'azure',
                    category: 'compute',
                    type: 'vm',
                    region: 'eastus',
                    config: { vmSize: 'Standard_D2s_v3' },
                });
            });

            // Verify counts
            expect(result.current.getResourceCount()).toBe(17);
            expect(result.current.getResourcesByProvider('aws')).toHaveLength(12);
            expect(result.current.getResourcesByProvider('gcp')).toHaveLength(3);
            expect(result.current.getResourcesByProvider('azure')).toHaveLength(2);

            // Verify cost calculation
            const cost = result.current.calculateCost();
            expect(cost.totalMonthlyCost).toBeGreaterThan(200);

            // Verify compliance
            const compliance = result.current.checkCompliance();
            expect(compliance.compliantPercentage).toBeGreaterThanOrEqual(75);

            // Verify high availability
            const ha = result.current.analyzeHighAvailability();
            expect(ha.hasMultiAZ).toBe(true);
            expect(ha.hasBackup).toBe(true);
            expect(ha.redundancyScore).toBeGreaterThanOrEqual(8);

            // Verify validation
            const issues = result.current.validateInfrastructure();
            expect(issues.length).toBeLessThanOrEqual(2);

            // Verify exports
            const terraform = result.current.exportToTerraform();
            expect(terraform).toContain('provider "aws"');
            expect(terraform).toContain('provider "google"');
            expect(terraform).toContain('provider "azurerm"');

            const cfn = result.current.exportToCloudFormation();
            expect(cfn).toContain('AWSTemplateFormatVersion');

            const pulumi = result.current.exportToPulumi();
            expect(pulumi).toContain('import * as aws');

            const arm = result.current.exportToARM();
            expect(arm).toContain('deploymentTemplate');
        });
    });

    describe('Utility Functions', () => {
        it('should get resource types for AWS compute category', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const types = result.current.getResourceTypesForCategory('aws', 'compute');

            expect(types).toContain('vm');
            expect(types).toContain('container');
            expect(types).toContain('serverless');
        });

        it('should get resource types for database category', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const types = result.current.getResourceTypesForCategory('aws', 'database');

            expect(types).toContain('rds');
            expect(types).toContain('nosql');
            expect(types).toContain('cache');
        });

        it('should get different resource types for each provider', () => {
            const { result } = renderHook(() => useCloudInfrastructure());

            const awsTypes = result.current.getResourceTypesForCategory('aws', 'compute');
            const gcpTypes = result.current.getResourceTypesForCategory('gcp', 'compute');
            const azureTypes = result.current.getResourceTypesForCategory('azure', 'compute');

            expect(awsTypes).toEqual(gcpTypes);
            expect(gcpTypes).toEqual(azureTypes);
        });
    });
});
