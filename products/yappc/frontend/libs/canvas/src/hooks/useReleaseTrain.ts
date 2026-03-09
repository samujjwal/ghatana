/**
 * Release Train Hook
 * 
 * @doc.type hook
 * @doc.purpose State management for multi-team release orchestration with dependency tracking
 * @doc.layer product
 * @doc.pattern Custom React Hook
 * 
 * Provides comprehensive release train management including:
 * - Team management (CRUD operations)
 * - Feature tracking with status management
 * - Dependency management between features
 * - Blocker tracking and impact analysis
 * - Critical path calculation (longest dependency chain)
 * - Timeline estimation based on team velocity
 * - Release readiness checks
 * - Risk assessment per feature and overall release
 * 
 * @example
 * ```tsx
 * const {
 *   teams,
 *   features,
 *   addFeature,
 *   calculateCriticalPath,
 *   getReleaseRisk
 * } = useReleaseTrain();
 * ```
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Feature status in the release train
 */
export type FeatureStatus = 'not-started' | 'in-progress' | 'testing' | 'blocked' | 'done';

/**
 * Type of blocker affecting a feature
 */
export type BlockerType = 'technical' | 'dependency' | 'resource' | 'external';

/**
 * Type of readiness check
 */
export type ReadinessCheckType = 'tests' | 'security' | 'docs' | 'performance' | 'accessibility';

/**
 * Risk level for features and release
 */
export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

/**
 * Team in the release train
 */
export interface Team {
    /**
     * Unique team identifier
     */
    id: string;

    /**
     * Team name
     */
    name: string;

    /**
     * Team velocity (story points per week)
     */
    velocity: number;

    /**
     * Team capacity (percentage, 0-100)
     */
    capacity: number;
}

/**
 * Feature to be released
 */
export interface Feature {
    /**
     * Unique feature identifier
     */
    id: string;

    /**
     * Feature title
     */
    title: string;

    /**
     * Feature description
     */
    description: string;

    /**
     * Team responsible for this feature
     */
    teamId: string;

    /**
     * Current status
     */
    status: FeatureStatus;

    /**
     * Estimated days to complete
     */
    estimatedDays: number;

    /**
     * Actual days spent so far
     */
    actualDays: number;

    /**
     * Feature dependencies (IDs of features this depends on)
     */
    dependencies: string[];

    /**
     * Feature blockers
     */
    blockers: Blocker[];

    /**
     * Start date (optional)
     */
    startDate?: Date;

    /**
     * Target completion date (optional)
     */
    targetDate?: Date;

    /**
     * Actual completion date (optional)
     */
    completionDate?: Date;
}

/**
 * Blocker preventing feature progress
 */
export interface Blocker {
    /**
     * Unique blocker identifier
     */
    id: string;

    /**
     * Feature this blocker affects
     */
    featureId: string;

    /**
     * Type of blocker
     */
    type: BlockerType;

    /**
     * Blocker description
     */
    description: string;

    /**
     * When the blocker was created
     */
    createdAt: Date;

    /**
     * When the blocker was resolved (optional)
     */
    resolvedAt?: Date;
}

/**
 * Feature dependency relationship
 */
export interface Dependency {
    /**
     * Unique dependency identifier
     */
    id: string;

    /**
     * Feature that has the dependency
     */
    featureId: string;

    /**
     * Feature that this feature depends on
     */
    dependsOnFeatureId: string;
}

/**
 * Readiness check for a feature
 */
export interface ReadinessCheck {
    /**
     * Type of check
     */
    type: ReadinessCheckType;

    /**
     * Whether the check passed
     */
    passed: boolean;

    /**
     * Optional notes
     */
    notes?: string;
}

/**
 * Return type of useReleaseTrain hook
 */
export interface UseReleaseTrainReturn {
    // State
    release: string;
    setRelease: (release: string) => void;
    selectedTeam: string | null;
    setSelectedTeam: (teamId: string | null) => void;
    selectedFeature: string | null;
    setSelectedFeature: (featureId: string | null) => void;

    // Team Management
    getTeams: () => Team[];
    addTeam: (team: Omit<Team, 'id'>) => string;
    updateTeam: (id: string, updates: Partial<Team>) => void;
    deleteTeam: (id: string) => void;

    // Feature Management
    getFeatures: () => Feature[];
    getFeaturesByTeam: (teamId: string) => Feature[];
    getFeaturesByStatus: (status: FeatureStatus) => Feature[];
    addFeature: (feature: Omit<Feature, 'id'>) => string;
    updateFeature: (id: string, updates: Partial<Feature>) => void;
    deleteFeature: (id: string) => void;

