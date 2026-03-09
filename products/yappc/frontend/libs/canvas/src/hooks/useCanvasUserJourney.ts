/**
 * Consolidated Canvas User Journey Hook
 * 
 * Replaces: useUserJourney + journey features
 * Provides: User journey mapping
 */

import { useCallback, useState } from 'react';

export interface Persona {
  id: string;
  name: string;
  role: string;
  goals: string[];
}

export interface UserJourney {
  id: string;
  name: string;
  persona: Persona;
  stages: JourneyStage[];
  touchpoints: Touchpoint[];
}

export interface JourneyStage {
  id: string;
  name: string;
  description: string;
  emotions: string[];
  painPoints: string[];
}

export interface Touchpoint {
  id: string;
  stageId: string;
  name: string;
  channel: string;
  sentiment: 'positive' | 'neutral' | 'negative';
}

export interface UserJourneySpec {
  name: string;
  persona: Persona;
  stages: Omit<JourneyStage, 'id'>[];
}

export interface JourneyAnalysis {
  painPoints: string[];
  opportunities: string[];
  recommendations: string[];
}

export interface UseCanvasUserJourneyOptions {
  canvasId: string;
  persona?: Persona;
}

export interface UseCanvasUserJourneyReturn {
  journeys: UserJourney[];
  createJourney: (journey: UserJourneySpec) => Promise<UserJourney>;
  updateJourney: (id: string, updates: Partial<UserJourney>) => Promise<void>;
  deleteJourney: (id: string) => Promise<void>;
  analyzeJourney: (journeyId: string) => Promise<JourneyAnalysis>;
  isLoading: boolean;
  error: Error | null;
}

export function useCanvasUserJourney(
  options: UseCanvasUserJourneyOptions
): UseCanvasUserJourneyReturn {
  const { canvasId, persona } = options;

  const [journeys, setJourneys] = useState<UserJourney[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const createJourney = useCallback(async (spec: UserJourneySpec): Promise<UserJourney> => {
    const journey: UserJourney = {
      id: `journey-${Date.now()}`,
      name: spec.name,
      persona: spec.persona,
      stages: spec.stages.map((s, i) => ({ ...s, id: `stage-${i}` })),
      touchpoints: [],
    };
    setJourneys(prev => [...prev, journey]);
    return journey;
  }, []);

  const updateJourney = useCallback(
    async (id: string, updates: Partial<UserJourney>): Promise<void> => {
      setJourneys(prev => prev.map(j => (j.id === id ? { ...j, ...updates } : j)));
    },
    []
  );

  const deleteJourney = useCallback(async (id: string): Promise<void> => {
    setJourneys(prev => prev.filter(j => j.id !== id));
  }, []);

  const analyzeJourney = useCallback(async (journeyId: string): Promise<JourneyAnalysis> => {
    const journey = journeys.find(j => j.id === journeyId);
    if (!journey) throw new Error('Journey not found');

    return {
      painPoints: journey.stages.flatMap(s => s.painPoints),
      opportunities: ['Improve onboarding', 'Streamline checkout'],
      recommendations: ['Add live chat support', 'Simplify form fields'],
    };
  }, [journeys]);

  return {
    journeys,
    createJourney,
    updateJourney,
    deleteJourney,
    analyzeJourney,
    isLoading,
    error,
  };
}
