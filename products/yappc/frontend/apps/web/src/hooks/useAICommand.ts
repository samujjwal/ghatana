/**
 * useAICommand Hook
 *
 * Processes natural language commands and interacts with AI services.
 * Handles project creation, modifications, and navigation intents.
 *
 * @doc.type hook
 * @doc.purpose AI command processing
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useMutation } from '@tanstack/react-query';
import { useCreateProject, useWorkspaceContext } from './useWorkspaceData';
import { LifecyclePhase } from '../../../shared/types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export type AIResponseType = 'create' | 'modify' | 'generate' | 'navigate' | 'deploy' | 'help';

export interface AIResponse {
    type: AIResponseType;
    summary: string;
    details: {
        name?: string;
        projectType?: string;
        techStack?: string[];
        features?: string[];
        estimatedTime?: string;
        targetUrl?: string;
        lifecyclePhase?: LifecyclePhase;
        nextActions?: string[];
        canvasData?: {
            components: Array<{ type: string; label: string; x: number; y: number }>;
            connections: Array<{ from: string; to: string }>;
        };
    };
    confidence: number;
    suggestedAction?: () => void;
}

export interface UseAICommandResult {
    /** Process a natural language intent */
    processIntent: (intent: string) => Promise<void>;
    /** Current AI response */
    response: AIResponse | null;
    /** Whether AI is processing */
    isProcessing: boolean;
    /** Any error that occurred */
    error: Error | null;
    /** Update the response (for editing) */
    updateResponse: (updates: Partial<AIResponse['details']>) => void;
    /** Clear the current response */
    clearResponse: () => void;
    /** Confirm the suggested action */
    confirmAction: () => void;
}

// ============================================================================
// Intent Analysis
// ============================================================================

interface ParsedIntent {
    type: AIResponseType;
    projectName?: string;
    projectType?: string;
    features: string[];
    techStack: string[];
}