    // Dependency Management
    addDependency: (featureId: string, dependsOnFeatureId: string) => string;
    removeDependency: (dependencyId: string) => void;
    getDependencies: () => Dependency[];
    getFeatureDependencies: (featureId: string) => Dependency[];

    // Blocker Management
    addBlocker: (blocker: Omit<Blocker, 'id' | 'createdAt'>) => string;
    removeBlocker: (blockerId: string) => void;
    getBlockers: () => Blocker[];
    getFeatureBlockers: (featureId: string) => Blocker[];

    // Readiness Checks
    updateReadinessCheck: (featureId: string, check: ReadinessCheck) => void;
    getReadinessChecks: (featureId: string) => ReadinessCheck[];
    isFeatureReady: (featureId: string) => boolean;

    // Timeline & Path Analysis
    calculateCriticalPath: () => string[];
    getFeatureTimeline: (featureId: string) => { start: Date; end: Date } | null;
    estimateReleaseDate: () => Date | null;
    getTeamVelocity: (teamId: string) => number;

    // Risk Assessment
    calculateFeatureRisk: (featureId: string) => RiskLevel;
    getReleaseRisk: () => RiskLevel;
}

/**
 * Release Train Hook
 * 
 * Provides state management and operations for multi-team release orchestration
 * including team management, feature tracking, dependency management, blocker tracking,
 * critical path calculation, and risk assessment.
 * 
 * @returns Release train state and operations
 */
