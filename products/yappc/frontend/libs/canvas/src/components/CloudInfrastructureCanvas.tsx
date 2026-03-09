/**
 * Cloud Infrastructure Canvas Component
 * 
 * @doc.type component
 * @doc.purpose Technical Architect - Multi-Cloud Infrastructure Design (Journey 16.1)
 * @doc.layer product
 * @doc.pattern Canvas
 * 
 * Comprehensive multi-cloud infrastructure designer supporting AWS, Google Cloud Platform,
 * and Microsoft Azure with visual resource composition, cost estimation, compliance validation,
 * and infrastructure-as-code export.
 * 
 * Features:
 * - Multi-cloud resource library (AWS/GCP/Azure services)
 * - Visual topology designer with drag-and-drop
 * - Network architecture (VPC/VNet/VPC, subnets, routing)
 * - Compute resources (EC2/Compute Engine/VM, containers, serverless)
 * - Data services (RDS/Cloud SQL/SQL Database, storage, caching)
 * - Real-time cost estimation with regional pricing
 * - Security compliance validation (CIS benchmarks, best practices)
 * - High availability and disaster recovery recommendations
 * - Export to Terraform, CloudFormation, Pulumi, ARM templates
 * 
 * @example
 * ```tsx
 * <CloudInfrastructureCanvas
 *   onExport={(config, format) => console.log(config)}
 *   onValidate={(issues) => console.log(issues)}
 * />
 * ```
 */

import React, { useState } from 'react';
import { useCloudInfrastructure } from '../hooks/useCloudInfrastructure';
import type {
    CloudProvider,
    CloudResource,
    ResourceCategory,
    ResourceType,
    Region,
} from '../hooks/useCloudInfrastructure';

/**
 * Component Props
 */
export interface CloudInfrastructureCanvasProps {
    /**
     * Callback when infrastructure is exported
     */
    onExport?: (config: string, format: 'terraform' | 'cloudformation' | 'pulumi' | 'arm') => void;

    /**
     * Callback when infrastructure is validated
     */
    onValidate?: (issues: string[]) => void;
}

/**
 * Cloud Infrastructure Canvas Component
 */
