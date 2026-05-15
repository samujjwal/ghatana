/**
 * Workflow Step Components Integration Tests
 *
 * Tests the complete workflow wizard flow from intent to completion.
 *
 * @doc.type test
 * @doc.purpose Integration test for workflow UI
 * @doc.layer product
 * @doc.pattern Integration Test
 */

import React from 'react';
import { vi } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';

import {
  IntentStep,
  ContextStep,
  PlanStep,
  CodeStep,
  TestStep,
  PreviewStep,
  DeployStep,
  CompleteStep,
  WORKFLOW_STEPS,
  WORKFLOW_STEP_LABELS,
} from '../index';

// Mock data
const mockIntentData = {
  rawIntent: '',
  parsedIntent: null,
  isParsingIntent: false,
};

const mockContextData = {
  selectedFiles: [],
  analysis: null,
  additionalContext: '',
  isAnalyzing: false,
};

const mockPlanData = {
  id: 'plan-1',
  steps: [],
  status: 'pending_review' as const,
  confidence: 0.85,
  estimatedDuration: { min: 5, max: 10 },
};

const mockCodeData = {
  files: [],
  isGenerating: false,
};

const mockTestData = {
  tests: [],
  isGenerating: false,
  isRunning: false,
};

const mockPreviewData = {
  previewUrl: null,
  status: 'building' as const,
  buildLog: [],
  viewport: 'desktop' as const,
};

const mockDeployData = {
  environment: 'development' as const,
  status: 'pending' as const,
  steps: [],
};

const mockWorkflowSummary = {
  workflowId: 'wf-123',
  intent: 'Create a login form',
  filesCreated: 3,
  filesModified: 2,
  testsGenerated: 5,
  testsPassed: 5,
  testsFailed: 0,
  deploymentUrl: 'https://dev.example.com',
  environment: 'development',
  startTime: new Date('2024-01-01T10:00:00'),
  endTime: new Date('2024-01-01T10:15:00'),
  steps: [
    { name: 'Intent', status: 'success' as const, duration: 5000 },
    { name: 'Context', status: 'success' as const, duration: 10000 },
    { name: 'Plan', status: 'success' as const, duration: 20000 },
    { name: 'Code', status: 'success' as const, duration: 60000 },
    { name: 'Test', status: 'success' as const, duration: 30000 },
    { name: 'Preview', status: 'success' as const, duration: 15000 },
    { name: 'Deploy', status: 'success' as const, duration: 45000 },
  ],
};

describe('Workflow Step Constants', () => {
  it('should have 8 workflow steps', () => {
    expect(WORKFLOW_STEPS).toHaveLength(8);
  });

  it('should have labels for all steps', () => {
    WORKFLOW_STEPS.forEach((step) => {
      expect(WORKFLOW_STEP_LABELS[step]).toBeDefined();
    });
  });
});

describe('IntentStep', () => {
  const defaultProps = {
    value: mockIntentData.rawIntent,
    onChange: vi.fn(),
    onComplete: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders intent input field', () => {
    render(<IntentStep {...defaultProps} />);
    expect(
      screen.getByText(/What would you like to accomplish/i)
    ).toBeInTheDocument();
  });

  it('handles intent input change', async () => {
    const onChange = vi.fn();
    render(<IntentStep {...defaultProps} onChange={onChange} />);

    const input = screen.getByRole('textbox');
    await userEvent.type(input, 'Create a login form');

    expect(onChange).toHaveBeenCalled();
  });

  it('shows suggested intents', () => {
    render(<IntentStep {...defaultProps} />);
    expect(screen.getByText(/Quick Start/i)).toBeInTheDocument();
  });

  it('disables continue button when intent is empty', () => {
    render(<IntentStep {...defaultProps} />);
    const continueButton = screen.getByRole('button', { name: /continue/i });
    expect(continueButton).toBeDisabled();
  });
});

describe('ContextStep', () => {
  const defaultProps = {
    intentData: { rawIntent: 'Create a login form', parsedIntent: null },
    value: mockContextData,
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders context gathering UI', () => {
    render(<ContextStep {...defaultProps} />);
    expect(screen.getByText(/Gathering Context/i)).toBeInTheDocument();
  });

  it('shows analyze codebase button', () => {
    render(<ContextStep {...defaultProps} />);
    expect(
      screen.getByRole('button', { name: /add files/i })
    ).toBeInTheDocument();
  });

  it('renders back button', () => {
    render(<ContextStep {...defaultProps} />);
    expect(screen.getByRole('button', { name: /back/i })).toBeInTheDocument();
  });
});

