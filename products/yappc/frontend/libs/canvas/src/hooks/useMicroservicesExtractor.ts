/**
 * Microservices Extractor Hook
 * 
 * @doc.type hook
 * @doc.purpose State management for microservices extraction from monolith
 * @doc.layer product
 * @doc.pattern Custom Hook
 * 
 * Manages monolith entities, bounded contexts, and service boundaries for
 * solution architects to extract microservices from monolithic applications.
 * 
 * Features:
 * - Entity management (classes, tables, APIs, modules)
 * - Bounded context identification and management
 * - Service boundary creation with extraction strategies
 * - Coupling/cohesion analysis
 * - Complexity calculation
 * - AI-powered context identification
 * - Export to C4 Model, Mermaid, PlantUML
 * 
 * @example
 * ```tsx
 * const {
 *   addEntity,
 *   addContext,
 *   analyzeCoupling,
 *   exportToC4Model,
 * } = useMicroservicesExtractor();
 * ```
 */

import { useState, useCallback } from 'react';

/**
 * Entity type in the monolith
 */
export type EntityType = 'class' | 'table' | 'api' | 'module';

/**
 * Extraction strategy for microservices
 */
export type ExtractionStrategy = 'strangler_fig' | 'choreography' | 'orchestration' | 'big_bang';

/**
 * Coupling level between contexts
 */
export type CouplingLevel = 'low' | 'medium' | 'high';

/**
 * Monolith entity (class, table, API, module)
 */
export interface MonolithEntity {
    id: string;
    name: string;
    type: EntityType;
    description?: string;
    dependencies: string[]; // IDs of other entities
    contextId?: string; // Assigned bounded context
}

/**
 * Bounded context in the domain
 */
export interface BoundedContext {
    id: string;
    name: string;
    description?: string;
    domain?: string; // Core, Supporting, Generic
    entityIds: string[]; // Entities in this context
}

/**
 * Service boundary (one or more bounded contexts)
 */
export interface ServiceBoundary {
    id: string;
    name: string;
    contextIds: string[];
    strategy: ExtractionStrategy;
    estimatedEffort?: string;
}

/**
 * Coupling analysis result
 */
export interface CouplingAnalysis {
    level: CouplingLevel;
    dependencyCount: number;
    externalDependencies: string[];
}

/**
 * Cohesion analysis result
 */
export interface CohesionAnalysis {
    score: number; // 0-10
    entityCount: number;
    relatedEntities: string[];
}

/**
 * Complexity calculation result
 */
export interface ComplexityResult {
    score: number; // 0-10
    entityCount: number;
    contextCount: number;
    averageCoupling: number;
    recommendation: string;
}

/**
 * Strategy recommendation result
 */
export interface StrategyRecommendation {
    strategy: ExtractionStrategy;
    rationale: string;
    risks: string[];
}

/**
 * Hook options
 */
export interface UseMicroservicesExtractorOptions {
    monolithName?: string;
}

/**
 * Hook result
 */
export interface UseMicroservicesExtractorResult {
    // State
    monolithName: string;
    setMonolithName: (name: string) => void;
    entities: MonolithEntity[];
    contexts: BoundedContext[];
    serviceBoundaries: ServiceBoundary[];

    // Entity operations
    addEntity: (entity: Omit<MonolithEntity, 'id'>) => string;
    updateEntity: (id: string, updates: Partial<MonolithEntity>) => void;
    deleteEntity: (id: string) => void;
    getEntity: (id: string) => MonolithEntity | undefined;
    getEntityCount: () => number;
    getEntitiesByType: (type: EntityType) => MonolithEntity[];

    // Context operations
    addContext: (context: Omit<BoundedContext, 'id' | 'entityIds'>) => string;
    updateContext: (id: string, updates: Partial<BoundedContext>) => void;
    deleteContext: (id: string) => void;
    getContext: (id: string) => BoundedContext | undefined;
    getContextCount: () => number;
    assignEntityToContext: (entityId: string, contextId: string) => void;
    removeEntityFromContext: (entityId: string) => void;

