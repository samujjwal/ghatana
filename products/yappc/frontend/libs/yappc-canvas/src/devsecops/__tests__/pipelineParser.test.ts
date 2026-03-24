import { describe, it, expect } from 'vitest';

import {
  createPipelineParserConfig,
  parseGitHubActions,
  parseGitLabCI,
  parseJenkins,
  parseCircleCI,
  pipelineToCanvas,
  calculateJobLevels,
  groupJobsByStage,
  getJobStyle,
  getGateStyle,
  addMetricsToPipeline,
  type CIPlatform,
  type JobStatus,
  type GateType,
  type PipelineJob,
  type PipelineStage,
  type PipelineGate,
  type JobMetrics,
  type Pipeline,
  type PipelineExecution,
  type PipelineParserConfig,
} from '../pipelineParser';

describe.skip('PipelineParser - Configuration', () => {
  it('should create default configuration', () => {
    const config = createPipelineParserConfig();
    
    expect(config.layout).toBe('horizontal');
    expect(config.showMetrics).toBe(true);
    expect(config.showGates).toBe(true);
    expect(config.jobSpacing).toEqual({ x: 300, y: 150 });
    expect(config.stageSpacing).toBe(100);
  });

  it('should create configuration with overrides', () => {
    const config = createPipelineParserConfig({
      layout: 'vertical',
      showMetrics: false,
      jobSpacing: { x: 200, y: 200 },
    });
    
    expect(config.layout).toBe('vertical');
    expect(config.showMetrics).toBe(false);
    expect(config.showGates).toBe(true);
    expect(config.jobSpacing).toEqual({ x: 200, y: 200 });
  });
});

describe.skip('PipelineParser - GitHub Actions', () => {
  it('should parse simple GitHub Actions workflow', () => {
    const yaml = `
name: CI
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - run: npm install
      - run: npm test
`;

    const pipeline = parseGitHubActions(yaml);
    
    expect(pipeline.platform).toBe('github-actions');
    expect(pipeline.name).toBe('CI');
    expect(pipeline.jobs).toHaveLength(1);
    expect(pipeline.jobs[0].id).toBe('build');
    expect(pipeline.jobs[0].name).toBe('build');
    expect(pipeline.jobs[0].dependsOn).toEqual([]);
  });

  it('should parse GitHub Actions with job dependencies', () => {
    const yaml = `
name: Build and Deploy
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - run: npm run build
  test:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - run: npm test
  deploy:
    needs: [build, test]
    runs-on: ubuntu-latest
    steps:
      - run: npm run deploy
`;

    const pipeline = parseGitHubActions(yaml);
    
    expect(pipeline.jobs).toHaveLength(3);
    
    const buildJob = pipeline.jobs.find(j => j.id === 'build');
    expect(buildJob?.dependsOn).toEqual([]);
    
    const testJob = pipeline.jobs.find(j => j.id === 'test');
    expect(testJob?.dependsOn).toEqual(['build']);
    
    const deployJob = pipeline.jobs.find(j => j.id === 'deploy');
    expect(deployJob?.dependsOn).toContain('build');
    expect(deployJob?.dependsOn).toContain('test');
  });

  it('should extract triggers from GitHub Actions', () => {
    const yaml = `
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - run: npm test
`;

    const pipeline = parseGitHubActions(yaml);
    
    expect(pipeline.triggers).toContain('push');
    expect(pipeline.triggers).toContain('pull_request');
  });

  it('should handle multiple jobs in GitHub Actions', () => {
    const yaml = `
name: Multi-Job
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - run: npm run lint
  test:
    runs-on: ubuntu-latest
    steps:
      - run: npm test
  build:
    runs-on: ubuntu-latest
    steps:
      - run: npm run build
`;

    const pipeline = parseGitHubActions(yaml);
    
    expect(pipeline.jobs).toHaveLength(3);
    expect(pipeline.jobs.map(j => j.id)).toEqual(['lint', 'test', 'build']);
  });

  it('should handle environment in GitHub Actions', () => {
    const yaml = `
name: Deploy
jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - run: deploy.sh
`;

    const pipeline = parseGitHubActions(yaml);
    
    const deployJob = pipeline.jobs[0];
    expect(deployJob.metadata.environment).toBe('production');
  });
});

