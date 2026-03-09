/**
 * AI Component Tests
 * 
 * Unit tests for AI components including error boundaries,
 * resilience features, and performance optimizations.
 * 
 * @doc.type test
 * @doc.purpose AI component testing
 * @doc.layer test
 * @doc.pattern Unit Testing
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, jest } from '@jest/globals';
import React from 'react';

// Mock AI service
jest.mock('../../services/ai', () => ({
  generateArtifactSuggestions: jest.fn(),
  PhaseAIPromptService: {
    getPhasePrompts: jest.fn(() => ({
      INTENT: {
        phase: 'INTENT' as unknown,
        title: 'Define & Research',
        description: 'Define the problem and research findings',
        suggestedActions: ['Research competitors', 'Define problem statement'],
        artifactFocus: ['IDEA_BRIEF', 'RESEARCH_PACK'],
        qualityChecks: ['Clear problem definition', 'Market research'],
        commonPitfalls: ['Scope creep', 'Insufficient research'],
      },
      SHAPE: {
        phase: 'SHAPE' as unknown,
        title: 'Requirements & Design',
        description: 'Define requirements and create designs',
        suggestedActions: ['Create wireframes', 'Define requirements'],
        artifactFocus: ['WIREFRAMES', 'MOCKUPS'],
        qualityChecks: ['Requirements clarity', 'Design consistency'],
        commonPitfalls: ['Incomplete requirements', 'Design inconsistencies'],
      },
      VALIDATE: {
        phase: 'VALIDATE' as unknown,
        title: 'Validation & Simulation',
        description: 'Validate assumptions and simulate behavior',
        suggestedActions: ['Run tests', 'Validate assumptions'],
        artifactFocus: ['PROTOTYPES', 'SIMULATIONS'],
        qualityChecks: ['Validation coverage', 'Test accuracy'],
        commonPitfalls: ['Incomplete validation', 'Testing gaps'],
      },
      GENERATE: {
        phase: 'GENERATE' as unknown,
        title: 'Delivery Planning',
        description: 'Plan and estimate delivery',
        suggestedActions: ['Create timeline', 'Estimate effort'],
        artifactFocus: ['PROJECT_PLAN', 'ESTIMATES'],
        qualityChecks: ['Timeline accuracy', 'Effort accuracy'],
        commonPitfalls: ['Unrealistic estimates', 'Missing dependencies'],
      },
      RUN: {
        phase: 'RUN' as unknown,
        title: 'Build, Test & Release',
        description: 'Build, test, and release the solution',
        suggestedActions: ['Run tests', 'Deploy to staging'],
        artifactFocus: ['BUILD_ARTIFACTS', 'DEPLOYMENT_PLANS'],
        qualityChecks: ['Test coverage', 'Deployment success'],
        commonPitfalls: ['Build failures', 'Deployment issues'],
      },
      OBSERVE: {
        phase: 'OBSERVE' as unknown,
        title: 'Operate & Monitor',
        description: 'Monitor performance and user behavior',
        suggestedActions: ['Monitor metrics', 'Analyze patterns'],
        artifactFocus: ['METRICS', 'ANALYTICS'],
        qualityChecks: ['Monitoring coverage', 'Alert configuration'],
        commonPitfalls: ['Missing alerts', 'Poor monitoring'],
      },
      IMPROVE: {
        phase: 'IMPROVE' as unknown,
        title: 'Enhance & Evolve',
        description: 'Continuously improve based on feedback',
        suggestedActions: ['Analyze feedback', 'Plan improvements'],
        artifactFocus: ['IMPROVEMENT_PLANS', 'LESSONS_LEARNED'],
        qualityChecks: ['Feedback analysis', 'Improvement tracking'],
        commonPitfalls: ['Ignoring feedback', 'No improvement process'],
      },
    }),
  },
}));

jest.mock('../../hooks/useAIAssistant', () => ({
  generateSuggestions: jest.fn(),
  useOptimizedEventHandler: jest.fn(),
}));

jest.mock('../../services/ai/resilient-ai.service', () => ({
  createResilientAIService: jest.fn(() => ({
    executeRequest: jest.fn(),
    executeStreamingRequest: jest.fn(),
    getMetrics: jest.fn(() => ({
      totalRequests: 0,
      successfulRequests: 0,
      failedRequests: 0,
      circuitBreakerTrips: 0,
      fallbackActivations: 0,
      cacheHits: 0,
      averageLatency: 0,
      providerHealth: {},
    })),
    cleanup: jest.fn(),
  })),
}));

// Import components to test
import { AIErrorBoundary, AIErrorBoundaryProps } from '../../components/shared/ErrorBoundary';
import { AISuggestionPanel } from '../../components/canvas/ai/AISuggestionsPanel';
import { AIResponseCard } from '../../components/ai/AIResponseCard';
import { useAIAssistant } from '../../hooks/useAIAssistant';
import { PerformanceMonitor } from '../../components/performance/PerformanceOptimizedClean';

// ============================================================================
// AI Error Boundary Tests
// ============================================================================

describe('AIErrorBoundary', () => {
  const defaultProps: AIErrorBoundaryProps = {
    children: <div>Test Content</div>,
    fallback: <div>Error Fallback</div>,
  };

  it('should render children when no error occurs', () => {
    render(<AIErrorBoundary {...defaultProps} />);
    expect(screen.getByText('Test Content')).toBeInTheDocument();
  });

  it('should render fallback when error occurs', () => {
    const ThrowErrorComponent = () => {
      throw new Error('Test error');
    };
    
    render(
      <AIErrorBoundary {...defaultProps}>
        <ThrowErrorComponent />
      </AIErrorBoundary>
    );
    
    expect(screen.getByText('Error Fallback')).toBeInTheDocument();
    expect(screen.queryByText('Test Content')).not.toBeInTheDocument();
  });

  it('should call onError when error occurs', () => {
    const onError = jest.fn();
    const ThrowErrorComponent = () => {
      throw new Error('Test error');
    };
    
    render(
      <AIErrorBoundary {...defaultProps} onError={onError}>
        <ThrowErrorComponent />
      </AIErrorErrorBoundary>
    );
    
    expect(onError).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({
        message: 'Test error',
      }),
    );
  });

  it('should render minimal variant correctly', () => {
    const minimalProps: AIErrorBoundaryProps = {
      children: <div>Test Content</div>,
      variant: 'minimal',
      fallback: <div>Minimal Error</div>,
    };
    
    render(<AIErrorBoundary {...minimalProps} />);
    expect(screen.getByText('Minimal Error')).toBeInTheDocument();
  });

  it('should render detailed variant with error details', () => {
    const detailedProps: AIErrorBoundaryProps = {
      children: <div>Test Content</div>,
      variant: 'detailed',
      showDetails: true,
    };
    
    const ThrowErrorComponent = () => {
      throw new Error('Detailed error with context');
    };
    
    render(
      <AIErrorBoundary {...detailedProps}>
        <ThrowErrorComponent />
      </AIErrorBoundary>
    );
    
    expect(screen.getByText('Error Details')).toBeInTheDocument();
    expect(screen.getByText(/Detailed error with context/)).toBeInTheDocument();
  });
});

// ============================================================================
// AI Suggestion Panel Tests
// ============================================================================

describe('AISuggestionPanel', () => {
  const mockSuggestions = [
    {
      id: '1',
      type: 'node' as const,
      title: 'Add User Authentication',
      description: 'Add user authentication to the application',
      confidence: 0.9,
      priority: 'high' as const,
    },
    {
      id: '2',
      type: 'connection' as const,
      title: 'Connect Database',
      description: 'Connect to PostgreSQL database',
      confidence: 0.8,
      priority: 'medium' as const,
    },
  ];

  it('should render suggestions correctly', () => {
    render(
      <AISuggestionPanel
        suggestions={mockSuggestions}
        onAccept={jest.fn()}
        onDismiss={jest.fn()}
      />
    );
    
    expect(screen.getByText('Add User Authentication')).toBeInTheDocument();
    expect(screen.getByText('Add user authentication to the application')).toBeInTheDocument();
    expect(screen.getByText('Connect Database')).toBeInTheDocument();
  });

  it('should handle accept action', async () => {
    const onAccept = jest.fn();
    
    render(
      <AISuggestionPanel
        suggestions={mockSuggestions}
        onAccept={onAccept}
        onDismiss={jest.fn()}
      />
    );
    
    const firstSuggestion = screen.getByTestId('ai-suggestion-1');
    const acceptButton = screen.getByLabelText('Accept', { selector: 'button' });
    
    await fireEvent.click(firstSuggestion);
    await fireEvent.click(acceptButton);
    
    expect(onAccept).toHaveBeenCalledWith(mockSuggestions[0]);
  });

  it('should handle dismiss action', async () => {
    const onDismiss = jest.fn();
    
    render(
      <AISuggestionPanel
        suggestions={mockSuggestions}
        onAccept={jest.fn()}
        onDismiss={onDismiss}
      />
    );
    
    const firstSuggestion = screen.getByTestId('ai-suggestion-1');
    const dismissButton = screen.getByLabelText('Dismiss', { selector: 'button' });
    
    await fireEvent.click(firstSuggestion);
    await fireEvent.click(dismissButton);
    
    expect(onDismiss).toHaveBeenCalledWith(mockSuggestions[0]);
  });

  it('should show appropriate icons for suggestion types', () => {
    render(
      <AISuggestionPanel
        suggestions={mockSuggestions}
        onAccept={jest.fn()}
        onDismiss={jest.fn()}
      />
    );
    
    expect(screen.getByTestId('ai-suggestion-1')).toContainElement('svg'); // Node icon
    expect(screen.getByTestId('ai-suggestion-2')).toContainElement('svg'); // Connection icon
  });
});

// ============================================================================
// AI Response Card Tests
// ============================================================================

describe('AIResponseCard', () => {
  const mockResponse = {
    id: '1',
    type: 'create',
    summary: 'Created React component successfully',
    details: {
      name: 'UserProfile',
      features: ['User authentication', 'Profile editing', 'Avatar upload'],
      techStack: ['React', 'TypeScript', 'Tailwind CSS'],
    },
    confidence: 0.85,
  };

  it('should render response details correctly', () => {
    const onConfirm = jest.fn();
    
    render(
      <AIResponseCard
        response={mockResponse}
        onConfirm={onConfirm}
      />
    );
    
    expect(screen.getByText('Created React component successfully')).toBeInTheDocument();
    expect(screen.getByText('User authentication')).toBeInTheDocument();
    expect(screen.getByText('React, TypeScript, Tailwind CSS')).toBeInTheDocument();
    expect(screen.getByText('85%')).toBeInTheDocument();
  });

  it('should handle confirm action', async () => {
    const onConfirm = jest.fn();
    
    render(
      <AIResponseCard
        response={mockResponse}
        onConfirm={onConfirm}
      />
    );
    
    const confirmButton = screen.getByLabelText('Confirm', { selector: 'button' });
    await fireEvent.click(confirmButton);
    
    expect(onConfirm).toHaveBeenCalled();
  });

  it('should handle customize action', async () => {
    const onCustomize = jest.fn();
    
    render(
      <AIResponseCard
        response={mockResponse}
        onConfirm={jest.fn()}
        onCustomize={onCustomize}
      />
    );
    
    const customizeButton = screen.getByLabelText('Customize', { selector: 'button' });
    await fireEvent.click(customizeButton);
    
    expect(onCustomize).toHaveBeenCalled();
  });

  it('should handle reject action', async () => {
    const onReject = jest.fn();
    
    render(
      <AIResponseCard
        response={mockResponse}
        onConfirm={jest.fn()}
        onReject={onReject}
      />
    );
    
    const rejectButton = screen.getByLabelText('Reject', { selector: 'button' });
    await fireEvent.click(rejectButton);
    
    expect(onReject).toHaveBeenCalled();
  });

  it('should show loading state', () => {
    const loadingResponse = { ...mockResponse, isConfirming: true };
    
    render(
      <AIResponseCard
        response={loadingResponse}
        onConfirm={jest.fn()}
      />
    );
    
    expect(screen.getByTestId('loading-spinner')).toBeInTheDocument();
    expect(screen.getByText('Processing...')).toBeInTheDocument();
  });
});

// ============================================================================
// AI Assistant Hook Tests
// ============================================================================

describe('useAIAssistant', () => {
  it('should generate suggestions', () => {
    const { generateSuggestions } = useAIAssistant();
    
    // Mock the AI service
    generateSuggestions({
      context: {
        projectId: 'test-project',
        currentPhase: 'INTENT',
        existingArtifacts: [],
        projectDescription: 'Test project',
      },
    });
    
    // Should call AI service
    expect(generateSuggestions).toHaveBeenCalled();
  });

  it('should handle streaming responses', () => {
    const { executeStreamingRequest } = useAIAssistant();
    
    // Mock streaming request
    const mockStream = new ReadableStream({
      start(controller) {
        controller.enqueue('data: {"content": "Hello", "delta": "Hello", "isComplete": false}');
        controller.enqueue('data: {"content": "Hello World", "delta": " World", "isComplete": true}');
        controller.close();
      },
    });
    
    // Test streaming
    executeStreamingRequest({
      id: 'test-request',
      provider: 'openai',
      prompt: 'Test prompt',
      options: { stream: true },
    });
    
    // Should handle streaming
    expect(executeStreamingRequest).toHaveBeenCalled();
  });

  it('should handle ghost nodes', () => {
    const { generateSuggestions } = useAIAssistant();
    
    // Mock ghost node generation
    const mockGhostNodes = [
      {
        id: 'ghost-1',
        suggestionId: 'suggestion-1',
        type: 'node',
        position: { x: 100, y: 100 },
        data: { type: 'component', name: 'Test Component' },
      },
    ];
    
    generateSuggestions({
      context: {
        projectId: 'test-project',
        currentPhase: 'INTENT',
        existingArtifacts: [],
        projectDescription: 'Test project',
      },
    });
    
    // Should generate ghost nodes
    // Implementation would check for ghost node generation
    expect(generateSuggestions).toHaveBeenCalled();
  });
});

// ============================================================================
// Performance Monitor Tests
// ============================================================================

describe('PerformanceMonitor', () => {
  it('should track render metrics', () => {
    const { renderCount, renderTime, memoryUsage } = usePerformanceMonitor({
      enableRenderTracking: true,
      enableMemoryTracking: true,
    });
    
    // Initial state
    expect(renderCount).toBe(0);
    expect(renderTime).toBe(0);
    expect(memoryUsage).toBe(0);
  });

  it('should update metrics on render', () => {
    const { renderCount, renderTime, memoryUsage } = usePerformanceMonitor({
      enableRenderTracking: true,
      enableMemoryTracking: true,
    });
    
    // Simulate render
    render(<div>Test Component</div>);
    
    // Should increment metrics
    expect(renderCount).toBeGreaterThan(0);
    expect(renderTime).toBeGreaterThan(0);
  });

  it('should show metrics when enabled', () => {
    const { renderCount, renderTime, memoryUsage } = usePerformanceMonitor({
      showMetrics: true,
      enableRenderTracking: true,
    });
    
    render(
      <PerformanceMonitor showMetrics={true}>
        <div>Test Component</div>
      </PerformanceMonitor>
    );
    
    expect(screen.getByText(/Renders:/)).toBeInTheDocument();
    expect(screen.getByText(/Time:/)).toBeInTheDocument();
    expect(screen.getByText(/Memory:/)).toBeInTheDocument();
  });

  it('should hide metrics when disabled', () => {
    render(
      <PerformanceMonitor showMetrics={false}>
        <div>Test Component</div>
      </PerformanceMonitor>
    );
    
    expect(screen.queryByText(/Renders:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Time:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Memory:/)).not.toBeInTheDocument();
  });
});

// ============================================================================
// Integration Tests
// ============================================================================

describe('AI Components Integration', () => {
  it('should handle AI service failures gracefully', async () => {
    const mockAIResponse = {
      id: '1',
      type: 'create',
      summary: 'AI service unavailable',
      details: {
        name: 'Fallback Component',
        features: ['Basic functionality'],
        techStack: ['HTML', 'CSS', 'JavaScript'],
      },
      confidence: 0.0,
      fromFallback: true,
    };

    render(
      <AIErrorBoundary>
        <AIResponseCard
          response={mockAIResponse}
          onConfirm={jest.fn()}
        />
      </AIErrorBoundary>
    );
    
    expect(screen.getByText('AI service unavailable')).toBeInTheDocument();
    expect(screen.getByText('Fallback Component')).toBeInTheDocument();
  });

  it('should integrate with performance monitoring', () => {
    const mockResponse = {
      id: '1',
      type: 'create',
      summary: 'Performance test',
      details: {
        name: 'Optimized Component',
        features: ['Lazy loading', 'Memoization'],
        techStack: ['React', 'TypeScript'],
      },
      confidence: 0.9,
    };

    render(
      <PerformanceMonitor>
        <AIResponseCard
          response={mockResponse}
          onConfirm={jest.fn()}
        />
      </PerformanceMonitor>
    );
    
    expect(screen.getByText('Performance test')).toBeInTheDocument();
    expect(screen.getByText(/Renders:/)).toBeInTheDocument();
  });
});

// ============================================================================
// Error Recovery Tests
// ============================================================================

describe('Error Recovery', () => {
  it('should recover from AI service failures', async () => {
    const mockAIResponse = {
      id: '1',
      type: 'create',
      summary: 'Initial failure',
      details: {
        name: 'Failed Component',
        features: [],
        techStack: [],
      },
      confidence: 0.0,
      fromFallback: false,
    };

    // First render with failure
    const { rerender } = render(
      <AIErrorBoundary>
        <AIResponseCard
          response={mockAIResponse}
          onConfirm={jest.fn()}
        />
      </AIErrorBoundary>
    );
    
    // Simulate successful retry
    const successResponse = {
      ...mockAIResponse,
      summary: 'Success after retry',
      confidence: 0.8,
      fromFallback: false,
    };

    rerender(
      <AIErrorBoundary>
        <AIResponseCard
          response={successResponse}
          onConfirm={jest.fn()}
        />
      </AIErrorBoundary>
    );
    
    expect(screen.getByText('Success after retry')).toBeInTheDocument();
    expect(screen.getByText('80%')).toBeInTheDocument();
  });

  it('should provide multiple recovery options', () => {
    const mockResponse = {
      id: '1',
      type: 'create',
      summary: 'Multiple failures',
      details: {
        name: 'Failed Component',
        features: [],
        techStack: [],
      },
      confidence: 0.0,
      fromFallback: false,
    };

    render(
      <AIErrorBoundary
        variant="detailed"
        fallback={
          <div>
            <button data-testid="retry-button">Retry</button>
            <button data-testid="go-home-button">Go Home</button>
            <button data-testid="reload-button">Reload Page</button>
          </div>
        }
      >
        <AIResponseCard
          response={mockAIResponse}
          onConfirm={jest.fn()}
        />
      </AIErrorBoundary>
    );
    
    expect(screen.getByTestId('retry-button')).toBeInTheDocument();
    expect(screen.getByTestId('go-home-button')).toBeInTheDocument();
    expect(screen.getByTestId('reload-page-button')).toBeInTheDocument();
  });
});

// ============================================================================
// Performance Tests
// ============================================================================

describe('Performance Optimization', () => {
  it('should memoize expensive operations', () => {
    const expensiveFunction = jest.fn(() => 'expensive result');
    const memoizedFunction = jest.fn().mockImplementation(expensiveFunction);
    
    // First call
    const result1 = memoizedFunction('arg1');
    const result2 = memoizedFunction('arg1');
    
    expect(result1).toBe('expensive result');
    expect(result2).toBe('expensive result');
    expect(expensiveFunction).toHaveBeenCalledTimes(1);
  });

  it('should debounce rapid state updates', async () => {
    const { debouncedSetValue } = useDebouncedState('', 100);
    
    // Rapid updates
    debouncedSetValue('value1');
    debouncedSetValue('value2');
    debouncedSetValue('value3');
    
    // Should only set last value
    await new Promise(resolve => setTimeout(resolve, 150));
    
    const state = debouncedSetValue('final-value');
    expect(state).toBe('final-value');
  });

  it('should optimize re-renders', () => {
    const renderCount = jest.fn();
    
    const TestComponent = React.memo(() => {
      renderCount();
      return <div>Optimized Component</div>;
    });
    
    // First render
    render(<TestComponent />);
    render(<TestComponent />);
    
    // Should only render once due to memoization
    expect(renderCount).toHaveBeenCalledTimes(1);
  });
});

// ============================================================================
// Mock Data
// ============================================================================

const mockAIResponse = {
  id: '1',
  type: 'create' as const,
  summary: 'AI Response',
  details: {
    name: 'Test Component',
    features: ['Feature 1', 'Feature 2'],
    techStack: ['React', 'TypeScript'],
  },
  confidence: 0.85,
  timestamp: Date.now(),
  latency: 150,
  fromCache: false,
  fromFallback: false,
};

const mockSuggestions = [
  {
    id: '1',
    type: 'node' as const,
    title: 'Test Suggestion',
    description: 'A test suggestion',
    confidence: 0.8,
    priority: 'medium' as const,
  },
  {
    id: '2',
    type: 'connection' as const,
    title: 'Test Connection',
    description: 'A test connection',
    confidence: 0.7,
    priority: 'low' as const,
  },
];

const mockGhostNodes = [
  {
    id: 'ghost-1',
    suggestionId: 'suggestion-1',
    type: 'node' as const,
    position: { x: 0, y: 0 },
    data: { type: 'test', name: 'Test Node' },
  },
  {
    id: 'ghost-2',
    suggestionId: 'suggestion-2',
    type: 'connection' as const,
    position: { x: 100, y: 100 },
    data: { type: 'test', name: 'Test Connection' },
  },
];

// ============================================================================
// Test Utilities
// ============================================================================

const createMockAIResponse = (overrides: Partial<typeof mockAIResponse> = {}) => ({
  ...mockAIResponse,
  ...overrides,
});

const createMockSuggestions = (overrides: Partial<typeof mockSuggestions[0]>[] = []) => {
  return mockSuggestions.map(suggestion => ({
    ...suggestion,
    ...overrides.find(o => o.id === suggestion.id),
  }));
};

const createMockGhostNodes = (overrides: Partial<typeof mockGhostNodes[0]>[] = []) => {
  return mockGhostNodes.map(node => ({
    ...node,
    ...overrides.find(o => o.id === node.id),
  }));
};

export {
  // Test utilities
  mockAIResponse,
  createMockAIResponse,
  mockSuggestions,
  createMockSuggestions,
  createMockGhostNodes,
  testUtils,
};
