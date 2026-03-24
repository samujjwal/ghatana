/**
 * useUserJourney Hook
 * 
 * State management for user journey mapping
 * 
 * @doc.type hook
 * @doc.purpose User journey state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Emotion types for journey stages
 */
export type EmotionType = 'very-negative' | 'negative' | 'neutral' | 'positive' | 'very-positive';

/**
 * Touchpoint types
 */
export type TouchpointType = 'digital' | 'physical' | 'human';

/**
 * Journey touchpoint
 */
export interface JourneyTouchpoint {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Touchpoint name
     */
    name: string;

    /**
     * Touchpoint type
     */
    type: TouchpointType;

    /**
     * Channel (optional)
     */
    channel?: string;
}

/**
 * Journey pain point
 */
export interface JourneyPainPoint {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Pain point description
     */
    description: string;

    /**
     * Severity (1-3)
     */
    severity: 1 | 2 | 3;

    /**
     * Category (optional)
     */
    category?: string;
}

/**
 * Journey emotion
 */
export interface JourneyEmotion {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Emotion type
     */
    type: EmotionType;

    /**
     * Timestamp
     */
    timestamp: string;
}

/**
 * User quote
 */
export interface UserQuote {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Quote text
     */
    text: string;

    /**
     * Source (optional)
     */
    source?: string;
}

/**
 * Journey stage
 */
export interface JourneyStage {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Stage name
     */
    name: string;

    /**
     * Stage description
     */
    description?: string;

    /**
     * Touchpoints in this stage
     */
    touchpoints?: JourneyTouchpoint[];

    /**
     * Pain points in this stage
     */
    painPoints?: JourneyPainPoint[];

    /**
     * Emotions in this stage
     */
    emotions?: JourneyEmotion[];

    /**
     * User quotes in this stage
     */
    userQuotes?: UserQuote[];
}

/**
 * Options for useUserJourney hook
 */
export interface UseUserJourneyOptions {
    /**
     * Initial journey name
     */
    initialJourneyName?: string;

    /**
     * Initial persona
     */
    initialPersona?: string;
}

/**
 * Result of useUserJourney hook
 */
export interface UseUserJourneyResult {
    /**
     * Journey stages
     */
    stages: JourneyStage[];

    /**
     * Journey name
     */
    journeyName: string;

    /**
     * Persona
     */
    persona: string;

    /**
     * Set journey name
     */
    setJourneyName: (name: string) => void;

    /**
     * Set persona
     */
    setPersona: (persona: string) => void;

    /**
     * Add a journey stage
     */
    addStage: (name: string, description?: string) => string;

    /**
     * Update a stage
     */
    updateStage: (stageId: string, updates: Partial<Omit<JourneyStage, 'id'>>) => void;

    /**
     * Delete a stage
     */
    deleteStage: (stageId: string) => void;

    /**
     * Get stage by ID
     */
    getStage: (stageId: string) => JourneyStage | undefined;

    /**
     * Add touchpoint to stage
     */
    addTouchpoint: (stageId: string, touchpoint: Omit<JourneyTouchpoint, 'id'>) => void;

    /**
     * Delete touchpoint
     */
    deleteTouchpoint: (stageId: string, touchpointId: string) => void;

    /**
     * Add pain point to stage
     */
    addPainPoint: (stageId: string, painPoint: Omit<JourneyPainPoint, 'id'>) => void;

    /**
     * Delete pain point
     */
    deletePainPoint: (stageId: string, painPointId: string) => void;

    /**
     * Add emotion to stage
     */
    addEmotion: (stageId: string, emotion: EmotionType) => void;

    /**
     * Delete emotion
     */
    deleteEmotion: (stageId: string, emotionId: string) => void;

    /**
     * Add user quote to stage
     */
    addUserQuote: (stageId: string, quote: Omit<UserQuote, 'id'>) => void;

    /**
     * Delete user quote
     */
    deleteUserQuote: (stageId: string, quoteId: string) => void;

    /**
     * Analyze interview transcript with AI
     */
    analyzeTranscript: (transcript: string) => Promise<void>;

    /**
     * Generate heatmap data
     */
    generateHeatmap: () => Array<{ stageId: string; intensity: number }>;

    /**
     * Export journey as JSON
     */
    exportJourney: () => string;