export const CloudInfrastructureCanvas: React.FC<CloudInfrastructureCanvasProps> = ({
    onExport,
    onValidate,
}) => {
    const {
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

        // Analysis
        calculateCost,
        validateInfrastructure,
        checkCompliance,
        analyzeHighAvailability,
        generateRecommendations,

        // Export
        exportToTerraform,
        exportToCloudFormation,
        exportToPulumi,
        exportToARM,

        // Utilities
        getAvailableRegions,
        getResourceTypesForCategory,
    } = useCloudInfrastructure();

    // Dialog states
    const [addResourceDialogOpen, setAddResourceDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [validationDialogOpen, setValidationDialogOpen] = useState(false);
    const [costBreakdownDialogOpen, setCostBreakdownDialogOpen] = useState(false);

    // Form states
    const [newResourceName, setNewResourceName] = useState('');
    const [newResourceCategory, setNewResourceCategory] = useState<ResourceCategory>('compute');
    const [newResourceType, setNewResourceType] = useState<ResourceType>('vm');
    const [newResourceConfig, setNewResourceConfig] = useState('');

    const [exportFormat, setExportFormat] = useState<'terraform' | 'cloudformation' | 'pulumi' | 'arm'>('terraform');
    const [selectedResourceId, setSelectedResourceId] = useState<string | null>(null);

    /**
     * Cloud provider configurations
     */
    const PROVIDERS: Record<CloudProvider, { label: string; color: string; icon: string }> = {
        aws: { label: 'AWS', color: 'bg-orange-100 border-orange-300 text-orange-800', icon: '☁️' },
        gcp: { label: 'Google Cloud', color: 'bg-blue-100 border-blue-300 text-blue-800', icon: '🔵' },
        azure: { label: 'Microsoft Azure', color: 'bg-sky-100 border-sky-300 text-sky-800', icon: '🔷' },
    };

    /**
     * Resource category configurations
     */
    const CATEGORIES: Record<ResourceCategory, { label: string; color: string; icon: string }> = {
        compute: { label: 'Compute', color: 'bg-purple-50 border-purple-200 text-purple-800', icon: '💻' },
        network: { label: 'Network', color: 'bg-green-50 border-green-200 text-green-800', icon: '🌐' },
        storage: { label: 'Storage', color: 'bg-blue-50 border-blue-200 text-blue-800', icon: '💾' },
        database: { label: 'Database', color: 'bg-indigo-50 border-indigo-200 text-indigo-800', icon: '🗄️' },
        security: { label: 'Security', color: 'bg-red-50 border-red-200 text-red-800', icon: '🔒' },
        analytics: { label: 'Analytics', color: 'bg-yellow-50 border-yellow-200 text-yellow-800', icon: '📊' },
    };

    /**
     * Handle resource addition
     */
    const handleAddResource = () => {
        if (!newResourceName.trim()) return;

        const config: Record<string, unknown> = newResourceConfig ? JSON.parse(newResourceConfig) : {};

        addResource({
            name: newResourceName,
            provider: selectedProvider,
            category: newResourceCategory,
            type: newResourceType,
            region: selectedRegion,
            config,
        });

        // Reset form
        setNewResourceName('');
        setNewResourceConfig('');
        setAddResourceDialogOpen(false);
    };

    /**
     * Handle validation
     */
    const handleValidate = () => {
        const issues = validateInfrastructure();
        onValidate?.(issues);
        setValidationDialogOpen(true);
    };

    /**
     * Handle export
     */
    const handleExport = () => {
        let exportedConfig: string;

        switch (exportFormat) {
            case 'terraform':
                exportedConfig = exportToTerraform();
                break;
            case 'cloudformation':
                exportedConfig = exportToCloudFormation();
                break;
            case 'pulumi':
                exportedConfig = exportToPulumi();
                break;
            case 'arm':
                exportedConfig = exportToARM();
                break;
            default:
                exportedConfig = exportToTerraform();
        }

        onExport?.(exportedConfig, exportFormat);
        setExportDialogOpen(false);
    };

    // Calculate metrics
    const costEstimate = calculateCost();
    const complianceReport = checkCompliance();
    const haAnalysis = analyzeHighAvailability();
    const recommendations = generateRecommendations();
    const validationIssues = validateInfrastructure();

    return (
        <div className="h-full flex flex-col bg-gray-50">
            {/* Toolbar */}
            <div className="bg-white border-b border-gray-200 p-4">
                <div className="flex items-center gap-4">
                    <span className="text-2xl">☁️</span>
                    <input
                        type="text"
                        value={infrastructureName}
                        onChange={(e) => setInfrastructureName(e.target.value)}
                        placeholder="Cloud Infrastructure Name"
                        className="flex-1 text-xl font-semibold border-none outline-none focus:ring-2 focus:ring-blue-500 rounded px-2"
                    />

                    {/* Provider Selector */}
                    <div className="flex gap-2">
                        {Object.entries(PROVIDERS).map(([provider, config]) => (
                            <button
                                key={provider}
                                onClick={() => setSelectedProvider(provider as CloudProvider)}
                                className={`px-4 py-2 rounded-lg border-2 transition-colors ${selectedProvider === provider
                                    ? config.color
                                    : 'bg-white border-gray-300 text-gray-700 hover:border-gray-400'
                                    }`}
                            >
                                {config.icon} {config.label}
                            </button>
                        ))}
                    </div>

                    {/* Region Selector */}
                    <select
                        value={selectedRegion}
                        onChange={(e) => setSelectedRegion(e.target.value as Region)}
                        className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                    >
                        {getAvailableRegions(selectedProvider).map((region) => (
                            <option key={region} value={region}>
                                {region}
                            </option>
                        ))}
                    </select>

                    <div className="flex items-center gap-2">
                        <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
                            📦 {getResourceCount()} Resources
                        </span>
                        <span className="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm font-medium">
                            💰 ${costEstimate.totalMonthlyCost.toFixed(2)}/mo
                        </span>
                    </div>

                    <button
                        onClick={handleValidate}
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                        title="Validate Infrastructure"
                    >
                        ✓ Validate
                    </button>
                    <button
                        onClick={() => setExportDialogOpen(true)}
                        className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors"
                        title="Export Configuration"
                    >
                        ↓ Export
                    </button>
                </div>
            </div>

            {/* Main Content */}
            <div className="flex flex-1 overflow-hidden">
                {/* Left Panel - Resource Palette */}
                <div className="w-80 bg-white border-r border-gray-200 p-4 overflow-y-auto">
                    <div className="mb-4">
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="text-lg font-semibold text-gray-900">Resource Palette</h3>
                            <button
                                onClick={() => setAddResourceDialogOpen(true)}
                                className="px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
                            >
                                + Add
                            </button>
                        </div>

                        <div className="space-y-4">
                            {Object.entries(CATEGORIES).map(([category, config]) => {
                                const categoryResources = getResourcesByCategory(category as ResourceCategory);
                                const resourceTypes = getResourceTypesForCategory(selectedProvider, category as ResourceCategory);

                                return (
                                    <div key={category}>
                                        <div className="flex items-center justify-between mb-2">
                                            <div className="text-sm font-medium text-gray-700">
                                                {config.icon} {config.label}
                                            </div>
                                            <span className="text-xs text-gray-500">
                                                {categoryResources.length} / {resourceTypes.length} types
                                            </span>
                                        </div>

                                        <div className="space-y-1">
                                            {resourceTypes.slice(0, 3).map((type) => (
                                                <button
                                                    key={type}
                                                    onClick={() => {
                                                        setNewResourceCategory(category as ResourceCategory);
                                                        setNewResourceType(type);
                                                        setAddResourceDialogOpen(true);
                                                    }}
                                                    className={`w-full px-3 py-2 text-left text-sm rounded-lg border-2 transition-colors ${config.color
                                                        } hover:shadow-md`}
                                                >
                                                    {type}
                                                </button>
                                            ))}
                                            {resourceTypes.length > 3 && (
                                                <div className="text-xs text-gray-500 pl-3">
                                                    +{resourceTypes.length - 3} more
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>

                    {/* Quick Stats */}
                    <div className="mt-6 pt-6 border-t border-gray-200">
                        <h3 className="text-sm font-semibold text-gray-900 mb-3">Quick Stats</h3>
                        <div className="space-y-2">
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Provider</span>
                                <span className="font-medium">{PROVIDERS[selectedProvider].label}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Region</span>
                                <span className="font-medium">{selectedRegion}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Resources</span>
                                <span className="font-medium">{getResourceCount()}</span>
                            </div>
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Compliance</span>
                                <span className={`font-medium ${complianceReport.compliantPercentage >= 80 ? 'text-green-600' :
                                    complianceReport.compliantPercentage >= 60 ? 'text-yellow-600' : 'text-red-600'
                                    }`}>
                                    {complianceReport.compliantPercentage}%
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Center Panel - Resource List */}
                <div className="flex-1 p-6 overflow-y-auto">
                    <div className="max-w-5xl mx-auto">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-xl font-semibold text-gray-900">Infrastructure Resources</h3>
                            <button
                                onClick={() => setCostBreakdownDialogOpen(true)}
                                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
                            >
                                💰 Cost Breakdown
                            </button>
                        </div>

                        {Object.entries(CATEGORIES).map(([category, config]) => {
                            const categoryResources = getResourcesByCategory(category as ResourceCategory);
                            if (categoryResources.length === 0) return null;

                            return (
                                <div key={category} className="mb-6">
                                    <h4 className="text-lg font-medium text-gray-900 mb-3 flex items-center gap-2">
                                        {config.icon} {config.label}
                                        <span className="text-sm text-gray-500">({categoryResources.length})</span>
                                    </h4>

                                    <div className="space-y-2">
                                        {categoryResources.map((resource) => {
                                            const providerConfig = PROVIDERS[resource.provider];

                                            return (
                                                <div
                                                    key={resource.id}
                                                    className={`bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow cursor-pointer ${selectedResourceId === resource.id ? 'ring-2 ring-blue-500' : ''
                                                        }`}
                                                    onClick={() => setSelectedResourceId(resource.id)}
                                                >
                                                    <div className="flex justify-between items-start">
                                                        <div className="flex-1">
                                                            <div className="flex items-center gap-3">
                                                                <span className="font-medium text-gray-900">{resource.name}</span>
                                                                <span className={`px-2 py-0.5 text-xs rounded ${providerConfig.color}`}>
                                                                    {providerConfig.icon} {providerConfig.label}
                                                                </span>
                                                                <span className="px-2 py-0.5 bg-gray-100 text-gray-700 text-xs rounded">
                                                                    {resource.type}
                                                                </span>
                                                            </div>

                                                            <div className="mt-2 flex items-center gap-4 text-sm text-gray-600">
                                                                <span>📍 {resource.region}</span>
                                                                {resource.config.size != null && (
                                                                    <span>💻 {String(resource.config.size)}</span>
                                                                )}
                                                                {resource.monthlyCost && (
                                                                    <span className="text-green-600 font-medium">
                                                                        ${resource.monthlyCost.toFixed(2)}/mo
                                                                    </span>
                                                                )}
                                                            </div>

                                                            {resource.tags && Object.keys(resource.tags).length > 0 && (
                                                                <div className="mt-2 flex flex-wrap gap-1">
                                                                    {Object.entries(resource.tags).slice(0, 3).map(([key, value]) => (
                                                                        <span key={key} className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded">
                                                                            {key}: {String(value)}
                                                                        </span>
                                                                    ))}
                                                                </div>
                                                            )}
                                                        </div>

                                                        <button
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                deleteResource(resource.id);
                                                            }}
                                                            className="text-red-600 hover:text-red-800"
                                                            title="Delete Resource"
                                                        >
                                                            ×
                                                        </button>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            );
                        })}

                        {resources.length === 0 && (
                            <div className="p-12 bg-blue-50 border border-blue-200 rounded-lg text-center">
                                <div className="text-4xl mb-4">☁️</div>
                                <div className="text-gray-700 font-medium mb-2">No resources added yet</div>
                                <div className="text-sm text-gray-500 mb-4">
                                    Start building your cloud infrastructure by adding resources from the palette
                                </div>
                                <button
                                    onClick={() => setAddResourceDialogOpen(true)}
                                    className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                                >
                                    Add First Resource
                                </button>
                            </div>
                        )}
                    </div>
                </div>

                {/* Right Panel - Analysis & Recommendations */}
                <div className="w-96 bg-white border-l border-gray-200 p-4 overflow-y-auto">
                    {/* Cost Summary */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Cost Estimate</h3>
                        <div className="bg-gradient-to-r from-green-500 to-green-600 rounded-lg p-4 text-white">
                            <div className="text-sm opacity-90">Monthly Cost</div>
                            <div className="text-3xl font-bold">${costEstimate.totalMonthlyCost.toFixed(2)}</div>
                            <div className="text-sm opacity-90 mt-1">Annual: ${(costEstimate.totalMonthlyCost * 12).toFixed(2)}</div>
                        </div>

                        <div className="mt-3 space-y-2">
                            {Object.entries(costEstimate.breakdown).slice(0, 4).map(([category, cost]) => (
                                <div key={category} className="flex justify-between text-sm">
                                    <span className="text-gray-600 capitalize">{category}</span>
                                    <span className="font-medium">${cost.toFixed(2)}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Compliance Report */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Compliance</h3>
                        <div className="space-y-3">
                            <div className="flex justify-between items-center">
                                <span className="text-sm text-gray-600">Overall Score</span>
                                <span className={`text-lg font-bold ${complianceReport.compliantPercentage >= 80 ? 'text-green-600' :
                                    complianceReport.compliantPercentage >= 60 ? 'text-yellow-600' : 'text-red-600'
                                    }`}>
                                    {complianceReport.compliantPercentage}%
                                </span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className={`h-2 rounded-full ${complianceReport.compliantPercentage >= 80 ? 'bg-green-600' :
                                        complianceReport.compliantPercentage >= 60 ? 'bg-yellow-600' : 'bg-red-600'
                                        }`}
                                    style={{ width: `${complianceReport.compliantPercentage}%` }}
                                />
                            </div>

                            <div className="space-y-2">
                                {complianceReport.checks.slice(0, 4).map((check, index) => (
                                    <div key={index} className="flex items-center gap-2 text-sm">
                                        <span className={check.passed ? 'text-green-600' : 'text-red-600'}>
                                            {check.passed ? '✓' : '✗'}
                                        </span>
                                        <span className="text-gray-700">{check.name}</span>
                                    </div>
                                ))}
                                {complianceReport.checks.length > 4 && (
                                    <div className="text-xs text-gray-500 pl-6">
                                        +{complianceReport.checks.length - 4} more checks
                                    </div>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* High Availability */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">High Availability</h3>
                        <div className="space-y-2">
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">Multi-AZ</span>
                                <span className={`font-medium ${haAnalysis.hasMultiAZ ? 'text-green-600' : 'text-red-600'}`}>
                                    {haAnalysis.hasMultiAZ ? '✓ Yes' : '✗ No'}
                                </span>
                            </div>
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">Redundancy</span>
                                <span className="font-medium">{haAnalysis.redundancyScore}/10</span>
                            </div>
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">Backup</span>
                                <span className={`font-medium ${haAnalysis.hasBackup ? 'text-green-600' : 'text-red-600'}`}>
                                    {haAnalysis.hasBackup ? '✓ Yes' : '✗ No'}
                                </span>
                            </div>
                        </div>
                    </div>

                    {/* Recommendations */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Recommendations</h3>
                        <div className="space-y-2">
                            {recommendations.slice(0, 5).map((rec, index) => (
                                <div key={index} className="p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                                    <div className="flex gap-2">
                                        <span className="text-yellow-600 flex-shrink-0">💡</span>
                                        <span className="text-sm text-gray-700">{rec}</span>
                                    </div>
                                </div>
                            ))}
                            {recommendations.length === 0 && (
                                <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800">
                                    ✓ No recommendations - infrastructure looks good!
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Validation Status */}
                    {validationIssues.length > 0 && (
                        <div className="mb-6">
                            <h3 className="text-lg font-semibold text-gray-900 mb-3">Validation Issues</h3>
                            <div className="space-y-2">
                                {validationIssues.slice(0, 3).map((issue, index) => (
                                    <div key={index} className="p-3 bg-red-50 border border-red-200 rounded-lg">
                                        <div className="flex gap-2">
                                            <span className="text-red-600 flex-shrink-0">⚠️</span>
                                            <span className="text-sm text-gray-700">{issue}</span>
                                        </div>
                                    </div>
                                ))}
                                {validationIssues.length > 3 && (
                                    <button
                                        onClick={() => setValidationDialogOpen(true)}
                                        className="w-full px-3 py-2 bg-red-100 text-red-800 text-sm rounded hover:bg-red-200"
                                    >
                                        View all {validationIssues.length} issues
                                    </button>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Add Resource Dialog */}
            {addResourceDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Add Cloud Resource</h2>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Resource Name</label>
                                <input
                                    type="text"
                                    value={newResourceName}
                                    onChange={(e) => setNewResourceName(e.target.value)}
                                    placeholder="e.g., web-server-01"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                    autoFocus
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
                                <select
                                    value={newResourceCategory}
                                    onChange={(e) => setNewResourceCategory(e.target.value as ResourceCategory)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                >
                                    {Object.entries(CATEGORIES).map(([category, config]) => (
                                        <option key={category} value={category}>
                                            {config.icon} {config.label}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Resource Type</label>
                                <select
                                    value={newResourceType}
                                    onChange={(e) => setNewResourceType(e.target.value as ResourceType)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                >
                                    {getResourceTypesForCategory(selectedProvider, newResourceCategory).map((type) => (
                                        <option key={type} value={type}>
                                            {type}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">
                                    Configuration (JSON, Optional)
                                </label>
                                <textarea
                                    value={newResourceConfig}
                                    onChange={(e) => setNewResourceConfig(e.target.value)}
                                    placeholder='{"size": "t2.micro", "storage": "20GB"}'
                                    rows={3}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 font-mono text-sm"
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-2 mt-6">
                            <button
                                onClick={() => setAddResourceDialogOpen(false)}
                                className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleAddResource}
                                disabled={!newResourceName.trim()}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Add Resource
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Export Dialog */}
            {exportDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Export Infrastructure as Code</h2>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-2">Export Format</label>
                                <div className="space-y-2">
                                    <button
                                        onClick={() => setExportFormat('terraform')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'terraform'
                                            ? 'bg-purple-50 border-purple-600'
                                            : 'bg-white border-gray-300 hover:border-purple-400'
                                            }`}
                                    >
                                        <div className="font-medium">Terraform (HCL)</div>
                                        <div className="text-xs text-gray-600">Multi-cloud, most popular</div>
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('cloudformation')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'cloudformation'
                                            ? 'bg-orange-50 border-orange-600'
                                            : 'bg-white border-gray-300 hover:border-orange-400'
                                            }`}
                                    >
                                        <div className="font-medium">AWS CloudFormation (YAML)</div>
                                        <div className="text-xs text-gray-600">Native AWS support</div>
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('pulumi')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'pulumi'
                                            ? 'bg-indigo-50 border-indigo-600'
                                            : 'bg-white border-gray-300 hover:border-indigo-400'
                                            }`}
                                    >
                                        <div className="font-medium">Pulumi (TypeScript)</div>
                                        <div className="text-xs text-gray-600">Code-based infrastructure</div>
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('arm')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'arm'
                                            ? 'bg-sky-50 border-sky-600'
                                            : 'bg-white border-gray-300 hover:border-sky-400'
                                            }`}
                                    >
                                        <div className="font-medium">Azure ARM Template (JSON)</div>
                                        <div className="text-xs text-gray-600">Native Azure support</div>
                                    </button>
                                </div>
                            </div>

                            <div className="p-3 bg-gray-50 rounded-lg">
                                <div className="text-sm text-gray-700">
                                    <div className="font-medium mb-1">Summary:</div>
                                    <div>• {getResourceCount()} Resources</div>
                                    <div>• ${costEstimate.totalMonthlyCost.toFixed(2)}/month estimated cost</div>
                                    <div>• {complianceReport.compliantPercentage}% compliance</div>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end gap-2 mt-6">
                            <button
                                onClick={() => setExportDialogOpen(false)}
                                className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleExport}
                                disabled={resources.length === 0}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Export
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Validation Dialog */}
            {validationDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-lg w-full mx-4 max-h-[80vh] overflow-y-auto">
                        <h2 className="text-xl font-semibold mb-4">Infrastructure Validation</h2>

                        {validationIssues.length === 0 ? (
                            <div className="p-4 bg-green-50 border border-green-200 rounded-lg text-center">
                                <div className="text-4xl mb-2">✓</div>
                                <div className="text-green-800 font-medium">Infrastructure is valid!</div>
                                <div className="text-sm text-green-600 mt-1">No issues found</div>
                            </div>
                        ) : (
                            <div className="space-y-2">
                                {validationIssues.map((issue, index) => (
                                    <div key={index} className="p-3 bg-red-50 border border-red-200 rounded-lg flex gap-2">
                                        <span className="text-red-600 flex-shrink-0">⚠️</span>
                                        <span className="text-sm text-gray-700">{issue}</span>
                                    </div>
                                ))}
                            </div>
                        )}

                        <div className="flex justify-end mt-6">
                            <button
                                onClick={() => setValidationDialogOpen(false)}
                                className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700"
                            >
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Cost Breakdown Dialog */}
            {costBreakdownDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-lg w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Cost Breakdown</h2>

                        <div className="space-y-4">
                            <div className="bg-gradient-to-r from-green-500 to-green-600 rounded-lg p-4 text-white">
                                <div className="text-sm opacity-90">Total Monthly Cost</div>
                                <div className="text-4xl font-bold">${costEstimate.totalMonthlyCost.toFixed(2)}</div>
                                <div className="text-sm opacity-90 mt-1">
                                    Annual: ${(costEstimate.totalMonthlyCost * 12).toFixed(2)}
                                </div>
                            </div>

                            <div>
                                <h3 className="text-sm font-semibold text-gray-900 mb-2">By Category</h3>
                                <div className="space-y-2">
                                    {Object.entries(costEstimate.breakdown)
                                        .sort(([, a], [, b]) => b - a)
                                        .map(([category, cost]) => {
                                            const percentage = (cost / costEstimate.totalMonthlyCost) * 100;
                                            return (
                                                <div key={category}>
                                                    <div className="flex justify-between text-sm mb-1">
                                                        <span className="text-gray-700 capitalize">{category}</span>
                                                        <span className="font-medium">${cost.toFixed(2)} ({percentage.toFixed(1)}%)</span>
                                                    </div>
                                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                                        <div
                                                            className="bg-green-600 h-2 rounded-full"
                                                            style={{ width: `${percentage}%` }}
                                                        />
                                                    </div>
                                                </div>
                                            );
                                        })}
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end mt-6">
                            <button
                                onClick={() => setCostBreakdownDialogOpen(false)}
                                className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700"
                            >
                                Close
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default CloudInfrastructureCanvas;

// Export types
export type {
    CloudProvider,
    CloudResource,
    ResourceCategory,
    ResourceType,
    Region,
};
