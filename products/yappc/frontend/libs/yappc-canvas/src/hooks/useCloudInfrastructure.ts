/**
 * Cloud Infrastructure Hook
 * 
 * @doc.type hook
 * @doc.purpose State management for multi-cloud infrastructure design (Journey 16.1)
 * @doc.layer product
 * @doc.pattern State Management Hook
 * 
 * Manages cloud resources across AWS, Google Cloud Platform, and Microsoft Azure
 * with cost estimation, compliance validation, high availability analysis, and
 * infrastructure-as-code export.
 * 
 * Features:
 * - Multi-cloud resource management (AWS/GCP/Azure)
 * - Resource categories (compute, network, storage, database, security, analytics)
 * - Real-time cost estimation with regional pricing
 * - Compliance validation (CIS benchmarks, best practices)
 * - High availability and disaster recovery analysis
 * - Topology validation and recommendations
 * - Export to Terraform, CloudFormation, Pulumi, ARM templates
 * 
 * @example
 * ```typescript
 * const {
 *   resources,
 *   addResource,
 *   calculateCost,
 *   validateInfrastructure,
 *   exportToTerraform
 * } = useCloudInfrastructure();
 * 
 * // Add compute resource
 * addResource({
 *   name: 'web-server-01',
 *   provider: 'aws',
 *   category: 'compute',
 *   type: 'ec2',
 *   region: 'us-east-1',
 *   config: { instanceType: 't2.micro', storage: '20GB' }
 * });
 * 
 * // Calculate costs
 * const cost = calculateCost(); // { totalMonthlyCost: 8.50, breakdown: { compute: 8.50 } }
 * 
 * // Export to Terraform
 * const terraform = exportToTerraform();
 * ```
 */

import { useState, useCallback } from 'react';

/**
 * Cloud providers
 */
export type CloudProvider = 'aws' | 'gcp' | 'azure';

/**
 * Resource categories
 */
export type ResourceCategory = 'compute' | 'network' | 'storage' | 'database' | 'security' | 'analytics';

/**
 * Resource types (provider-agnostic naming)
 */
export type ResourceType = 'vm' | 'container' | 'serverless' | 'vpc' | 'subnet' | 'loadbalancer' |
    'storage' | 'cdn' | 'rds' | 'nosql' | 'cache' | 'firewall' | 'waf' | 'kms' |
    'datawarehouse' | 'etl' | 'streaming';

/**
 * Cloud regions
 */
export type Region = 'us-east-1' | 'us-west-2' | 'eu-west-1' | 'ap-southeast-1' |
    'us-central1' | 'europe-west1' | 'asia-east1' |
    'eastus' | 'westeurope' | 'southeastasia';

/**
 * Cloud resource entity
 */
export interface CloudResource {
    id: string;
    name: string;
    provider: CloudProvider;
    category: ResourceCategory;
    type: ResourceType;
    region: Region;
    config: Record<string, unknown>;
    tags?: Record<string, string>;
    monthlyCost?: number;
    dependencies?: string[];
}

/**
 * Cost estimate result
 */
export interface CostEstimate {
    totalMonthlyCost: number;
    breakdown: Record<string, number>;
}

/**
 * Compliance check result
 */
export interface ComplianceCheck {
    name: string;
    passed: boolean;
    severity: 'low' | 'medium' | 'high' | 'critical';
    description?: string;
}

/**
 * Compliance report
 */
export interface ComplianceReport {
    compliantPercentage: number;
    checks: ComplianceCheck[];
}

/**
 * High availability analysis result
 */
export interface HighAvailabilityAnalysis {
    hasMultiAZ: boolean;
    hasBackup: boolean;
    redundancyScore: number;
    singlePointsOfFailure: string[];
}

/**
 * Hook return type
 */
export interface UseCloudInfrastructureReturn {
    // Infrastructure state
    infrastructureName: string;
    setInfrastructureName: (name: string) => void;
    selectedProvider: CloudProvider;
    setSelectedProvider: (provider: CloudProvider) => void;
    selectedRegion: Region;
    setSelectedRegion: (region: Region) => void;
    resources: CloudResource[];

    // Resource operations
    addResource: (resource: Omit<CloudResource, 'id'>) => string;
    updateResource: (id: string, updates: Partial<CloudResource>) => void;
    deleteResource: (id: string) => void;
    getResource: (id: string) => CloudResource | undefined;
    getResourceCount: () => number;
    getResourcesByCategory: (category: ResourceCategory) => CloudResource[];
    getResourcesByProvider: (provider: CloudProvider) => CloudResource[];

