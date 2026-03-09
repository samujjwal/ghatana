/**
 * Zero-Trust Architecture Canvas Component
 * 
 * @doc.type component
 * @doc.purpose Security Architect - Zero-Trust Architecture (Journey 15.1)
 * @doc.layer product
 * @doc.pattern Canvas
 * 
 * Helps security architects design and implement zero-trust security architectures
 * with identity-centric security controls, microsegmentation, and least-privilege access.
 * 
 * Features:
 * - Security zone management (trust boundaries, DMZ, private networks)
 * - Identity provider integration (IdP, SSO, MFA)
 * - Policy engine configuration (attribute-based access control)
 * - Microsegmentation with network policies
 * - Device trust scoring and posture assessment
 * - Continuous verification workflows
 * - Threat detection and response integration
 * - Export to Terraform, Kubernetes NetworkPolicies, security diagrams
 * 
 * @example
 * ```tsx
 * <ZeroTrustArchitectureCanvas
 *   onExport={(config) => console.log(config)}
 * />
 * ```
 */

import React, { useState } from 'react';
import { useZeroTrustArchitecture } from '../hooks/useZeroTrustArchitecture';
import type {
    SecurityZone,
    IdentityProvider,
    PolicyRule,
    TrustLevel,
    SecurityZoneType,
} from '../hooks/useZeroTrustArchitecture';

/**
 * Component Props
 */
export interface ZeroTrustArchitectureCanvasProps {
    /**
     * Callback when architecture is exported
     */
    onExport?: (config: string, format: 'terraform' | 'k8s' | 'diagram') => void;

    /**
     * Callback when architecture is validated
     */
    onValidate?: (issues: string[]) => void;
}

/**
 * Zero-Trust Architecture Canvas Component
 */