    /**
     * Get total touchpoint count
     */
    getTouchpointCount: () => number;

    /**
     * Get total pain point count
     */
    getPainPointCount: () => number;

    /**
     * Get total quote count
     */
    getQuoteCount: () => number;
}

/**
 * User journey mapping hook
 */
export const useUserJourney = (options: UseUserJourneyOptions = {}): UseUserJourneyResult => {
    const { initialJourneyName = 'Customer Journey', initialPersona = 'End User' } = options;

    // State
    const [stages, setStages] = useState<JourneyStage[]>([]);
    const [journeyName, setJourneyName] = useState(initialJourneyName);
    const [persona, setPersona] = useState(initialPersona);

    // Generate unique ID
    const generateId = useCallback((prefix: string) => {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }, []);

    // Add stage
    const addStage = useCallback((name: string, description?: string): string => {
        const id = generateId('stage');
        const newStage: JourneyStage = {
            id,
            name,
            description,
            touchpoints: [],
            painPoints: [],
            emotions: [],
            userQuotes: [],
        };
        setStages(prev => [...prev, newStage]);
        return id;
    }, [generateId]);

    // Update stage
    const updateStage = useCallback((stageId: string, updates: Partial<Omit<JourneyStage, 'id'>>) => {
        setStages(prev =>
            prev.map(stage =>
                stage.id === stageId ? { ...stage, ...updates } : stage
            )
        );
    }, []);

    // Delete stage
    const deleteStage = useCallback((stageId: string) => {
        setStages(prev => prev.filter(stage => stage.id !== stageId));
    }, []);

    // Get stage
    const getStage = useCallback((stageId: string): JourneyStage | undefined => {
        return stages.find(stage => stage.id === stageId);
    }, [stages]);

    // Add touchpoint
    const addTouchpoint = useCallback((stageId: string, touchpoint: Omit<JourneyTouchpoint, 'id'>) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    const newTouchpoint: JourneyTouchpoint = {
                        ...touchpoint,
                        id: generateId('touchpoint'),
                    };
                    return {
                        ...stage,
                        touchpoints: [...(stage.touchpoints || []), newTouchpoint],
                    };
                }
                return stage;
            })
        );
    }, [generateId]);

    // Delete touchpoint
    const deleteTouchpoint = useCallback((stageId: string, touchpointId: string) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    return {
                        ...stage,
                        touchpoints: (stage.touchpoints || []).filter(tp => tp.id !== touchpointId),
                    };
                }
                return stage;
            })
        );
    }, []);

    // Add pain point
    const addPainPoint = useCallback((stageId: string, painPoint: Omit<JourneyPainPoint, 'id'>) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    const newPainPoint: JourneyPainPoint = {
                        ...painPoint,
                        id: generateId('painpoint'),
                    };
                    return {
                        ...stage,
                        painPoints: [...(stage.painPoints || []), newPainPoint],
                    };
                }
                return stage;
            })
        );
    }, [generateId]);

    // Delete pain point
    const deletePainPoint = useCallback((stageId: string, painPointId: string) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    return {
                        ...stage,
                        painPoints: (stage.painPoints || []).filter(pp => pp.id !== painPointId),
                    };
                }
                return stage;
            })
        );
    }, []);

    // Add emotion
    const addEmotion = useCallback((stageId: string, emotion: EmotionType) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    const newEmotion: JourneyEmotion = {
                        id: generateId('emotion'),
                        type: emotion,
                        timestamp: new Date().toISOString(),
                    };
                    return {
                        ...stage,
                        emotions: [...(stage.emotions || []), newEmotion],
                    };
                }
                return stage;
            })
        );
    }, [generateId]);

    // Delete emotion
    const deleteEmotion = useCallback((stageId: string, emotionId: string) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    return {
                        ...stage,
                        emotions: (stage.emotions || []).filter(e => e.id !== emotionId),
                    };
                }
                return stage;
            })
        );
    }, []);

    // Add user quote
    const addUserQuote = useCallback((stageId: string, quote: Omit<UserQuote, 'id'>) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    const newQuote: UserQuote = {
                        ...quote,
                        id: generateId('quote'),
                    };
                    return {
                        ...stage,
                        userQuotes: [...(stage.userQuotes || []), newQuote],
                    };
                }
                return stage;
            })
        );
    }, [generateId]);

    // Delete user quote
    const deleteUserQuote = useCallback((stageId: string, quoteId: string) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id === stageId) {
                    return {
                        ...stage,
                        userQuotes: (stage.userQuotes || []).filter(q => q.id !== quoteId),
                    };
                }
                return stage;
            })
        );
    }, []);

    // Analyze transcript (mock implementation - would integrate with AI service)
    const analyzeTranscript = useCallback(async (transcript: string): Promise<void> => {
        // This would integrate with an AI service to extract:
        // - Pain points from negative language
        // - Emotions from sentiment analysis
        // - Quotes from significant statements

        // Mock implementation: simple keyword detection
        const lines = transcript.split('\n').filter(line => line.trim());

        // Detect pain points
        const painKeywords = ['frustrated', 'difficult', 'confusing', 'annoying', 'problem', 'issue'];
        lines.forEach(line => {
            const lowerLine = line.toLowerCase();
            if (painKeywords.some(keyword => lowerLine.includes(keyword)) && stages.length > 0) {
                addPainPoint(stages[0].id, {
                    description: line,
                    severity: 2,
                    category: 'Extracted from transcript',
                });
            }
        });

        // Detect positive quotes
        const positiveKeywords = ['love', 'great', 'easy', 'helpful', 'like'];
        lines.forEach(line => {
            const lowerLine = line.toLowerCase();
            if (positiveKeywords.some(keyword => lowerLine.includes(keyword)) && stages.length > 0) {
                addUserQuote(stages[0].id, {
                    text: line,
                    source: 'Transcript analysis',
                });
            }
        });

        // Add emotions based on sentiment
        if (stages.length > 0) {
            const negativeCount = lines.filter(line =>
                painKeywords.some(keyword => line.toLowerCase().includes(keyword))
            ).length;

            if (negativeCount > 2) {
                addEmotion(stages[0].id, 'negative');
            } else if (negativeCount > 0) {
                addEmotion(stages[0].id, 'neutral');
            } else {
                addEmotion(stages[0].id, 'positive');
            }
        }
    }, [stages, addPainPoint, addUserQuote, addEmotion]);

    // Generate heatmap
    const generateHeatmap = useCallback((): Array<{ stageId: string; intensity: number }> => {
        return stages.map(stage => {
            const painPoints = stage.painPoints || [];
            const severity = painPoints.reduce((sum, pp) => sum + pp.severity, 0);
            const intensity = Math.min(severity / 3, 1); // Normalize to 0-1
            return { stageId: stage.id, intensity };
        });
    }, [stages]);

    // Export journey
    const exportJourney = useCallback((): string => {
        const journey = {
            name: journeyName,
            persona,
            stages: stages.map(stage => ({
                name: stage.name,
                description: stage.description,
                touchpoints: stage.touchpoints,
                painPoints: stage.painPoints,
                emotions: stage.emotions,
                userQuotes: stage.userQuotes,
            })),
            metadata: {
                exportedAt: new Date().toISOString(),
                touchpointCount: getTouchpointCount(),
                painPointCount: getPainPointCount(),
                quoteCount: getQuoteCount(),
            },
        };
        return JSON.stringify(journey, null, 2);
    }, [journeyName, persona, stages]);

    // Get touchpoint count
    const getTouchpointCount = useCallback((): number => {
        return stages.reduce((count, stage) => count + (stage.touchpoints?.length || 0), 0);
    }, [stages]);

    // Get pain point count
    const getPainPointCount = useCallback((): number => {
        return stages.reduce((count, stage) => count + (stage.painPoints?.length || 0), 0);
    }, [stages]);

    // Get quote count
    const getQuoteCount = useCallback((): number => {
        return stages.reduce((count, stage) => count + (stage.userQuotes?.length || 0), 0);
    }, [stages]);

    return {
        stages,
        journeyName,
        persona,
        setJourneyName,
        setPersona,
        addStage,
        updateStage,
        deleteStage,
        getStage,
        addTouchpoint,
        deleteTouchpoint,
        addPainPoint,
        deletePainPoint,
        addEmotion,
        deleteEmotion,
        addUserQuote,
        deleteUserQuote,
        analyzeTranscript,
        generateHeatmap,
        exportJourney,
        getTouchpointCount,
        getPainPointCount,
        getQuoteCount,
    };
};