describe.skip('PipelineParser - GitLab CI', () => {
  it('should parse simple GitLab CI pipeline', () => {
    const yaml = `
stages:
  - build
  - test

build-job:
  stage: build
  script:
    - npm install
    - npm run build

test-job:
  stage: test
  script:
    - npm test
`;

    const pipeline = parseGitLabCI(yaml);
    
    expect(pipeline.platform).toBe('gitlab-ci');
    expect(pipeline.jobs).toHaveLength(2);
    expect(pipeline.stages).toHaveLength(2);
    expect(pipeline.stages.map(s => s.name)).toEqual(['build', 'test']);
    
    const buildJob = pipeline.jobs.find(j => j.id === 'build-job');
    expect(buildJob?.stage).toBe('build');
    
    const testJob = pipeline.jobs.find(j => j.id === 'test-job');
    expect(testJob?.stage).toBe('test');
  });

  it('should parse GitLab CI with needs dependencies', () => {
    const yaml = `
stages:
  - build
  - test
  - deploy

build:
  stage: build
  script:
    - npm run build

test:
  stage: test
  needs: [build]
  script:
    - npm test

deploy:
  stage: deploy
  needs: [build, test]
  script:
    - npm run deploy
`;

    const pipeline = parseGitLabCI(yaml);
    
    const buildJob = pipeline.jobs.find(j => j.id === 'build');
    expect(buildJob?.dependsOn).toEqual([]);
    
    const testJob = pipeline.jobs.find(j => j.id === 'test');
    expect(testJob?.dependsOn).toEqual(['build']);
    
    const deployJob = pipeline.jobs.find(j => j.id === 'deploy');
    expect(deployJob?.dependsOn).toContain('build');
    expect(deployJob?.dependsOn).toContain('test');
  });

  it('should handle stage-based dependencies in GitLab CI', () => {
    const yaml = `
stages:
  - stage1
  - stage2

job-a:
  stage: stage1
  script: echo "a"

job-b:
  stage: stage2
  script: echo "b"
`;

    const pipeline = parseGitLabCI(yaml);
    
    // job-b in stage2 should depend on all jobs in stage1
    const jobB = pipeline.jobs.find(j => j.id === 'job-b');
    expect(jobB?.dependsOn).toContain('job-a');
  });

  it('should extract only configuration from GitLab CI', () => {
    const yaml = `
stages:
  - test

variables:
  NODE_ENV: test

build:
  stage: test
  only:
    - main
  script:
    - npm test
`;

    const pipeline = parseGitLabCI(yaml);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.only).toContain('main');
  });

  it('should handle artifacts in GitLab CI', () => {
    const yaml = `
stages:
  - build

build:
  stage: build
  script:
    - npm run build
  artifacts:
    paths:
      - dist/
`;

    const pipeline = parseGitLabCI(yaml);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.artifacts).toEqual({ paths: ['dist/'] });
  });
});

