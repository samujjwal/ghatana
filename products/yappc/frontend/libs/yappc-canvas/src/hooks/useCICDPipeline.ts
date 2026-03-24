/**
 * useCICDPipeline Hook
 * 
 * State management for CI/CD pipeline configuration
 * 
 * @doc.type hook
 * @doc.purpose CI/CD pipeline design and export
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback } from 'react';

/**
 * Stage types
 */
export type StageType = 'build' | 'test' | 'security' | 'deploy' | 'approval';

/**
 * Stage status
 */
export type StageStatus = 'pending' | 'running' | 'success' | 'failed';

/**
 * Pipeline step
 */
export interface PipelineStep {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Step name
     */
    name: string;

    /**
     * Command to execute
     */
    command: string;

    /**
     * Working directory (optional)
     */
    workingDirectory?: string;

    /**
     * Continue on error (optional)
     */
    continueOnError?: boolean;
}

/**
 * Pipeline stage
 */
export interface PipelineStage {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Stage name
     */
    name: string;

    /**
     * Stage type
     */
    type: StageType;

    /**
     * Description (optional)
     */
    description?: string;

    /**
     * Steps in this stage
     */
    steps?: PipelineStep[];

    /**
     * Environment variables
     */
    environmentVariables?: Record<string, string>;

    /**
     * Stage status
     */
    status: StageStatus;

    /**
     * Estimated duration in minutes (optional)
     */
    estimatedDuration?: number;

    /**
     * Dependencies (stage IDs that must complete first)
     */
    dependencies?: string[];
}

/**
 * Options for useCICDPipeline hook
 */
export interface UseCICDPipelineOptions {
    /**
     * Initial pipeline name
     */
    initialPipelineName?: string;

    /**
     * Initial repository
     */
    initialRepository?: string;
}

/**
 * Result of useCICDPipeline hook
 */
export interface UseCICDPipelineResult {
    /**
     * Pipeline stages
     */
    stages: PipelineStage[];

    /**
     * Pipeline name
     */
    pipelineName: string;

    /**
     * Repository URL
     */
    repository: string;

    /**
     * Set pipeline name
     */
    setPipelineName: (name: string) => void;

    /**
     * Set repository
     */
    setRepository: (repo: string) => void;

    /**
     * Add a stage
     */
    addStage: (stage: Omit<PipelineStage, 'id' | 'status' | 'steps' | 'environmentVariables'>) => string;

    /**
     * Update a stage
     */
    updateStage: (stageId: string, updates: Partial<Omit<PipelineStage, 'id'>>) => void;

    /**
     * Delete a stage
     */
    deleteStage: (stageId: string) => void;

    /**
     * Get stage by ID
     */
    getStage: (stageId: string) => PipelineStage | undefined;

    /**
     * Add step to stage
     */
    addStep: (stageId: string, step: Omit<PipelineStep, 'id'>) => void;

    /**
     * Delete step from stage
     */
    deleteStep: (stageId: string, stepId: string) => void;

    /**
     * Add environment variable to stage
     */
    addEnvironmentVariable: (stageId: string, key: string, value: string) => void;

    /**
     * Delete environment variable from stage
     */
    deleteEnvironmentVariable: (stageId: string, key: string) => void;

    /**
     * Validate pipeline configuration
     */
    validatePipeline: () => string[];

    /**
     * Calculate total estimated duration
     */
    calculateDuration: () => number;

    /**
     * Export to GitHub Actions YAML
     */
    exportToGitHubActions: () => string;

    /**
     * Export to Jenkins Jenkinsfile
     */
    exportToJenkins: () => string;

    /**
     * Get total stage count
     */
    getStageCount: () => number;

    /**
     * Get total step count
     */
    getStepCount: () => number;
}

/**
 * CI/CD Pipeline hook
 */
