/**
 * AI Atoms Tests
 *
 * Tests the AI state atoms including copilot sessions, insights, and predictions.
 * Uses Jotai's createStore for isolated atom testing.
 */

import { createStore } from 'jotai';
import {
  copilotSessionAtom,
  copilotLoadingAtom,
  copilotErrorAtom,
  aiInsightsAtom,
  aiInsightsLoadingAtom,
  aiPredictionsAtom,
  appendCopilotMessageAtom,
  clearCopilotSessionAtom,
  dismissInsightAtom,
} from '../aiAtoms';
import type { CopilotSession, AIInsight, AIPrediction } from '../aiAtoms';

// ---------------------------------------------------------------------------
// copilotSessionAtom - Default Value
// ---------------------------------------------------------------------------

describe('copilotSessionAtom', () => {
  it('initializes with null session', () => {
    const store = createStore();
    const session = store.get(copilotSessionAtom);
    expect(session).toBeNull();
  });

  it('can store a copilot session', () => {
    const store = createStore();
    const newSession: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    store.set(copilotSessionAtom, newSession);
    const session = store.get(copilotSessionAtom);

    expect(session).not.toBeNull();
    expect(session?.id).toBe('session-1');
    expect(session?.projectId).toBe('proj-1');
  });

  it('preserves session across multiple accesses', () => {
    const store = createStore();
    const session: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [
        { id: 'msg-1', role: 'user', content: 'Hello', createdAt: '' },
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    store.set(copilotSessionAtom, session);
    const get1 = store.get(copilotSessionAtom);
    const get2 = store.get(copilotSessionAtom);

    expect(get1).toEqual(get2);
    expect(get1?.messages).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// copilotLoadingAtom - Default Value
// ---------------------------------------------------------------------------

describe('copilotLoadingAtom', () => {
  it('initializes with false', () => {
    const store = createStore();
    const isLoading = store.get(copilotLoadingAtom);
    expect(isLoading).toBe(false);
  });

  it('can toggle loading state', () => {
    const store = createStore();
    store.set(copilotLoadingAtom, true);
    expect(store.get(copilotLoadingAtom)).toBe(true);

    store.set(copilotLoadingAtom, false);
    expect(store.get(copilotLoadingAtom)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// copilotErrorAtom - Default Value
// ---------------------------------------------------------------------------

describe('copilotErrorAtom', () => {
  it('initializes with null error', () => {
    const store = createStore();
    const error = store.get(copilotErrorAtom);
    expect(error).toBeNull();
  });

  it('can store an error message', () => {
    const store = createStore();
    store.set(copilotErrorAtom, 'Network error');
    const error = store.get(copilotErrorAtom);
    expect(error).toBe('Network error');
  });

  it('can clear error by setting to null', () => {
    const store = createStore();
    store.set(copilotErrorAtom, 'Network error');
    store.set(copilotErrorAtom, null);
    expect(store.get(copilotErrorAtom)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// appendCopilotMessageAtom - Action Atom
// ---------------------------------------------------------------------------

describe('appendCopilotMessageAtom', () => {
  it('appends message to existing session', () => {
    const store = createStore();
    const session: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [
        { id: 'msg-1', role: 'user', content: 'Hello', createdAt: '' },
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    store.set(copilotSessionAtom, session);

    const newMessage = {
      id: 'msg-2',
      role: 'assistant' as const,
      content: 'Hi there!',
      createdAt: new Date().toISOString(),
    };

    store.set(appendCopilotMessageAtom, newMessage);
    const updated = store.get(copilotSessionAtom);

    expect(updated?.messages).toHaveLength(2);
    expect(updated?.messages[1].id).toBe('msg-2');
  });

  it('creates session if none exists', () => {
    const store = createStore();
    expect(store.get(copilotSessionAtom)).toBeNull();

    const message = {
      id: 'msg-1',
      role: 'assistant' as const,
      content: 'Starting conversation',
      createdAt: new Date().toISOString(),
    };

    store.set(appendCopilotMessageAtom, message);
    const session = store.get(copilotSessionAtom);

    expect(session).not.toBeNull();
    expect(session?.messages).toHaveLength(1);
  });

  it('maintains message order', () => {
    const store = createStore();
    for (let i = 1; i <= 5; i++) {
      const message = {
        id: `msg-${i}`,
        role: (i % 2 === 0 ? 'assistant' : 'user') as const,
        content: `Message ${i}`,
        createdAt: new Date().toISOString(),
      };
      store.set(appendCopilotMessageAtom, message);
    }

    const session = store.get(copilotSessionAtom);
    expect(session?.messages).toHaveLength(5);
    expect(session?.messages[0].id).toBe('msg-1');
    expect(session?.messages[4].id).toBe('msg-5');
  });

  it('preserves message content exactly', () => {
    const store = createStore();
    const message = {
      id: 'msg-1',
      role: 'user' as const,
      content: 'This is a longer message with special chars: @#$%',
      createdAt: new Date().toISOString(),
    };

    store.set(appendCopilotMessageAtom, message);
    const session = store.get(copilotSessionAtom);

    expect(session?.messages[0].content).toBe(message.content);
  });
});

// ---------------------------------------------------------------------------
// clearCopilotSessionAtom - Action Atom
// ---------------------------------------------------------------------------

describe('clearCopilotSessionAtom', () => {
  it('clears the copilot session', () => {
    const store = createStore();
    const session: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [
        { id: 'msg-1', role: 'user', content: 'Hello', createdAt: '' },
        { id: 'msg-2', role: 'assistant', content: 'Hi!', createdAt: '' },
      ],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    store.set(copilotSessionAtom, session);
    expect(store.get(copilotSessionAtom)).not.toBeNull();

    store.set(clearCopilotSessionAtom, true);
    expect(store.get(copilotSessionAtom)).toBeNull();
  });

  it('clears loading and error states', () => {
    const store = createStore();
    const session: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    store.set(copilotSessionAtom, session);
    store.set(copilotLoadingAtom, true);
    store.set(copilotErrorAtom, 'Some error');

    store.set(clearCopilotSessionAtom, true);

    expect(store.get(copilotSessionAtom)).toBeNull();
    expect(store.get(copilotLoadingAtom)).toBe(false);
    expect(store.get(copilotErrorAtom)).toBeNull();
  });

  it('is idempotent when session already null', () => {
    const store = createStore();
    expect(store.get(copilotSessionAtom)).toBeNull();

    store.set(clearCopilotSessionAtom, true);
    expect(store.get(copilotSessionAtom)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// aiInsightsAtom - Default Value
// ---------------------------------------------------------------------------

describe('aiInsightsAtom', () => {
  it('initializes as empty array', () => {
    const store = createStore();
    const insights = store.get(aiInsightsAtom);
    expect(insights).toEqual([]);
  });

  it('can store multiple insights', () => {
    const store = createStore();
    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Performance Issue',
        description: 'High CPU usage detected',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'insight-2',
        projectId: 'proj-1',
        title: 'Code Smell',
        description: 'Duplicate code in module X',
        severity: 'medium',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiInsightsAtom, insights);
    const retrieved = store.get(aiInsightsAtom);

    expect(retrieved).toHaveLength(2);
    expect(retrieved[0].id).toBe('insight-1');
    expect(retrieved[1].severity).toBe('medium');
  });
});

// ---------------------------------------------------------------------------
// aiInsightsLoadingAtom - Default Value
// ---------------------------------------------------------------------------

describe('aiInsightsLoadingAtom', () => {
  it('initializes with false', () => {
    const store = createStore();
    const isLoading = store.get(aiInsightsLoadingAtom);
    expect(isLoading).toBe(false);
  });

  it('can track loading state', () => {
    const store = createStore();
    store.set(aiInsightsLoadingAtom, true);
    expect(store.get(aiInsightsLoadingAtom)).toBe(true);

    store.set(aiInsightsLoadingAtom, false);
    expect(store.get(aiInsightsLoadingAtom)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// dismissInsightAtom - Action Atom
// ---------------------------------------------------------------------------

describe('dismissInsightAtom', () => {
  it('removes insight by id', () => {
    const store = createStore();
    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Issue 1',
        description: 'Description 1',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'insight-2',
        projectId: 'proj-1',
        title: 'Issue 2',
        description: 'Description 2',
        severity: 'medium',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiInsightsAtom, insights);
    store.set(dismissInsightAtom, 'insight-1');

    const remaining = store.get(aiInsightsAtom);
    expect(remaining).toHaveLength(1);
    expect(remaining[0].id).toBe('insight-2');
  });

  it('handles non-existent insight id gracefully', () => {
    const store = createStore();
    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Issue',
        description: 'Desc',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiInsightsAtom, insights);
    store.set(dismissInsightAtom, 'nonexistent');

    const retrieved = store.get(aiInsightsAtom);
    expect(retrieved).toHaveLength(1);
  });

  it('can dismiss all insights one by one', () => {
    const store = createStore();
    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Issue 1',
        description: 'Desc 1',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'insight-2',
        projectId: 'proj-1',
        title: 'Issue 2',
        description: 'Desc 2',
        severity: 'medium',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'insight-3',
        projectId: 'proj-1',
        title: 'Issue 3',
        description: 'Desc 3',
        severity: 'low',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiInsightsAtom, insights);

    store.set(dismissInsightAtom, 'insight-1');
    expect(store.get(aiInsightsAtom)).toHaveLength(2);

    store.set(dismissInsightAtom, 'insight-2');
    expect(store.get(aiInsightsAtom)).toHaveLength(1);

    store.set(dismissInsightAtom, 'insight-3');
    expect(store.get(aiInsightsAtom)).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// aiPredictionsAtom - Default Value
// ---------------------------------------------------------------------------

describe('aiPredictionsAtom', () => {
  it('initializes as empty array', () => {
    const store = createStore();
    const predictions = store.get(aiPredictionsAtom);
    expect(predictions).toEqual([]);
  });

  it('can store multiple predictions', () => {
    const store = createStore();
    const predictions: AIPrediction[] = [
      {
        id: 'pred-1',
        projectId: 'proj-1',
        description: 'Project will miss deadline',
        confidence: 0.85,
        recommendation: 'Increase team capacity',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'pred-2',
        projectId: 'proj-1',
        description: 'Code quality degrading',
        confidence: 0.72,
        recommendation: 'Review PR process',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiPredictionsAtom, predictions);
    const retrieved = store.get(aiPredictionsAtom);

    expect(retrieved).toHaveLength(2);
    expect(retrieved[0].confidence).toBe(0.85);
    expect(retrieved[1].recommendation).toContain('Review');
  });

  it('handles predictions with varying confidence levels', () => {
    const store = createStore();
    const predictions: AIPrediction[] = [
      {
        id: 'pred-1',
        projectId: 'proj-1',
        description: 'High confidence',
        confidence: 0.95,
        recommendation: 'Act immediately',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'pred-2',
        projectId: 'proj-1',
        description: 'Low confidence',
        confidence: 0.45,
        recommendation: 'Monitor',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(aiPredictionsAtom, predictions);
    const retrieved = store.get(aiPredictionsAtom);

    const highConf = retrieved.filter((p) => p.confidence > 0.8);
    const lowConf = retrieved.filter((p) => p.confidence < 0.5);

    expect(highConf).toHaveLength(1);
    expect(lowConf).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------
// Integration - Multiple Atoms Together
// ---------------------------------------------------------------------------

describe('AI Atoms - Integration', () => {
  it('manages copilot and insights independently', () => {
    const store = createStore();

    const session: CopilotSession = {
      id: 'session-1',
      projectId: 'proj-1',
      messages: [],
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Issue',
        description: 'Desc',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
    ];

    store.set(copilotSessionAtom, session);
    store.set(aiInsightsAtom, insights);

    expect(store.get(copilotSessionAtom)?.id).toBe('session-1');
    expect(store.get(aiInsightsAtom)).toHaveLength(1);

    store.set(clearCopilotSessionAtom, true);

    expect(store.get(copilotSessionAtom)).toBeNull();
    expect(store.get(aiInsightsAtom)).toHaveLength(1);
  });

  it('handles complete AI state lifecycle', () => {
    const store = createStore();

    // Start copilot session
    const message1 = {
      id: 'msg-1',
      role: 'user' as const,
      content: 'Analyze this project',
      createdAt: new Date().toISOString(),
    };
    store.set(appendCopilotMessageAtom, message1);
    expect(store.get(copilotSessionAtom)?.messages).toHaveLength(1);

    // Simulate loading
    store.set(copilotLoadingAtom, true);
    expect(store.get(copilotLoadingAtom)).toBe(true);

    // Receive response
    store.set(copilotLoadingAtom, false);
    const message2 = {
      id: 'msg-2',
      role: 'assistant' as const,
      content: 'Here are insights...',
      createdAt: new Date().toISOString(),
    };
    store.set(appendCopilotMessageAtom, message2);

    // Update insights from analysis
    const insights: AIInsight[] = [
      {
        id: 'insight-1',
        projectId: 'proj-1',
        title: 'Finding',
        description: 'Analysis result',
        severity: 'high',
        createdAt: new Date().toISOString(),
      },
    ];
    store.set(aiInsightsAtom, insights);

    // Verify state
    expect(store.get(copilotSessionAtom)?.messages).toHaveLength(2);
    expect(store.get(aiInsightsAtom)).toHaveLength(1);
    expect(store.get(copilotLoadingAtom)).toBe(false);

    // Clear session
    store.set(clearCopilotSessionAtom, true);
    expect(store.get(copilotSessionAtom)).toBeNull();
    expect(store.get(aiInsightsAtom)).toHaveLength(1);
  });
});