describe.skip('PipelineParser - Jenkins', () => {
  it('should parse simple Jenkins declarative pipeline', () => {
    const jenkinsfile = `
pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        sh 'npm install'
        sh 'npm run build'
      }
    }
    stage('Test') {
      steps {
        sh 'npm test'
      }
    }
  }
}
`;

    const pipeline = parseJenkins(jenkinsfile);
    
    expect(pipeline.platform).toBe('jenkins');
    expect(pipeline.jobs).toHaveLength(2);
    
    const buildJob = pipeline.jobs.find(j => j.name === 'Build');
    expect(buildJob).toBeDefined();
    expect(buildJob?.stage).toBe('Build');
    
    const testJob = pipeline.jobs.find(j => j.name === 'Test');
    expect(testJob).toBeDefined();
    expect(testJob?.dependsOn).toContain(buildJob?.id);
  });

  it('should handle parallel stages in Jenkins', () => {
    const jenkinsfile = `
pipeline {
  agent any
  stages {
    stage('Parallel Tests') {
      parallel {
        stage('Unit Tests') {
          steps {
            sh 'npm run test:unit'
          }
        }
        stage('Integration Tests') {
          steps {
            sh 'npm run test:integration'
          }
        }
      }
    }
  }
}
`;

    const pipeline = parseJenkins(jenkinsfile);
    
    expect(pipeline.jobs).toHaveLength(2);
    const unitTest = pipeline.jobs.find(j => j.name === 'Unit Tests');
    const integrationTest = pipeline.jobs.find(j => j.name === 'Integration Tests');
    
    expect(unitTest?.dependsOn).toEqual([]);
    expect(integrationTest?.dependsOn).toEqual([]);
  });

  it('should extract agent information from Jenkins', () => {
    const jenkinsfile = `
pipeline {
  agent { label 'linux' }
  stages {
    stage('Build') {
      steps {
        sh 'make build'
      }
    }
  }
}
`;

    const pipeline = parseJenkins(jenkinsfile);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.agent).toBe('linux');
  });

  it('should handle when conditions in Jenkins', () => {
    const jenkinsfile = `
pipeline {
  agent any
  stages {
    stage('Deploy') {
      when {
        branch 'main'
      }
      steps {
        sh 'deploy.sh'
      }
    }
  }
}
`;

    const pipeline = parseJenkins(jenkinsfile);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.when).toBe('branch main');
  });
});

describe.skip('PipelineParser - CircleCI', () => {
  it('should parse simple CircleCI config', () => {
    const yaml = `
version: 2.1
jobs:
  build:
    docker:
      - image: node:14
    steps:
      - checkout
      - run: npm install
      - run: npm run build
  test:
    docker:
      - image: node:14
    steps:
      - checkout
      - run: npm test

workflows:
  version: 2
  build-and-test:
    jobs:
      - build
      - test:
          requires:
            - build
`;

    const pipeline = parseCircleCI(yaml);
    
    expect(pipeline.platform).toBe('circleci');
    expect(pipeline.jobs).toHaveLength(2);
    
    const testJob = pipeline.jobs.find(j => j.id === 'test');
    expect(testJob?.dependsOn).toContain('build');
  });

  it('should handle CircleCI filters', () => {
    const yaml = `
version: 2.1
jobs:
  deploy:
    docker:
      - image: node:14
    steps:
      - run: deploy.sh

workflows:
  version: 2
  deploy:
    jobs:
      - deploy:
          filters:
            branches:
              only: main
`;

    const pipeline = parseCircleCI(yaml);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.filters).toEqual({
      branches: { only: 'main' }
    });
  });

  it('should extract docker executor from CircleCI', () => {
    const yaml = `
version: 2.1
jobs:
  test:
    docker:
      - image: cimg/node:16.13
    steps:
      - run: npm test
workflows:
  version: 2
  test:
    jobs:
      - test
`;

    const pipeline = parseCircleCI(yaml);
    
    const job = pipeline.jobs[0];
    expect(job.metadata.executor).toBe('docker: cimg/node:16.13');
  });

  it('should handle CircleCI orbs', () => {
    const yaml = `
version: 2.1
orbs:
  node: circleci/node@5.0
jobs:
  test:
    docker:
      - image: cimg/base:stable
    steps:
      - node/test
workflows:
  version: 2
  test:
    jobs:
      - test
`;

    const pipeline = parseCircleCI(yaml);
    
    expect(pipeline.metadata.orbs).toEqual(['circleci/node@5.0']);
  });
});

