/**
 * @doc.type hook
 * @doc.purpose Requirement wireframer hook for Journey 21.1 (Product Designer - Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import { UserStoryParser, type ParsedUserStory, type WireframeElement, type BusinessRule, type FlowStep } from '../services/UserStoryParser';

// Re-export types for external use
export type { ParsedUserStory, WireframeElement, BusinessRule, FlowStep };

/**
 * Flow simulation state
 */
export interface FlowSimulation {
    currentStep: number;
    isPlaying: boolean;
    speed: number; // milliseconds per step
    completed: Set<string>; // Element IDs that have been visited
}

/**
 * Hook options
 */
export interface UseRequirementWireframerOptions {
    autoSimulate?: boolean;
    defaultSpeed?: number;
}

/**
 * Hook return value
 */
export interface UseRequirementWireframerResult {
    // Parsing
    parseStory: (story: string) => ParsedUserStory | null;
    parsedStory: ParsedUserStory | null;
    isValid: boolean;
    errors: string[];
    warnings: string[];

    // Elements
    elements: WireframeElement[];
    addElement: (element: Omit<WireframeElement, 'id'>) => void;
    updateElement: (id: string, updates: Partial<WireframeElement>) => void;
    deleteElement: (id: string) => void;

    // Business Rules
    rules: BusinessRule[];
    addRule: (rule: Omit<BusinessRule, 'id'>) => void;
    updateRule: (id: string, updates: Partial<BusinessRule>) => void;
    deleteRule: (id: string) => void;
    linkRuleToElement: (ruleId: string, elementId: string) => void;
    unlinkRuleFromElement: (ruleId: string, elementId: string) => void;

    // Flow
    flow: FlowStep[];
    addFlowStep: (step: Omit<FlowStep, 'id'>) => void;
    updateFlowStep: (id: string, updates: Partial<FlowStep>) => void;
    deleteFlowStep: (id: string) => void;
    reorderFlow: (fromIndex: number, toIndex: number) => void;

    // Simulation
    simulation: FlowSimulation;
    startSimulation: () => void;
    pauseSimulation: () => void;
    resetSimulation: () => void;
    nextStep: () => void;
    previousStep: () => void;
    setSimulationSpeed: (speed: number) => void;

    // Generation
    generateAcceptanceCriteria: () => string[];
    estimateComplexity: () => {
        score: number;
        level: 'low' | 'medium' | 'high' | 'very-high';
        factors: string[];
    };

    // Export
    exportAsJSON: () => string;
    exportAsMarkdown: () => string;
}

/**
 * Requirement Wireframer Hook
 * 
 * Provides functionality for parsing user stories into wireframes,
 * managing business rules, and simulating user flows.
 * 
 * @example
 * ```tsx
 * const {
 *   parseStory,
 *   parsedStory,
 *   startSimulation,
 *   simulation,
 * } = useRequirementWireframer();
 * 
 * parseStory("As a user, I want to filter products by price");
 * ```
 */