    // Analysis operations
    calculateCost: () => CostEstimate;
    validateInfrastructure: () => string[];
    checkCompliance: () => ComplianceReport;
    analyzeHighAvailability: () => HighAvailabilityAnalysis;
    generateRecommendations: () => string[];

    // Export operations
    exportToTerraform: () => string;
    exportToCloudFormation: () => string;
    exportToPulumi: () => string;
    exportToARM: () => string;

    // Utility operations
    getAvailableRegions: (provider: CloudProvider) => Region[];
    getResourceTypesForCategory: (provider: CloudProvider, category: ResourceCategory) => ResourceType[];
}

/**
 * Resource type mappings by provider and category
 */
const RESOURCE_TYPES: Record<CloudProvider, Record<ResourceCategory, ResourceType[]>> = {
    aws: {
        compute: ['vm', 'container', 'serverless'],
        network: ['vpc', 'subnet', 'loadbalancer'],
        storage: ['storage', 'cdn'],
        database: ['rds', 'nosql', 'cache'],
        security: ['firewall', 'waf', 'kms'],
        analytics: ['datawarehouse', 'etl', 'streaming'],
    },
    gcp: {
        compute: ['vm', 'container', 'serverless'],
        network: ['vpc', 'subnet', 'loadbalancer'],
        storage: ['storage', 'cdn'],
        database: ['rds', 'nosql', 'cache'],
        security: ['firewall', 'waf', 'kms'],
        analytics: ['datawarehouse', 'etl', 'streaming'],
    },
    azure: {
        compute: ['vm', 'container', 'serverless'],
        network: ['vpc', 'subnet', 'loadbalancer'],
        storage: ['storage', 'cdn'],
        database: ['rds', 'nosql', 'cache'],
        security: ['firewall', 'waf', 'kms'],
        analytics: ['datawarehouse', 'etl', 'streaming'],
    },
};

/**
 * Regional availability by provider
 */
const REGIONS_BY_PROVIDER: Record<CloudProvider, Region[]> = {
    aws: ['us-east-1', 'us-west-2', 'eu-west-1', 'ap-southeast-1'],
    gcp: ['us-central1', 'europe-west1', 'asia-east1'],
    azure: ['eastus', 'westeurope', 'southeastasia'],
};

/**
 * Base pricing (monthly USD) by resource type
 */
const BASE_PRICING: Record<ResourceType, number> = {
    vm: 20,
    container: 15,
    serverless: 5,
    vpc: 0,
    subnet: 0,
    loadbalancer: 25,
    storage: 10,
    cdn: 20,
    rds: 50,
    nosql: 30,
    cache: 40,
    firewall: 15,
    waf: 30,
    kms: 1,
    datawarehouse: 100,
    etl: 50,
    streaming: 60,
};

/**
 * Cloud Infrastructure Hook
 */
