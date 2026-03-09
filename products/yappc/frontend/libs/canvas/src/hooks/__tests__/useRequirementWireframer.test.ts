/**
 * @doc.type test
 * @doc.purpose Unit tests for useRequirementWireframer hook (Journey 21.1 - Product Designer Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useRequirementWireframer } from '../useRequirementWireframer';

describe('useRequirementWireframer', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    describe('Initialization', () => {
        it('should initialize with default state', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            expect(result.current.userStory).toBe('');
            expect(result.current.parsedStory).toBeNull();
            expect(result.current.validation).toBeNull();
            expect(result.current.elements).toEqual([]);
            expect(result.current.rules).toEqual([]);
            expect(result.current.flow).toEqual([]);
            expect(result.current.flowSimulation).toBeNull();
        });

        it('should accept initial user story', () => {
            const initialStory = 'As a user, I want to view products';
            const { result } = renderHook(() =>
                useRequirementWireframer({ initialUserStory: initialStory })
            );

            expect(result.current.userStory).toBe(initialStory);
        });

        it('should auto-parse when autoParseOnChange is true', () => {
            const { result } = renderHook(() =>
                useRequirementWireframer({
                    initialUserStory: 'As a user, I want to search products',
                    autoParseOnChange: true,
                })
            );

            expect(result.current.parsedStory).not.toBeNull();
            expect(result.current.elements.length).toBeGreaterThan(0);
        });
    });

    describe('User Story Management', () => {
        it('should update user story', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a customer, I want to filter items');
            });

            expect(result.current.userStory).toBe('As a customer, I want to filter items');
        });

        it('should parse user story', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a user, I want to search products');
                result.current.parseStory();
            });

            expect(result.current.parsedStory).not.toBeNull();
            expect(result.current.parsedStory?.actor).toBe('user');
            expect(result.current.elements.length).toBeGreaterThan(0);
        });

        it('should validate user story', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a user, I want to search');
                result.current.parseStory();
            });

            expect(result.current.validation).not.toBeNull();
            expect(result.current.validation?.valid).toBe(true);
        });

        it('should detect invalid user story', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('');
                result.current.parseStory();
            });

            expect(result.current.validation?.valid).toBe(false);
            expect(result.current.validation?.errors.length).toBeGreaterThan(0);
        });

        it('should clear story', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a user, I want to search');
                result.current.parseStory();
                result.current.clearStory();
            });

            expect(result.current.userStory).toBe('');
            expect(result.current.parsedStory).toBeNull();
            expect(result.current.elements).toEqual([]);
            expect(result.current.rules).toEqual([]);
            expect(result.current.flow).toEqual([]);
        });
    });

    describe('Element Management', () => {
        beforeEach(() => { });

        it('should add element', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Search Bar',
                    description: 'Search input field',
                });
            });

            expect(result.current.elements.length).toBe(1);
            expect(result.current.elements[0].label).toBe('Search Bar');
        });

        it('should update element', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Search Bar',
                });
                result.current.updateElement('elem-1', { label: 'Updated Search' });
            });

            expect(result.current.elements[0].label).toBe('Updated Search');
        });

        it('should delete element', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Search Bar',
                });
                result.current.deleteElement('elem-1');
            });

            expect(result.current.elements.length).toBe(0);
        });

        it('should remove element from flow when deleted', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Search Bar',
                });
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    elementId: 'elem-1',
                    label: 'User searches',
                });
                result.current.deleteElement('elem-1');
            });

            expect(result.current.flow.length).toBe(0);
        });

        it('should remove element from rules when deleted', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Search Bar',
                });
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Search must not be empty',
                    appliesTo: ['elem-1'],
                });
                result.current.deleteElement('elem-1');
            });

            expect(result.current.rules[0].appliesTo).toEqual([]);
        });
    });

    describe('Rule Management', () => {
        it('should add rule', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Email must be valid',
                });
            });

            expect(result.current.rules.length).toBe(1);
            expect(result.current.rules[0].description).toBe('Email must be valid');
        });

        it('should update rule', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Email must be valid',
                });
                result.current.updateRule('rule-1', { description: 'Updated rule' });
            });

            expect(result.current.rules[0].description).toBe('Updated rule');
        });

        it('should delete rule', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Email must be valid',
                });
                result.current.deleteRule('rule-1');
            });

            expect(result.current.rules.length).toBe(0);
        });

        it('should link rule to element', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Email Field',
                });
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Email must be valid',
                    appliesTo: ['elem-1'],
                });
            });

            expect(result.current.rules[0].appliesTo).toContain('elem-1');
        });
    });

    describe('Flow Management', () => {
        it('should add flow step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'User opens page',
                });
            });

            expect(result.current.flow.length).toBe(1);
            expect(result.current.flow[0].label).toBe('User opens page');
        });

        it('should update flow step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'User opens page',
                });
                result.current.updateFlowStep('flow-1', { label: 'Updated step' });
            });

            expect(result.current.flow[0].label).toBe('Updated step');
        });

        it('should delete flow step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'Step 1',
                });
                result.current.deleteFlowStep('flow-1');
            });

            expect(result.current.flow.length).toBe(0);
        });

        it('should reorder flow steps', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'Step 1',
                });
                result.current.addFlowStep({
                    id: 'flow-2',
                    order: 2,
                    label: 'Step 2',
                });
                result.current.reorderFlow(['flow-2', 'flow-1']);
            });

            expect(result.current.flow[0].id).toBe('flow-2');
            expect(result.current.flow[0].order).toBe(1);
            expect(result.current.flow[1].id).toBe('flow-1');
            expect(result.current.flow[1].order).toBe(2);
        });
    });

    describe('Flow Simulation', () => {
        beforeEach(() => {
            vi.useFakeTimers();
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should start simulation', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'Step 1',
                });
                result.current.startSimulation();
            });

            expect(result.current.flowSimulation?.isPlaying).toBe(true);
            expect(result.current.flowSimulation?.currentStep).toBe(0);
        });

        it('should auto-advance steps during simulation', async () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.startSimulation();
            });

            expect(result.current.flowSimulation?.currentStep).toBe(0);

            act(() => {
                vi.advanceTimersByTime(2000); // Default speed
            });

            await waitFor(() => {
                expect(result.current.flowSimulation?.currentStep).toBe(1);
            });
        });

        it('should pause simulation', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({
                    id: 'flow-1',
                    order: 1,
                    label: 'Step 1',
                });
                result.current.startSimulation();
                result.current.pauseSimulation();
            });

            expect(result.current.flowSimulation?.isPlaying).toBe(false);
        });

        it('should reset simulation', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.startSimulation();
                result.current.nextStep();
                result.current.resetSimulation();
            });

            expect(result.current.flowSimulation?.currentStep).toBe(0);
            expect(result.current.flowSimulation?.isPlaying).toBe(false);
        });

        it('should go to next step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.startSimulation();
                result.current.pauseSimulation();
                result.current.nextStep();
            });

            expect(result.current.flowSimulation?.currentStep).toBe(1);
        });

        it('should go to previous step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.startSimulation();
                result.current.pauseSimulation();
                result.current.nextStep();
                result.current.previousStep();
            });

            expect(result.current.flowSimulation?.currentStep).toBe(0);
        });

        it('should not go beyond last step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.startSimulation();
                result.current.pauseSimulation();
                result.current.nextStep(); // Already at last step
            });

            expect(result.current.flowSimulation?.currentStep).toBe(0);
        });

        it('should not go before first step', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.startSimulation();
                result.current.pauseSimulation();
                result.current.previousStep(); // Already at first step
            });

            expect(result.current.flowSimulation?.currentStep).toBe(0);
        });

        it('should set simulation speed', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.startSimulation();
                result.current.setSimulationSpeed(1000);
            });

            expect(result.current.flowSimulation?.speed).toBe(1000);
        });

        it('should mark steps as completed', async () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.startSimulation();
            });

            act(() => {
                vi.advanceTimersByTime(2000);
            });

            await waitFor(() => {
                expect(result.current.flowSimulation?.completed.size).toBe(1);
                expect(result.current.flowSimulation?.completed.has('flow-1')).toBe(true);
            });
        });
    });

    describe('Export', () => {
        it('should export as JSON', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a user, I want to search');
                result.current.parseStory();
            });

            const json = result.current.exportAsJSON();
            const parsed = JSON.parse(json);

            expect(parsed.userStory).toBe('As a user, I want to search');
            expect(parsed.parsedStory).not.toBeNull();
            expect(Array.isArray(parsed.elements)).toBe(true);
            expect(Array.isArray(parsed.rules)).toBe(true);
            expect(Array.isArray(parsed.flow)).toBe(true);
        });

        it('should export as Markdown', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.setUserStory('As a user, I want to search products');
                result.current.parseStory();
            });

            const markdown = result.current.exportAsMarkdown();

            expect(markdown).toContain('# User Story');
            expect(markdown).toContain('## Parsed Information');
            expect(markdown).toContain('## UI Elements');
            expect(markdown).toContain('## Business Rules');
            expect(markdown).toContain('## User Flow');
        });

        it('should include custom elements in export', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addElement({
                    id: 'elem-1',
                    type: 'component',
                    label: 'Custom Element',
                });
            });

            const json = result.current.exportAsJSON();
            const parsed = JSON.parse(json);

            expect(parsed.elements.length).toBe(1);
            expect(parsed.elements[0].label).toBe('Custom Element');
        });
    });

    describe('Complex Workflows', () => {
        it('should handle complete wireframing workflow', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                // 1. Set and parse story
                result.current.setUserStory('As a user, I want to search and filter products');
                result.current.parseStory();

                // 2. Add custom element
                result.current.addElement({
                    id: 'custom-1',
                    type: 'component',
                    label: 'Advanced Filter',
                });

                // 3. Add business rule
                result.current.addRule({
                    id: 'rule-1',
                    type: 'validation',
                    description: 'Search cannot be empty',
                    appliesTo: [result.current.elements[0]?.id || ''],
                });

                // 4. Customize flow
                result.current.updateFlowStep(result.current.flow[0]?.id || '', {
                    description: 'User enters search term',
                });
            });

            expect(result.current.parsedStory).not.toBeNull();
            expect(result.current.elements.length).toBeGreaterThan(1);
            expect(result.current.rules.length).toBeGreaterThan(0);
            expect(result.current.flow.length).toBeGreaterThan(0);

            // Export and verify
            const json = JSON.parse(result.current.exportAsJSON());
            expect(json.elements.some((e: unknown) => e.label === 'Advanced Filter')).toBe(true);
        });

        it('should maintain consistency when reordering', () => {
            const { result } = renderHook(() => useRequirementWireframer());

            act(() => {
                result.current.addFlowStep({ id: 'flow-1', order: 1, label: 'Step 1' });
                result.current.addFlowStep({ id: 'flow-2', order: 2, label: 'Step 2' });
                result.current.addFlowStep({ id: 'flow-3', order: 3, label: 'Step 3' });
                result.current.reorderFlow(['flow-3', 'flow-1', 'flow-2']);
            });

            const orders = result.current.flow.map(f => f.order);
            expect(orders).toEqual([1, 2, 3]); // Maintains sequential ordering
        });
    });
});