describe.skip('PipelineParser - DAG Layout', () => {
  it('should calculate job levels for simple pipeline', () => {
    const jobs: PipelineJob[] = [
      { id: 'a', name: 'Job A', stage: 'build', dependsOn: [], metadata: {} },
      { id: 'b', name: 'Job B', stage: 'test', dependsOn: ['a'], metadata: {} },
      { id: 'c', name: 'Job C', stage: 'deploy', dependsOn: ['b'], metadata: {} },
    ];

    const levels = calculateJobLevels(jobs);
    
    expect(levels.get('a')).toBe(0);
    expect(levels.get('b')).toBe(1);
    expect(levels.get('c')).toBe(2);
  });

  it('should calculate levels for parallel jobs', () => {
    const jobs: PipelineJob[] = [
      { id: 'a', name: 'Job A', stage: 'build', dependsOn: [], metadata: {} },
      { id: 'b', name: 'Job B', stage: 'test', dependsOn: ['a'], metadata: {} },
      { id: 'c', name: 'Job C', stage: 'test', dependsOn: ['a'], metadata: {} },
      { id: 'd', name: 'Job D', stage: 'deploy', dependsOn: ['b', 'c'], metadata: {} },
    ];

    const levels = calculateJobLevels(jobs);
    
    expect(levels.get('a')).toBe(0);
    expect(levels.get('b')).toBe(1);
    expect(levels.get('c')).toBe(1);
    expect(levels.get('d')).toBe(2);
  });

  it('should handle diamond dependency graph', () => {
    const jobs: PipelineJob[] = [
      { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
      { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      { id: 'c', name: 'C', stage: 's2', dependsOn: ['a'], metadata: {} },
      { id: 'd', name: 'D', stage: 's3', dependsOn: ['b', 'c'], metadata: {} },
    ];

    const levels = calculateJobLevels(jobs);
    
    expect(levels.get('a')).toBe(0);
    expect(levels.get('b')).toBe(1);
    expect(levels.get('c')).toBe(1);
    expect(levels.get('d')).toBe(2);
  });

  it('should group jobs by stage', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 'build', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 'build', dependsOn: [], metadata: {} },
        { id: 'c', name: 'C', stage: 'test', dependsOn: [], metadata: {} },
      ],
      stages: [] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const grouped = groupJobsByStage(pipeline);
    
    expect(grouped.size).toBe(2);
    expect(grouped.get('build')).toHaveLength(2);
    expect(grouped.get('test')).toHaveLength(1);
  });
});

