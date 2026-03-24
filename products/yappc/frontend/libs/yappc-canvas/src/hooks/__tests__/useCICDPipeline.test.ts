/**
 * Tests for useCICDPipeline hook
 */

import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCICDPipeline, type StageType } from '../useCICDPipeline';

describe('useCICDPipeline', () => {
    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => useCICDPipeline());

            expect(result.current.pipelineName).toBe('CI/CD Pipeline');
            expect(result.current.repository).toBe('');
            expect(result.current.stages).toHaveLength(0);
            expect(result.current.getStageCount()).toBe(0);
            expect(result.current.getStepCount()).toBe(0);
        });

        it('should initialize with custom pipeline name', () => {
            const { result } = renderHook(() =>
                useCICDPipeline({ initialPipelineName: 'Production Pipeline' })
            );

            expect(result.current.pipelineName).toBe('Production Pipeline');
        });

        it('should initialize with repository', () => {
            const { result } = renderHook(() =>
                useCICDPipeline({ initialRepository: 'github.com/org/repo' })
            );

            expect(result.current.repository).toBe('github.com/org/repo');
        });
    });

    describe('Configuration Management', () => {
        it('should update pipeline name', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setPipelineName('Development Pipeline');
            });

            expect(result.current.pipelineName).toBe('Development Pipeline');
        });

        it('should update repository', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setRepository('gitlab.com/company/project');
            });

            expect(result.current.repository).toBe('gitlab.com/company/project');
        });
    });

    describe('Stage Management', () => {
        it('should add build stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({
                    name: 'Build',
                    type: 'build',
                    description: 'Compile source code',
                });
            });

            expect(result.current.stages).toHaveLength(1);
            expect(result.current.stages[0].name).toBe('Build');
            expect(result.current.stages[0].type).toBe('build');
            expect(result.current.stages[0].status).toBe('pending');
            expect(result.current.stages[0].id).toBe(stageId!);
        });

        it('should add test stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.addStage({
                    name: 'Test',
                    type: 'test',
                });
            });

            expect(result.current.stages[0].type).toBe('test');
        });

        it('should add multiple stages', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStage({ name: 'Test', type: 'test' });
                result.current.addStage({ name: 'Deploy', type: 'deploy' });
            });

            expect(result.current.stages).toHaveLength(3);
            expect(result.current.getStageCount()).toBe(3);
        });

        it('should update stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
            });

            act(() => {
                result.current.updateStage(stageId!, {
                    name: 'Build & Package',
                    estimatedDuration: 10,
                });
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.name).toBe('Build & Package');
            expect(stage?.estimatedDuration).toBe(10);
        });

        it('should delete stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
            });

            act(() => {
                result.current.deleteStage(stageId!);
            });

            expect(result.current.stages).toHaveLength(0);
        });

        it('should get stage by ID', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({
                    name: 'Security Scan',
                    type: 'security',
                    description: 'SAST analysis',
                });
            });

            const stage = result.current.getStage(stageId!);
            expect(stage).toBeDefined();
            expect(stage?.name).toBe('Security Scan');
            expect(stage?.type).toBe('security');
        });
    });

    describe('Step Management', () => {
        it('should add step to stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(stageId, {
                    name: 'Install Dependencies',
                    command: 'npm install',
                });
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.steps).toHaveLength(1);
            expect(stage?.steps?.[0].name).toBe('Install Dependencies');
            expect(stage?.steps?.[0].command).toBe('npm install');
        });

        it('should add step with working directory', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(stageId, {
                    name: 'Build Frontend',
                    command: 'npm run build',
                    workingDirectory: './frontend',
                });
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.steps?.[0].workingDirectory).toBe('./frontend');
        });

        it('should add multiple steps to stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(stageId, { name: 'Step 1', command: 'echo 1' });
                result.current.addStep(stageId, { name: 'Step 2', command: 'echo 2' });
                result.current.addStep(stageId, { name: 'Step 3', command: 'echo 3' });
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.steps).toHaveLength(3);
            expect(result.current.getStepCount()).toBe(3);
        });

        it('should delete step from stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(stageId, { name: 'Step 1', command: 'echo 1' });
                result.current.addStep(stageId, { name: 'Step 2', command: 'echo 2' });
            });

            const stage = result.current.getStage(stageId!);
            const stepId = stage?.steps?.[0].id!;

            act(() => {
                result.current.deleteStep(stageId!, stepId);
            });

            const updatedStage = result.current.getStage(stageId!);
            expect(updatedStage?.steps).toHaveLength(1);
        });

        it('should count steps across all stages', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const stage1 = result.current.addStage({ name: 'Build', type: 'build' });
                const stage2 = result.current.addStage({ name: 'Test', type: 'test' });

                result.current.addStep(stage1, { name: 'Step 1', command: 'cmd1' });
                result.current.addStep(stage1, { name: 'Step 2', command: 'cmd2' });
                result.current.addStep(stage2, { name: 'Step 3', command: 'cmd3' });
            });

            expect(result.current.getStepCount()).toBe(3);
        });
    });

    describe('Environment Variables', () => {
        it('should add environment variable to stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addEnvironmentVariable(stageId, 'NODE_ENV', 'production');
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.environmentVariables?.NODE_ENV).toBe('production');
        });

        it('should add multiple environment variables', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addEnvironmentVariable(stageId, 'NODE_ENV', 'production');
                result.current.addEnvironmentVariable(stageId, 'API_URL', 'https://api.example.com');
                result.current.addEnvironmentVariable(stageId, 'DEBUG', 'false');
            });

            const stage = result.current.getStage(stageId!);
            expect(Object.keys(stage?.environmentVariables || {})).toHaveLength(3);
        });

        it('should delete environment variable', () => {
            const { result } = renderHook(() => useCICDPipeline());

            let stageId: string;
            act(() => {
                stageId = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addEnvironmentVariable(stageId, 'NODE_ENV', 'production');
                result.current.addEnvironmentVariable(stageId, 'DEBUG', 'true');
            });

            act(() => {
                result.current.deleteEnvironmentVariable(stageId!, 'DEBUG');
            });

            const stage = result.current.getStage(stageId!);
            expect(stage?.environmentVariables?.NODE_ENV).toBe('production');
            expect(stage?.environmentVariables?.DEBUG).toBeUndefined();
        });
    });

    describe('Validation', () => {
        it('should validate empty pipeline', () => {
            const { result } = renderHook(() => useCICDPipeline());

            const issues = result.current.validatePipeline();

            expect(issues).toContain('Pipeline has no stages defined');
        });

        it('should require pipeline name', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setPipelineName('');
            });

            const issues = result.current.validatePipeline();

            expect(issues.some(i => i.includes('Pipeline name is required'))).toBe(true);
        });

        it('should require repository', () => {
            const { result } = renderHook(() => useCICDPipeline());

            const issues = result.current.validatePipeline();

            expect(issues.some(i => i.includes('Repository URL is required'))).toBe(true);
        });

        it('should require stage steps', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.addStage({ name: 'Empty Stage', type: 'build' });
            });

            const issues = result.current.validatePipeline();

            expect(issues.some(i => i.includes('has no steps'))).toBe(true);
        });

        it('should require build stage', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const testStage = result.current.addStage({ name: 'Test', type: 'test' });
                result.current.addStep(testStage, { name: 'Run Tests', command: 'npm test' });
                result.current.setPipelineName('Test Pipeline');
                result.current.setRepository('github.com/org/repo');
            });

            const issues = result.current.validatePipeline();

            expect(issues.some(i => i.includes('should have at least one build stage'))).toBe(true);
        });

        it('should pass validation for complete pipeline', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setPipelineName('Complete Pipeline');
                result.current.setRepository('github.com/org/repo');
                const buildStage = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(buildStage, { name: 'Build', command: 'npm run build' });
            });

            const issues = result.current.validatePipeline();

            expect(issues).toHaveLength(0);
        });
    });

    describe('Duration Calculation', () => {
        it('should calculate default duration', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.addStage({ name: 'Stage 1', type: 'build' });
                result.current.addStage({ name: 'Stage 2', type: 'test' });
            });

            const duration = result.current.calculateDuration();
            expect(duration).toBe(10); // 2 stages × 5 min default
        });

        it('should calculate custom duration', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const stage1 = result.current.addStage({ name: 'Build', type: 'build' });
                const stage2 = result.current.addStage({ name: 'Test', type: 'test' });

                result.current.updateStage(stage1, { estimatedDuration: 8 });
                result.current.updateStage(stage2, { estimatedDuration: 12 });
            });

            const duration = result.current.calculateDuration();
            expect(duration).toBe(20);
        });
    });

    describe('GitHub Actions Export', () => {
        it('should export to GitHub Actions YAML', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setPipelineName('CI Pipeline');
                const buildStage = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(buildStage, { name: 'Install', command: 'npm install' });
                result.current.addStep(buildStage, { name: 'Build', command: 'npm run build' });
            });

            const yaml = result.current.exportToGitHubActions();

            expect(yaml).toContain('name: CI Pipeline');
            expect(yaml).toContain('jobs:');
            expect(yaml).toContain('build:');
            expect(yaml).toContain('npm install');
            expect(yaml).toContain('npm run build');
        });

        it('should include environment variables in export', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const stage = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addEnvironmentVariable(stage, 'NODE_ENV', 'production');
                result.current.addStep(stage, { name: 'Build', command: 'npm run build' });
            });

            const yaml = result.current.exportToGitHubActions();

            expect(yaml).toContain('env:');
            expect(yaml).toContain('NODE_ENV: production');
        });
    });

    describe('Jenkins Export', () => {
        it('should export to Jenkins Jenkinsfile', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const buildStage = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(buildStage, { name: 'Build', command: 'mvn clean package' });
            });

            const jenkinsfile = result.current.exportToJenkins();

            expect(jenkinsfile).toContain('pipeline {');
            expect(jenkinsfile).toContain('agent any');
            expect(jenkinsfile).toContain('stages {');
            expect(jenkinsfile).toContain("stage('Build')");
            expect(jenkinsfile).toContain("sh 'mvn clean package'");
        });

        it('should include environment variables in Jenkinsfile', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                const stage = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addEnvironmentVariable(stage, 'JAVA_HOME', '/usr/lib/jvm/java-17');
                result.current.addStep(stage, { name: 'Build', command: 'mvn package' });
            });

            const jenkinsfile = result.current.exportToJenkins();

            expect(jenkinsfile).toContain('environment {');
            expect(jenkinsfile).toContain("JAVA_HOME = '/usr/lib/jvm/java-17'");
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle complete CI/CD pipeline', () => {
            const { result } = renderHook(() => useCICDPipeline());

            act(() => {
                result.current.setPipelineName('Full Pipeline');
                result.current.setRepository('github.com/company/app');

                // Build
                const build = result.current.addStage({ name: 'Build', type: 'build' });
                result.current.addStep(build, { name: 'Install', command: 'npm ci' });
                result.current.addStep(build, { name: 'Compile', command: 'npm run build' });
                result.current.addEnvironmentVariable(build, 'NODE_ENV', 'production');
                result.current.updateStage(build, { estimatedDuration: 8 });

                // Test
                const test = result.current.addStage({ name: 'Test', type: 'test' });
                result.current.addStep(test, { name: 'Unit Tests', command: 'npm test' });
                result.current.addStep(test, { name: 'E2E Tests', command: 'npm run test:e2e' });
                result.current.updateStage(test, { estimatedDuration: 15 });

                // Security
                const security = result.current.addStage({ name: 'Security', type: 'security' });
                result.current.addStep(security, { name: 'SAST', command: 'npm run security:scan' });
                result.current.updateStage(security, { estimatedDuration: 5 });

                // Deploy
                const deploy = result.current.addStage({ name: 'Deploy', type: 'deploy' });
                result.current.addStep(deploy, { name: 'Deploy to Prod', command: 'npm run deploy' });
                result.current.updateStage(deploy, { estimatedDuration: 10 });
            });

            expect(result.current.getStageCount()).toBe(4);
            expect(result.current.getStepCount()).toBe(6);
            expect(result.current.calculateDuration()).toBe(38);

            const issues = result.current.validatePipeline();
            expect(issues).toHaveLength(0);

            const yaml = result.current.exportToGitHubActions();
            expect(yaml).toContain('Build');
            expect(yaml).toContain('Test');
            expect(yaml).toContain('Security');
            expect(yaml).toContain('Deploy');
        });
    });
});
