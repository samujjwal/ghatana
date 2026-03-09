/**
 * Release Train Hook Tests
 * 
 * Comprehensive test suite for useReleaseTrain hook covering:
 * - Team management (CRUD operations)
 * - Feature management (CRUD, filtering)
 * - Dependency management (add, remove, query)
 * - Blocker management (add, remove, auto-status updates)
 * - Readiness checks (update, query, validation)
 * - Critical path calculation (longest dependency chain)
 * - Timeline estimation (release date calculation)
 * - Risk assessment (feature and release risk)
 * - Complex multi-team release scenarios
 */

import { renderHook, act } from '@testing-library/react';
import { useReleaseTrain } from './useReleaseTrain';
import type { FeatureStatus, BlockerType, ReadinessCheckType, RiskLevel } from './useReleaseTrain';

describe('useReleaseTrain', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useReleaseTrain());

            expect(result.current.release).toBe('Q1 2025 Release');
            expect(result.current.selectedTeam).toBeNull();
            expect(result.current.selectedFeature).toBeNull();
            expect(result.current.getTeams()).toEqual([]);
            expect(result.current.getFeatures()).toEqual([]);
            expect(result.current.getDependencies()).toEqual([]);
            expect(result.current.getBlockers()).toEqual([]);
        });

        it('should allow setting release name', () => {
            const { result } = renderHook(() => useReleaseTrain());

            act(() => {
                result.current.setRelease('Q2 2025 Release');
            });

            expect(result.current.release).toBe('Q2 2025 Release');
        });

        it('should allow selecting team and feature', () => {
            const { result } = renderHook(() => useReleaseTrain());

            act(() => {
                result.current.setSelectedTeam('team-1');
                result.current.setSelectedFeature('feature-1');
            });

            expect(result.current.selectedTeam).toBe('team-1');
            expect(result.current.selectedFeature).toBe('feature-1');
        });
    });

    describe('Team Management', () => {
        it('should add a team', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
            });

            const teams = result.current.getTeams();
            expect(teams).toHaveLength(1);
            expect(teams[0].id).toBe(teamId!);
            expect(teams[0].name).toBe('Frontend Team');
            expect(teams[0].velocity).toBe(15);
            expect(teams[0].capacity).toBe(100);
        });

        it('should update a team', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
            });

            act(() => {
                result.current.updateTeam(teamId!, { velocity: 20, capacity: 80 });
            });

            const teams = result.current.getTeams();
            expect(teams[0].velocity).toBe(20);
            expect(teams[0].capacity).toBe(80);
            expect(teams[0].name).toBe('Frontend Team');
        });

        it('should delete a team and its features', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId: teamId!,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
            });

            act(() => {
                result.current.deleteTeam(teamId!);
            });

            expect(result.current.getTeams()).toHaveLength(0);
            expect(result.current.getFeatures()).toHaveLength(0);
        });

        it('should get team velocity', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
            });

            const velocity = result.current.getTeamVelocity(teamId!);
            expect(velocity).toBe(15);
        });
    });

    describe('Feature Management', () => {
        it('should add a feature with readiness checks', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'User Authentication',
                    description: 'Implement user authentication',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 10,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
            });

            const features = result.current.getFeatures();
            expect(features).toHaveLength(1);
            expect(features[0].id).toBe(featureId!);
            expect(features[0].title).toBe('User Authentication');

            // Check readiness checks initialized
            const checks = result.current.getReadinessChecks(featureId!);
            expect(checks).toHaveLength(5);
            expect(checks.every((c) => !c.passed)).toBe(true);
        });

        it('should update a feature', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'User Authentication',
                    description: 'Implement user authentication',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 10,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
            });

            act(() => {
                result.current.updateFeature(featureId!, {
                    status: 'in-progress',
                    actualDays: 3,
                });
            });

            const features = result.current.getFeatures();
            expect(features[0].status).toBe('in-progress');
            expect(features[0].actualDays).toBe(3);
        });

        it('should delete a feature and cleanup dependencies', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId1: string;
            let featureId2: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId1 = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                featureId2 = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(featureId2!, featureId1!);
            });

            act(() => {
                result.current.deleteFeature(featureId1!);
            });

            expect(result.current.getFeatures()).toHaveLength(1);
            expect(result.current.getDependencies()).toHaveLength(0);
        });

        it('should filter features by team', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let team1Id: string;
            let team2Id: string;
            act(() => {
                team1Id = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                team2Id = result.current.addTeam({
                    name: 'Backend Team',
                    velocity: 12,
                    capacity: 100,
                });
                result.current.addFeature({
                    title: 'Frontend Feature',
                    description: 'Description',
                    teamId: team1Id,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addFeature({
                    title: 'Backend Feature',
                    description: 'Description',
                    teamId: team2Id,
                    status: 'testing',
                    estimatedDays: 8,
                    actualDays: 7,
                    dependencies: [],
                    blockers: [],
                });
            });

            const team1Features = result.current.getFeaturesByTeam(team1Id!);
            expect(team1Features).toHaveLength(1);
            expect(team1Features[0].title).toBe('Frontend Feature');

            const team2Features = result.current.getFeaturesByTeam(team2Id!);
            expect(team2Features).toHaveLength(1);
            expect(team2Features[0].title).toBe('Backend Feature');
        });

        it('should filter features by status', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addFeature({
                    title: 'Feature 3',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 6,
                    actualDays: 1,
                    dependencies: [],
                    blockers: [],
                });
            });

            const doneFeatures = result.current.getFeaturesByStatus('done');
            expect(doneFeatures).toHaveLength(1);
            expect(doneFeatures[0].title).toBe('Feature 1');

            const inProgressFeatures = result.current.getFeaturesByStatus('in-progress');
            expect(inProgressFeatures).toHaveLength(2);
        });
    });

    describe('Dependency Management', () => {
        it('should add dependency between features', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 8,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(feature2Id, feature1Id);
            });

            const dependencies = result.current.getDependencies();
            expect(dependencies).toHaveLength(1);
            expect(dependencies[0].featureId).toBe(feature2Id!);
            expect(dependencies[0].dependsOnFeatureId).toBe(feature1Id!);
        });

        it('should remove dependency', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            let depId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 8,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                depId = result.current.addDependency(feature2Id, feature1Id);
            });

            act(() => {
                result.current.removeDependency(depId!);
            });

            expect(result.current.getDependencies()).toHaveLength(0);
        });

        it('should get feature dependencies', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            let feature3Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 3,
                    actualDays: 3,
                    dependencies: [],
                    blockers: [],
                });
                feature3Id = result.current.addFeature({
                    title: 'Feature 3',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(feature3Id, feature1Id);
                result.current.addDependency(feature3Id, feature2Id);
            });

            const feature3Deps = result.current.getFeatureDependencies(feature3Id!);
            expect(feature3Deps).toHaveLength(2);
        });
    });

    describe('Blocker Management', () => {
        it('should add blocker and auto-update feature status', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
            });

            act(() => {
                result.current.addBlocker({
                    featureId: featureId!,
                    type: 'technical',
                    description: 'API dependency not ready',
                });
            });

            const blockers = result.current.getBlockers();
            expect(blockers).toHaveLength(1);
            expect(blockers[0].type).toBe('technical');

            // Check feature status updated to blocked
            const features = result.current.getFeatures();
            expect(features[0].status).toBe('blocked');
        });

        it('should remove blocker and revert feature status', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            let blockerId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                blockerId = result.current.addBlocker({
                    featureId: featureId!,
                    type: 'technical',
                    description: 'API dependency not ready',
                });
            });

            act(() => {
                result.current.removeBlocker(blockerId!);
            });

            expect(result.current.getBlockers()).toHaveLength(0);

            // Check feature status reverted to in-progress
            const features = result.current.getFeatures();
            expect(features[0].status).toBe('in-progress');
        });

        it('should get feature blockers', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 3,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addBlocker({
                    featureId: feature1Id,
                    type: 'technical',
                    description: 'Blocker 1',
                });
                result.current.addBlocker({
                    featureId: feature1Id,
                    type: 'resource',
                    description: 'Blocker 2',
                });
                result.current.addBlocker({
                    featureId: feature2Id,
                    type: 'external',
                    description: 'Blocker 3',
                });
            });

            const feature1Blockers = result.current.getFeatureBlockers(feature1Id!);
            expect(feature1Blockers).toHaveLength(2);

            const feature2Blockers = result.current.getFeatureBlockers(feature2Id!);
            expect(feature2Blockers).toHaveLength(1);
        });
    });

    describe('Readiness Checks', () => {
        it('should update readiness check', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'testing',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
            });

            act(() => {
                result.current.updateReadinessCheck(featureId!, {
                    type: 'tests',
                    passed: true,
                    notes: 'All tests passing',
                });
            });

            const checks = result.current.getReadinessChecks(featureId!);
            const testsCheck = checks.find((c) => c.type === 'tests');
            expect(testsCheck?.passed).toBe(true);
            expect(testsCheck?.notes).toBe('All tests passing');
        });

        it('should check if feature is ready', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'testing',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
            });

            // Initially not ready
            expect(result.current.isFeatureReady(featureId!)).toBe(false);

            // Pass all checks
            act(() => {
                (['tests', 'security', 'docs', 'performance', 'accessibility'] as ReadinessCheckType[]).forEach((type) => {
                    result.current.updateReadinessCheck(featureId!, {
                        type,
                        passed: true,
                    });
                });
            });

            expect(result.current.isFeatureReady(featureId!)).toBe(true);
        });
    });

    describe('Critical Path Calculation', () => {
        it('should calculate critical path with no dependencies', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
            });

            const criticalPath = result.current.calculateCriticalPath();
            expect(criticalPath.length).toBeGreaterThan(0);
        });

        it('should calculate critical path with linear dependencies', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            let feature3Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 8,
                    actualDays: 3,
                    dependencies: [],
                    blockers: [],
                });
                feature3Id = result.current.addFeature({
                    title: 'Feature 3',
                    description: 'Description',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 6,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(feature2Id, feature1Id);
                result.current.addDependency(feature3Id, feature2Id);
            });

            const criticalPath = result.current.calculateCriticalPath();
            expect(criticalPath).toHaveLength(3);
            expect(criticalPath).toEqual([feature1Id, feature2Id, feature3Id]);
        });

        it('should calculate critical path with branching dependencies', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            let feature3Id: string;
            let feature4Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Base feature',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Short branch',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 3,
                    actualDays: 1,
                    dependencies: [],
                    blockers: [],
                });
                feature3Id = result.current.addFeature({
                    title: 'Feature 3',
                    description: 'Medium branch',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 8,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                feature4Id = result.current.addFeature({
                    title: 'Feature 4',
                    description: 'Long branch continuation',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 6,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });

                // Create branching dependencies
                result.current.addDependency(feature2Id, feature1Id);
                result.current.addDependency(feature3Id, feature1Id);
                result.current.addDependency(feature4Id, feature3Id);
            });

            const criticalPath = result.current.calculateCriticalPath();
            // Longest path should be feature1 -> feature3 -> feature4
            expect(criticalPath).toHaveLength(3);
            expect(criticalPath).toContain(feature1Id);
            expect(criticalPath).toContain(feature3Id);
            expect(criticalPath).toContain(feature4Id);
        });
    });

    describe('Timeline Estimation', () => {
        it('should estimate release date based on critical path', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let feature1Id: string;
            let feature2Id: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                feature1Id = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 10,
                    actualDays: 2,
                    dependencies: [],
                    blockers: [],
                });
                feature2Id = result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'not-started',
                    estimatedDays: 15,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(feature2Id, feature1Id);
            });

            const releaseDate = result.current.estimateReleaseDate();
            expect(releaseDate).not.toBeNull();

            // Should be approximately 25 days from now (10 + 15)
            const today = new Date();
            const daysDiff = Math.floor((releaseDate!.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
            expect(daysDiff).toBeGreaterThanOrEqual(24);
            expect(daysDiff).toBeLessThanOrEqual(26);
        });

        it('should return null release date with no features', () => {
            const { result } = renderHook(() => useReleaseTrain());

            const releaseDate = result.current.estimateReleaseDate();
            expect(releaseDate).toBeNull();
        });
    });

    describe('Risk Assessment', () => {
        it('should calculate low risk for feature with no blockers', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'testing',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
            });

            const risk = result.current.calculateFeatureRisk(featureId!);
            expect(risk).toBe('low');
        });

        it('should calculate high risk for blocked feature', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'blocked',
                    estimatedDays: 5,
                    actualDays: 3,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addBlocker({
                    featureId,
                    type: 'technical',
                    description: 'Critical blocker',
                });
                result.current.addBlocker({
                    featureId,
                    type: 'resource',
                    description: 'Resource blocker',
                });
            });

            const risk = result.current.calculateFeatureRisk(featureId!);
            expect(risk).toBe('critical');
        });

        it('should calculate medium risk for feature over estimate', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            let featureId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                featureId = result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'in-progress',
                    estimatedDays: 5,
                    actualDays: 7, // Over estimate by 40%
                    dependencies: [],
                    blockers: [],
                });
            });

            const risk = result.current.calculateFeatureRisk(featureId!);
            expect(['medium', 'high']).toContain(risk);
        });

        it('should calculate release risk with no features', () => {
            const { result } = renderHook(() => useReleaseTrain());

            const risk = result.current.getReleaseRisk();
            expect(risk).toBe('low');
        });

        it('should calculate low release risk with all features done', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addFeature({
                    title: 'Feature 2',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 8,
                    actualDays: 7,
                    dependencies: [],
                    blockers: [],
                });
            });

            const risk = result.current.getReleaseRisk();
            expect(risk).toBe('low');
        });

        it('should calculate critical release risk with many blocked features', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });

                // Add 10 features, 3 blocked (30%)
                for (let i = 0; i < 10; i++) {
                    const featureId = result.current.addFeature({
                        title: `Feature ${i + 1}`,
                        description: 'Description',
                        teamId,
                        status: i < 3 ? 'blocked' : 'in-progress',
                        estimatedDays: 5,
                        actualDays: 2,
                        dependencies: [],
                        blockers: [],
                    });

                    if (i < 3) {
                        result.current.addBlocker({
                            featureId,
                            type: 'technical',
                            description: 'Blocker',
                        });
                    }
                }
            });

            const risk = result.current.getReleaseRisk();
            expect(risk).toBe('critical');
        });

        it('should calculate high release risk with low completion', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let teamId: string;
            act(() => {
                teamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });

                // Add 10 features, only 1 done (10% completion)
                result.current.addFeature({
                    title: 'Feature 1',
                    description: 'Description',
                    teamId,
                    status: 'done',
                    estimatedDays: 5,
                    actualDays: 5,
                    dependencies: [],
                    blockers: [],
                });

                for (let i = 1; i < 10; i++) {
                    result.current.addFeature({
                        title: `Feature ${i + 1}`,
                        description: 'Description',
                        teamId,
                        status: 'not-started',
                        estimatedDays: 5,
                        actualDays: 0,
                        dependencies: [],
                        blockers: [],
                    });
                }
            });

            const risk = result.current.getReleaseRisk();
            expect(['critical', 'high']).toContain(risk);
        });
    });

    describe('Complex Multi-Team Release Scenario', () => {
        it('should handle complex release with multiple teams, dependencies, and blockers', () => {
            const { result } = renderHook(() => useReleaseTrain());

            let frontendTeamId: string;
            let backendTeamId: string;
            let infraTeamId: string;
            let authFeatureId: string;
            let apiFeatureId: string;
            let dbFeatureId: string;
            let uiFeatureId: string;
            let deployFeatureId: string;

            act(() => {
                // Create 3 teams
                frontendTeamId = result.current.addTeam({
                    name: 'Frontend Team',
                    velocity: 15,
                    capacity: 100,
                });
                backendTeamId = result.current.addTeam({
                    name: 'Backend Team',
                    velocity: 12,
                    capacity: 100,
                });
                infraTeamId = result.current.addTeam({
                    name: 'Infrastructure Team',
                    velocity: 10,
                    capacity: 100,
                });

                // Create features with dependencies
                // Infrastructure: Database setup (no dependencies)
                dbFeatureId = result.current.addFeature({
                    title: 'Database Setup',
                    description: 'PostgreSQL setup',
                    teamId: infraTeamId,
                    status: 'done',
                    estimatedDays: 3,
                    actualDays: 3,
                    dependencies: [],
                    blockers: [],
                });

                // Backend: API (depends on Database)
                apiFeatureId = result.current.addFeature({
                    title: 'REST API',
                    description: 'RESTful API implementation',
                    teamId: backendTeamId,
                    status: 'testing',
                    estimatedDays: 10,
                    actualDays: 10,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(apiFeatureId, dbFeatureId);

                // Backend: Authentication (depends on API, has blocker)
                authFeatureId = result.current.addFeature({
                    title: 'Authentication Service',
                    description: 'OAuth2 implementation',
                    teamId: backendTeamId,
                    status: 'blocked',
                    estimatedDays: 8,
                    actualDays: 4,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(authFeatureId, apiFeatureId);
                result.current.addBlocker({
                    featureId: authFeatureId,
                    type: 'external',
                    description: 'Waiting for OAuth provider credentials',
                });

                // Frontend: UI Components (depends on Authentication)
                uiFeatureId = result.current.addFeature({
                    title: 'UI Components',
                    description: 'React components',
                    teamId: frontendTeamId,
                    status: 'not-started',
                    estimatedDays: 12,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(uiFeatureId, authFeatureId);

                // Infrastructure: Deployment (depends on UI)
                deployFeatureId = result.current.addFeature({
                    title: 'Deployment Pipeline',
                    description: 'CI/CD setup',
                    teamId: infraTeamId,
                    status: 'not-started',
                    estimatedDays: 5,
                    actualDays: 0,
                    dependencies: [],
                    blockers: [],
                });
                result.current.addDependency(deployFeatureId, uiFeatureId);
            });

            // Verify teams
            const teams = result.current.getTeams();
            expect(teams).toHaveLength(3);

            // Verify features
            const features = result.current.getFeatures();
            expect(features).toHaveLength(5);

            // Verify critical path (should be: DB -> API -> Auth -> UI -> Deploy)
            const criticalPath = result.current.calculateCriticalPath();
            expect(criticalPath).toHaveLength(5);
            expect(criticalPath).toEqual([dbFeatureId, apiFeatureId, authFeatureId, uiFeatureId, deployFeatureId]);

            // Verify release date estimation
            const releaseDate = result.current.estimateReleaseDate();
            expect(releaseDate).not.toBeNull();
            const expectedDays = 3 + 10 + 8 + 12 + 5; // 38 days
            const today = new Date();
            const daysDiff = Math.floor((releaseDate!.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
            expect(daysDiff).toBeGreaterThanOrEqual(37);
            expect(daysDiff).toBeLessThanOrEqual(39);

            // Verify blockers
            const authBlockers = result.current.getFeatureBlockers(authFeatureId);
            expect(authBlockers).toHaveLength(1);
            expect(authBlockers[0].type).toBe('external');

            // Verify feature risk
            const authRisk = result.current.calculateFeatureRisk(authFeatureId);
            expect(authRisk).toBe('critical');

            // Verify release risk (should be high/critical due to blocked critical path feature)
            const releaseRisk = result.current.getReleaseRisk();
            expect(['high', 'critical']).toContain(releaseRisk);

            // Verify team features
            const frontendFeatures = result.current.getFeaturesByTeam(frontendTeamId);
            expect(frontendFeatures).toHaveLength(1);
            expect(frontendFeatures[0].title).toBe('UI Components');

            const backendFeatures = result.current.getFeaturesByTeam(backendTeamId);
            expect(backendFeatures).toHaveLength(2);

            const infraFeatures = result.current.getFeaturesByTeam(infraTeamId);
            expect(infraFeatures).toHaveLength(2);

            // Verify feature status breakdown
            const doneFeatures = result.current.getFeaturesByStatus('done');
            expect(doneFeatures).toHaveLength(1);

            const blockedFeatures = result.current.getFeaturesByStatus('blocked');
            expect(blockedFeatures).toHaveLength(1);

            const notStartedFeatures = result.current.getFeaturesByStatus('not-started');
            expect(notStartedFeatures).toHaveLength(2);

            // Simulate blocker resolution
            act(() => {
                result.current.removeBlocker(authBlockers[0].id);
                result.current.updateFeature(authFeatureId, { status: 'testing' });
            });

            // Verify blocker removed
            expect(result.current.getFeatureBlockers(authFeatureId)).toHaveLength(0);

            // Verify risk improved
            const updatedAuthRisk = result.current.calculateFeatureRisk(authFeatureId);
            expect(['low', 'medium']).toContain(updatedAuthRisk);
        });
    });
});