describe.skip('PipelineParser - Canvas Conversion', () => {
  it('should convert pipeline to canvas document', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test Pipeline',
      jobs: [
        { id: 'build', name: 'Build', stage: 'build', dependsOn: [], metadata: {} },
        { id: 'test', name: 'Test', stage: 'test', dependsOn: ['build'], metadata: {} },
      ],
      stages: ['build', 'test'] as unknown, // String array for backward compat
      gates: [],
      triggers: ['push'],
      metadata: {},
    };

    const config = createPipelineParserConfig();
    const doc = pipelineToCanvas(pipeline, config);
    
    expect(doc.id).toBe('pipeline-Test Pipeline');
    expect(doc.title).toBe('Pipeline: Test Pipeline');
    expect(Object.keys(doc.elements)).toHaveLength(3); // 2 jobs + 1 edge
    expect(doc.elementOrder).toHaveLength(3);
  });

  it('should position jobs horizontally', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig({ layout: 'horizontal' });
    const doc = pipelineToCanvas(pipeline, config);
    
    const jobA = doc.elements['a'];
    const jobB = doc.elements['b'];
    
    expect(jobA.type).toBe('node');
    expect(jobB.type).toBe('node');
    
    if (jobA.type === 'node' && jobB.type === 'node') {
      expect(jobB.transform.position.x).toBeGreaterThan(jobA.transform.position.x);
    }
  });

  it('should position jobs vertically', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig({ layout: 'vertical' });
    const doc = pipelineToCanvas(pipeline, config);
    
    const jobA = doc.elements['a'];
    const jobB = doc.elements['b'];
    
    if (jobA.type === 'node' && jobB.type === 'node') {
      expect(jobB.transform.position.y).toBeGreaterThan(jobA.transform.position.y);
    }
  });

  it('should create edges for job dependencies', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig();
    const doc = pipelineToCanvas(pipeline, config);
    
    const edge = doc.elements['edge-a-b'];
    expect(edge).toBeDefined();
    expect(edge.type).toBe('edge');
    
    if (edge.type === 'edge') {
      const canvasEdge = edge as import('../../types/canvas-document').CanvasEdge;
      expect(canvasEdge.sourceId).toBe('a');
      expect(canvasEdge.targetId).toBe('b');
    }
  });

  it('should handle parallel jobs at same level', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
        { id: 'c', name: 'C', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig();
    const doc = pipelineToCanvas(pipeline, config);
    
    const jobB = doc.elements['b'];
    const jobC = doc.elements['c'];
    
    if (jobB.type === 'node' && jobC.type === 'node') {
      // Parallel jobs should be at different Y positions
      expect(jobB.transform.position.y).not.toBe(jobC.transform.position.y);
    }
  });

  it('should add stage labels when showStageLabels is true', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 'build', dependsOn: [], metadata: {} },
      ],
      stages: ['build'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig({ showStageLabels: true });
    const doc = pipelineToCanvas(pipeline, config);
    
    // Should have stage label node
    const stageLabelExists = Object.values(doc.elements).some(
      el => el.type === 'node' && (el as unknown).nodeType === 'stage-label'
    );
    expect(stageLabelExists).toBe(true);
  });

  it('should include gates when showGates is true', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [
        { id: 'gate-1', type: 'manual-approval', beforeJob: 'b', name: 'Approve Deploy' },
      ],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig({ showGates: true });
    const doc = pipelineToCanvas(pipeline, config);
    
    const gate = doc.elements['gate-1'];
    expect(gate).toBeDefined();
    expect(gate.type).toBe('node');
    
    if (gate.type === 'node') {
      expect((gate as unknown).nodeType).toBe('pipeline-gate');
    }
  });

  it('should calculate correct bounds for document', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'a', name: 'A', stage: 's1', dependsOn: [], metadata: {} },
        { id: 'b', name: 'B', stage: 's2', dependsOn: ['a'], metadata: {} },
      ],
      stages: ['s1', 's2'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig();
    const doc = pipelineToCanvas(pipeline, config);
    
    const bounds = doc.metadata.bounds as { width: number; height: number; x: number; y: number };
    expect(bounds.width).toBeGreaterThan(0);
    expect(bounds.height).toBeGreaterThan(0);
  });
});

