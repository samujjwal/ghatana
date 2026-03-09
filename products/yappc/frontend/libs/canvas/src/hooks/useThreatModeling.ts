/**
 * useThreatModeling Hook
 * 
 * State management for security threat modeling
 * 
 * @doc.type hook
 * @doc.purpose Threat modeling state management with STRIDE framework
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * STRIDE threat categories
 */
export type ThreatCategory =
    | 'spoofing'
    | 'tampering'
    | 'repudiation'
    | 'informationDisclosure'
    | 'denialOfService'
    | 'elevationOfPrivilege';

/**
 * Risk severity levels
 */
export type RiskLevel = 'critical' | 'high' | 'medium' | 'low';

/**
 * Mitigation status
 */
export type MitigationStatus = 'planned' | 'in-progress' | 'implemented';

/**
 * Asset types
 */
export type AssetType = 'data' | 'service' | 'infrastructure' | 'user';

/**
 * Mitigation strategy
 */
export interface Mitigation {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Mitigation strategy description
     */
    strategy: string;

    /**
     * Implementation details (optional)
     */
    implementation?: string;

    /**
     * Owner/responsible party (optional)
     */
    owner?: string;

    /**
     * Implementation status
     */
    status: MitigationStatus;
}

/**
 * Security threat
 */
export interface Threat {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Threat title
     */
    title: string;

    /**
     * Threat description
     */
    description: string;

    /**
     * STRIDE category
     */
    category: ThreatCategory;

    /**
     * Risk level (calculated or manual)
     */
    riskLevel: RiskLevel;

    /**
     * Affected asset (optional)
     */
    affectedAsset?: string;

    /**
     * Attack vector (optional)
     */
    attackVector?: string;

    /**
     * Mitigation strategies
     */
    mitigations?: Mitigation[];

    /**
     * Impact score (1-10)
     */
    impact?: number;

    /**
     * Likelihood score (1-10)
     */
    likelihood?: number;
}

/**
 * Protected asset
 */
export interface Asset {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Asset name
     */
    name: string;

    /**
     * Asset type
     */
    type: AssetType;

    /**
     * Asset description (optional)
     */
    description?: string;
}

/**
 * Options for useThreatModeling hook
 */
export interface UseThreatModelingOptions {
    /**
     * Initial model name
     */
    initialModelName?: string;

    /**
     * Initial system description
     */
    initialSystemDescription?: string;
}

/**
 * Result of useThreatModeling hook
 */
export interface UseThreatModelingResult {
    /**
     * All identified threats
     */
    threats: Threat[];

    /**
     * Protected assets
     */
    assets: Asset[];

    /**
     * Threat model name
     */
    modelName: string;

    /**
     * System description
     */
    systemDescription: string;

    /**
     * Set model name
     */
    setModelName: (name: string) => void;

    /**
     * Set system description
     */
    setSystemDescription: (description: string) => void;

    /**
     * Add a new threat
     */
    addThreat: (threat: Omit<Threat, 'id' | 'riskLevel' | 'mitigations'>) => string;

    /**
     * Update a threat
     */
    updateThreat: (threatId: string, updates: Partial<Omit<Threat, 'id'>>) => void;

    /**
     * Delete a threat
     */
    deleteThreat: (threatId: string) => void;

    /**
     * Get threat by ID
     */
    getThreat: (threatId: string) => Threat | undefined;

    /**
     * Add a new asset
     */
    addAsset: (asset: Omit<Asset, 'id'>) => string;

    /**
     * Delete an asset
     */
    deleteAsset: (assetId: string) => void;

    /**
     * Add mitigation to threat
     */
    addMitigation: (threatId: string, mitigation: Omit<Mitigation, 'id' | 'status'>) => void;

    /**
     * Update mitigation status
     */
    updateMitigationStatus: (threatId: string, mitigationId: string, status: MitigationStatus) => void;

    /**
     * Delete mitigation
     */
    deleteMitigation: (threatId: string, mitigationId: string) => void;

    /**
     * Calculate risk score for threat
     */
    calculateRiskScore: (threatId: string) => number;

    /**
     * Run STRIDE analysis
     */
    analyzeSTRIDE: () => {
        categoryBreakdown: Record<ThreatCategory, { count: number; mitigatedCount: number }>;
        unmitigatedThreats: Threat[];
        overallRisk: RiskLevel;
        averageRiskScore: number;
    };

    /**
     * Export threat model as JSON
     */
    exportModel: () => string;