function parseIntent(intent: string): ParsedIntent {
    const lowerIntent = intent.toLowerCase();

    // Detect project type
    let projectType = 'FULL_STACK';
    if (lowerIntent.includes('api') || lowerIntent.includes('backend') || lowerIntent.includes('server')) {
        projectType = 'BACKEND';
    } else if (lowerIntent.includes('mobile') || lowerIntent.includes('app')) {
        projectType = 'MOBILE';
    } else if (lowerIntent.includes('library') || lowerIntent.includes('package') || lowerIntent.includes('component')) {
        projectType = 'UI';
    } else if (lowerIntent.includes('landing') || lowerIntent.includes('website') || lowerIntent.includes('page')) {
        projectType = 'UI';
    } else if (lowerIntent.includes('desktop') || lowerIntent.includes('windows') || lowerIntent.includes('macos')) {
        projectType = 'DESKTOP';
    }

    // Detect features
    const features: string[] = [];
    const featurePatterns = [
        { pattern: /auth|login|sign.?up|user/i, feature: 'User Authentication' },
        { pattern: /dashboard|admin|panel/i, feature: 'Dashboard' },
        { pattern: /payment|stripe|checkout/i, feature: 'Payment Integration' },
        { pattern: /search|filter/i, feature: 'Search & Filtering' },
        { pattern: /crud|create|edit|delete/i, feature: 'CRUD Operations' },
        { pattern: /api|rest|graphql/i, feature: 'API Layer' },
        { pattern: /database|db|storage/i, feature: 'Database' },
        { pattern: /upload|image|file/i, feature: 'File Uploads' },
        { pattern: /notification|email|alert/i, feature: 'Notifications' },
        { pattern: /chat|message|real.?time/i, feature: 'Real-time Features' },
        { pattern: /todo|task|list|kanban|project/i, feature: 'Task Management' },
        { pattern: /blog|post|article|content/i, feature: 'CMS Features' },
        { pattern: /shop|store|commerce|cart/i, feature: 'E-commerce' },
    ];

    for (const { pattern, feature } of featurePatterns) {
        if (pattern.test(intent)) {
            features.push(feature);
        }
    }

    // Smart inferences
    if (features.includes('Task Management') || features.includes('CMS Features') || features.includes('E-commerce')) {
        if (!features.includes('Database')) features.push('Database');
        if (!features.includes('CRUD Operations')) features.push('CRUD Operations');
    }

    // If no features detected, add defaults based on type
    if (features.length === 0) {
        if (projectType === 'FULL_STACK' || projectType === 'UI') {
            features.push('Responsive UI', 'Routing');
        } else if (projectType === 'BACKEND') {
            features.push('REST API', 'Database');
        }
    }

    // Detect tech stack
    const techStack: string[] = [];
    if (projectType === 'FULL_STACK' || projectType === 'UI' || projectType === 'MOBILE') {
        techStack.push('React');
    }
    if (projectType === 'BACKEND' || projectType === 'FULL_STACK' || features.includes('API Layer') || features.includes('Database')) {
        techStack.push('Node.js');
    }
    if (features.includes('Database') || projectType === 'BACKEND' || projectType === 'FULL_STACK') {
        techStack.push('PostgreSQL');
    }

    // Extract project name (simplified - look for quotes or "called X")
    let projectName: string | undefined;
    const quotedMatch = intent.match(/["']([^"']+)["']/);
    if (quotedMatch) {
        projectName = quotedMatch[1];
    } else {
        const calledMatch = intent.match(/called\s+(\w+)/i);
        if (calledMatch) {
            projectName = calledMatch[1];
        }
    }

    // Generate name from intent if not specified
    if (!projectName) {
        const words = intent
            .replace(/[^a-zA-Z\s]/g, '')
            .split(/\s+/)
            .filter(w => w.length > 3 && !['want', 'need', 'build', 'create', 'make', 'with', 'that', 'have', 'this', 'from'].includes(w.toLowerCase()))
            .slice(0, 2);

        if (words.length > 0) {
            projectName = words.map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join('');
        }
    }

    // Determine intent type
    let type: AIResponseType = 'create';
    if (lowerIntent.includes('deploy') || lowerIntent.includes('publish') || lowerIntent.includes('launch')) {
        type = 'deploy';
    } else if (lowerIntent.includes('change') || lowerIntent.includes('update') || lowerIntent.includes('modify') || lowerIntent.includes('edit')) {
        type = 'modify';
    } else if (lowerIntent.includes('add') || lowerIntent.includes('generate') || lowerIntent.includes('create component')) {
        type = 'generate';
    } else if (lowerIntent.includes('go to') || lowerIntent.includes('open') || lowerIntent.includes('show')) {
        type = 'navigate';
    } else if (lowerIntent.includes('help') || lowerIntent.includes('how') || lowerIntent.includes('what')) {
        type = 'help';
    }

    return {
        type,
        projectName,
        projectType,
        features,
        techStack,
    };
}

function generateProjectName(parsed: ParsedIntent): string {
    if (parsed.projectName) {
        return parsed.projectName;
    }

    const prefixes = ['My', 'New', 'Quick'];
    const typeNames: Record<string, string> = {
        FULL_STACK: 'WebApp',
        BACKEND: 'API',
        MOBILE: 'MobileApp',
        UI: 'Library',
        DESKTOP: 'DesktopApp',
    };

    const prefix = prefixes[Math.floor(Math.random() * prefixes.length)];
    const typeName = typeNames[parsed.projectType || 'FULL_STACK'] || 'Project';

    return `${prefix}${typeName}`;
}