    // Service boundary operations
    createServiceBoundary: (boundary: Omit<ServiceBoundary, 'id'>) => string;
    updateServiceBoundary: (id: string, updates: Partial<ServiceBoundary>) => void;
    deleteServiceBoundary: (id: string) => void;
    getServiceBoundary: (id: string) => ServiceBoundary | undefined;
    getServiceBoundaryCount: () => number;

    // Analysis
    analyzeCoupling: (contextId: string) => CouplingAnalysis;
    analyzeCohesion: (contextId: string) => CohesionAnalysis;
    calculateComplexity: () => ComplexityResult;
    identifyBoundedContexts: () => Promise<BoundedContext[]>;
    recommendStrategy: () => StrategyRecommendation;
    validateBoundaries: () => string[];

    // Export
    exportToC4Model: () => string;
    exportToMermaid: () => string;
    exportToServiceMesh: () => string;
}

/**
 * Generate unique ID
 */
const generateId = (): string => {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
};

/**
 * Microservices Extractor Hook
 */
export const useMicroservicesExtractor = (
    options: UseMicroservicesExtractorOptions = {}
): UseMicroservicesExtractorResult => {
    const [monolithName, setMonolithName] = useState(options.monolithName || 'Monolith Application');
    const [entities, setEntities] = useState<MonolithEntity[]>([]);
    const [contexts, setContexts] = useState<BoundedContext[]>([]);
    const [serviceBoundaries, setServiceBoundaries] = useState<ServiceBoundary[]>([]);

    /**
     * Add entity
     */
    const addEntity = useCallback((entity: Omit<MonolithEntity, 'id'>): string => {
        const id = generateId();
        const newEntity: MonolithEntity = { ...entity, id };
        setEntities((prev) => [...prev, newEntity]);
        return id;
    }, []);

    /**
     * Update entity
     */
    const updateEntity = useCallback((id: string, updates: Partial<MonolithEntity>): void => {
        setEntities((prev) =>
            prev.map((entity) =>
                entity.id === id ? { ...entity, ...updates } : entity
            )
        );
    }, []);

    /**
     * Delete entity
     */
    const deleteEntity = useCallback((id: string): void => {
        setEntities((prev) => prev.filter((entity) => entity.id !== id));

        // Remove from contexts
        setContexts((prev) =>
            prev.map((context) => ({
                ...context,
                entityIds: context.entityIds.filter((eid) => eid !== id),
            }))
        );
    }, []);

    /**
     * Get entity by ID
     */
    const getEntity = useCallback((id: string): MonolithEntity | undefined => {
        return entities.find((entity) => entity.id === id);
    }, [entities]);

    /**
     * Get entity count
     */
    const getEntityCount = useCallback((): number => {
        return entities.length;
    }, [entities.length]);

    /**
     * Get entities by type
     */
    const getEntitiesByType = useCallback((type: EntityType): MonolithEntity[] => {
        return entities.filter((entity) => entity.type === type);
    }, [entities]);

    /**
     * Add context
     */
    const addContext = useCallback((context: Omit<BoundedContext, 'id' | 'entityIds'>): string => {
        const id = generateId();
        const newContext: BoundedContext = { ...context, id, entityIds: [] };
        setContexts((prev) => [...prev, newContext]);
        return id;
    }, []);

    /**
     * Update context
     */
    const updateContext = useCallback((id: string, updates: Partial<BoundedContext>): void => {
        setContexts((prev) =>
            prev.map((context) =>
                context.id === id ? { ...context, ...updates } : context
            )
        );
    }, []);

    /**
     * Delete context
     */
    const deleteContext = useCallback((id: string): void => {
        // Remove context
        setContexts((prev) => prev.filter((context) => context.id !== id));

        // Unassign entities
        setEntities((prev) =>
            prev.map((entity) =>
                entity.contextId === id ? { ...entity, contextId: undefined } : entity
            )
        );

        // Remove from service boundaries
        setServiceBoundaries((prev) =>
            prev.map((boundary) => ({
                ...boundary,
                contextIds: boundary.contextIds.filter((cid) => cid !== id),
            }))
        );
    }, []);

    /**
     * Get context by ID
     */
    const getContext = useCallback((id: string): BoundedContext | undefined => {
        return contexts.find((context) => context.id === id);
    }, [contexts]);

    /**
     * Get context count
     */
    const getContextCount = useCallback((): number => {
        return contexts.length;
    }, [contexts.length]);

    /**
     * Assign entity to context
     */
    const assignEntityToContext = useCallback((entityId: string, contextId: string): void => {
        // Update entity
        setEntities((prev) =>
            prev.map((entity) =>
                entity.id === entityId ? { ...entity, contextId } : entity
            )
        );

        // Update context
        setContexts((prev) =>
            prev.map((context) => {
                if (context.id === contextId) {
                    return {
                        ...context,
                        entityIds: [...new Set([...context.entityIds, entityId])],
                    };
                }
                // Remove from other contexts
                return {
                    ...context,
                    entityIds: context.entityIds.filter((id) => id !== entityId),
                };
            })
        );
    }, []);

    /**
     * Remove entity from context
     */
    const removeEntityFromContext = useCallback((entityId: string): void => {
        setEntities((prev) =>
            prev.map((entity) =>
                entity.id === entityId ? { ...entity, contextId: undefined } : entity
            )
        );

        setContexts((prev) =>
            prev.map((context) => ({
                ...context,
                entityIds: context.entityIds.filter((id) => id !== entityId),
            }))
        );
    }, []);

    /**
     * Create service boundary
     */
    const createServiceBoundary = useCallback((boundary: Omit<ServiceBoundary, 'id'>): string => {
        const id = generateId();
        const newBoundary: ServiceBoundary = { ...boundary, id };
        setServiceBoundaries((prev) => [...prev, newBoundary]);
        return id;
    }, []);

    /**
     * Update service boundary
     */
    const updateServiceBoundary = useCallback((id: string, updates: Partial<ServiceBoundary>): void => {
        setServiceBoundaries((prev) =>
            prev.map((boundary) =>
                boundary.id === id ? { ...boundary, ...updates } : boundary
            )
        );
    }, []);

    /**
     * Delete service boundary
     */
    const deleteServiceBoundary = useCallback((id: string): void => {
        setServiceBoundaries((prev) => prev.filter((boundary) => boundary.id !== id));
    }, []);

    /**
     * Get service boundary by ID
     */
    const getServiceBoundary = useCallback((id: string): ServiceBoundary | undefined => {
        return serviceBoundaries.find((boundary) => boundary.id === id);
    }, [serviceBoundaries]);

    /**
     * Get service boundary count
     */
    const getServiceBoundaryCount = useCallback((): number => {
        return serviceBoundaries.length;
    }, [serviceBoundaries.length]);

    /**
     * Analyze coupling for a context
     */
    const analyzeCoupling = useCallback((contextId: string): CouplingAnalysis => {
        const context = contexts.find((c) => c.id === contextId);
        if (!context) {
            return { level: 'low', dependencyCount: 0, externalDependencies: [] };
        }

        const contextEntityIds = new Set(context.entityIds);
        const externalDependencies: string[] = [];
        let totalDependencies = 0;

        // Check dependencies for each entity in the context
        context.entityIds.forEach((entityId) => {
            const entity = entities.find((e) => e.id === entityId);
            if (entity) {
                entity.dependencies.forEach((depId) => {
                    totalDependencies++;
                    if (!contextEntityIds.has(depId)) {
                        externalDependencies.push(depId);
                    }
                });
            }
        });

        // Calculate coupling level based on external dependencies ratio
        const externalRatio = totalDependencies > 0
            ? externalDependencies.length / totalDependencies
            : 0;

        const level: CouplingLevel =
            externalRatio > 0.5 ? 'high' :
                externalRatio > 0.25 ? 'medium' :
                    'low';

        return {
            level,
            dependencyCount: totalDependencies,
            externalDependencies: [...new Set(externalDependencies)],
        };
    }, [contexts, entities]);

    /**
     * Analyze cohesion for a context
     */
    const analyzeCohesion = useCallback((contextId: string): CohesionAnalysis => {
        const context = contexts.find((c) => c.id === contextId);
        if (!context) {
            return { score: 0, entityCount: 0, relatedEntities: [] };
        }

        const contextEntities = entities.filter((e) => e.contextId === contextId);
        const entityCount = contextEntities.length;

        if (entityCount === 0) {
            return { score: 0, entityCount: 0, relatedEntities: [] };
        }

        // Calculate internal relationships
        const contextEntityIds = new Set(context.entityIds);
        let internalConnections = 0;
        const relatedEntities: string[] = [];

        contextEntities.forEach((entity) => {
            entity.dependencies.forEach((depId) => {
                if (contextEntityIds.has(depId)) {
                    internalConnections++;
                    relatedEntities.push(depId);
                }
            });
        });

        // Score based on entity count and internal connections
        // Higher score = more cohesive (entities are related to each other)
        const maxPossibleConnections = entityCount * (entityCount - 1);
        const connectionRatio = maxPossibleConnections > 0
            ? internalConnections / maxPossibleConnections
            : 0;

        const score = Math.min(10, Math.round(connectionRatio * 20 + entityCount * 0.5));

        return {
            score,
            entityCount,
            relatedEntities: [...new Set(relatedEntities)],
        };
    }, [contexts, entities]);

    /**
     * Calculate overall complexity
     */
    const calculateComplexity = useCallback((): ComplexityResult => {
        const entityCount = entities.length;
        const contextCount = contexts.length;

        if (entityCount === 0) {
            return {
                score: 0,
                entityCount: 0,
                contextCount: 0,
                averageCoupling: 0,
                recommendation: 'Add entities to start analysis',
            };
        }

        // Calculate average coupling
        const couplingScores = contexts.map((context) => {
            const coupling = analyzeCoupling(context.id);
            return coupling.level === 'high' ? 3 : coupling.level === 'medium' ? 2 : 1;
        });
        const averageCoupling = contextCount > 0
            ? couplingScores.reduce((sum, score) => sum + score, 0) / contextCount
            : 0;

        // Calculate complexity score (0-10)
        // Higher entity count and coupling increase complexity
        const entityFactor = Math.min(5, entityCount / 20);
        const couplingFactor = averageCoupling * 1.5;
        const contextFactor = contextCount === 0 ? 2 : Math.max(0, 3 - contextCount * 0.5);

        const score = Math.min(10, entityFactor + couplingFactor + contextFactor);

        const recommendation =
            score > 7 ? 'High complexity. Consider breaking down into more bounded contexts.' :
                score > 4 ? 'Moderate complexity. Review coupling between contexts.' :
                    'Low complexity. Good bounded context separation.';

        return {
            score: Math.round(score * 10) / 10,
            entityCount,
            contextCount,
            averageCoupling: Math.round(averageCoupling * 10) / 10,
            recommendation,
        };
    }, [entities, contexts, analyzeCoupling]);

    /**
     * Identify bounded contexts using AI
     */
    const identifyBoundedContexts = useCallback(async (): Promise<BoundedContext[]> => {
        // Mock AI analysis - in real implementation, would call AI service
        // Groups entities by naming patterns and dependencies

        if (entities.length === 0) {
            return [];
        }

        // Simple heuristic: group by first word in name
        const groups = new Map<string, MonolithEntity[]>();

        entities.forEach((entity) => {
            const firstWord = entity.name.split(/(?=[A-Z])|\s|_/)[0].toLowerCase();
            const existing = groups.get(firstWord) || [];
            groups.set(firstWord, [...existing, entity]);
        });

        // Create contexts for groups with 2+ entities
        const newContexts: BoundedContext[] = [];

        groups.forEach((groupEntities, key) => {
            if (groupEntities.length >= 2) {
                const contextId = generateId();
                const contextName = key.charAt(0).toUpperCase() + key.slice(1) + ' Management';

                const newContext: BoundedContext = {
                    id: contextId,
                    name: contextName,
                    description: `Auto-identified context for ${key}-related entities`,
                    domain: 'Core',
                    entityIds: groupEntities.map((e) => e.id),
                };

                newContexts.push(newContext);

                // Assign entities to context
                groupEntities.forEach((entity) => {
                    assignEntityToContext(entity.id, contextId);
                });
            }
        });

        setContexts((prev) => [...prev, ...newContexts]);

        return newContexts;
    }, [entities, assignEntityToContext]);

    /**
     * Recommend extraction strategy
     */
    const recommendStrategy = useCallback((): StrategyRecommendation => {
        const complexity = calculateComplexity();
        const hasHighCoupling = contexts.some((context) => {
            const coupling = analyzeCoupling(context.id);
            return coupling.level === 'high';
        });

        if (complexity.score > 7 || hasHighCoupling) {
            return {
                strategy: 'strangler_fig',
                rationale: 'High complexity and coupling suggest incremental migration to reduce risk',
                risks: ['Long migration timeline', 'Requires maintaining two systems'],
            };
        }

        if (contexts.length >= 3 && !hasHighCoupling) {
            return {
                strategy: 'choreography',
                rationale: 'Well-defined contexts with low coupling are ideal for event-driven architecture',
                risks: ['Complex debugging', 'Event versioning challenges'],
            };
        }

        if (contexts.length <= 2) {
            return {
                strategy: 'orchestration',
                rationale: 'Few contexts benefit from centralized coordination',
                risks: ['Single point of failure', 'Orchestrator becomes complex'],
            };
        }

        return {
            strategy: 'strangler_fig',
            rationale: 'Safest approach for gradual migration',
            risks: ['Requires careful planning', 'Long timeline'],
        };
    }, [contexts, calculateComplexity, analyzeCoupling]);

    /**
     * Validate service boundaries
     */
    const validateBoundaries = useCallback((): string[] => {
        const issues: string[] = [];

        if (serviceBoundaries.length === 0 && contexts.length > 0) {
            issues.push('No service boundaries defined. Create services from bounded contexts.');
        }

        serviceBoundaries.forEach((boundary) => {
            if (boundary.contextIds.length === 0) {
                issues.push(`Service "${boundary.name}" has no bounded contexts assigned.`);
            }

            // Check for overlapping contexts
            const contextSet = new Set<string>();
            serviceBoundaries.forEach((otherBoundary) => {
                if (otherBoundary.id !== boundary.id) {
                    otherBoundary.contextIds.forEach((contextId) => {
                        if (boundary.contextIds.includes(contextId)) {
                            contextSet.add(contextId);
                        }
                    });
                }
            });

            if (contextSet.size > 0) {
                const contextNames = Array.from(contextSet)
                    .map((id) => getContext(id)?.name || 'Unknown')
                    .join(', ');
                issues.push(`Service "${boundary.name}" shares contexts with other services: ${contextNames}`);
            }
        });

        return issues;
    }, [serviceBoundaries, contexts, getContext]);

    /**
     * Export to C4 Model (PlantUML)
     */
    const exportToC4Model = useCallback((): string => {
        const lines: string[] = [
            '@startuml',
            '!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml',
            '',
            `title ${monolithName} - Microservices Architecture`,
            '',
            'LAYOUT_WITH_LEGEND()',
            '',
        ];

        // Add service boundaries as containers
        serviceBoundaries.forEach((boundary) => {
            const boundaryContexts = boundary.contextIds
                .map((id) => getContext(id))
                .filter((c): c is BoundedContext => c !== undefined);

            const description = boundaryContexts.map((c) => c.name).join(', ');

            lines.push(
                `Container(${boundary.id}, "${boundary.name}", "${boundary.strategy}", "${description}")`
            );
        });

        lines.push('');

        // Add relationships based on dependencies
        const relationships = new Set<string>();
        serviceBoundaries.forEach((boundary) => {
            const boundaryEntityIds = new Set(
                boundary.contextIds.flatMap((cid) => {
                    const context = getContext(cid);
                    return context ? context.entityIds : [];
                })
            );

            entities.forEach((entity) => {
                if (boundaryEntityIds.has(entity.id)) {
                    entity.dependencies.forEach((depId) => {
                        // Find which boundary the dependency belongs to
                        const targetBoundary = serviceBoundaries.find((b) => {
                            const targetEntityIds = new Set(
                                b.contextIds.flatMap((cid) => {
                                    const context = getContext(cid);
                                    return context ? context.entityIds : [];
                                })
                            );
                            return targetEntityIds.has(depId);
                        });

                        if (targetBoundary && targetBoundary.id !== boundary.id) {
                            const rel = `Rel(${boundary.id}, ${targetBoundary.id}, "depends on")`;
                            relationships.add(rel);
                        }
                    });
                }
            });
        });

        relationships.forEach((rel) => lines.push(rel));

        lines.push('');
        lines.push('@enduml');

        return lines.join('\n');
    }, [monolithName, serviceBoundaries, entities, getContext]);

    /**
     * Export to Mermaid diagram
     */
    const exportToMermaid = useCallback((): string => {
        const lines: string[] = [
            'graph TD',
            '',
        ];

        // Add service boundaries
        serviceBoundaries.forEach((boundary) => {
            const safeName = boundary.name.replace(/\s+/g, '_');
            lines.push(`  ${safeName}["${boundary.name}<br/>${boundary.strategy}"]`);
        });

        lines.push('');

        // Add relationships
        serviceBoundaries.forEach((boundary) => {
            const boundaryEntityIds = new Set(
                boundary.contextIds.flatMap((cid) => {
                    const context = getContext(cid);
                    return context ? context.entityIds : [];
                })
            );

            entities.forEach((entity) => {
                if (boundaryEntityIds.has(entity.id)) {
                    entity.dependencies.forEach((depId) => {
                        const targetBoundary = serviceBoundaries.find((b) => {
                            const targetEntityIds = new Set(
                                b.contextIds.flatMap((cid) => {
                                    const context = getContext(cid);
                                    return context ? context.entityIds : [];
                                })
                            );
                            return targetEntityIds.has(depId);
                        });

                        if (targetBoundary && targetBoundary.id !== boundary.id) {
                            const source = boundary.name.replace(/\s+/g, '_');
                            const target = targetBoundary.name.replace(/\s+/g, '_');
                            lines.push(`  ${source} --> ${target}`);
                        }
                    });
                }
            });
        });

        return lines.join('\n');
    }, [serviceBoundaries, entities, getContext]);

    /**
     * Export to service mesh (PlantUML)
     */
    const exportToServiceMesh = useCallback((): string => {
        const lines: string[] = [
            '@startuml',
            '',
            `title ${monolithName} - Service Mesh Architecture`,
            '',
        ];

        // Add services
        serviceBoundaries.forEach((boundary) => {
            lines.push(`component "${boundary.name}" as ${boundary.id}`);
        });

        lines.push('');

        // Add relationships
        serviceBoundaries.forEach((boundary) => {
            const boundaryEntityIds = new Set(
                boundary.contextIds.flatMap((cid) => {
                    const context = getContext(cid);
                    return context ? context.entityIds : [];
                })
            );

            entities.forEach((entity) => {
                if (boundaryEntityIds.has(entity.id)) {
                    entity.dependencies.forEach((depId) => {
                        const targetBoundary = serviceBoundaries.find((b) => {
                            const targetEntityIds = new Set(
                                b.contextIds.flatMap((cid) => {
                                    const context = getContext(cid);
                                    return context ? context.entityIds : [];
                                })
                            );
                            return targetEntityIds.has(depId);
                        });

                        if (targetBoundary && targetBoundary.id !== boundary.id) {
                            lines.push(`${boundary.id} --> ${targetBoundary.id}`);
                        }
                    });
                }
            });
        });

        lines.push('');
        lines.push('@enduml');

        return lines.join('\n');
    }, [monolithName, serviceBoundaries, entities, getContext]);

    return {
        // State
        monolithName,
        setMonolithName,
        entities,
        contexts,
        serviceBoundaries,

        // Entity operations
        addEntity,
        updateEntity,
        deleteEntity,
        getEntity,
        getEntityCount,
        getEntitiesByType,

        // Context operations
        addContext,
        updateContext,
        deleteContext,
        getContext,
        getContextCount,
        assignEntityToContext,
        removeEntityFromContext,

        // Service boundary operations
        createServiceBoundary,
        updateServiceBoundary,
        deleteServiceBoundary,
        getServiceBoundary,
        getServiceBoundaryCount,

        // Analysis
        analyzeCoupling,
        analyzeCohesion,
        calculateComplexity,
        identifyBoundedContexts,
        recommendStrategy,
        validateBoundaries,

        // Export
        exportToC4Model,
        exportToMermaid,
        exportToServiceMesh,
    };
};