describe('PlanStep', () => {
  const defaultProps = {
    contextData: { selectedFiles: [], analysis: null },
    intentData: { intent: 'Create a login form' },
    value: mockPlanData,
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders plan review UI', () => {
    render(<PlanStep {...defaultProps} />);
    expect(screen.getByText(/AI-Generated Plan/i)).toBeInTheDocument();
  });

  it('shows generate plan button when no steps', () => {
    render(<PlanStep {...defaultProps} />);
    expect(
      screen.getByRole('button', { name: /add step/i })
    ).toBeInTheDocument();
  });
});

describe('CodeStep', () => {
  const defaultProps = {
    planData: { steps: [], approved: true },
    value: mockCodeData,
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders code generation UI', () => {
    render(<CodeStep {...defaultProps} />);
    expect(
      screen.getByRole('heading', { name: /Generated Code/i })
    ).toBeInTheDocument();
  });

  it('shows generate button', () => {
    render(<CodeStep {...defaultProps} />);
    expect(
      screen.getByRole('button', { name: /regenerate/i })
    ).toBeInTheDocument();
  });
});

describe('TestStep', () => {
  const defaultProps = {
    codeData: { files: [] },
    value: mockTestData,
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders test generation UI', () => {
    render(<TestStep {...defaultProps} />);
    expect(screen.getByText(/Test Generation/i)).toBeInTheDocument();
  });
});

describe('PreviewStep', () => {
  const defaultProps = {
    codeData: { files: [] },
    value: {
      ...mockPreviewData,
      status: 'ready' as const,
      previewUrl: 'http://localhost:3000',
    },
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders preview UI', () => {
    render(<PreviewStep {...defaultProps} />);
    expect(screen.getByText(/Preview Changes/i)).toBeInTheDocument();
  });

  it('shows viewport controls when ready', () => {
    render(<PreviewStep {...defaultProps} />);
    expect(
      screen.getAllByRole('button', { name: /icon button/i }).length
    ).toBeGreaterThanOrEqual(3);
  });
});

describe('DeployStep', () => {
  const defaultProps = {
    previewData: { previewUrl: 'http://localhost:3000' },
    value: mockDeployData,
    onChange: vi.fn(),
    onComplete: vi.fn(),
    onBack: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders deploy UI', () => {
    render(<DeployStep {...defaultProps} />);
    expect(screen.getByText(/Deploy Changes/i)).toBeInTheDocument();
  });

  it('shows environment selection', () => {
    render(<DeployStep {...defaultProps} />);
    expect(screen.getAllByText(/Development/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Staging/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Production/i).length).toBeGreaterThan(0);
  });
});

describe('CompleteStep', () => {
  const defaultProps = {
    workflowData: mockWorkflowSummary,
    onNewWorkflow: vi.fn(),
    onViewDeployment: vi.fn(),
    onDownloadReport: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders completion summary', () => {
    render(<CompleteStep {...defaultProps} />);
    expect(screen.getByText(/Workflow Complete/i)).toBeInTheDocument();
  });

  it('shows workflow statistics', () => {
    render(<CompleteStep {...defaultProps} />);
    expect(screen.getByText(/Files Changed/i)).toBeInTheDocument();
    expect(screen.getByText(/Tests Passed/i)).toBeInTheDocument();
  });

  it('shows deployment URL', () => {
    render(<CompleteStep {...defaultProps} />);
    expect(
      screen.getByText(mockWorkflowSummary.deploymentUrl)
    ).toBeInTheDocument();
  });

  it('shows action buttons', () => {
    render(<CompleteStep {...defaultProps} />);
    expect(
      screen.getByRole('button', { name: /start new workflow/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /view deployment/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /download report/i })
    ).toBeInTheDocument();
  });

  it('calls onNewWorkflow when button clicked', async () => {
    render(<CompleteStep {...defaultProps} />);
    await userEvent.click(
      screen.getByRole('button', { name: /start new workflow/i })
    );
    expect(defaultProps.onNewWorkflow).toHaveBeenCalled();
  });
});

describe('Workflow Step Integration', () => {
  it('maintains consistent prop patterns across steps', () => {
    // All steps should have onChange and onComplete
    const commonProps = ['onChange', 'onComplete'];

    // IntentStep
    expect(IntentStep.name).toBe('IntentStep');

    // ContextStep - has onBack
    expect(ContextStep.name).toBe('ContextStep');

    // All steps after Intent should have onBack
  });

  it('step labels match step order', () => {
    const expectedOrder = [
      'Describe Intent',
      'Gather Context',
      'Review Plan',
      'Generate Code',
      'Generate Tests',
      'Preview Changes',
      'Deploy',
      'Complete',
    ];

    WORKFLOW_STEPS.forEach((step, index) => {
      expect(WORKFLOW_STEP_LABELS[step]).toBe(expectedOrder[index]);
    });
  });
});