export function useRequirementWireframer(
    options: UseRequirementWireframerOptions = {}
): UseRequirementWireframerResult {
    const { autoSimulate = false, defaultSpeed = 1000 } = options;

    const [parsedStory, setParsedStory] = useState<ParsedUserStory | null>(null);
    const [isValid, setIsValid] = useState(true);
    const [errors, setErrors] = useState<string[]>([]);
    const [warnings, setWarnings] = useState<string[]>([]);
    const [elements, setElements] = useState<WireframeElement[]>([]);
    const [rules, setRules] = useState<BusinessRule[]>([]);
    const [flow, setFlow] = useState<FlowStep[]>([]);
    const [simulation, setSimulation] = useState<FlowSimulation>({
        currentStep: 0,
        isPlaying: false,
        speed: defaultSpeed,
        completed: new Set(),
    });
    const [simulationInterval, setSimulationInterval] = useState<NodeJS.Timeout | null>(null);

    /**
     * Parse user story
     */
    const parseStory = useCallback((story: string): ParsedUserStory | null => {
        // Validate
        const validation = UserStoryParser.validateUserStory(story);
        setIsValid(validation.valid);
        setErrors(validation.errors);
        setWarnings(validation.warnings);

        if (!validation.valid) {
            return null;
        }

        // Parse
        const parsed = UserStoryParser.parseUserStory(story);
        setParsedStory(parsed);
        setElements(parsed.elements);
        setRules(parsed.rules);
        setFlow(parsed.flow);

        // Auto-simulate if enabled
        if (autoSimulate && parsed.flow.length > 0) {
            setTimeout(() => startSimulation(), 500);
        }

        return parsed;
    }, [autoSimulate]);

    /**
     * Add element
     */
    const addElement = useCallback((element: Omit<WireframeElement, 'id'>) => {
        const newElement: WireframeElement = {
            ...element,
            id: `element-${Date.now()}`,
        };
        setElements(prev => [...prev, newElement]);

        // Add to flow
        addFlowStep({
            elementId: newElement.id,
            description: `User interacts with ${newElement.label}`,
            order: flow.length + 1,
        });
    }, [flow.length]);

    /**
     * Update element
     */
    const updateElement = useCallback((id: string, updates: Partial<WireframeElement>) => {
        setElements(prev => prev.map(el => (el.id === id ? { ...el, ...updates } : el)));
    }, []);

    /**
     * Delete element
     */
    const deleteElement = useCallback((id: string) => {
        setElements(prev => prev.filter(el => el.id !== id));

        // Remove from flow
        setFlow(prev => prev.filter(step => step.elementId !== id));

        // Unlink from rules
        setRules(prev =>
            prev.map(rule => ({
                ...rule,
                appliesTo: rule.appliesTo.filter(elId => elId !== id),
            }))
        );
    }, []);

    /**
     * Add business rule
     */
    const addRule = useCallback((rule: Omit<BusinessRule, 'id'>) => {
        const newRule: BusinessRule = {
            ...rule,
            id: `rule-${Date.now()}`,
        };
        setRules(prev => [...prev, newRule]);
    }, []);

    /**
     * Update business rule
     */
    const updateRule = useCallback((id: string, updates: Partial<BusinessRule>) => {
        setRules(prev => prev.map(rule => (rule.id === id ? { ...rule, ...updates } : rule)));
    }, []);

    /**
     * Delete business rule
     */
    const deleteRule = useCallback((id: string) => {
        setRules(prev => prev.filter(rule => rule.id !== id));
    }, []);

    /**
     * Link rule to element
     */
    const linkRuleToElement = useCallback((ruleId: string, elementId: string) => {
        setRules(prev =>
            prev.map(rule =>
                rule.id === ruleId && !rule.appliesTo.includes(elementId)
                    ? { ...rule, appliesTo: [...rule.appliesTo, elementId] }
                    : rule
            )
        );
    }, []);

    /**
     * Unlink rule from element
     */
    const unlinkRuleFromElement = useCallback((ruleId: string, elementId: string) => {
        setRules(prev =>
            prev.map(rule =>
                rule.id === ruleId
                    ? { ...rule, appliesTo: rule.appliesTo.filter(id => id !== elementId) }
                    : rule
            )
        );
    }, []);

    /**
     * Add flow step
     */
    const addFlowStep = useCallback((step: Omit<FlowStep, 'id'>) => {
        const newStep: FlowStep = {
            ...step,
            id: `step-${Date.now()}`,
        };
        setFlow(prev => [...prev, newStep].sort((a, b) => a.order - b.order));
    }, []);

    /**
     * Update flow step
     */
    const updateFlowStep = useCallback((id: string, updates: Partial<FlowStep>) => {
        setFlow(prev =>
            prev
                .map(step => (step.id === id ? { ...step, ...updates } : step))
                .sort((a, b) => a.order - b.order)
        );
    }, []);

    /**
     * Delete flow step
     */
    const deleteFlowStep = useCallback((id: string) => {
        setFlow(prev => {
            const filtered = prev.filter(step => step.id !== id);
            // Reorder remaining steps
            return filtered.map((step, index) => ({ ...step, order: index + 1 }));
        });
    }, []);

    /**
     * Reorder flow
     */
    const reorderFlow = useCallback((fromIndex: number, toIndex: number) => {
        setFlow(prev => {
            const newFlow = [...prev];
            const [removed] = newFlow.splice(fromIndex, 1);
            newFlow.splice(toIndex, 0, removed);
            // Update order numbers
            return newFlow.map((step, index) => ({ ...step, order: index + 1 }));
        });
    }, []);

    /**
     * Start simulation
     */
    const startSimulation = useCallback(() => {
        if (flow.length === 0) return;

        setSimulation(prev => ({ ...prev, isPlaying: true }));

        const interval = setInterval(() => {
            setSimulation(prev => {
                if (prev.currentStep >= flow.length - 1) {
                    // End of flow
                    clearInterval(interval);
                    return { ...prev, isPlaying: false };
                }

                const nextStep = prev.currentStep + 1;
                const nextElement = flow[nextStep].elementId;

                return {
                    ...prev,
                    currentStep: nextStep,
                    completed: new Set([...prev.completed, nextElement]),
                };
            });
        }, simulation.speed);

        setSimulationInterval(interval);
    }, [flow, simulation.speed]);

    /**
     * Pause simulation
     */
    const pauseSimulation = useCallback(() => {
        if (simulationInterval) {
            clearInterval(simulationInterval);
            setSimulationInterval(null);
        }
        setSimulation(prev => ({ ...prev, isPlaying: false }));
    }, [simulationInterval]);

    /**
     * Reset simulation
     */
    const resetSimulation = useCallback(() => {
        if (simulationInterval) {
            clearInterval(simulationInterval);
            setSimulationInterval(null);
        }
        setSimulation(prev => ({
            ...prev,
            currentStep: 0,
            isPlaying: false,
            completed: new Set(),
        }));
    }, [simulationInterval]);

    /**
     * Next step
     */
    const nextStep = useCallback(() => {
        setSimulation(prev => {
            if (prev.currentStep >= flow.length - 1) return prev;

            const nextStep = prev.currentStep + 1;
            const nextElement = flow[nextStep].elementId;

            return {
                ...prev,
                currentStep: nextStep,
                completed: new Set([...prev.completed, nextElement]),
            };
        });
    }, [flow]);

    /**
     * Previous step
     */
    const previousStep = useCallback(() => {
        setSimulation(prev => {
            if (prev.currentStep <= 0) return prev;

            const prevStep = prev.currentStep - 1;
            const prevElement = flow[prevStep].elementId;
            const newCompleted = new Set(prev.completed);
            newCompleted.delete(prevElement);

            return {
                ...prev,
                currentStep: prevStep,
                completed: newCompleted,
            };
        });
    }, [flow]);

    /**
     * Set simulation speed
     */
    const setSimulationSpeed = useCallback((speed: number) => {
        setSimulation(prev => ({ ...prev, speed }));

        // Restart interval if playing
        if (simulation.isPlaying && simulationInterval) {
            clearInterval(simulationInterval);
            startSimulation();
        }
    }, [simulation.isPlaying, simulationInterval, startSimulation]);

    /**
     * Generate acceptance criteria
     */
    const generateAcceptanceCriteria = useCallback((): string[] => {
        if (!parsedStory) return [];
        return UserStoryParser.generateAcceptanceCriteria(parsedStory);
    }, [parsedStory]);

    /**
     * Estimate complexity
     */
    const estimateComplexity = useCallback(() => {
        if (!parsedStory) {
            return { score: 0, level: 'low' as const, factors: [] };
        }
        return UserStoryParser.estimateComplexity(parsedStory);
    }, [parsedStory]);

    /**
     * Export as JSON
     */
    const exportAsJSON = useCallback((): string => {
        return JSON.stringify(
            {
                story: parsedStory,
                elements,
                rules,
                flow,
                acceptanceCriteria: generateAcceptanceCriteria(),
                complexity: estimateComplexity(),
            },
            null,
            2
        );
    }, [parsedStory, elements, rules, flow, generateAcceptanceCriteria, estimateComplexity]);

    /**
     * Export as Markdown
     */
    const exportAsMarkdown = useCallback((): string => {
        if (!parsedStory) return '';

        const complexity = estimateComplexity();
        const criteria = generateAcceptanceCriteria();

        let md = `# ${parsedStory.title}\n\n`;
        md += `## User Story\n\n`;
        md += `${parsedStory.description}\n\n`;
        md += `**Actor:** ${parsedStory.actor}\n\n`;
        md += `**Goal:** ${parsedStory.goal}\n\n`;

        md += `## Complexity\n\n`;
        md += `**Level:** ${complexity.level} (Score: ${complexity.score})\n\n`;
        if (complexity.factors.length > 0) {
            md += `**Factors:**\n`;
            complexity.factors.forEach(factor => {
                md += `- ${factor}\n`;
            });
            md += `\n`;
        }

        md += `## Wireframe Elements\n\n`;
        elements.forEach(element => {
            md += `### ${element.label}\n`;
            md += `- **Type:** ${element.type}\n`;
            if (element.description) {
                md += `- **Description:** ${element.description}\n`;
            }
            md += `\n`;
        });

        if (rules.length > 0) {
            md += `## Business Rules\n\n`;
            rules.forEach((rule, index) => {
                md += `${index + 1}. **${rule.description}**\n`;
                md += `   - Condition: ${rule.condition}\n`;
                md += `   - Action: ${rule.action}\n`;
                if (rule.appliesTo.length > 0) {
                    const elementLabels = rule.appliesTo
                        .map(id => elements.find(el => el.id === id)?.label)
                        .filter(Boolean);
                    md += `   - Applies to: ${elementLabels.join(', ')}\n`;
                }
                md += `\n`;
            });
        }

        md += `## User Flow\n\n`;
        flow.forEach((step, index) => {
            const element = elements.find(el => el.id === step.elementId);
            md += `${index + 1}. ${step.description}${element ? ` (${element.label})` : ''}\n`;
        });
        md += `\n`;

        md += `## Acceptance Criteria\n\n`;
        criteria.forEach((criterion, index) => {
            md += `${index + 1}. ${criterion}\n`;
        });

        return md;
    }, [parsedStory, elements, rules, flow, generateAcceptanceCriteria, estimateComplexity]);

    return {
        parseStory,
        parsedStory,
        isValid,
        errors,
        warnings,
        elements,
        addElement,
        updateElement,
        deleteElement,
        rules,
        addRule,
        updateRule,
        deleteRule,
        linkRuleToElement,
        unlinkRuleFromElement,
        flow,
        addFlowStep,
        updateFlowStep,
        deleteFlowStep,
        reorderFlow,
        simulation,
        startSimulation,
        pauseSimulation,
        resetSimulation,
        nextStep,
        previousStep,
        setSimulationSpeed,
        generateAcceptanceCriteria,
        estimateComplexity,
        exportAsJSON,
        exportAsMarkdown,
    };
}