function generateCanvasData(parsed: ParsedIntent) {
    const components: Array<{ type: string; label: string; x: number; y: number }> = [];
    let yOffset = 100;
    const xSpacing = 300;

    // Always start with entry point based on project type
    if (parsed.projectType === 'UI' || parsed.projectType === 'FULL_STACK') {
        components.push({ type: 'page', label: 'Home Page', x: 100, y: yOffset });
        yOffset += 150;
    }

    // Add authentication components
    if (parsed.features.includes('User Authentication')) {
        components.push({ type: 'page', label: 'Login Page', x: 100, y: yOffset });
        components.push({ type: 'api', label: 'Auth Service', x: 100 + xSpacing, y: yOffset });
        yOffset += 150;
    }

    // Add dashboard components
    if (parsed.features.includes('Dashboard')) {
        components.push({ type: 'page', label: 'Dashboard', x: 100, y: yOffset });
        components.push({ type: 'component', label: 'Stats Widget', x: 100 + xSpacing, y: yOffset });
        components.push({ type: 'component', label: 'Charts', x: 100 + xSpacing * 2, y: yOffset });
        yOffset += 150;
    }

    // Add CRUD components
    if (parsed.features.includes('CRUD Operations')) {
        components.push({ type: 'page', label: 'List View', x: 100, y: yOffset });
        components.push({ type: 'page', label: 'Detail View', x: 100 + xSpacing, y: yOffset });
        components.push({ type: 'api', label: 'CRUD API', x: 100 + xSpacing * 2, y: yOffset });
        yOffset += 150;
    }

    // Add payment components
    if (parsed.features.includes('Payment Integration')) {
        components.push({ type: 'page', label: 'Checkout', x: 100, y: yOffset });
        components.push({ type: 'api', label: 'Payment Service', x: 100 + xSpacing, y: yOffset });
        yOffset += 150;
    }

    // Add search components
    if (parsed.features.includes('Search & Filtering')) {
        components.push({ type: 'component', label: 'Search Bar', x: 100, y: yOffset });
        components.push({ type: 'component', label: 'Filter Panel', x: 100 + xSpacing, y: yOffset });
        components.push({ type: 'component', label: 'Results Grid', x: 100 + xSpacing * 2, y: yOffset });
        yOffset += 150;
    }

    // Add backend/API layer
    if (parsed.projectType === 'BACKEND' || parsed.projectType === 'FULL_STACK' || parsed.features.includes('API Layer')) {
        if (!components.some(c => c.label.includes('API'))) {
            components.push({ type: 'api', label: 'REST API', x: 100 + xSpacing, y: 100 });
        }
    }

    // Add database (positioned to the right)
    if (parsed.features.includes('Database') || parsed.projectType === 'BACKEND' || parsed.projectType === 'FULL_STACK') {
        if (!components.some(c => c.type === 'data')) {
            const avgY = components.length > 0
                ? components.reduce((sum, c) => sum + c.y, 0) / components.length
                : 250;
            components.push({ type: 'data', label: 'Database', x: 100 + xSpacing * 3, y: avgY });
        }
    }

    // Add mobile-specific screens
    if (parsed.projectType === 'MOBILE') {
        components.push({ type: 'page', label: 'Splash Screen', x: 100, y: 100 });
        components.push({ type: 'page', label: 'Main Navigation', x: 100, y: 250 });
        components.push({ type: 'page', label: 'Profile Screen', x: 100, y: 400 });
    }

    // If no components added, add a default structure
    if (components.length === 0) {
        components.push({ type: 'page', label: 'Main View', x: 100, y: 100 });
        components.push({ type: 'component', label: 'Core Component', x: 100 + xSpacing, y: 100 });
    }

    return {
        components,
        connections: [],
    };
}