    /**
     * Get total threat count
     */
    getThreatCount: () => number;

    /**
     * Get total asset count
     */
    getAssetCount: () => number;

    /**
     * Get total mitigation count
     */
    getMitigationCount: () => number;
}

/**
 * Threat modeling hook with STRIDE framework
 */
export const useThreatModeling = (options: UseThreatModelingOptions = {}): UseThreatModelingResult => {
    const { initialModelName = 'Threat Model', initialSystemDescription = '' } = options;

    // State
    const [threats, setThreats] = useState<Threat[]>([]);
    const [assets, setAssets] = useState<Asset[]>([]);
    const [modelName, setModelName] = useState(initialModelName);
    const [systemDescription, setSystemDescription] = useState(initialSystemDescription);

    // Generate unique ID
    const generateId = useCallback((prefix: string) => {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }, []);

    // Calculate risk level based on impact and likelihood
    const calculateRiskLevel = useCallback((impact?: number, likelihood?: number): RiskLevel => {
        if (!impact || !likelihood) return 'medium';

        const score = impact * likelihood;
        if (score >= 64) return 'critical';
        if (score >= 36) return 'high';
        if (score >= 16) return 'medium';
        return 'low';
    }, []);

    // Add threat
    const addThreat = useCallback((threat: Omit<Threat, 'id' | 'riskLevel' | 'mitigations'>): string => {
        const id = generateId('threat');
        const riskLevel = calculateRiskLevel(threat.impact, threat.likelihood);

        const newThreat: Threat = {
            ...threat,
            id,
            riskLevel,
            mitigations: [],
        };

        setThreats(prev => [...prev, newThreat]);
        return id;
    }, [generateId, calculateRiskLevel]);

    // Update threat
    const updateThreat = useCallback((threatId: string, updates: Partial<Omit<Threat, 'id'>>) => {
        setThreats(prev =>
            prev.map(threat => {
                if (threat.id !== threatId) return threat;

                const updated = { ...threat, ...updates };
                // Recalculate risk level if impact or likelihood changed
                if (updates.impact !== undefined || updates.likelihood !== undefined) {
                    updated.riskLevel = calculateRiskLevel(updated.impact, updated.likelihood);
                }
                return updated;
            })
        );
    }, [calculateRiskLevel]);

    // Delete threat
    const deleteThreat = useCallback((threatId: string) => {
        setThreats(prev => prev.filter(threat => threat.id !== threatId));
    }, []);

    // Get threat
    const getThreat = useCallback((threatId: string): Threat | undefined => {
        return threats.find(t => t.id === threatId);
    }, [threats]);

    // Add asset
    const addAsset = useCallback((asset: Omit<Asset, 'id'>): string => {
        const id = generateId('asset');
        const newAsset: Asset = {
            ...asset,
            id,
        };

        setAssets(prev => [...prev, newAsset]);
        return id;
    }, [generateId]);

    // Delete asset
    const deleteAsset = useCallback((assetId: string) => {
        setAssets(prev => prev.filter(asset => asset.id !== assetId));
    }, []);

    // Add mitigation
    const addMitigation = useCallback((threatId: string, mitigation: Omit<Mitigation, 'id' | 'status'>) => {
        const id = generateId('mitigation');
        const newMitigation: Mitigation = {
            ...mitigation,
            id,
            status: 'planned',
        };

        setThreats(prev =>
            prev.map(threat =>
                threat.id === threatId
                    ? { ...threat, mitigations: [...(threat.mitigations || []), newMitigation] }
                    : threat
            )
        );
    }, [generateId]);

    // Update mitigation status
    const updateMitigationStatus = useCallback((threatId: string, mitigationId: string, status: MitigationStatus) => {
        setThreats(prev =>
            prev.map(threat =>
                threat.id === threatId
                    ? {
                        ...threat,
                        mitigations: (threat.mitigations || []).map(m =>
                            m.id === mitigationId ? { ...m, status } : m
                        ),
                    }
                    : threat
            )
        );
    }, []);

    // Delete mitigation
    const deleteMitigation = useCallback((threatId: string, mitigationId: string) => {
        setThreats(prev =>
            prev.map(threat =>
                threat.id === threatId
                    ? {
                        ...threat,
                        mitigations: (threat.mitigations || []).filter(m => m.id !== mitigationId),
                    }
                    : threat
            )
        );
    }, []);

    // Calculate risk score
    const calculateRiskScore = useCallback((threatId: string): number => {
        const threat = threats.find(t => t.id === threatId);
        if (!threat) return 0;

        // If impact and likelihood are set, use them
        if (threat.impact && threat.likelihood) {
            return Math.round((threat.impact * threat.likelihood) / 10);
        }

        // Otherwise, calculate based on risk level and mitigations
        const baseScore = {
            critical: 10,
            high: 7,
            medium: 5,
            low: 3,
        }[threat.riskLevel];

        // Reduce score based on implemented mitigations
        const implementedCount = (threat.mitigations || []).filter(m => m.status === 'implemented').length;
        const totalMitigations = (threat.mitigations || []).length;

        if (totalMitigations === 0) return baseScore;

        const mitigationFactor = 1 - (implementedCount / totalMitigations) * 0.5; // Max 50% reduction
        return Math.max(1, Math.round(baseScore * mitigationFactor));
    }, [threats]);

    // STRIDE analysis
    const analyzeSTRIDE = useCallback(() => {
        const categories: ThreatCategory[] = [
            'spoofing',
            'tampering',
            'repudiation',
            'informationDisclosure',
            'denialOfService',
            'elevationOfPrivilege',
        ];

        const categoryBreakdown = categories.reduce((acc, category) => {
            const categoryThreats = threats.filter(t => t.category === category);
            const mitigatedCount = categoryThreats.filter(t =>
                (t.mitigations || []).some(m => m.status === 'implemented')
            ).length;

            acc[category] = {
                count: categoryThreats.length,
                mitigatedCount,
            };
            return acc;
        }, {} as Record<ThreatCategory, { count: number; mitigatedCount: number }>);

        // Find unmitigated threats
        const unmitigatedThreats = threats.filter(t =>
            !t.mitigations || t.mitigations.length === 0 || !t.mitigations.some(m => m.status === 'implemented')
        );

        // Calculate overall risk
        const totalScore = threats.reduce((sum, threat) => sum + calculateRiskScore(threat.id), 0);
        const averageRiskScore = threats.length > 0 ? totalScore / threats.length : 0;

        const overallRisk: RiskLevel =
            averageRiskScore >= 8
                ? 'critical'
                : averageRiskScore >= 6
                    ? 'high'
                    : averageRiskScore >= 4
                        ? 'medium'
                        : 'low';

        return {
            categoryBreakdown,
            unmitigatedThreats,
            overallRisk,
            averageRiskScore,
        };
    }, [threats, calculateRiskScore]);

    // Export model
    const exportModel = useCallback((): string => {
        const model = {
            name: modelName,
            description: systemDescription,
            threats: threats.map(threat => ({
                title: threat.title,
                description: threat.description,
                category: threat.category,
                riskLevel: threat.riskLevel,
                riskScore: calculateRiskScore(threat.id),
                affectedAsset: threat.affectedAsset,
                attackVector: threat.attackVector,
                impact: threat.impact,
                likelihood: threat.likelihood,
                mitigations: threat.mitigations?.map(m => ({
                    strategy: m.strategy,
                    implementation: m.implementation,
                    owner: m.owner,
                    status: m.status,
                })),
            })),
            assets: assets.map(asset => ({
                name: asset.name,
                type: asset.type,
                description: asset.description,
            })),
            analysis: analyzeSTRIDE(),
            metadata: {
                exportedAt: new Date().toISOString(),
                threatCount: getThreatCount(),
                assetCount: getAssetCount(),
                mitigationCount: getMitigationCount(),
            },
        };

        return JSON.stringify(model, null, 2);
    }, [modelName, systemDescription, threats, assets, calculateRiskScore, analyzeSTRIDE]);

    // Get threat count
    const getThreatCount = useCallback((): number => {
        return threats.length;
    }, [threats]);

    // Get asset count
    const getAssetCount = useCallback((): number => {
        return assets.length;
    }, [assets]);

    // Get mitigation count
    const getMitigationCount = useCallback((): number => {
        return threats.reduce((count, threat) => count + (threat.mitigations?.length || 0), 0);
    }, [threats]);

    return {
        threats,
        assets,
        modelName,
        systemDescription,
        setModelName,
        setSystemDescription,
        addThreat,
        updateThreat,
        deleteThreat,
        getThreat,
        addAsset,
        deleteAsset,
        addMitigation,
        updateMitigationStatus,
        deleteMitigation,
        calculateRiskScore,
        analyzeSTRIDE,
        exportModel,
        getThreatCount,
        getAssetCount,
        getMitigationCount,
    };
};