export const ZeroTrustArchitectureCanvas: React.FC<ZeroTrustArchitectureCanvasProps> = ({
    onExport,
    onValidate,
}) => {
    const {
        // State
        architectureName,
        setArchitectureName,
        securityZones,
        identityProviders,
        policyRules,

        // Zone operations
        addSecurityZone,
        updateSecurityZone,
        deleteSecurityZone,
        getSecurityZone,
        getSecurityZoneCount,

        // Identity provider operations
        addIdentityProvider,
        updateIdentityProvider,
        deleteIdentityProvider,
        getIdentityProvider,
        getIdentityProviderCount,

        // Policy operations
        addPolicyRule,
        updatePolicyRule,
        deletePolicyRule,
        getPolicyRule,
        getPolicyRuleCount,

        // Analysis
        validateArchitecture,
        calculateTrustScore,
        analyzeCoverage,
        generateRecommendations,

        // Export
        exportToTerraform,
        exportToKubernetes,
        exportToDiagram,
    } = useZeroTrustArchitecture();

    // Dialog states
    const [addZoneDialogOpen, setAddZoneDialogOpen] = useState(false);
    const [addIdpDialogOpen, setAddIdpDialogOpen] = useState(false);
    const [addPolicyDialogOpen, setAddPolicyDialogOpen] = useState(false);
    const [exportDialogOpen, setExportDialogOpen] = useState(false);
    const [validationDialogOpen, setValidationDialogOpen] = useState(false);

    // Form states
    const [newZoneName, setNewZoneName] = useState('');
    const [newZoneType, setNewZoneType] = useState<SecurityZoneType>('private');
    const [newZoneDescription, setNewZoneDescription] = useState('');
    const [newZoneTrustLevel, setNewZoneTrustLevel] = useState<TrustLevel>('medium');

    const [newIdpName, setNewIdpName] = useState('');
    const [newIdpType, setNewIdpType] = useState<'saml' | 'oauth' | 'oidc'>('oidc');
    const [newIdpEndpoint, setNewIdpEndpoint] = useState('');

    const [newPolicyName, setNewPolicyName] = useState('');
    const [newPolicySource, setNewPolicySource] = useState('');
    const [newPolicyDestination, setNewPolicyDestination] = useState('');
    const [newPolicyAction, setNewPolicyAction] = useState<'allow' | 'deny'>('deny');

    const [exportFormat, setExportFormat] = useState<'terraform' | 'k8s' | 'diagram'>('terraform');
    const [selectedZoneId, setSelectedZoneId] = useState<string | null>(null);

    /**
     * Zone type configurations
     */
    const ZONE_TYPES: Record<SecurityZoneType, { label: string; color: string; icon: string }> = {
        public: { label: 'Public Zone', color: 'bg-red-100 border-red-300 text-red-800', icon: '🌐' },
        dmz: { label: 'DMZ', color: 'bg-orange-100 border-orange-300 text-orange-800', icon: '🛡️' },
        private: { label: 'Private Network', color: 'bg-blue-100 border-blue-300 text-blue-800', icon: '🔒' },
        restricted: { label: 'Restricted', color: 'bg-purple-100 border-purple-300 text-purple-800', icon: '🚫' },
        management: { label: 'Management', color: 'bg-green-100 border-green-300 text-green-800', icon: '⚙️' },
    };

    /**
     * Trust level configurations
     */
    const TRUST_LEVELS: Record<TrustLevel, { label: string; color: string; score: number }> = {
        untrusted: { label: 'Untrusted', color: 'text-red-600', score: 0 },
        low: { label: 'Low Trust', color: 'text-orange-600', score: 25 },
        medium: { label: 'Medium Trust', color: 'text-yellow-600', score: 50 },
        high: { label: 'High Trust', color: 'text-blue-600', score: 75 },
        verified: { label: 'Verified', color: 'text-green-600', score: 100 },
    };

    /**
     * Handle zone addition
     */
    const handleAddZone = () => {
        if (!newZoneName.trim()) return;

        addSecurityZone({
            name: newZoneName,
            type: newZoneType,
            description: newZoneDescription || undefined,
            trustLevel: newZoneTrustLevel,
            resources: [],
        });

        // Reset form
        setNewZoneName('');
        setNewZoneType('private');
        setNewZoneDescription('');
        setNewZoneTrustLevel('medium');
        setAddZoneDialogOpen(false);
    };

    /**
     * Handle IdP addition
     */
    const handleAddIdp = () => {
        if (!newIdpName.trim() || !newIdpEndpoint.trim()) return;

        addIdentityProvider({
            name: newIdpName,
            type: newIdpType,
            endpoint: newIdpEndpoint,
            mfaEnabled: true,
        });

        // Reset form
        setNewIdpName('');
        setNewIdpType('oidc');
        setNewIdpEndpoint('');
        setAddIdpDialogOpen(false);
    };

    /**
     * Handle policy addition
     */
    const handleAddPolicy = () => {
        if (!newPolicyName.trim() || !newPolicySource.trim() || !newPolicyDestination.trim()) return;

        addPolicyRule({
            name: newPolicyName,
            sourceZone: newPolicySource,
            destinationZone: newPolicyDestination,
            action: newPolicyAction,
            conditions: [],
        });

        // Reset form
        setNewPolicyName('');
        setNewPolicySource('');
        setNewPolicyDestination('');
        setNewPolicyAction('deny');
        setAddPolicyDialogOpen(false);
    };

    /**
     * Handle validation
     */
    const handleValidate = () => {
        const issues = validateArchitecture();
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
            case 'k8s':
                exportedConfig = exportToKubernetes();
                break;
            case 'diagram':
                exportedConfig = exportToDiagram();
                break;
            default:
                exportedConfig = exportToTerraform();
        }

        onExport?.(exportedConfig, exportFormat);
        setExportDialogOpen(false);
    };

    // Calculate metrics
    const trustScore = calculateTrustScore();
    const coverage = analyzeCoverage();
    const recommendations = generateRecommendations();
    const validationIssues = validateArchitecture();

    return (
        <div className="h-full flex flex-col bg-gray-50">
            {/* Toolbar */}
            <div className="bg-white border-b border-gray-200 p-4">
                <div className="flex items-center gap-4">
                    <span className="text-2xl">🔐</span>
                    <input
                        type="text"
                        value={architectureName}
                        onChange={(e) => setArchitectureName(e.target.value)}
                        placeholder="Zero-Trust Architecture Name"
                        className="flex-1 text-xl font-semibold border-none outline-none focus:ring-2 focus:ring-blue-500 rounded px-2"
                    />

                    <div className="flex items-center gap-2">
                        <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm font-medium">
                            🛡️ {getSecurityZoneCount()} Zones
                        </span>
                        <span className="px-3 py-1 bg-green-100 text-green-800 rounded-full text-sm font-medium">
                            👤 {getIdentityProviderCount()} IdPs
                        </span>
                        <span className="px-3 py-1 bg-purple-100 text-purple-800 rounded-full text-sm font-medium">
                            📋 {getPolicyRuleCount()} Policies
                        </span>
                    </div>

                    <button
                        onClick={handleValidate}
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                        title="Validate Architecture"
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
                {/* Left Panel - Security Zones */}
                <div className="w-80 bg-white border-r border-gray-200 p-4 overflow-y-auto">
                    <div className="mb-4">
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="text-lg font-semibold text-gray-900">Security Zones</h3>
                            <button
                                onClick={() => setAddZoneDialogOpen(true)}
                                className="px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
                            >
                                + Add Zone
                            </button>
                        </div>

                        <div className="space-y-2">
                            {Object.entries(ZONE_TYPES).map(([type, config]) => {
                                const zonesOfType = securityZones.filter((z) => z.type === type);
                                if (zonesOfType.length === 0) return null;

                                return (
                                    <div key={type}>
                                        <div className="text-xs font-medium text-gray-500 mb-1 uppercase">
                                            {config.icon} {config.label} ({zonesOfType.length})
                                        </div>
                                        {zonesOfType.map((zone) => (
                                            <div
                                                key={zone.id}
                                                className={`p-3 border-2 rounded-lg cursor-pointer transition-all ${config.color
                                                    } ${selectedZoneId === zone.id ? 'ring-2 ring-blue-500' : ''}`}
                                                onClick={() => setSelectedZoneId(zone.id)}
                                            >
                                                <div className="flex justify-between items-start">
                                                    <div className="flex-1">
                                                        <div className="font-medium">{zone.name}</div>
                                                        {zone.description && (
                                                            <div className="text-xs opacity-75 mt-1">{zone.description}</div>
                                                        )}
                                                        <div className={`text-xs mt-1 font-medium ${TRUST_LEVELS[zone.trustLevel].color}`}>
                                                            Trust: {TRUST_LEVELS[zone.trustLevel].label}
                                                        </div>
                                                    </div>
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            deleteSecurityZone(zone.id);
                                                        }}
                                                        className="text-red-600 hover:text-red-800"
                                                        title="Delete Zone"
                                                    >
                                                        ×
                                                    </button>
                                                </div>
                                                {zone.resources && zone.resources.length > 0 && (
                                                    <div className="mt-2 flex flex-wrap gap-1">
                                                        {zone.resources.slice(0, 3).map((resource, idx) => (
                                                            <span key={idx} className="px-2 py-0.5 bg-white bg-opacity-50 rounded text-xs">
                                                                {resource}
                                                            </span>
                                                        ))}
                                                        {zone.resources.length > 3 && (
                                                            <span className="px-2 py-0.5 bg-white bg-opacity-50 rounded text-xs">
                                                                +{zone.resources.length - 3}
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        ))}
                                    </div>
                                );
                            })}
                        </div>

                        {securityZones.length === 0 && (
                            <div className="p-4 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-800">
                                Add security zones to define trust boundaries
                            </div>
                        )}
                    </div>

                    {/* Identity Providers Section */}
                    <div className="mt-6 pt-6 border-t border-gray-200">
                        <div className="flex justify-between items-center mb-3">
                            <h3 className="text-lg font-semibold text-gray-900">Identity Providers</h3>
                            <button
                                onClick={() => setAddIdpDialogOpen(true)}
                                className="px-3 py-1 bg-green-600 text-white text-sm rounded hover:bg-green-700"
                            >
                                + Add IdP
                            </button>
                        </div>

                        <div className="space-y-2">
                            {identityProviders.map((idp) => (
                                <div key={idp.id} className="p-3 bg-green-50 border border-green-200 rounded-lg">
                                    <div className="flex justify-between items-start">
                                        <div className="flex-1">
                                            <div className="font-medium text-gray-900">{idp.name}</div>
                                            <div className="text-xs text-gray-600 mt-1">{idp.type.toUpperCase()}</div>
                                            <div className="text-xs text-gray-500 mt-1 truncate">{idp.endpoint}</div>
                                            {idp.mfaEnabled && (
                                                <span className="inline-block mt-1 px-2 py-0.5 bg-green-600 text-white text-xs rounded">
                                                    MFA ✓
                                                </span>
                                            )}
                                        </div>
                                        <button
                                            onClick={() => deleteIdentityProvider(idp.id)}
                                            className="text-red-600 hover:text-red-800"
                                            title="Delete IdP"
                                        >
                                            ×
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>

                        {identityProviders.length === 0 && (
                            <div className="p-4 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800">
                                Add identity providers for authentication
                            </div>
                        )}
                    </div>
                </div>

                {/* Center Panel - Policy Rules */}
                <div className="flex-1 p-6 overflow-y-auto">
                    <div className="max-w-4xl mx-auto">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-xl font-semibold text-gray-900">Policy Rules</h3>
                            <button
                                onClick={() => setAddPolicyDialogOpen(true)}
                                className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700"
                            >
                                + Add Policy
                            </button>
                        </div>

                        <div className="space-y-3">
                            {policyRules.map((policy) => {
                                const sourceZone = getSecurityZone(policy.sourceZone);
                                const destZone = getSecurityZone(policy.destinationZone);

                                return (
                                    <div key={policy.id} className="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow">
                                        <div className="flex justify-between items-start">
                                            <div className="flex-1">
                                                <div className="flex items-center gap-3">
                                                    <span className="font-medium text-gray-900">{policy.name}</span>
                                                    <span className={`px-2 py-1 text-xs font-medium rounded ${policy.action === 'allow'
                                                            ? 'bg-green-100 text-green-800'
                                                            : 'bg-red-100 text-red-800'
                                                        }`}>
                                                        {policy.action === 'allow' ? '✓ Allow' : '✗ Deny'}
                                                    </span>
                                                </div>

                                                <div className="mt-2 flex items-center gap-2 text-sm">
                                                    <span className="px-2 py-1 bg-gray-100 rounded">
                                                        {sourceZone?.name || policy.sourceZone}
                                                    </span>
                                                    <span className="text-gray-400">→</span>
                                                    <span className="px-2 py-1 bg-gray-100 rounded">
                                                        {destZone?.name || policy.destinationZone}
                                                    </span>
                                                </div>

                                                {policy.conditions && policy.conditions.length > 0 && (
                                                    <div className="mt-2 flex flex-wrap gap-1">
                                                        {policy.conditions.map((condition, idx) => (
                                                            <span key={idx} className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded">
                                                                {condition}
                                                            </span>
                                                        ))}
                                                    </div>
                                                )}
                                            </div>

                                            <button
                                                onClick={() => deletePolicyRule(policy.id)}
                                                className="text-red-600 hover:text-red-800"
                                                title="Delete Policy"
                                            >
                                                ×
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {policyRules.length === 0 && (
                            <div className="p-8 bg-purple-50 border border-purple-200 rounded-lg text-center">
                                <div className="text-4xl mb-2">📋</div>
                                <div className="text-gray-700">No policy rules defined</div>
                                <div className="text-sm text-gray-500 mt-1">Add policies to control access between zones</div>
                            </div>
                        )}
                    </div>
                </div>

                {/* Right Panel - Metrics & Recommendations */}
                <div className="w-80 bg-white border-l border-gray-200 p-4 overflow-y-auto">
                    {/* Trust Score */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Trust Score</h3>
                        <div className="bg-gradient-to-r from-blue-500 to-blue-600 rounded-lg p-4 text-white">
                            <div className="text-3xl font-bold">{trustScore.overall}/100</div>
                            <div className="text-sm opacity-90 mt-1">{trustScore.rating}</div>
                        </div>

                        <div className="mt-3 space-y-2">
                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Identity</span>
                                <span className="font-medium">{trustScore.identityScore}/100</span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className="bg-blue-600 h-2 rounded-full"
                                    style={{ width: `${trustScore.identityScore}%` }}
                                />
                            </div>

                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Network</span>
                                <span className="font-medium">{trustScore.networkScore}/100</span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className="bg-green-600 h-2 rounded-full"
                                    style={{ width: `${trustScore.networkScore}%` }}
                                />
                            </div>

                            <div className="flex justify-between text-sm">
                                <span className="text-gray-600">Policy</span>
                                <span className="font-medium">{trustScore.policyScore}/100</span>
                            </div>
                            <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                    className="bg-purple-600 h-2 rounded-full"
                                    style={{ width: `${trustScore.policyScore}%` }}
                                />
                            </div>
                        </div>
                    </div>

                    {/* Coverage Analysis */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Coverage</h3>
                        <div className="space-y-2">
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">Zones Covered</span>
                                <span className="font-medium">{coverage.zonesWithPolicies}/{coverage.totalZones}</span>
                            </div>
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">IdP Integration</span>
                                <span className={`font-medium ${coverage.hasIdentityProvider ? 'text-green-600' : 'text-red-600'}`}>
                                    {coverage.hasIdentityProvider ? '✓' : '✗'}
                                </span>
                            </div>
                            <div className="flex justify-between items-center p-2 bg-gray-50 rounded">
                                <span className="text-sm text-gray-600">Policy Coverage</span>
                                <span className="font-medium">{coverage.policyPercentage}%</span>
                            </div>
                        </div>
                    </div>

                    {/* Recommendations */}
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-900 mb-3">Recommendations</h3>
                        <div className="space-y-2">
                            {recommendations.map((rec, index) => (
                                <div key={index} className="p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
                                    <div className="flex gap-2">
                                        <span className="text-yellow-600 flex-shrink-0">💡</span>
                                        <span className="text-sm text-gray-700">{rec}</span>
                                    </div>
                                </div>
                            ))}
                            {recommendations.length === 0 && (
                                <div className="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-800">
                                    ✓ No recommendations - architecture looks good!
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

            {/* Add Zone Dialog */}
            {addZoneDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Add Security Zone</h2>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Zone Name</label>
                                <input
                                    type="text"
                                    value={newZoneName}
                                    onChange={(e) => setNewZoneName(e.target.value)}
                                    placeholder="e.g., Web Application Tier"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                    autoFocus
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Zone Type</label>
                                <select
                                    value={newZoneType}
                                    onChange={(e) => setNewZoneType(e.target.value as SecurityZoneType)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                >
                                    {Object.entries(ZONE_TYPES).map(([type, config]) => (
                                        <option key={type} value={type}>
                                            {config.icon} {config.label}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Trust Level</label>
                                <select
                                    value={newZoneTrustLevel}
                                    onChange={(e) => setNewZoneTrustLevel(e.target.value as TrustLevel)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                >
                                    {Object.entries(TRUST_LEVELS).map(([level, config]) => (
                                        <option key={level} value={level}>
                                            {config.label} ({config.score})
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Description (Optional)</label>
                                <textarea
                                    value={newZoneDescription}
                                    onChange={(e) => setNewZoneDescription(e.target.value)}
                                    placeholder="Describe the security zone"
                                    rows={2}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-2 mt-6">
                            <button
                                onClick={() => setAddZoneDialogOpen(false)}
                                className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleAddZone}
                                disabled={!newZoneName.trim()}
                                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Add Zone
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Add IdP Dialog */}
            {addIdpDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Add Identity Provider</h2>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Provider Name</label>
                                <input
                                    type="text"
                                    value={newIdpName}
                                    onChange={(e) => setNewIdpName(e.target.value)}
                                    placeholder="e.g., Okta SSO"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
                                    autoFocus
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Protocol</label>
                                <select
                                    value={newIdpType}
                                    onChange={(e) => setNewIdpType(e.target.value as 'saml' | 'oauth' | 'oidc')}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
                                >
                                    <option value="oidc">OpenID Connect (OIDC)</option>
                                    <option value="oauth">OAuth 2.0</option>
                                    <option value="saml">SAML 2.0</option>
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Endpoint URL</label>
                                <input
                                    type="url"
                                    value={newIdpEndpoint}
                                    onChange={(e) => setNewIdpEndpoint(e.target.value)}
                                    placeholder="https://auth.example.com"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-green-500 focus:border-green-500"
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-2 mt-6">
                            <button
                                onClick={() => setAddIdpDialogOpen(false)}
                                className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleAddIdp}
                                disabled={!newIdpName.trim() || !newIdpEndpoint.trim()}
                                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Add Provider
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Add Policy Dialog */}
            {addPolicyDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Add Policy Rule</h2>

                        <div className="space-y-4">
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Policy Name</label>
                                <input
                                    type="text"
                                    value={newPolicyName}
                                    onChange={(e) => setNewPolicyName(e.target.value)}
                                    placeholder="e.g., Web to Database Access"
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                                    autoFocus
                                />
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Source Zone</label>
                                <select
                                    value={newPolicySource}
                                    onChange={(e) => setNewPolicySource(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                                >
                                    <option value="">Select source zone</option>
                                    {securityZones.map((zone) => (
                                        <option key={zone.id} value={zone.id}>
                                            {zone.name}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Destination Zone</label>
                                <select
                                    value={newPolicyDestination}
                                    onChange={(e) => setNewPolicyDestination(e.target.value)}
                                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-purple-500 focus:border-purple-500"
                                >
                                    <option value="">Select destination zone</option>
                                    {securityZones.map((zone) => (
                                        <option key={zone.id} value={zone.id}>
                                            {zone.name}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Action</label>
                                <div className="flex gap-2">
                                    <button
                                        onClick={() => setNewPolicyAction('allow')}
                                        className={`flex-1 px-4 py-2 rounded-lg border-2 transition-colors ${newPolicyAction === 'allow'
                                                ? 'bg-green-600 text-white border-green-600'
                                                : 'bg-white text-gray-700 border-gray-300 hover:border-green-500'
                                            }`}
                                    >
                                        ✓ Allow
                                    </button>
                                    <button
                                        onClick={() => setNewPolicyAction('deny')}
                                        className={`flex-1 px-4 py-2 rounded-lg border-2 transition-colors ${newPolicyAction === 'deny'
                                                ? 'bg-red-600 text-white border-red-600'
                                                : 'bg-white text-gray-700 border-gray-300 hover:border-red-500'
                                            }`}
                                    >
                                        ✗ Deny
                                    </button>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end gap-2 mt-6">
                            <button
                                onClick={() => setAddPolicyDialogOpen(false)}
                                className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleAddPolicy}
                                disabled={!newPolicyName.trim() || !newPolicySource || !newPolicyDestination}
                                className="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50 disabled:cursor-not-allowed"
                            >
                                Add Policy
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* Export Dialog */}
            {exportDialogOpen && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 max-w-md w-full mx-4">
                        <h2 className="text-xl font-semibold mb-4">Export Configuration</h2>

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
                                        <div className="font-medium">Terraform</div>
                                        <div className="text-xs text-gray-600">Infrastructure as Code</div>
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('k8s')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'k8s'
                                                ? 'bg-blue-50 border-blue-600'
                                                : 'bg-white border-gray-300 hover:border-blue-400'
                                            }`}
                                    >
                                        <div className="font-medium">Kubernetes NetworkPolicies</div>
                                        <div className="text-xs text-gray-600">YAML manifests</div>
                                    </button>
                                    <button
                                        onClick={() => setExportFormat('diagram')}
                                        className={`w-full px-4 py-3 rounded-lg border-2 text-left transition-colors ${exportFormat === 'diagram'
                                                ? 'bg-green-50 border-green-600'
                                                : 'bg-white border-gray-300 hover:border-green-400'
                                            }`}
                                    >
                                        <div className="font-medium">Architecture Diagram</div>
                                        <div className="text-xs text-gray-600">PlantUML / Mermaid</div>
                                    </button>
                                </div>
                            </div>

                            <div className="p-3 bg-gray-50 rounded-lg">
                                <div className="text-sm text-gray-700">
                                    <div className="font-medium mb-1">Summary:</div>
                                    <div>• {getSecurityZoneCount()} Security Zones</div>
                                    <div>• {getIdentityProviderCount()} Identity Providers</div>
                                    <div>• {getPolicyRuleCount()} Policy Rules</div>
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
                                disabled={securityZones.length === 0}
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
                        <h2 className="text-xl font-semibold mb-4">Architecture Validation</h2>

                        {validationIssues.length === 0 ? (
                            <div className="p-4 bg-green-50 border border-green-200 rounded-lg text-center">
                                <div className="text-4xl mb-2">✓</div>
                                <div className="text-green-800 font-medium">Architecture is valid!</div>
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
        </div>
    );
};

export default ZeroTrustArchitectureCanvas;

// Export types
export type {
    SecurityZone,
    IdentityProvider,
    PolicyRule,
    TrustLevel,
    SecurityZoneType,
};