export const useCICDPipeline = (options: UseCICDPipelineOptions = {}): UseCICDPipelineResult => {
    const { initialPipelineName = 'CI/CD Pipeline', initialRepository = '' } = options;

    // State
    const [stages, setStages] = useState<PipelineStage[]>([]);
    const [pipelineName, setPipelineName] = useState(initialPipelineName);
    const [repository, setRepository] = useState(initialRepository);

    // Generate unique ID
    const generateId = useCallback((prefix: string) => {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }, []);

    // Add stage
    const addStage = useCallback((stage: Omit<PipelineStage, 'id' | 'status' | 'steps' | 'environmentVariables'>): string => {
        const id = generateId('stage');
        const newStage: PipelineStage = {
            ...stage,
            id,
            status: 'pending',
            steps: [],
            environmentVariables: {},
        };

        setStages(prev => [...prev, newStage]);
        return id;
    }, [generateId]);

    // Update stage
    const updateStage = useCallback((stageId: string, updates: Partial<Omit<PipelineStage, 'id'>>) => {
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
    const getStage = useCallback((stageId: string): PipelineStage | undefined => {
        return stages.find(s => s.id === stageId);
    }, [stages]);

    // Add step
    const addStep = useCallback((stageId: string, step: Omit<PipelineStep, 'id'>) => {
        const id = generateId('step');
        const newStep: PipelineStep = {
            ...step,
            id,
        };

        setStages(prev =>
            prev.map(stage =>
                stage.id === stageId
                    ? { ...stage, steps: [...(stage.steps || []), newStep] }
                    : stage
            )
        );
    }, [generateId]);

    // Delete step
    const deleteStep = useCallback((stageId: string, stepId: string) => {
        setStages(prev =>
            prev.map(stage =>
                stage.id === stageId
                    ? {
                        ...stage,
                        steps: (stage.steps || []).filter(s => s.id !== stepId),
                    }
                    : stage
            )
        );
    }, []);

    // Add environment variable
    const addEnvironmentVariable = useCallback((stageId: string, key: string, value: string) => {
        setStages(prev =>
            prev.map(stage =>
                stage.id === stageId
                    ? {
                        ...stage,
                        environmentVariables: {
                            ...(stage.environmentVariables || {}),
                            [key]: value,
                        },
                    }
                    : stage
            )
        );
    }, []);

    // Delete environment variable
    const deleteEnvironmentVariable = useCallback((stageId: string, key: string) => {
        setStages(prev =>
            prev.map(stage => {
                if (stage.id !== stageId) return stage;

                const { [key]: _, ...remainingVars } = stage.environmentVariables || {};
                return {
                    ...stage,
                    environmentVariables: remainingVars,
                };
            })
        );
    }, []);

    // Validate pipeline
    const validatePipeline = useCallback((): string[] => {
        const issues: string[] = [];

        if (stages.length === 0) {
            issues.push('Pipeline has no stages defined');
        }

        if (!pipelineName.trim()) {
            issues.push('Pipeline name is required');
        }

        if (!repository.trim()) {
            issues.push('Repository URL is required');
        }

        stages.forEach((stage, index) => {
            if (!stage.name.trim()) {
                issues.push(`Stage ${index + 1} has no name`);
            }

            if (!stage.steps || stage.steps.length === 0) {
                issues.push(`Stage "${stage.name}" has no steps`);
            }

            stage.steps?.forEach((step, stepIndex) => {
                if (!step.command.trim()) {
                    issues.push(`Step ${stepIndex + 1} in stage "${stage.name}" has no command`);
                }
            });
        });

        // Check for build stage
        const hasBuild = stages.some(s => s.type === 'build');
        if (!hasBuild) {
            issues.push('Pipeline should have at least one build stage');
        }

        return issues;
    }, [stages, pipelineName, repository]);

    // Calculate duration
    const calculateDuration = useCallback((): number => {
        return stages.reduce((total, stage) => {
            return total + (stage.estimatedDuration || 5); // Default 5 min per stage
        }, 0);
    }, [stages]);

    // Export to GitHub Actions
    const exportToGitHubActions = useCallback((): string => {
        const yaml: string[] = [];

        yaml.push(`name: ${pipelineName}`);
        yaml.push('');
        yaml.push('on:');
        yaml.push('  push:');
        yaml.push('    branches: [ main ]');
        yaml.push('  pull_request:');
        yaml.push('    branches: [ main ]');
        yaml.push('');
        yaml.push('jobs:');

        stages.forEach(stage => {
            yaml.push(`  ${stage.name.toLowerCase().replace(/\s+/g, '-')}:`);
            yaml.push(`    name: ${stage.name}`);
            yaml.push('    runs-on: ubuntu-latest');

            if (stage.environmentVariables && Object.keys(stage.environmentVariables).length > 0) {
                yaml.push('    env:');
                Object.entries(stage.environmentVariables).forEach(([key, value]) => {
                    yaml.push(`      ${key}: ${value}`);
                });
            }

            yaml.push('    steps:');
            yaml.push('      - uses: actions/checkout@v3');

            stage.steps?.forEach(step => {
                yaml.push(`      - name: ${step.name}`);
                if (step.workingDirectory) {
                    yaml.push(`        working-directory: ${step.workingDirectory}`);
                }
                yaml.push(`        run: ${step.command}`);
            });

            yaml.push('');
        });

        return yaml.join('\n');
    }, [stages, pipelineName]);

    // Export to Jenkins
    const exportToJenkins = useCallback((): string => {
        const jenkinsfile: string[] = [];

        jenkinsfile.push('pipeline {');
        jenkinsfile.push('    agent any');
        jenkinsfile.push('');
        jenkinsfile.push('    environment {');

        // Collect all environment variables
        const allEnvVars = new Map<string, string>();
        stages.forEach(stage => {
            if (stage.environmentVariables) {
                Object.entries(stage.environmentVariables).forEach(([key, value]) => {
                    allEnvVars.set(key, value);
                });
            }
        });

        allEnvVars.forEach((value, key) => {
            jenkinsfile.push(`        ${key} = '${value}'`);
        });

        jenkinsfile.push('    }');
        jenkinsfile.push('');
        jenkinsfile.push('    stages {');

        stages.forEach(stage => {
            jenkinsfile.push(`        stage('${stage.name}') {`);
            jenkinsfile.push('            steps {');

            stage.steps?.forEach(step => {
                if (step.workingDirectory) {
                    jenkinsfile.push(`                dir('${step.workingDirectory}') {`);
                    jenkinsfile.push(`                    sh '${step.command}'`);
                    jenkinsfile.push('                }');
                } else {
                    jenkinsfile.push(`                sh '${step.command}'`);
                }
            });

            jenkinsfile.push('            }');
            jenkinsfile.push('        }');
        });

        jenkinsfile.push('    }');
        jenkinsfile.push('');
        jenkinsfile.push('    post {');
        jenkinsfile.push('        success {');
        jenkinsfile.push("            echo 'Pipeline succeeded!'");
        jenkinsfile.push('        }');
        jenkinsfile.push('        failure {');
        jenkinsfile.push("            echo 'Pipeline failed!'");
        jenkinsfile.push('        }');
        jenkinsfile.push('    }');
        jenkinsfile.push('}');

        return jenkinsfile.join('\n');
    }, [stages]);

    // Get stage count
    const getStageCount = useCallback((): number => {
        return stages.length;
    }, [stages]);

    // Get step count
    const getStepCount = useCallback((): number => {
        return stages.reduce((count, stage) => count + (stage.steps?.length || 0), 0);
    }, [stages]);

    return {
        stages,
        pipelineName,
        repository,
        setPipelineName,
        setRepository,
        addStage,
        updateStage,
        deleteStage,
        getStage,
        addStep,
        deleteStep,
        addEnvironmentVariable,
        deleteEnvironmentVariable,
        validatePipeline,
        calculateDuration,
        exportToGitHubActions,
        exportToJenkins,
        getStageCount,
        getStepCount,
    };
};