describe.skip('PipelineParser - Runtime Metrics', () => {
  it('should add metrics to pipeline jobs', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        { id: 'build', name: 'Build', stage: 'build', dependsOn: [], metadata: {} },
      ],
      stages: ['build'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const execution: PipelineExecution = {
      id: 'exec-1',
      pipelineId: 'pipeline-1',
      startTime: new Date('2024-01-01T10:00:00Z'),
      endTime: new Date('2024-01-01T10:15:00Z'),
      status: 'success',
      jobMetrics: [
        {
          jobId: 'build',
          status: 'success',
          startTime: new Date('2024-01-01T10:00:00Z'),
          endTime: new Date('2024-01-01T10:10:00Z'),
          duration: 600,
        },
      ],
    };

    const updatedPipeline = addMetricsToPipeline(pipeline, execution);
    
    const buildJob = updatedPipeline.jobs.find(j => j.id === 'build');
    expect(buildJob?.metadata.metrics).toBeDefined();
    expect(buildJob?.metadata.metrics?.status).toBe('success');
    expect(buildJob?.metadata.metrics?.duration).toBe(600);
  });

  it('should display metrics in canvas when showMetrics is true', () => {
    const pipeline: Pipeline = {
      platform: 'github-actions',
      name: 'Test',
      jobs: [
        {
          id: 'build',
          name: 'Build',
          stage: 'build',
          dependsOn: [],
          metadata: {
            metrics: {
              jobId: 'build',
              status: 'success',
              startTime: new Date('2024-01-01T10:00:00Z'),
              endTime: new Date('2024-01-01T10:10:00Z'),
              duration: 600,
            },
          },
        },
      ],
      stages: ['build'] as unknown,
      gates: [],
      triggers: [],
      metadata: {},
    };

    const config = createPipelineParserConfig({ showMetrics: true });
    const metrics = new Map([
      ['build', pipeline.jobs[0].metadata.metrics!],
    ]);
    const doc = pipelineToCanvas(pipeline, config, metrics);
    
    const buildJob = doc.elements['build'];
    expect(buildJob.metadata.duration).toBe(600);
    expect(buildJob.metadata.status).toBe('success');
  });

  it('should calculate pipeline total duration from execution', () => {
    const execution: PipelineExecution = {
      id: 'exec-1',
      pipelineId: 'pipeline-1',
      startTime: new Date('2024-01-01T10:00:00Z'),
      endTime: new Date('2024-01-01T10:20:00Z'),
      status: 'success',
      jobMetrics: [],
    };

    const duration = (execution.endTime.getTime() - execution.startTime.getTime()) / 1000;
    expect(duration).toBe(1200); // 20 minutes
  });

  it('should track resource usage metrics', () => {
    const metrics: JobMetrics = {
      jobId: 'build',
      status: 'success',
      startTime: new Date(),
      endTime: new Date(),
      duration: 300,
      resourceUsage: {
        cpu: 75.5,
        memory: 2048,
        diskIO: 1024,
      },
    };

    expect(metrics.resourceUsage?.cpu).toBe(75.5);
    expect(metrics.resourceUsage?.memory).toBe(2048);
  });
});

describe.skip('PipelineParser - Styling', () => {
  it('should apply pending status style', () => {
    const style = getJobStyle('pending');
    expect(style.fill).toBe('#808080');
    expect(style.stroke).toBe('#666');
  });

  it('should apply running status style', () => {
    const style = getJobStyle('running');
    expect(style.fill).toBe('#3b82f6');
    expect(style.stroke).toBe('#2563eb');
  });

  it('should apply success status style', () => {
    const style = getJobStyle('success');
    expect(style.fill).toBe('#10b981');
    expect(style.stroke).toBe('#059669');
  });

  it('should apply failure status style', () => {
    const style = getJobStyle('failure');
    expect(style.fill).toBe('#ef4444');
    expect(style.stroke).toBe('#dc2626');
  });

  it('should apply skipped status style', () => {
    const style = getJobStyle('skipped');
    expect(style.fill).toBe('#6b7280');
    expect(style.stroke).toBe('#4b5563');
  });

  it('should apply cancelled status style', () => {
    const style = getJobStyle('cancelled');
    expect(style.fill).toBe('#f59e0b');
    expect(style.stroke).toBe('#d97706');
  });

  it('should apply manual-approval gate style', () => {
    const style = getGateStyle('manual-approval');
    expect(style.fill).toBe('#fbbf24');
    expect(style.stroke).toBe('#f59e0b');
  });

  it('should apply automated-check gate style', () => {
    const style = getGateStyle('automated-check');
    expect(style.fill).toBe('#60a5fa');
    expect(style.stroke).toBe('#3b82f6');
  });

  it('should apply deployment-gate style', () => {
    const style = getGateStyle('deployment-gate');
    expect(style.fill).toBe('#a78bfa');
    expect(style.stroke).toBe('#8b5cf6');
  });
});
