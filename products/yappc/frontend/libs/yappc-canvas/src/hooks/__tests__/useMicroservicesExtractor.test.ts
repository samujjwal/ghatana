/**
 * Tests for useMicroservicesExtractor hook
 * 
 * @doc.type test
 * @doc.purpose Comprehensive tests for microservices extraction hook
 * @doc.layer product
 * @doc.pattern Unit Tests
 */

import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { useMicroservicesExtractor } from '../useMicroservicesExtractor';
import type { EntityType, ExtractionStrategy } from '../useMicroservicesExtractor';

describe('useMicroservicesExtractor', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            expect(result.current.monolithName).toBe('Monolith Application');
            expect(result.current.entities).toEqual([]);
            expect(result.current.contexts).toEqual([]);
            expect(result.current.serviceBoundaries).toEqual([]);
            expect(result.current.getEntityCount()).toBe(0);
            expect(result.current.getContextCount()).toBe(0);
            expect(result.current.getServiceBoundaryCount()).toBe(0);
        });

        it('should initialize with custom monolith name', () => {
            const { result } = renderHook(() =>
                useMicroservicesExtractor({ monolithName: 'Legacy ERP System' })
            );

            expect(result.current.monolithName).toBe('Legacy ERP System');
        });

        it('should allow updating monolith name', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.setMonolithName('E-commerce Platform');
            });

            expect(result.current.monolithName).toBe('E-commerce Platform');
        });
    });

    describe('Entity Management', () => {
        it('should add entity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            act(() => {
                entityId = result.current.addEntity({
                    name: 'UserService',
                    type: 'class',
                    description: 'Handles user operations',
                    dependencies: [],
                });
            });

            expect(result.current.entities).toHaveLength(1);
            expect(result.current.entities[0]).toMatchObject({
                id: entityId!,
                name: 'UserService',
                type: 'class',
                description: 'Handles user operations',
                dependencies: [],
            });
            expect(result.current.getEntityCount()).toBe(1);
        });

        it('should add multiple entities of different types', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addEntity({ name: 'OrderService', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'users', type: 'table', dependencies: [] });
                result.current.addEntity({ name: '/api/orders', type: 'api', dependencies: [] });
                result.current.addEntity({ name: 'auth', type: 'module', dependencies: [] });
            });

            expect(result.current.getEntityCount()).toBe(4);
            expect(result.current.getEntitiesByType('class')).toHaveLength(1);
            expect(result.current.getEntitiesByType('table')).toHaveLength(1);
            expect(result.current.getEntitiesByType('api')).toHaveLength(1);
            expect(result.current.getEntitiesByType('module')).toHaveLength(1);
        });

        it('should update entity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            act(() => {
                entityId = result.current.addEntity({
                    name: 'PaymentService',
                    type: 'class',
                    dependencies: [],
                });
            });

            act(() => {
                result.current.updateEntity(entityId!, {
                    description: 'Updated description',
                    dependencies: ['order-123'],
                });
            });

            const entity = result.current.getEntity(entityId!);
            expect(entity).toBeDefined();
            expect(entity?.description).toBe('Updated description');
            expect(entity?.dependencies).toEqual(['order-123']);
        });

        it('should delete entity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            act(() => {
                entityId = result.current.addEntity({
                    name: 'TempService',
                    type: 'class',
                    dependencies: [],
                });
            });

            expect(result.current.getEntityCount()).toBe(1);

            act(() => {
                result.current.deleteEntity(entityId!);
            });

            expect(result.current.getEntityCount()).toBe(0);
            expect(result.current.getEntity(entityId!)).toBeUndefined();
        });

        it('should get entity by ID', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            act(() => {
                entityId = result.current.addEntity({
                    name: 'InventoryService',
                    type: 'class',
                    dependencies: [],
                });
            });

            const entity = result.current.getEntity(entityId!);
            expect(entity).toBeDefined();
            expect(entity?.name).toBe('InventoryService');
        });

        it('should return undefined for non-existent entity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            const entity = result.current.getEntity('non-existent-id');
            expect(entity).toBeUndefined();
        });

        it('should get entities by type', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addEntity({ name: 'Service1', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'Service2', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'table1', type: 'table', dependencies: [] });
            });

            const classEntities = result.current.getEntitiesByType('class');
            expect(classEntities).toHaveLength(2);
            expect(classEntities.every((e) => e.type === 'class')).toBe(true);
        });

        it('should add entity with dependencies', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let userId: string;
            let orderId: string;

            act(() => {
                userId = result.current.addEntity({
                    name: 'UserService',
                    type: 'class',
                    dependencies: [],
                });
                orderId = result.current.addEntity({
                    name: 'OrderService',
                    type: 'class',
                    dependencies: [userId],
                });
            });

            const orderEntity = result.current.getEntity(orderId!);
            expect(orderEntity?.dependencies).toContain(userId!);
        });
    });

    describe('Bounded Context Management', () => {
        it('should add bounded context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            act(() => {
                contextId = result.current.addContext({
                    name: 'User Management',
                    description: 'User-related operations',
                    domain: 'Core',
                });
            });

            expect(result.current.contexts).toHaveLength(1);
            expect(result.current.contexts[0]).toMatchObject({
                id: contextId!,
                name: 'User Management',
                description: 'User-related operations',
                domain: 'Core',
                entityIds: [],
            });
            expect(result.current.getContextCount()).toBe(1);
        });

        it('should add multiple contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addContext({ name: 'Context 1' });
                result.current.addContext({ name: 'Context 2' });
                result.current.addContext({ name: 'Context 3' });
            });

            expect(result.current.getContextCount()).toBe(3);
        });

        it('should update context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            act(() => {
                contextId = result.current.addContext({ name: 'Original Name' });
            });

            act(() => {
                result.current.updateContext(contextId!, {
                    name: 'Updated Name',
                    description: 'New description',
                });
            });

            const context = result.current.getContext(contextId!);
            expect(context?.name).toBe('Updated Name');
            expect(context?.description).toBe('New description');
        });

        it('should delete context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            act(() => {
                contextId = result.current.addContext({ name: 'Temp Context' });
            });

            expect(result.current.getContextCount()).toBe(1);

            act(() => {
                result.current.deleteContext(contextId!);
            });

            expect(result.current.getContextCount()).toBe(0);
        });

        it('should get context by ID', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            act(() => {
                contextId = result.current.addContext({
                    name: 'Order Processing',
                    domain: 'Core',
                });
            });

            const context = result.current.getContext(contextId!);
            expect(context).toBeDefined();
            expect(context?.name).toBe('Order Processing');
            expect(context?.domain).toBe('Core');
        });

        it('should return undefined for non-existent context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            const context = result.current.getContext('non-existent-id');
            expect(context).toBeUndefined();
        });
    });

    describe('Entity-Context Assignment', () => {
        it('should assign entity to context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            let contextId: string;

            act(() => {
                entityId = result.current.addEntity({
                    name: 'UserService',
                    type: 'class',
                    dependencies: [],
                });
                contextId = result.current.addContext({ name: 'User Management' });
            });

            act(() => {
                result.current.assignEntityToContext(entityId!, contextId!);
            });

            const entity = result.current.getEntity(entityId!);
            const context = result.current.getContext(contextId!);

            expect(entity?.contextId).toBe(contextId!);
            expect(context?.entityIds).toContain(entityId!);
        });

        it('should reassign entity to different context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            let context1Id: string;
            let context2Id: string;

            act(() => {
                entityId = result.current.addEntity({
                    name: 'SharedService',
                    type: 'class',
                    dependencies: [],
                });
                context1Id = result.current.addContext({ name: 'Context 1' });
                context2Id = result.current.addContext({ name: 'Context 2' });
            });

            act(() => {
                result.current.assignEntityToContext(entityId!, context1Id!);
            });

            let context1 = result.current.getContext(context1Id!);
            expect(context1?.entityIds).toContain(entityId!);

            act(() => {
                result.current.assignEntityToContext(entityId!, context2Id!);
            });

            context1 = result.current.getContext(context1Id!);
            const context2 = result.current.getContext(context2Id!);

            expect(context1?.entityIds).not.toContain(entityId!);
            expect(context2?.entityIds).toContain(entityId!);
        });

        it('should remove entity from context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            let contextId: string;

            act(() => {
                entityId = result.current.addEntity({
                    name: 'TempEntity',
                    type: 'class',
                    dependencies: [],
                });
                contextId = result.current.addContext({ name: 'Temp Context' });
                result.current.assignEntityToContext(entityId!, contextId!);
            });

            let entity = result.current.getEntity(entityId!);
            expect(entity?.contextId).toBe(contextId!);

            act(() => {
                result.current.removeEntityFromContext(entityId!);
            });

            entity = result.current.getEntity(entityId!);
            const context = result.current.getContext(contextId!);

            expect(entity?.contextId).toBeUndefined();
            expect(context?.entityIds).not.toContain(entityId!);
        });

        it('should remove entity from context when entity is deleted', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            let contextId: string;

            act(() => {
                entityId = result.current.addEntity({
                    name: 'DeleteMe',
                    type: 'class',
                    dependencies: [],
                });
                contextId = result.current.addContext({ name: 'Test Context' });
                result.current.assignEntityToContext(entityId!, contextId!);
            });

            act(() => {
                result.current.deleteEntity(entityId!);
            });

            const context = result.current.getContext(contextId!);
            expect(context?.entityIds).toHaveLength(0);
        });

        it('should unassign entities when context is deleted', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entityId: string;
            let contextId: string;

            act(() => {
                entityId = result.current.addEntity({
                    name: 'OrphanEntity',
                    type: 'class',
                    dependencies: [],
                });
                contextId = result.current.addContext({ name: 'Delete Context' });
                result.current.assignEntityToContext(entityId!, contextId!);
            });

            act(() => {
                result.current.deleteContext(contextId!);
            });

            const entity = result.current.getEntity(entityId!);
            expect(entity?.contextId).toBeUndefined();
        });
    });

    describe('Service Boundary Management', () => {
        it('should create service boundary', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            let boundaryId: string;

            act(() => {
                contextId = result.current.addContext({ name: 'User Context' });
                boundaryId = result.current.createServiceBoundary({
                    name: 'User Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            expect(result.current.serviceBoundaries).toHaveLength(1);
            expect(result.current.serviceBoundaries[0]).toMatchObject({
                id: boundaryId!,
                name: 'User Service',
                contextIds: [contextId!],
                strategy: 'strangler_fig',
            });
            expect(result.current.getServiceBoundaryCount()).toBe(1);
        });

        it('should create boundary with multiple contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let context1Id: string;
            let context2Id: string;

            act(() => {
                context1Id = result.current.addContext({ name: 'Context 1' });
                context2Id = result.current.addContext({ name: 'Context 2' });
                result.current.createServiceBoundary({
                    name: 'Combined Service',
                    contextIds: [context1Id, context2Id],
                    strategy: 'choreography',
                });
            });

            expect(result.current.serviceBoundaries[0].contextIds).toHaveLength(2);
        });

        it('should update service boundary', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let boundaryId: string;
            let contextId: string;

            act(() => {
                contextId = result.current.addContext({ name: 'Test Context' });
                boundaryId = result.current.createServiceBoundary({
                    name: 'Original Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            act(() => {
                result.current.updateServiceBoundary(boundaryId!, {
                    name: 'Updated Service',
                    strategy: 'choreography',
                });
            });

            const boundary = result.current.getServiceBoundary(boundaryId!);
            expect(boundary?.name).toBe('Updated Service');
            expect(boundary?.strategy).toBe('choreography');
        });

        it('should delete service boundary', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let boundaryId: string;
            let contextId: string;

            act(() => {
                contextId = result.current.addContext({ name: 'Test Context' });
                boundaryId = result.current.createServiceBoundary({
                    name: 'Temp Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            expect(result.current.getServiceBoundaryCount()).toBe(1);

            act(() => {
                result.current.deleteServiceBoundary(boundaryId!);
            });

            expect(result.current.getServiceBoundaryCount()).toBe(0);
        });

        it('should get service boundary by ID', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let boundaryId: string;
            let contextId: string;

            act(() => {
                contextId = result.current.addContext({ name: 'Test Context' });
                boundaryId = result.current.createServiceBoundary({
                    name: 'Test Service',
                    contextIds: [contextId],
                    strategy: 'orchestration',
                });
            });

            const boundary = result.current.getServiceBoundary(boundaryId!);
            expect(boundary).toBeDefined();
            expect(boundary?.name).toBe('Test Service');
            expect(boundary?.strategy).toBe('orchestration');
        });

        it('should remove context from boundaries when context is deleted', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            let boundaryId: string;

            act(() => {
                contextId = result.current.addContext({ name: 'Delete Context' });
                boundaryId = result.current.createServiceBoundary({
                    name: 'Test Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            act(() => {
                result.current.deleteContext(contextId!);
            });

            const boundary = result.current.getServiceBoundary(boundaryId!);
            expect(boundary?.contextIds).toHaveLength(0);
        });
    });

    describe('Coupling Analysis', () => {
        it('should return low coupling for context with no external dependencies', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entity1Id: string;
            let entity2Id: string;
            let contextId: string;

            act(() => {
                entity1Id = result.current.addEntity({
                    name: 'Entity1',
                    type: 'class',
                    dependencies: [],
                });
                entity2Id = result.current.addEntity({
                    name: 'Entity2',
                    type: 'class',
                    dependencies: [entity1Id],
                });
                contextId = result.current.addContext({ name: 'Isolated Context' });
                result.current.assignEntityToContext(entity1Id, contextId);
                result.current.assignEntityToContext(entity2Id, contextId);
            });

            const coupling = result.current.analyzeCoupling(contextId!);
            expect(coupling.level).toBe('low');
            expect(coupling.externalDependencies).toHaveLength(0);
        });

        it('should return high coupling for context with many external dependencies', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entity1Id: string;
            let entity2Id: string;
            let entity3Id: string;
            let contextId: string;

            act(() => {
                entity1Id = result.current.addEntity({
                    name: 'External1',
                    type: 'class',
                    dependencies: [],
                });
                entity2Id = result.current.addEntity({
                    name: 'External2',
                    type: 'class',
                    dependencies: [],
                });
                entity3Id = result.current.addEntity({
                    name: 'InternalEntity',
                    type: 'class',
                    dependencies: [entity1Id, entity2Id],
                });
                contextId = result.current.addContext({ name: 'Coupled Context' });
                result.current.assignEntityToContext(entity3Id, contextId);
            });

            const coupling = result.current.analyzeCoupling(contextId!);
            expect(coupling.level).toBe('high');
            expect(coupling.externalDependencies).toHaveLength(2);
            expect(coupling.dependencyCount).toBe(2);
        });

        it('should return medium coupling for mixed dependencies', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entity1Id: string;
            let entity2Id: string;
            let entity3Id: string;
            let contextId: string;

            act(() => {
                entity1Id = result.current.addEntity({
                    name: 'Internal',
                    type: 'class',
                    dependencies: [],
                });
                entity2Id = result.current.addEntity({
                    name: 'External',
                    type: 'class',
                    dependencies: [],
                });
                entity3Id = result.current.addEntity({
                    name: 'Mixed',
                    type: 'class',
                    dependencies: [entity1Id, entity2Id],
                });
                contextId = result.current.addContext({ name: 'Mixed Context' });
                result.current.assignEntityToContext(entity1Id, contextId);
                result.current.assignEntityToContext(entity3Id, contextId);
            });

            const coupling = result.current.analyzeCoupling(contextId!);
            expect(coupling.level).toBe('medium');
            expect(coupling.externalDependencies.length).toBeGreaterThan(0);
        });
    });

    describe('Cohesion Analysis', () => {
        it('should calculate cohesion score', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entity1Id: string;
            let entity2Id: string;
            let entity3Id: string;
            let contextId: string;

            act(() => {
                entity1Id = result.current.addEntity({
                    name: 'Entity1',
                    type: 'class',
                    dependencies: [],
                });
                entity2Id = result.current.addEntity({
                    name: 'Entity2',
                    type: 'class',
                    dependencies: [entity1Id],
                });
                entity3Id = result.current.addEntity({
                    name: 'Entity3',
                    type: 'class',
                    dependencies: [entity1Id, entity2Id],
                });
                contextId = result.current.addContext({ name: 'Cohesive Context' });
                result.current.assignEntityToContext(entity1Id, contextId);
                result.current.assignEntityToContext(entity2Id, contextId);
                result.current.assignEntityToContext(entity3Id, contextId);
            });

            const cohesion = result.current.analyzeCohesion(contextId!);
            expect(cohesion.score).toBeGreaterThan(0);
            expect(cohesion.entityCount).toBe(3);
            expect(cohesion.relatedEntities.length).toBeGreaterThan(0);
        });

        it('should return zero cohesion for empty context', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let contextId: string;
            act(() => {
                contextId = result.current.addContext({ name: 'Empty Context' });
            });

            const cohesion = result.current.analyzeCohesion(contextId!);
            expect(cohesion.score).toBe(0);
            expect(cohesion.entityCount).toBe(0);
        });
    });

    describe('Complexity Calculation', () => {
        it('should calculate complexity for empty monolith', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            const complexity = result.current.calculateComplexity();
            expect(complexity.score).toBe(0);
            expect(complexity.entityCount).toBe(0);
            expect(complexity.contextCount).toBe(0);
            expect(complexity.recommendation).toContain('Add entities');
        });

        it('should calculate complexity with entities and contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                for (let i = 0; i < 5; i++) {
                    result.current.addEntity({
                        name: `Entity${i}`,
                        type: 'class',
                        dependencies: [],
                    });
                }
                result.current.addContext({ name: 'Context 1' });
                result.current.addContext({ name: 'Context 2' });
            });

            const complexity = result.current.calculateComplexity();
            expect(complexity.score).toBeGreaterThan(0);
            expect(complexity.entityCount).toBe(5);
            expect(complexity.contextCount).toBe(2);
            expect(complexity.recommendation).toBeDefined();
        });

        it('should recommend breaking down for high complexity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                // Add many entities with high coupling
                for (let i = 0; i < 30; i++) {
                    result.current.addEntity({
                        name: `Entity${i}`,
                        type: 'class',
                        dependencies: [],
                    });
                }
            });

            const complexity = result.current.calculateComplexity();
            expect(complexity.score).toBeGreaterThan(5);
            expect(complexity.recommendation).toContain('complexity');
        });
    });

    describe('AI Bounded Context Identification', () => {
        it('should identify bounded contexts from entities', async () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addEntity({ name: 'UserService', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'UserRepository', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'OrderService', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'OrderRepository', type: 'class', dependencies: [] });
            });

            let identifiedContexts: unknown[];
            await act(async () => {
                identifiedContexts = await result.current.identifyBoundedContexts();
            });

            expect(identifiedContexts!.length).toBeGreaterThan(0);
            expect(result.current.getContextCount()).toBeGreaterThan(0);
        });

        it('should group entities by naming patterns', async () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addEntity({ name: 'PaymentProcessor', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'PaymentGateway', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'InvoiceGenerator', type: 'class', dependencies: [] });
                result.current.addEntity({ name: 'InvoiceService', type: 'class', dependencies: [] });
            });

            await act(async () => {
                await result.current.identifyBoundedContexts();
            });

            const contexts = result.current.contexts;
            expect(contexts.length).toBeGreaterThan(0);

            // Check that entities are grouped
            contexts.forEach((context) => {
                expect(context.entityIds.length).toBeGreaterThanOrEqual(2);
            });
        });
    });

    describe('Strategy Recommendation', () => {
        it('should recommend strangler fig for high complexity', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                // Create high complexity scenario
                for (let i = 0; i < 25; i++) {
                    result.current.addEntity({
                        name: `Entity${i}`,
                        type: 'class',
                        dependencies: [],
                    });
                }
            });

            const recommendation = result.current.recommendStrategy();
            expect(recommendation.strategy).toBe('strangler_fig');
            expect(recommendation.rationale).toBeDefined();
            expect(recommendation.risks).toBeInstanceOf(Array);
        });

        it('should recommend choreography for well-defined contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                // Create multiple contexts with low coupling
                const context1Id = result.current.addContext({ name: 'Context 1' });
                const context2Id = result.current.addContext({ name: 'Context 2' });
                const context3Id = result.current.addContext({ name: 'Context 3' });

                const entity1Id = result.current.addEntity({
                    name: 'Entity1',
                    type: 'class',
                    dependencies: [],
                });
                const entity2Id = result.current.addEntity({
                    name: 'Entity2',
                    type: 'class',
                    dependencies: [],
                });

                result.current.assignEntityToContext(entity1Id, context1Id);
                result.current.assignEntityToContext(entity2Id, context2Id);
            });

            const recommendation = result.current.recommendStrategy();
            expect(recommendation.strategy).toBe('choreography');
            expect(recommendation.rationale).toContain('event');
        });

        it('should recommend orchestration for few contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'Single Context' });
                const entityId = result.current.addEntity({
                    name: 'Entity',
                    type: 'class',
                    dependencies: [],
                });
                result.current.assignEntityToContext(entityId, contextId);
            });

            const recommendation = result.current.recommendStrategy();
            expect(recommendation.strategy).toBe('orchestration');
        });
    });

    describe('Boundary Validation', () => {
        it('should return no issues for valid boundaries', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'Valid Context' });
                result.current.createServiceBoundary({
                    name: 'Valid Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            const issues = result.current.validateBoundaries();
            expect(issues).toHaveLength(0);
        });

        it('should detect missing service boundaries', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.addContext({ name: 'Orphan Context' });
            });

            const issues = result.current.validateBoundaries();
            expect(issues.length).toBeGreaterThan(0);
            expect(issues[0]).toContain('No service boundaries');
        });

        it('should detect boundaries without contexts', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                result.current.createServiceBoundary({
                    name: 'Empty Service',
                    contextIds: [],
                    strategy: 'strangler_fig',
                });
            });

            const issues = result.current.validateBoundaries();
            expect(issues.length).toBeGreaterThan(0);
            expect(issues.some((issue) => issue.includes('no bounded contexts'))).toBe(true);
        });

        it('should detect overlapping contexts in boundaries', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'Shared Context' });
                result.current.createServiceBoundary({
                    name: 'Service 1',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
                result.current.createServiceBoundary({
                    name: 'Service 2',
                    contextIds: [contextId],
                    strategy: 'choreography',
                });
            });

            const issues = result.current.validateBoundaries();
            expect(issues.length).toBeGreaterThan(0);
            expect(issues.some((issue) => issue.includes('shares contexts'))).toBe(true);
        });
    });

    describe('Export Functionality', () => {
        it('should export to C4 Model', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'User Context' });
                result.current.createServiceBoundary({
                    name: 'User Service',
                    contextIds: [contextId],
                    strategy: 'strangler_fig',
                });
            });

            const c4Model = result.current.exportToC4Model();
            expect(c4Model).toContain('@startuml');
            expect(c4Model).toContain('User Service');
            expect(c4Model).toContain('@enduml');
        });

        it('should export to Mermaid', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'Order Context' });
                result.current.createServiceBoundary({
                    name: 'Order Service',
                    contextIds: [contextId],
                    strategy: 'choreography',
                });
            });

            const mermaid = result.current.exportToMermaid();
            expect(mermaid).toContain('graph TD');
            expect(mermaid).toContain('Order_Service');
        });

        it('should export to service mesh', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            act(() => {
                const contextId = result.current.addContext({ name: 'Payment Context' });
                result.current.createServiceBoundary({
                    name: 'Payment Service',
                    contextIds: [contextId],
                    strategy: 'orchestration',
                });
            });

            const serviceMesh = result.current.exportToServiceMesh();
            expect(serviceMesh).toContain('@startuml');
            expect(serviceMesh).toContain('Payment Service');
        });

        it('should include relationships in export', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            let entity1Id: string;
            let entity2Id: string;
            let context1Id: string;
            let context2Id: string;

            act(() => {
                entity1Id = result.current.addEntity({
                    name: 'Entity1',
                    type: 'class',
                    dependencies: [],
                });
                entity2Id = result.current.addEntity({
                    name: 'Entity2',
                    type: 'class',
                    dependencies: [entity1Id],
                });

                context1Id = result.current.addContext({ name: 'Context 1' });
                context2Id = result.current.addContext({ name: 'Context 2' });

                result.current.assignEntityToContext(entity1Id, context1Id);
                result.current.assignEntityToContext(entity2Id, context2Id);

                result.current.createServiceBoundary({
                    name: 'Service 1',
                    contextIds: [context1Id],
                    strategy: 'strangler_fig',
                });
                result.current.createServiceBoundary({
                    name: 'Service 2',
                    contextIds: [context2Id],
                    strategy: 'strangler_fig',
                });
            });

            const c4Model = result.current.exportToC4Model();
            expect(c4Model).toContain('Rel(');
        });
    });

    describe('Complex Scenario', () => {
        it('should handle complete microservices extraction workflow', () => {
            const { result } = renderHook(() => useMicroservicesExtractor());

            // Setup monolith
            act(() => {
                result.current.setMonolithName('Legacy E-commerce Platform');
            });

            // Add entities
            let userServiceId: string;
            let userRepoId: string;
            let orderServiceId: string;
            let orderRepoId: string;
            let paymentServiceId: string;

            act(() => {
                userServiceId = result.current.addEntity({
                    name: 'UserService',
                    type: 'class',
                    dependencies: [],
                });
                userRepoId = result.current.addEntity({
                    name: 'UserRepository',
                    type: 'class',
                    dependencies: [],
                });
                orderServiceId = result.current.addEntity({
                    name: 'OrderService',
                    type: 'class',
                    dependencies: [userServiceId],
                });
                orderRepoId = result.current.addEntity({
                    name: 'OrderRepository',
                    type: 'class',
                    dependencies: [],
                });
                paymentServiceId = result.current.addEntity({
                    name: 'PaymentService',
                    type: 'class',
                    dependencies: [orderServiceId],
                });
            });

            expect(result.current.getEntityCount()).toBe(5);

            // Create bounded contexts
            let userContextId: string;
            let orderContextId: string;
            let paymentContextId: string;

            act(() => {
                userContextId = result.current.addContext({
                    name: 'User Management',
                    domain: 'Core',
                });
                orderContextId = result.current.addContext({
                    name: 'Order Processing',
                    domain: 'Core',
                });
                paymentContextId = result.current.addContext({
                    name: 'Payment Processing',
                    domain: 'Supporting',
                });
            });

            expect(result.current.getContextCount()).toBe(3);

            // Assign entities to contexts
            act(() => {
                result.current.assignEntityToContext(userServiceId!, userContextId!);
                result.current.assignEntityToContext(userRepoId!, userContextId!);
                result.current.assignEntityToContext(orderServiceId!, orderContextId!);
                result.current.assignEntityToContext(orderRepoId!, orderContextId!);
                result.current.assignEntityToContext(paymentServiceId!, paymentContextId!);
            });

            // Analyze coupling and cohesion
            const userCoupling = result.current.analyzeCoupling(userContextId!);
            const orderCohesion = result.current.analyzeCohesion(orderContextId!);

            expect(userCoupling.level).toBe('low');
            expect(orderCohesion.score).toBeGreaterThan(0);

            // Calculate complexity
            const complexity = result.current.calculateComplexity();
            expect(complexity.contextCount).toBe(3);

            // Get strategy recommendation
            const recommendation = result.current.recommendStrategy();
            expect(recommendation.strategy).toBeDefined();

            // Create service boundaries
            act(() => {
                result.current.createServiceBoundary({
                    name: 'User Service',
                    contextIds: [userContextId!],
                    strategy: 'strangler_fig',
                });
                result.current.createServiceBoundary({
                    name: 'Order Service',
                    contextIds: [orderContextId!, paymentContextId!],
                    strategy: 'choreography',
                });
            });

            expect(result.current.getServiceBoundaryCount()).toBe(2);

            // Validate boundaries
            const issues = result.current.validateBoundaries();
            expect(issues).toHaveLength(0);

            // Export
            const c4Model = result.current.exportToC4Model();
            expect(c4Model).toContain('User Service');
            expect(c4Model).toContain('Order Service');
            expect(c4Model).toContain('Legacy E-commerce Platform');
        });
    });
});