export const useCloudInfrastructure = (): UseCloudInfrastructureReturn => {
    // State
    const [infrastructureName, setInfrastructureName] = useState('Cloud Infrastructure');
    const [selectedProvider, setSelectedProvider] = useState<CloudProvider>('aws');
    const [selectedRegion, setSelectedRegion] = useState<Region>('us-east-1');
    const [resources, setResources] = useState<CloudResource[]>([]);

    // Resource operations

    const addResource = useCallback((resource: Omit<CloudResource, 'id'>): string => {
        const id = `resource-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
        const basePrice = BASE_PRICING[resource.type] || 0;

        // Calculate actual cost based on config
        let monthlyCost = basePrice;
        if (resource.config.size === 'large') monthlyCost *= 2;
        else if (resource.config.size === 'xlarge') monthlyCost *= 4;

        const newResource: CloudResource = {
            ...resource,
            id,
            monthlyCost,
        };

        setResources((prev) => [...prev, newResource]);
        return id;
    }, []);

    const updateResource = useCallback((id: string, updates: Partial<CloudResource>) => {
        setResources((prev) =>
            prev.map((resource) => (resource.id === id ? { ...resource, ...updates } : resource))
        );
    }, []);

    const deleteResource = useCallback((id: string) => {
        setResources((prev) => prev.filter((resource) => resource.id !== id));
    }, []);

    const getResource = useCallback(
        (id: string): CloudResource | undefined => {
            return resources.find((resource) => resource.id === id);
        },
        [resources]
    );

    const getResourceCount = useCallback((): number => {
        return resources.length;
    }, [resources]);

    const getResourcesByCategory = useCallback(
        (category: ResourceCategory): CloudResource[] => {
            return resources.filter((resource) => resource.category === category);
        },
        [resources]
    );

    const getResourcesByProvider = useCallback(
        (provider: CloudProvider): CloudResource[] => {
            return resources.filter((resource) => resource.provider === provider);
        },
        [resources]
    );

    // Analysis operations

    /**
     * Calculate total infrastructure cost
     */
    const calculateCost = useCallback((): CostEstimate => {
        const breakdown: Record<string, number> = {};
        let totalMonthlyCost = 0;

        resources.forEach((resource) => {
            const cost = resource.monthlyCost || 0;
            totalMonthlyCost += cost;

            const category = resource.category;
            breakdown[category] = (breakdown[category] || 0) + cost;
        });

        return {
            totalMonthlyCost,
            breakdown,
        };
    }, [resources]);

    /**
     * Validate infrastructure configuration
     */
    const validateInfrastructure = useCallback((): string[] => {
        const issues: string[] = [];

        // Basic validation
        if (resources.length === 0) {
            issues.push('No resources defined');
            return issues;
        }

        // Check for network resources
        const hasVPC = resources.some((r) => r.type === 'vpc');
        if (!hasVPC && resources.length > 2) {
            issues.push('No VPC/VNet defined - resources should be in a virtual network');
        }

        // Check for compute without network
        const computeResources = getResourcesByCategory('compute');
        const networkResources = getResourcesByCategory('network');
        if (computeResources.length > 0 && networkResources.length === 0) {
            issues.push('Compute resources without network configuration');
        }

        // Check for database without subnet
        const databaseResources = getResourcesByCategory('database');
        const hasSubnet = resources.some((r) => r.type === 'subnet');
        if (databaseResources.length > 0 && !hasSubnet) {
            issues.push('Database resources should be placed in subnets');
        }

        // Check for security resources
        const securityResources = getResourcesByCategory('security');
        if (securityResources.length === 0 && resources.length > 3) {
            issues.push('No security resources (firewall/WAF) defined');
        }

        // Check for single region deployment
        const regions = new Set(resources.map((r) => r.region));
        if (regions.size === 1 && resources.length > 5) {
            issues.push('All resources in single region - consider multi-region for HA');
        }

        // Check for cost optimization
        const cost = calculateCost();
        if (cost.totalMonthlyCost > 1000) {
            const vmCount = resources.filter((r) => r.type === 'vm').length;
            if (vmCount > 5) {
                issues.push('Consider using auto-scaling groups or containers to optimize VM costs');
            }
        }

        return issues;
    }, [resources, getResourcesByCategory, calculateCost]);

    /**
     * Check compliance with best practices
     */
    const checkCompliance = useCallback((): ComplianceReport => {
        const checks: ComplianceCheck[] = [];

        // CIS Benchmark: Encryption at rest
        const storageResources = getResourcesByCategory('storage').concat(getResourcesByCategory('database'));
        const encryptedStorage = storageResources.filter(
            (r) => r.config.encryption === true || r.config.encrypted === true
        );
        checks.push({
            name: 'Data encryption at rest',
            passed: encryptedStorage.length === storageResources.length || storageResources.length === 0,
            severity: 'critical',
            description: 'All storage and database resources should be encrypted',
        });

        // CIS Benchmark: Network segmentation
        const hasVPC = resources.some((r) => r.type === 'vpc');
        const hasSubnet = resources.some((r) => r.type === 'subnet');
        checks.push({
            name: 'Network segmentation',
            passed: hasVPC && hasSubnet,
            severity: 'high',
            description: 'Resources should be segmented into VPC and subnets',
        });

        // CIS Benchmark: Firewall/Security groups
        const hasFirewall = resources.some((r) => r.type === 'firewall');
        const computeCount = getResourcesByCategory('compute').length;
        checks.push({
            name: 'Firewall protection',
            passed: hasFirewall || computeCount === 0,
            severity: 'high',
            description: 'Compute resources should have firewall protection',
        });

        // Best Practice: Backup enabled
        const databaseResources = getResourcesByCategory('database');
        const databasesWithBackup = databaseResources.filter((r) => r.config.backup === true);
        checks.push({
            name: 'Database backups enabled',
            passed: databasesWithBackup.length === databaseResources.length || databaseResources.length === 0,
            severity: 'high',
            description: 'All databases should have automated backups',
        });

        // Best Practice: Load balancer for HA
        const hasLB = resources.some((r) => r.type === 'loadbalancer');
        checks.push({
            name: 'Load balancer for high availability',
            passed: hasLB || computeCount < 2,
            severity: 'medium',
            description: 'Multiple compute instances should use a load balancer',
        });

        // Best Practice: Monitoring and logging
        const hasLogging = resources.some((r) => r.config.logging === true);
        checks.push({
            name: 'Logging enabled',
            passed: hasLogging,
            severity: 'medium',
            description: 'Enable logging for audit and troubleshooting',
        });

        // Best Practice: WAF for web applications
        const hasWAF = resources.some((r) => r.type === 'waf');
        checks.push({
            name: 'Web Application Firewall',
            passed: hasWAF || computeCount === 0,
            severity: 'medium',
            description: 'Web-facing applications should use WAF',
        });

        // Best Practice: KMS for key management
        const hasKMS = resources.some((r) => r.type === 'kms');
        checks.push({
            name: 'Key Management Service',
            passed: hasKMS || storageResources.length === 0,
            severity: 'medium',
            description: 'Use KMS for centralized key management',
        });

        // Calculate compliance percentage
        const passedChecks = checks.filter((c) => c.passed).length;
        const compliantPercentage = Math.round((passedChecks / checks.length) * 100);

        return {
            compliantPercentage,
            checks,
        };
    }, [resources, getResourcesByCategory]);

    /**
     * Analyze high availability configuration
     */
    const analyzeHighAvailability = useCallback((): HighAvailabilityAnalysis => {
        // Check for multi-AZ deployment
        const regions = new Set(resources.map((r) => r.region));
        const hasMultiAZ = regions.size > 1;

        // Check for backup configuration
        const databaseResources = getResourcesByCategory('database');
        const storageResources = getResourcesByCategory('storage');
        const criticalResources = [...databaseResources, ...storageResources];
        const hasBackup = criticalResources.some((r) => r.config.backup === true);

        // Calculate redundancy score (0-10)
        let redundancyScore = 0;

        // +2 for load balancer
        if (resources.some((r) => r.type === 'loadbalancer')) redundancyScore += 2;

        // +2 for multi-AZ
        if (hasMultiAZ) redundancyScore += 2;

        // +2 for backup
        if (hasBackup) redundancyScore += 2;

        // +2 for multiple compute instances
        const computeCount = getResourcesByCategory('compute').length;
        if (computeCount >= 2) redundancyScore += 2;

        // +2 for CDN
        if (resources.some((r) => r.type === 'cdn')) redundancyScore += 2;

        redundancyScore = Math.min(redundancyScore, 10);

        // Find single points of failure
        const singlePointsOfFailure: string[] = [];

        if (computeCount === 1) {
            singlePointsOfFailure.push('Single compute instance - no redundancy');
        }

        if (databaseResources.length === 1 && !databaseResources[0].config.replication) {
            singlePointsOfFailure.push('Single database instance without replication');
        }

        if (!resources.some((r) => r.type === 'loadbalancer') && computeCount > 1) {
            singlePointsOfFailure.push('Multiple instances without load balancer');
        }

        return {
            hasMultiAZ,
            hasBackup,
            redundancyScore,
            singlePointsOfFailure,
        };
    }, [resources, getResourcesByCategory]);

    /**
     * Generate architecture recommendations
     */
    const generateRecommendations = useCallback((): string[] => {
        const recommendations: string[] = [];

        if (resources.length === 0) {
            recommendations.push('Add cloud resources to start building your infrastructure');
            return recommendations;
        }

        // Network recommendations
        const hasVPC = resources.some((r) => r.type === 'vpc');
        if (!hasVPC && resources.length > 1) {
            recommendations.push('Create a VPC/VNet to isolate your resources');
        }

        const hasSubnet = resources.some((r) => r.type === 'subnet');
        if (!hasSubnet && resources.length > 2) {
            recommendations.push('Add subnets for network segmentation');
        }

        // Security recommendations
        const hasFirewall = resources.some((r) => r.type === 'firewall');
        const computeCount = getResourcesByCategory('compute').length;
        if (!hasFirewall && computeCount > 0) {
            recommendations.push('Add firewall/security groups to protect compute resources');
        }

        const hasWAF = resources.some((r) => r.type === 'waf');
        if (!hasWAF && computeCount > 0) {
            recommendations.push('Consider adding WAF for web application protection');
        }

        const hasKMS = resources.some((r) => r.type === 'kms');
        const storageCount = getResourcesByCategory('storage').length + getResourcesByCategory('database').length;
        if (!hasKMS && storageCount > 0) {
            recommendations.push('Use KMS for centralized encryption key management');
        }

        // High availability recommendations
        const haAnalysis = analyzeHighAvailability();
        if (!haAnalysis.hasMultiAZ && resources.length > 3) {
            recommendations.push('Deploy resources across multiple availability zones for HA');
        }

        if (!haAnalysis.hasBackup && getResourcesByCategory('database').length > 0) {
            recommendations.push('Enable automated backups for all databases');
        }

        const hasLB = resources.some((r) => r.type === 'loadbalancer');
        if (!hasLB && computeCount >= 2) {
            recommendations.push('Add a load balancer to distribute traffic across instances');
        }

        // Cost optimization recommendations
        const cost = calculateCost();
        if (cost.totalMonthlyCost > 500) {
            const vmCount = resources.filter((r) => r.type === 'vm').length;
            if (vmCount > 3) {
                recommendations.push('Consider using auto-scaling or spot instances to reduce costs');
            }
        }

        // Storage recommendations
        const hasCDN = resources.some((r) => r.type === 'cdn');
        const hasStorage = resources.some((r) => r.type === 'storage');
        if (hasStorage && !hasCDN) {
            recommendations.push('Add CDN for faster content delivery and reduced bandwidth costs');
        }

        // Database recommendations
        const hasCaching = resources.some((r) => r.type === 'cache');
        const hasDatabase = resources.some((r) => r.type === 'rds' || r.type === 'nosql');
        if (hasDatabase && !hasCaching) {
            recommendations.push('Add caching layer (Redis/Memcached) to reduce database load');
        }

        return recommendations;
    }, [resources, getResourcesByCategory, analyzeHighAvailability, calculateCost]);

    // Export operations

    /**
     * Export to Terraform (HCL)
     */
    const exportToTerraform = useCallback((): string => {
        const lines: string[] = [];
        lines.push('# Cloud Infrastructure Configuration');
        lines.push(`# Generated from: ${infrastructureName}`);
        lines.push('# Provider: Terraform');
        lines.push('');

        // Group by provider
        const providerGroups: Record<CloudProvider, CloudResource[]> = {
            aws: [],
            gcp: [],
            azure: [],
        };

        resources.forEach((resource) => {
            providerGroups[resource.provider].push(resource);
        });

        // Export AWS resources
        if (providerGroups.aws.length > 0) {
            lines.push('# AWS Resources');
            lines.push('provider "aws" {');
            lines.push(`  region = "${providerGroups.aws[0].region}"`);
            lines.push('}');
            lines.push('');

            providerGroups.aws.forEach((resource) => {
                const terraformType = `aws_${resource.type}`;
                lines.push(`resource "${terraformType}" "${resource.id}" {`);
                lines.push(`  name = "${resource.name}"`);

                // Add config properties
                Object.entries(resource.config).forEach(([key, value]) => {
                    lines.push(`  ${key} = "${value}"`);
                });

                // Add tags
                if (resource.tags && Object.keys(resource.tags).length > 0) {
                    lines.push('  tags = {');
                    Object.entries(resource.tags).forEach(([key, value]) => {
                        lines.push(`    ${key} = "${value}"`);
                    });
                    lines.push('  }');
                }

                lines.push('}');
                lines.push('');
            });
        }

        // Export GCP resources
        if (providerGroups.gcp.length > 0) {
            lines.push('# Google Cloud Resources');
            lines.push('provider "google" {');
            lines.push(`  region = "${providerGroups.gcp[0].region}"`);
            lines.push('}');
            lines.push('');

            providerGroups.gcp.forEach((resource) => {
                const terraformType = `google_${resource.type}`;
                lines.push(`resource "${terraformType}" "${resource.id}" {`);
                lines.push(`  name = "${resource.name}"`);

                Object.entries(resource.config).forEach(([key, value]) => {
                    lines.push(`  ${key} = "${value}"`);
                });

                lines.push('}');
                lines.push('');
            });
        }

        // Export Azure resources
        if (providerGroups.azure.length > 0) {
            lines.push('# Azure Resources');
            lines.push('provider "azurerm" {');
            lines.push('  features {}');
            lines.push('}');
            lines.push('');

            providerGroups.azure.forEach((resource) => {
                const terraformType = `azurerm_${resource.type}`;
                lines.push(`resource "${terraformType}" "${resource.id}" {`);
                lines.push(`  name = "${resource.name}"`);
                lines.push(`  location = "${resource.region}"`);

                Object.entries(resource.config).forEach(([key, value]) => {
                    lines.push(`  ${key} = "${value}"`);
                });

                lines.push('}');
                lines.push('');
            });
        }

        return lines.join('\n');
    }, [infrastructureName, resources]);

    /**
     * Export to AWS CloudFormation (YAML)
     */
    const exportToCloudFormation = useCallback((): string => {
        const lines: string[] = [];
        lines.push('AWSTemplateFormatVersion: "2010-09-09"');
        lines.push(`Description: ${infrastructureName}`);
        lines.push('');
        lines.push('Resources:');

        const awsResources = getResourcesByProvider('aws');

        awsResources.forEach((resource) => {
            const cfnType = `AWS::${resource.category}::${resource.type}`;
            lines.push(`  ${resource.id}:`);
            lines.push(`    Type: ${cfnType}`);
            lines.push('    Properties:');
            lines.push(`      Name: ${resource.name}`);

            Object.entries(resource.config).forEach(([key, value]) => {
                lines.push(`      ${key}: ${value}`);
            });

            if (resource.tags) {
                lines.push('      Tags:');
                Object.entries(resource.tags).forEach(([key, value]) => {
                    lines.push(`        - Key: ${key}`);
                    lines.push(`          Value: ${value}`);
                });
            }

            lines.push('');
        });

        return lines.join('\n');
    }, [infrastructureName, getResourcesByProvider]);

    /**
     * Export to Pulumi (TypeScript)
     */
    const exportToPulumi = useCallback((): string => {
        const lines: string[] = [];
        lines.push('import * as pulumi from "@pulumi/pulumi";');
        lines.push('import * as aws from "@pulumi/aws";');
        lines.push('import * as gcp from "@pulumi/gcp";');
        lines.push('import * as azure from "@pulumi/azure";');
        lines.push('');
        lines.push(`// ${infrastructureName}`);
        lines.push('');

        resources.forEach((resource) => {
            const provider = resource.provider === 'aws' ? 'aws' : resource.provider === 'gcp' ? 'gcp' : 'azure';
            const pulumiType = `${provider}.${resource.category}.${resource.type}`;

            lines.push(`const ${resource.id} = new ${pulumiType}("${resource.name}", {`);

            Object.entries(resource.config).forEach(([key, value]) => {
                lines.push(`  ${key}: "${value}",`);
            });

            if (resource.tags) {
                lines.push('  tags: {');
                Object.entries(resource.tags).forEach(([key, value]) => {
                    lines.push(`    ${key}: "${value}",`);
                });
                lines.push('  },');
            }

            lines.push('});');
            lines.push('');
        });

        return lines.join('\n');
    }, [infrastructureName, resources]);

    /**
     * Export to Azure ARM Template (JSON)
     */
    const exportToARM = useCallback((): string => {
        const azureResources = getResourcesByProvider('azure');

        const template = {
            '$schema': 'https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#',
            contentVersion: '1.0.0.0',
            metadata: {
                description: infrastructureName,
            },
            resources: azureResources.map((resource) => ({
                type: `Microsoft.${resource.category}/${resource.type}`,
                apiVersion: '2021-01-01',
                name: resource.name,
                location: resource.region,
                properties: resource.config,
                tags: resource.tags || {},
            })),
        };

        return JSON.stringify(template, null, 2);
    }, [infrastructureName, getResourcesByProvider]);

    // Utility operations

    const getAvailableRegions = useCallback((provider: CloudProvider): Region[] => {
        return REGIONS_BY_PROVIDER[provider];
    }, []);

    const getResourceTypesForCategory = useCallback(
        (provider: CloudProvider, category: ResourceCategory): ResourceType[] => {
            return RESOURCE_TYPES[provider][category] || [];
        },
        []
    );

    return {
        // State
        infrastructureName,
        setInfrastructureName,
        selectedProvider,
        setSelectedProvider,
        selectedRegion,
        setSelectedRegion,
        resources,

        // Resource operations
        addResource,
        updateResource,
        deleteResource,
        getResource,
        getResourceCount,
        getResourcesByCategory,
        getResourcesByProvider,

        // Analysis operations
        calculateCost,
        validateInfrastructure,
        checkCompliance,
        analyzeHighAvailability,
        generateRecommendations,

        // Export operations
        exportToTerraform,
        exportToCloudFormation,
        exportToPulumi,
        exportToARM,

        // Utility operations
        getAvailableRegions,
        getResourceTypesForCategory,
    };
};

export default useCloudInfrastructure;