export const useReleaseTrain = (): UseReleaseTrainReturn => {
    // State
    const [release, setRelease] = useState<string>('Q1 2025 Release');
    const [selectedTeam, setSelectedTeam] = useState<string | null>(null);
    const [selectedFeature, setSelectedFeature] = useState<string | null>(null);
    const [teams, setTeams] = useState<Team[]>([]);
    const [features, setFeatures] = useState<Feature[]>([]);
    const [dependencies, setDependencies] = useState<Dependency[]>([]);
    const [blockers, setBlockers] = useState<Blocker[]>([]);
    const [readinessChecks, setReadinessChecks] = useState<Map<string, ReadinessCheck[]>>(new Map());

    // Team Management
    const getTeams = useCallback((): Team[] => {
        return teams;
    }, [teams]);

    const addTeam = useCallback((team: Omit<Team, 'id'>): string => {
        const id = `team-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newTeam: Team = {
            id,
            ...team,
        };
        setTeams((prev) => [...prev, newTeam]);
        return id;
    }, []);

    const updateTeam = useCallback((id: string, updates: Partial<Team>): void => {
        setTeams((prev) =>
            prev.map((team) => (team.id === id ? { ...team, ...updates } : team))
        );
    }, []);

    const deleteTeam = useCallback((id: string): void => {
        setTeams((prev) => prev.filter((team) => team.id !== id));
        // Also delete features belonging to this team
        setFeatures((prev) => prev.filter((feature) => feature.teamId !== id));
    }, []);

    // Feature Management
    const getFeatures = useCallback((): Feature[] => {
        return features;
    }, [features]);

    const getFeaturesByTeam = useCallback((teamId: string): Feature[] => {
        return features.filter((feature) => feature.teamId === teamId);
    }, [features]);

    const getFeaturesByStatus = useCallback((status: FeatureStatus): Feature[] => {
        return features.filter((feature) => feature.status === status);
    }, [features]);

    const addFeature = useCallback((feature: Omit<Feature, 'id'>): string => {
        const id = `feature-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newFeature: Feature = {
            id,
            ...feature,
        };
        setFeatures((prev) => [...prev, newFeature]);

        // Initialize readiness checks
        const checks: ReadinessCheck[] = [
            { type: 'tests', passed: false },
            { type: 'security', passed: false },
            { type: 'docs', passed: false },
            { type: 'performance', passed: false },
            { type: 'accessibility', passed: false },
        ];
        setReadinessChecks((prev) => new Map(prev).set(id, checks));

        return id;
    }, []);

    const updateFeature = useCallback((id: string, updates: Partial<Feature>): void => {
        setFeatures((prev) =>
            prev.map((feature) => (feature.id === id ? { ...feature, ...updates } : feature))
        );
    }, []);

    const deleteFeature = useCallback((id: string): void => {
        setFeatures((prev) => prev.filter((feature) => feature.id !== id));
        // Also delete dependencies and blockers
        setDependencies((prev) =>
            prev.filter((dep) => dep.featureId !== id && dep.dependsOnFeatureId !== id)
        );
        setBlockers((prev) => prev.filter((blocker) => blocker.featureId !== id));
        setReadinessChecks((prev) => {
            const newMap = new Map(prev);
            newMap.delete(id);
            return newMap;
        });
    }, []);

    // Dependency Management
    const addDependency = useCallback((featureId: string, dependsOnFeatureId: string): string => {
        const id = `dep-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newDependency: Dependency = {
            id,
            featureId,
            dependsOnFeatureId,
        };
        setDependencies((prev) => [...prev, newDependency]);
        return id;
    }, []);

    const removeDependency = useCallback((dependencyId: string): void => {
        setDependencies((prev) => prev.filter((dep) => dep.id !== dependencyId));
    }, []);

    const getDependencies = useCallback((): Dependency[] => {
        return dependencies;
    }, [dependencies]);

    const getFeatureDependencies = useCallback((featureId: string): Dependency[] => {
        return dependencies.filter((dep) => dep.featureId === featureId);
    }, [dependencies]);

    // Blocker Management
    const addBlocker = useCallback((blocker: Omit<Blocker, 'id' | 'createdAt'>): string => {
        const id = `blocker-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newBlocker: Blocker = {
            id,
            ...blocker,
            createdAt: new Date(),
        };
        setBlockers((prev) => [...prev, newBlocker]);

        // Auto-update feature status to blocked
        setFeatures((prev) =>
            prev.map((feature) =>
                feature.id === blocker.featureId ? { ...feature, status: 'blocked' } : feature
            )
        );

        return id;
    }, []);

    const removeBlocker = useCallback((blockerId: string): void => {
        const blocker = blockers.find((b) => b.id === blockerId);
        if (!blocker) return;

        setBlockers((prev) => prev.filter((b) => b.id !== blockerId));

        // Check if feature still has blockers, if not, update status
        const remainingBlockers = blockers.filter(
            (b) => b.featureId === blocker.featureId && b.id !== blockerId
        );

        if (remainingBlockers.length === 0) {
            setFeatures((prev) =>
                prev.map((feature) =>
                    feature.id === blocker.featureId && feature.status === 'blocked'
                        ? { ...feature, status: 'in-progress' }
                        : feature
                )
            );
        }
    }, [blockers]);

    const getBlockers = useCallback((): Blocker[] => {
        return blockers;
    }, [blockers]);

    const getFeatureBlockers = useCallback((featureId: string): Blocker[] => {
        return blockers.filter((blocker) => blocker.featureId === featureId && !blocker.resolvedAt);
    }, [blockers]);

    // Readiness Checks
    const updateReadinessCheck = useCallback((featureId: string, check: ReadinessCheck): void => {
        setReadinessChecks((prev) => {
            const checks = prev.get(featureId) || [];
            const updatedChecks = checks.map((c) =>
                c.type === check.type ? check : c
            );
            const newMap = new Map(prev);
            newMap.set(featureId, updatedChecks);
            return newMap;
        });
    }, []);

    const getReadinessChecks = useCallback((featureId: string): ReadinessCheck[] => {
        return readinessChecks.get(featureId) || [];
    }, [readinessChecks]);

    const isFeatureReady = useCallback((featureId: string): boolean => {
        const checks = readinessChecks.get(featureId) || [];
        return checks.every((check) => check.passed);
    }, [readinessChecks]);

    // Timeline & Path Analysis
    const calculateCriticalPath = useCallback((): string[] => {
        if (features.length === 0) return [];

        // Build dependency graph
        const graph = new Map<string, string[]>();
        features.forEach((feature) => {
            graph.set(feature.id, []);
        });
        dependencies.forEach((dep) => {
            const dependents = graph.get(dep.dependsOnFeatureId) || [];
            dependents.push(dep.featureId);
            graph.set(dep.dependsOnFeatureId, dependents);
        });

        // Find longest path using DFS
        let longestPath: string[] = [];

        const dfs = (featureId: string, path: string[], visited: Set<string>): void => {
            if (visited.has(featureId)) return; // Cycle detection

            visited.add(featureId);
            path.push(featureId);

            if (path.length > longestPath.length) {
                longestPath = [...path];
            }

            const dependents = graph.get(featureId) || [];
            dependents.forEach((dependent) => {
                dfs(dependent, path, visited);
            });

            path.pop();
            visited.delete(featureId);
        };

        // Start DFS from features with no dependencies
        features.forEach((feature) => {
            const hasDependencies = dependencies.some((dep) => dep.featureId === feature.id);
            if (!hasDependencies) {
                dfs(feature.id, [], new Set());
            }
        });

        return longestPath;
    }, [features, dependencies]);

    const getFeatureTimeline = useCallback((featureId: string): { start: Date; end: Date } | null => {
        const feature = features.find((f) => f.id === featureId);
        if (!feature || !feature.startDate) return null;

        const start = feature.startDate;
        const end = new Date(start);
        end.setDate(end.getDate() + feature.estimatedDays);

        return { start, end };
    }, [features]);

    const estimateReleaseDate = useCallback((): Date | null => {
        if (features.length === 0) return null;

        const criticalPath = calculateCriticalPath();
        if (criticalPath.length === 0) return null;

        let totalDays = 0;
        criticalPath.forEach((featureId) => {
            const feature = features.find((f) => f.id === featureId);
            if (feature) {
                totalDays += feature.estimatedDays;
            }
        });

        const today = new Date();
        const releaseDate = new Date(today);
        releaseDate.setDate(releaseDate.getDate() + totalDays);

        return releaseDate;
    }, [features, calculateCriticalPath]);

    const getTeamVelocity = useCallback((teamId: string): number => {
        const team = teams.find((t) => t.id === teamId);
        return team?.velocity || 0;
    }, [teams]);

    // Risk Assessment
    const calculateFeatureRisk = useCallback((featureId: string): RiskLevel => {
        const feature = features.find((f) => f.id === featureId);
        if (!feature) return 'low';

        let riskScore = 0;

        // Status risk
        if (feature.status === 'blocked') riskScore += 40;
        else if (feature.status === 'not-started') riskScore += 20;
        else if (feature.status === 'in-progress') riskScore += 10;

        // Blocker risk
        const featureBlockers = blockers.filter(
            (b) => b.featureId === featureId && !b.resolvedAt
        );
        riskScore += featureBlockers.length * 15;

        // Dependency risk
        const featureDeps = dependencies.filter((d) => d.featureId === featureId);
        riskScore += featureDeps.length * 5;

        // Estimate accuracy risk (actual vs estimated)
        if (feature.actualDays > feature.estimatedDays * 1.5) riskScore += 20;
        else if (feature.actualDays > feature.estimatedDays * 1.2) riskScore += 10;

        // Readiness risk
        const checks = readinessChecks.get(featureId) || [];
        const passedChecks = checks.filter((c) => c.passed).length;
        const totalChecks = checks.length;
        if (totalChecks > 0) {
            const readinessPercentage = (passedChecks / totalChecks) * 100;
            if (readinessPercentage < 50) riskScore += 20;
            else if (readinessPercentage < 75) riskScore += 10;
        }

        // Determine risk level
        if (riskScore >= 60) return 'critical';
        if (riskScore >= 40) return 'high';
        if (riskScore >= 20) return 'medium';
        return 'low';
    }, [features, blockers, dependencies, readinessChecks]);

    const getReleaseRisk = useCallback((): RiskLevel => {
        if (features.length === 0) return 'low';

        const blockedFeatures = features.filter((f) => f.status === 'blocked').length;
        const notStartedFeatures = features.filter((f) => f.status === 'not-started').length;
        const doneFeatures = features.filter((f) => f.status === 'done').length;

        const completionPercentage = (doneFeatures / features.length) * 100;
        const blockedPercentage = (blockedFeatures / features.length) * 100;

        // Critical if many blockers or low completion near deadline
        if (blockedPercentage > 20 || (completionPercentage < 50 && notStartedFeatures > features.length * 0.3)) {
            return 'critical';
        }

        // High if some blockers or moderate completion
        if (blockedPercentage > 10 || (completionPercentage < 70 && notStartedFeatures > features.length * 0.2)) {
            return 'high';
        }

        // Medium if few blockers or good progress
        if (blockedPercentage > 5 || completionPercentage < 85) {
            return 'medium';
        }

        return 'low';
    }, [features]);

    return {
        // State
        release,
        setRelease,
        selectedTeam,
        setSelectedTeam,
        selectedFeature,
        setSelectedFeature,

        // Team Management
        getTeams,
        addTeam,
        updateTeam,
        deleteTeam,

        // Feature Management
        getFeatures,
        getFeaturesByTeam,
        getFeaturesByStatus,
        addFeature,
        updateFeature,
        deleteFeature,

        // Dependency Management
        addDependency,
        removeDependency,
        getDependencies,
        getFeatureDependencies,

        // Blocker Management
        addBlocker,
        removeBlocker,
        getBlockers,
        getFeatureBlockers,

        // Readiness Checks
        updateReadinessCheck,
        getReadinessChecks,
        isFeatureReady,

        // Timeline & Path Analysis
        calculateCriticalPath,
        getFeatureTimeline,
        estimateReleaseDate,
        getTeamVelocity,

        // Risk Assessment
        calculateFeatureRisk,
        getReleaseRisk,
    };
};

export default useReleaseTrain;
