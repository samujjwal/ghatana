/**
 * AI Workflow Wizard Component
 *
 * Main wizard component that orchestrates all workflow steps.
 * Manages state transitions and step navigation.
 *
 * @doc.type component
 * @doc.purpose Workflow wizard orchestration
 * @doc.layer product
 * @doc.pattern Container
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Box, Surface as Paper, Stepper, Step, StepLabel, StepLabel as StepContent, Typography, Alert, LinearProgress, IconButton, Drawer } from '@ghatana/ui';
import { X as CloseIcon, Sparkles as AIIcon } from 'lucide-react';

import {
    IntentStep,
    IntentStepData,
    ContextStep,
    ContextStepData,
    PlanStep,
    PlanStepData,
    CodeStep,
    CodeStepData,
    TestStep,
    TestStepData,
    PreviewStep,
    PreviewStepData,
    DeployStep,
    DeployStepData,
    CompleteStep,
    WorkflowSummary,
    WORKFLOW_STEPS,
    WORKFLOW_STEP_LABELS,
    WorkflowStep,
} from './workflow';

export interface AiWorkflowWizardProps {
    open: boolean;
    onClose: () => void;
    onComplete?: (summary: WorkflowSummary) => void;
    initialIntent?: string;
}

interface WorkflowState {
    currentStep: number;
    intent: IntentStepData;
    context: ContextStepData;
    plan: PlanStepData;
    code: CodeStepData;
    test: TestStepData;
    preview: PreviewStepData;
    deploy: DeployStepData;
    startTime: Date | null;
}

const initialState: WorkflowState = {
    currentStep: 0,
    intent: {
        rawIntent: '',
        parsedIntent: null,
        isParsingIntent: false,
    },
    context: {
        selectedFiles: [],
        analysis: null,
        additionalContext: '',
        isAnalyzing: false,
    },
    plan: {
        steps: [],
        isLoading: false,
        approved: false,
    },
    code: {
        files: [],
        isGenerating: false,
    },
    test: {
        tests: [],
        isGenerating: false,
        isRunning: false,
    },
    preview: {
        previewUrl: null,
        status: 'building',
        buildLog: [],
        viewport: 'desktop',
    },
    deploy: {
        environment: 'development',
        status: 'pending',
        steps: [],
    },
    startTime: null,
};

export const AiWorkflowWizard: React.FC<AiWorkflowWizardProps> = ({
    open,
    onClose,
    onComplete,
    initialIntent = '',
}) => {
    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('md'));

    const [state, setState] = useState<WorkflowState>(() => ({
        ...initialState,
        intent: {
            ...initialState.intent,
            rawIntent: initialIntent,
        },
        startTime: new Date(),
    }));

    const [error, setError] = useState<string | null>(null);

    const handleStepChange = useCallback(<K extends keyof WorkflowState>(
        step: K,
        data: WorkflowState[K]
    ) => {
        setState((prev) => ({ ...prev, [step]: data }));
    }, []);

    const handleNext = useCallback(() => {
        setState((prev) => ({
            ...prev,
            currentStep: Math.min(prev.currentStep + 1, WORKFLOW_STEPS.length - 1),
        }));
        setError(null);
    }, []);

    const handleBack = useCallback(() => {
        setState((prev) => ({
            ...prev,
            currentStep: Math.max(prev.currentStep - 1, 0),
        }));
        setError(null);
    }, []);

    const handleComplete = useCallback(() => {
        const summary: WorkflowSummary = {
            workflowId: `wf-${Date.now()}`,
            intent: state.intent.rawIntent,
            filesCreated: state.code.files.filter((f) => f.operation === 'create').length,
            filesModified: state.code.files.filter((f) => f.operation === 'modify').length,
            testsGenerated: state.test.tests.length,
            testsPassed: state.test.tests.filter((t) => t.status === 'passed').length,
            testsFailed: state.test.tests.filter((t) => t.status === 'failed').length,
            deploymentUrl: state.deploy.deploymentUrl,
            environment: state.deploy.environment,
            startTime: state.startTime || new Date(),
            endTime: new Date(),
            steps: WORKFLOW_STEPS.slice(0, -1).map((step, index) => ({
                name: WORKFLOW_STEP_LABELS[step],
                status: 'success' as const,
                duration: 10000 + index * 5000, // Simulated durations
            })),
        };

        onComplete?.(summary);
    }, [state, onComplete]);

    const handleNewWorkflow = useCallback(() => {
        setState({
            ...initialState,
            startTime: new Date(),
        });
    }, []);

    const progress = useMemo(() => {
        return ((state.currentStep + 1) / WORKFLOW_STEPS.length) * 100;
    }, [state.currentStep]);

    const renderStep = () => {
        const step = WORKFLOW_STEPS[state.currentStep];

        switch (step) {
            case 'intent':
                return (
                    <IntentStep
                        value={state.intent}
                        onChange={(data) => handleStepChange('intent', data)}
                        onComplete={() => handleNext()}
                        error={error}
                    />
                );

            case 'context':
                return (
                    <ContextStep
                        intentData={state.intent}
                        value={state.context}
                        onChange={(data) => handleStepChange('context', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'plan':
                return (
                    <PlanStep
                        contextData={state.context}
                        value={state.plan}
                        onChange={(data) => handleStepChange('plan', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'code':
                return (
                    <CodeStep
                        planData={state.plan}
                        value={state.code}
                        onChange={(data) => handleStepChange('code', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'test':
                return (
                    <TestStep
                        codeData={state.code}
                        value={state.test}
                        onChange={(data) => handleStepChange('test', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'preview':
                return (
                    <PreviewStep
                        codeData={state.code}
                        value={state.preview}
                        onChange={(data) => handleStepChange('preview', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'deploy':
                return (
                    <DeployStep
                        previewData={state.preview}
                        value={state.deploy}
                        onChange={(data) => handleStepChange('deploy', data)}
                        onComplete={() => handleNext()}
                        onBack={handleBack}
                        error={error}
                    />
                );

            case 'complete':
                return (
                    <CompleteStep
                        workflowData={{
                            workflowId: `wf-${Date.now()}`,
                            intent: state.intent.rawIntent,
                            filesCreated: state.code.files.filter((f) => f.operation === 'create').length,
                            filesModified: state.code.files.filter((f) => f.operation === 'modify').length,
                            testsGenerated: state.test.tests.length,
                            testsPassed: state.test.tests.filter((t) => t.status === 'passed').length,
                            testsFailed: state.test.tests.filter((t) => t.status === 'failed').length,
                            deploymentUrl: state.deploy.deploymentUrl,
                            environment: state.deploy.environment,
                            startTime: state.startTime || new Date(),
                            endTime: new Date(),
                            steps: WORKFLOW_STEPS.slice(0, -1).map((s, i) => ({
                                name: WORKFLOW_STEP_LABELS[s],
                                status: 'success' as const,
                                duration: 10000 + i * 5000,
                            })),
                        }}
                        onNewWorkflow={handleNewWorkflow}
                        onViewDeployment={() => window.open(state.deploy.deploymentUrl, '_blank')}
                        onDownloadReport={() => {
                            // Generate and download report
                            const report = JSON.stringify(state, null, 2);
                            const blob = new Blob([report], { type: 'application/json' });
                            const url = URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = 'workflow-report.json';
                            a.click();
                        }}
                    />
                );

            default:
                return null;
        }
    };

    const content = (
        <Box className="h-full flex flex-col">
            {/* Header */}
            <Box
                className="p-4 flex items-center gap-2 border-gray-200 dark:border-gray-700 border-b" >
                <AIIcon tone="primary" />
                <Typography as="h6" className="grow">
                    AI Workflow Wizard
                </Typography>
                <IconButton onClick={onClose} size="sm">
                    <CloseIcon />
                </IconButton>
            </Box>

            {/* Progress */}
            <LinearProgress variant="determinate" value={progress} />

            {/* Stepper (desktop) */}
            {!isMobile && (
                <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Stepper activeStep={state.currentStep} alternativeLabel>
                        {WORKFLOW_STEPS.map((step) => (
                            <Step key={step}>
                                <StepLabel>{WORKFLOW_STEP_LABELS[step]}</StepLabel>
                            </Step>
                        ))}
                    </Stepper>
                </Box>
            )}

            {/* Mobile step indicator */}
            {isMobile && (
                <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Typography as="p" className="text-sm font-medium" color="text.secondary">
                        Step {state.currentStep + 1} of {WORKFLOW_STEPS.length}
                    </Typography>
                    <Typography as="p" className="text-lg font-medium">
                        {WORKFLOW_STEP_LABELS[WORKFLOW_STEPS[state.currentStep]]}
                    </Typography>
                </Box>
            )}

            {/* Step content */}
            <Box className="grow overflow-auto">
                {renderStep()}
            </Box>
        </Box>
    );

    if (isMobile) {
        return (
            <Drawer
                anchor="bottom"
                open={open}
                onClose={onClose}
                PaperProps={{
                    sx: { height: '90vh', borderTopLeftRadius: 16, borderTopRightRadius: 16 },
                }}
            >
                {content}
            </Drawer>
        );
    }

    return (
        <Drawer
            anchor="right"
            open={open}
            onClose={onClose}
            PaperProps={{
                sx: { width: '60vw', maxWidth: 900, minWidth: 500 },
            }}
        >
            {content}
        </Drawer>
    );
};

export default AiWorkflowWizard;