function generateNextActions(parsed: ParsedIntent): string[] {
    const actions: string[] = [];

    // Project type specific actions
    if (parsed.projectType === 'UI' || parsed.projectType === 'FULL_STACK') {
        actions.push('Design component hierarchy');
        actions.push('Set up navigation structure');
    }

    if (parsed.projectType === 'BACKEND' || parsed.projectType === 'FULL_STACK') {
        actions.push('Define API endpoints');
        actions.push('Set up database schema');
    }

    if (parsed.projectType === 'MOBILE') {
        actions.push('Create screen flow diagram');
        actions.push('Define navigation patterns');
    }

    // Feature-specific actions
    if (parsed.features.includes('User Authentication')) {
        actions.push('Configure authentication flow');
    }

    if (parsed.features.includes('Database')) {
        actions.push('Design data models');
    }

    if (parsed.features.includes('Dashboard')) {
        actions.push('Plan dashboard layout');
    }

    // Generic fallbacks
    if (actions.length === 0) {
        actions.push('Define project requirements');
        actions.push('Create initial canvas diagram');
        actions.push('Set up project structure');
    }

    return actions.slice(0, 3); // Return top 3 actions
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useAICommand(): UseAICommandResult {
    const navigate = useNavigate();
    const { currentWorkspace } = useWorkspaceContext();
    const createProject = useCreateProject();

    const [response, setResponse] = useState<AIResponse | null>(null);
    const [error, setError] = useState<Error | null>(null);

    // Process intent mutation
    const { mutate: processIntentMutation, isPending: isProcessing } = useMutation({
        mutationFn: async (intent: string): Promise<AIResponse> => {
            // Simulate AI processing time for better UX
            await new Promise(resolve => setTimeout(resolve, 800 + Math.random() * 500));

            const parsed = parseIntent(intent);
            const projectName = generateProjectName(parsed);

            // Build response based on intent type
            switch (parsed.type) {
                case 'create':
                    return {
                        type: 'create',
                        summary: `Create: ${projectName}`,
                        details: {
                            name: projectName,
                            projectType: parsed.projectType,
                            techStack: parsed.techStack,
                            lifecyclePhase: LifecyclePhase.SHAPE,
                            nextActions: generateNextActions(parsed),
                            features: parsed.features,
                            estimatedTime: '~30 seconds',
                            canvasData: (() => {
                                const canvasData = generateCanvasData(parsed);
                                console.log('[AICommand] Generated canvas data:', canvasData);
                                return canvasData;
                            })(),
                        },
                        confidence: 0.85 + Math.random() * 0.1,
                    };

                case 'navigate':
                    return {
                        type: 'navigate',
                        summary: 'Navigate to project',
                        details: {
                            targetUrl: '/app',
                        },
                        confidence: 0.9,
                    };

                case 'help':
                    return {
                        type: 'help',
                        summary: 'How can I help?',
                        details: {
                            features: [
                                'Type what you want to build (e.g., "A blog with user auth")',
                                'Describe changes (e.g., "Add a dark theme")',
                                'Ask for help (e.g., "How do I deploy?")',
                            ],
                        },
                        confidence: 1.0,
                    };

                default:
                    return {
                        type: parsed.type,
                        summary: `Processing: ${intent}`,
                        details: {
                            features: parsed.features,
                        },
                        confidence: 0.7,
                    };
            }
        },
        onSuccess: (result) => {
            setResponse(result);
            setError(null);
        },
        onError: (err) => {
            setError(err instanceof Error ? err : new Error('Failed to process intent'));
            setResponse(null);
        },
    });

    const processIntent = useCallback(async (intent: string) => {
        processIntentMutation(intent);
    }, [processIntentMutation]);

    const clearResponse = useCallback(() => {
        setResponse(null);
        setError(null);
    }, []);

    const updateResponse = useCallback((updates: Partial<AIResponse['details']>) => {
        if (!response) return;
        setResponse({
            ...response,
            details: {
                ...response.details,
                ...updates,
            },
        });
    }, [response]);

    const confirmAction = useCallback(async () => {
        if (!response) return;

        if (response.type === 'create' && response.details.name) {
            try {
                const workspaceId = currentWorkspace?.id || 'default';
                const project = await createProject.mutateAsync({
                    name: response.details.name,
                    description: response.details.features ? response.details.features.join(', ') : '',
                    type: response.details.projectType || 'FULL_STACK',
                    ownerWorkspaceId: workspaceId,
                });

                // Generate canvas components from project details
                const elements: unknown[] = [];
                let xOffset = 50;
                let yOffset = 50;
                const rowHeight = 150;
                const colWidth = 280;

                // Generate components based on features and tech stack
                const componentTypes = [
                    { type: 'component', label: 'Frontend', color: '#3B82F6' },
                    { type: 'api', label: 'API Server', color: '#10B981' },
                    { type: 'data', label: 'Database', color: '#F59E0B' },
                ];

                // Add tech stack as initial components if available
                if (response.details.techStack && Array.isArray(response.details.techStack)) {
                    response.details.techStack.forEach((tech: string, index: number) => {
                        if (index < 3) {
                            const compType = componentTypes[index] || { type: 'component', label: tech };
                            elements.push({
                                id: `node-${Date.now()}-${index}`,
                                kind: 'node',
                                type: compType.type,
                                position: {
                                    x: xOffset + (index % 2) * colWidth,
                                    y: yOffset + Math.floor(index / 2) * rowHeight,
                                },
                                data: {
                                    label: tech || compType.label,
                                    description: `${tech || compType.label} component`,
                                },
                                selected: false,
                            });
                        }
                    });
                }

                // If we don't have enough elements, add default ones
                if (elements.length === 0) {
                    componentTypes.slice(0, 3).forEach((comp, index) => {
                        elements.push({
                            id: `node-${Date.now()}-${index}`,
                            kind: 'node',
                            type: comp.type,
                            position: {
                                x: xOffset + (index % 2) * colWidth,
                                y: yOffset + Math.floor(index / 2) * rowHeight,
                            },
                            data: {
                                label: comp.label,
                                description: `${comp.label} component`,
                            },
                            selected: false,
                        });
                    });
                }

                console.log('[AICommand] Generated canvas elements:', elements);

                const canvasState = {
                    elements,
                    connections: [],
                    selectedElements: [],
                    lifecyclePhase: LifecyclePhase.SHAPE,
                };

                // Save via API to database
                try {
                    const response = await fetch('/api/canvas', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            projectId: project.id,
                            canvasId: 'main',
                            data: canvasState,
                        }),
                    });

                    if (!response.ok) {
                        console.error('[AICommand] Failed to save canvas via API:', response.status);
                        // Fallback to localStorage
                        const snapshot = {
                            id: `snapshot-${Date.now()}`,
                            projectId: project.id,
                            canvasId: 'main',
                            version: 1,
                            timestamp: Date.now(),
                            data: canvasState,
                            checksum: '',
                        };
                        const storageKey = `yappc-canvas:${project.id}:main`;
                        localStorage.setItem(storageKey, JSON.stringify(snapshot));
                    } else {
                        console.log('[AICommand] Canvas saved to database successfully');
                    }
                } catch (err) {
                    console.error('[AICommand] Error saving canvas:', err);
                    // Fallback to localStorage
                    const snapshot = {
                        id: `snapshot-${Date.now()}`,
                        projectId: project.id,
                        canvasId: 'main',
                        version: 1,
                        timestamp: Date.now(),
                        data: canvasState,
                        checksum: '',
                    };
                    const storageKey = `yappc-canvas:${project.id}:main`;
                    localStorage.setItem(storageKey, JSON.stringify(snapshot));
                }

                // Navigate to the new project's canvas with state
                navigate(`/app/p/${project.id}/canvas`, {
                    state: {
                        newProject: true,
                        features: response.details.features,
                        techStack: response.details.techStack,
                    },
                });
                clearResponse();
            } catch (err) {
                setError(err instanceof Error ? err : new Error('Failed to create project'));
            }
        } else if (response.type === 'navigate' && response.details.targetUrl) {
            navigate(response.details.targetUrl);
            clearResponse();
        }
    }, [response, currentWorkspace, createProject, navigate, clearResponse]);

    return {
        processIntent,
        response,
        isProcessing,
        error,
        clearResponse,
        confirmAction,
        updateResponse,
    };
}

export default useAICommand;
