/**
 * @fileoverview CI/CD Pipeline Visualization
 * 
 * Provides parsing and visualization for CI/CD pipeline configurations from
 * GitHub Actions, GitLab CI, Jenkins, and CircleCI. Renders pipelines as
 * directed acyclic graphs (DAGs) with job dependencies, runtime metrics,
 * and gate overlays.
 * 
 * @module libs/canvas/src/devsecops/pipelineParser
 */

import type { CanvasDocument, CanvasNode, CanvasEdge } from '../types/canvas-document';

/**
 * Supported CI/CD platforms
 */
export type CIPlatform = 'github-actions' | 'gitlab-ci' | 'jenkins' | 'circleci';

/**
 * Job status types
 */
export type JobStatus = 
  | 'pending'
  | 'running'
  | 'success'
  | 'failure'
  | 'skipped'
  | 'cancelled';

/**
 * Gate/approval types
 */
export type GateType = 'manual-approval' | 'automated-check' | 'deployment-gate';

/**
 * Pipeline job definition
 */
export interface PipelineJob {
  id: string;
  name: string;
  stage?: string;
  dependsOn: string[];
  metadata: Record<string, unknown>;
}

/**
 * Pipeline stage grouping
 */
export interface PipelineStage {
  name: string;
  jobs: string[];
  order: number;
}

/**
 * Pipeline gate/approval
 */
export interface PipelineGate {
  id: string;
  name: string;
  type: GateType;
  beforeJob: string;
  approvers?: string[];
  conditions?: string[];
  timeout?: number;
}

/**
 * Runtime metrics for a job
 */
export interface JobMetrics {
  jobId: string;
  status: JobStatus;
  startTime: Date;
  endTime: Date;
  duration: number; // in seconds
  exitCode?: number;
  resourceUsage?: {
    cpu?: number;
    memory?: number;
    diskIO?: number;
  };
}

/**
 * Pipeline definition (parsed from config)
 */
export interface Pipeline {
  platform: CIPlatform;
  name: string;
  jobs: PipelineJob[];
  stages: PipelineStage[];
  gates: PipelineGate[];
  triggers: string[];
  metadata: Record<string, unknown>;
}

/**
 * Pipeline with runtime data
 */
export interface PipelineExecution {
  id: string;
  pipelineId: string;
  startTime: Date;
  endTime: Date;
  status: JobStatus;
  jobMetrics: JobMetrics[];
}

/**
 * Pipeline parser configuration
 */
export interface PipelineParserConfig {
  layout?: 'horizontal' | 'vertical';
  showMetrics?: boolean;
  showGates?: boolean;
  showStageLabels?: boolean;
  jobSpacing?: { x: number; y: number };
  stageSpacing?: number;
}

/**
 * Create default pipeline parser config
 */
export function createPipelineParserConfig(
  overrides?: Partial<PipelineParserConfig>
): PipelineParserConfig {
  return {
    layout: 'horizontal',
    showMetrics: true,
    showGates: true,
    showStageLabels: false,
    jobSpacing: { x: 300, y: 150 },
    stageSpacing: 100,
    ...overrides,
  };
}

/**
 * Parse GitHub Actions workflow YAML
 * 
 * @param yaml - GitHub Actions workflow YAML content
 * @returns Parsed pipeline
 * 
 * @example
 * ```typescript
 * const yaml = `
 *   name: CI
 *   on: [push]
 *   jobs:
 *     build:
 *       runs-on: ubuntu-latest
 *       steps:
 *         - uses: actions/checkout@v3
 *         - run: npm install
 *         - run: npm test
 * `;
 * const pipeline = parseGitHubActions(yaml);
 * ```
 */
export function parseGitHubActions(yaml: string): Pipeline {
  const lines = yaml.split('\n');
  const pipeline: Pipeline = {
    name: 'GitHub Actions',
    platform: 'github-actions',
    jobs: [],
    stages: [],
    gates: [],
    triggers: [],
    metadata: {},
  };

  let currentJob: Partial<PipelineJob> | null = null;
  let inJobsSection = false;
  let inOnSection = false;

  for (const line of lines) {
    const trimmed = line.trim();
    
    // Parse name
    if (trimmed.startsWith('name:')) {
      pipeline.name = trimmed.replace('name:', '').trim().replace(/['"]/g, '');
    }
    
    // Parse triggers
    else if (trimmed.startsWith('on:')) {
      inOnSection = true;
      const trigger = trimmed.replace('on:', '').trim();
      if (trigger === '[push]' || trigger === 'push') {
        pipeline.triggers.push('push');
      }
    }
    else if (inOnSection && (trimmed === 'push:' || trimmed === 'pull_request:')) {
      const triggerName = trimmed.replace(':', '');
      if (!pipeline.triggers.includes(triggerName)) {
        pipeline.triggers.push(triggerName);
      }
    }
    
    // Jobs section
    else if (trimmed === 'jobs:') {
      inJobsSection = true;
      inOnSection = false;
    }
    
    // Job definition
    else if (inJobsSection && trimmed.endsWith(':') && !trimmed.startsWith('-') && !line.startsWith('    ')) {
      if (currentJob && currentJob.id) {
        pipeline.jobs.push(currentJob as PipelineJob);
      }
      const jobId = trimmed.replace(':', '');
      currentJob = {
        id: jobId,
        name: jobId,
        dependsOn: [],
        metadata: {},
      };
    }
    
    // Job needs (dependencies)
    else if (currentJob && trimmed.startsWith('needs:')) {
      const needs = trimmed.replace('needs:', '').trim();
      if (needs.startsWith('[')) {
        // Array format: needs: [job1, job2]
        const deps = needs.replace(/[\[\]]/g, '').split(',').map(d => d.trim());
        currentJob.dependsOn = deps;
      } else {
        // Single job: needs: job1
        currentJob.dependsOn = [needs];
      }
    }
    
    // Job environment
    else if (currentJob && trimmed.startsWith('environment:')) {
      const env = trimmed.replace('environment:', '').trim();
      currentJob.metadata.environment = env;
    }
  }

  // Add last job
  if (currentJob && currentJob.id) {
    pipeline.jobs.push(currentJob as PipelineJob);
  }

  // Create stages based on jobs
  if (pipeline.jobs.length > 0) {
    pipeline.stages.push({
      name: 'default',
      jobs: pipeline.jobs.map(j => j.id),
      order: 0,
    });
  }

  return pipeline;
}

/**
 * Parse GitLab CI YAML
 * 
 * @param yaml - GitLab CI YAML content
 * @returns Parsed pipeline
 * 
 * @example
 * ```typescript
 * const yaml = `
 *   stages:
 *     - build
 *     - test
 *   
 *   build-job:
 *     stage: build
 *     script:
 *       - npm install
 *   
 *   test-job:
 *     stage: test
 *     script:
 *       - npm test
 * `;
 * const pipeline = parseGitLabCI(yaml);
 * ```
 */
export function parseGitLabCI(yaml: string): Pipeline {
  const lines = yaml.split('\n');
  const pipeline: Pipeline = {
    name: 'GitLab CI',
    platform: 'gitlab-ci',
    jobs: [],
    stages: [],
    gates: [],
    triggers: [],
    metadata: {},
  };

  let currentJob: Partial<PipelineJob> | null = null;
  const stageNames: string[] = [];
  let collectingStages = false;

  for (const line of lines) {
    const trimmed = line.trim();
    
    // Parse stages
    if (trimmed.startsWith('stages:')) {
      collectingStages = true;
      continue;
    } else if (collectingStages && trimmed.startsWith('- ')) {
      // Collecting stage names
      const stageName = trimmed.replace('- ', '').trim();
      if (stageName && !stageName.includes(':')) {
        stageNames.push(stageName);
      }
    } else if (collectingStages && !trimmed.startsWith('- ')) {
      collectingStages = false;
    }
    
    // Job definition (ends with : and not indented)
    else if (trimmed.endsWith(':') && !trimmed.startsWith('-') && !line.startsWith(' ')) {
      if (currentJob && currentJob.id) {
        pipeline.jobs.push(currentJob as PipelineJob);
      }
      const jobId = trimmed.replace(':', '');
      if (!['stages', 'variables', 'default'].includes(jobId)) {
        currentJob = {
          id: jobId,
          name: jobId,
          dependsOn: [],
          metadata: {},
        };
      }
    }
    
    // Job stage
    else if (currentJob && trimmed.startsWith('stage:')) {
      const stage = trimmed.replace('stage:', '').trim();
      currentJob.stage = stage;
    }
    
    // Job needs (dependencies)
    else if (currentJob && trimmed.startsWith('needs:')) {
      const needs = trimmed.replace('needs:', '').trim();
      if (needs.startsWith('[')) {
        const deps = needs.replace(/[\[\]]/g, '').split(',').map(d => d.trim());
        currentJob.dependsOn = deps;
      }
    }
    
    // Job only
    else if (currentJob && trimmed.startsWith('only:')) {
      currentJob.metadata.only = [];
    } else if (currentJob && line.startsWith('    - ') && currentJob.metadata.only) {
      const only = trimmed.replace('- ', '').trim();
      currentJob.metadata.only.push(only);
    }
    
    // Job artifacts
    else if (currentJob && trimmed.startsWith('artifacts:')) {
      currentJob.metadata.artifacts = {};
    } else if (currentJob && trimmed.startsWith('paths:') && currentJob.metadata.artifacts !== undefined) {
      currentJob.metadata.artifacts.paths = [];
    } else if (currentJob && line.startsWith('      - ') && currentJob.metadata.artifacts?.paths) {
      const path = trimmed.replace('- ', '').trim();
      currentJob.metadata.artifacts.paths.push(path);
    }
  }

  // Add last job
  if (currentJob && currentJob.id) {
    pipeline.jobs.push(currentJob as PipelineJob);
  }

  // Apply stage-based dependencies
  stageNames.forEach((stageName, index) => {
    if (index > 0) {
      const prevStageName = stageNames[index - 1];
      const prevStageJobs = pipeline.jobs.filter(j => j.stage === prevStageName).map(j => j.id);
      const currentStageJobs = pipeline.jobs.filter(j => j.stage === stageName);
      
      currentStageJobs.forEach(job => {
        if (job.dependsOn.length === 0) {
          job.dependsOn.push(...prevStageJobs);
        }
      });
    }
  });

  // Create stages
  stageNames.forEach((stageName, index) => {
    const stageJobs = pipeline.jobs
      .filter(j => j.stage === stageName)
      .map(j => j.id);
    
    if (stageJobs.length > 0) {
      pipeline.stages.push({
        name: stageName,
        jobs: stageJobs,
        order: index,
      });
    }
  });

  return pipeline;
}

/**
 * Parse Jenkins Declarative Pipeline
 * 
 * @param jenkinsfile - Jenkinsfile content
 * @returns Parsed pipeline
 * 
 * @example
 * ```typescript
 * const jenkinsfile = `
 *   pipeline {
 *     stages {
 *       stage('Build') {
 *         steps {
 *           sh 'npm install'
 *         }
 *       }
 *       stage('Test') {
 *         steps {
 *           sh 'npm test'
 *         }
 *       }
 *     }
 *   }
 * `;
 * const pipeline = parseJenkins(jenkinsfile);
 * ```
 */
export function parseJenkins(jenkinsfile: string): Pipeline {
  const pipeline: Pipeline = {
    name: 'Jenkins Pipeline',
    platform: 'jenkins',
    jobs: [],
    stages: [],
    gates: [],
    triggers: [],
    metadata: {},
  };

  const lines = jenkinsfile.split('\n');
  let stageOrder = 0;
  let currentStage: string | null = null;
  let agent: string | null = null;
  let when: string | null = null;
  let inParallelBlock = false;
  let parallelStages: string[] = [];
  let braceDepth = 0; // Track brace nesting depth
  let parallelBraceStart = 0; // Brace depth when parallel block started
  
  for (const line of lines) {
    const trimmed = line.trim();
    
    // Track opening and closing braces
    if (trimmed.includes('{')) {
      braceDepth++;
    }
    if (trimmed.includes('}')) {
      braceDepth--;
      // End parallel block when we close back to the depth where parallel started
      if (inParallelBlock && braceDepth < parallelBraceStart) {
        inParallelBlock = false;
        parallelStages = [];
      }
    }
    
    // Agent
    const agentMatch = trimmed.match(/agent\s+\{\s*label\s+['"]([^'"]+)['"]\s*\}/);
    if (agentMatch) {
      agent = agentMatch[1];
    }
    
    // Parallel block start - remove the last added stage (it's just a wrapper)
    if (trimmed === 'parallel {' || trimmed.startsWith('parallel {')) {
      // Remove the last job and stage (the wrapper)
      if (pipeline.jobs.length > 0) {
        pipeline.jobs.pop();
        pipeline.stages.pop();
        stageOrder--; // Decrement back
      }
      inParallelBlock = true;
      parallelStages = [];
      parallelBraceStart = braceDepth; // Remember the depth where parallel started
      continue;
    }
    
    // Stage definition
    const stageMatch = trimmed.match(/stage\s*\(\s*['"]([^'"]+)['"]\s*\)/);
    if (stageMatch) {
      const stageName = stageMatch[1];
      
      // Track stages inside parallel block
      if (inParallelBlock) {
        parallelStages.push(stageName);
      }
      
      currentStage = stageName;
      const jobId = `stage-${currentStage.toLowerCase().replace(/\s+/g, '-')}`;
      
      // Parallel stages have no dependencies on each other
      const deps = inParallelBlock
        ? [] 
        : (stageOrder > 0 ? [`stage-${pipeline.stages[stageOrder - 1]?.name.toLowerCase().replace(/\s+/g, '-')}`] : []);
      
      pipeline.jobs.push({
        id: jobId,
        name: currentStage,
        stage: currentStage,
        dependsOn: deps,
        metadata: agent ? { agent } : {},
      });
      
      pipeline.stages.push({
        name: currentStage,
        jobs: [jobId],
        order: stageOrder,
      });
      
      // Only increment stage order for non-parallel stages or first parallel stage
      if (!inParallelBlock || parallelStages.length === 1) {
        stageOrder++;
      }
      when = null;
    }
    
    // When condition (can be on same line or separate lines)
    if (trimmed.startsWith('when {') || trimmed === 'when {') {
      // Multi-line when block - look for branch in next lines
      // Will be handled by the branch matching below
    }
    
    const whenMatch = trimmed.match(/branch\s+['"]([^'"]+)['"]/);
    if (whenMatch && currentStage) {
      when = `branch ${whenMatch[1]}`;
      const lastJob = pipeline.jobs[pipeline.jobs.length - 1];
      if (lastJob) {
        lastJob.metadata.when = when;
      }
    }
  }

  return pipeline;
}

/**
 * Parse CircleCI config
 * 
 * @param yaml - CircleCI config YAML content
 * @returns Parsed pipeline
 * 
 * @example
 * ```typescript
 * const yaml = `
 *   version: 2.1
 *   jobs:
 *     build:
 *       docker:
 *         - image: cimg/node:18.0
 *       steps:
 *         - checkout
 *         - run: npm install
 *   workflows:
 *     build-test:
 *       jobs:
 *         - build
 * `;
 * const pipeline = parseCircleCI(yaml);
 * ```
 */
export function parseCircleCI(yaml: string): Pipeline {
  const lines = yaml.split('\n');
  const pipeline: Pipeline = {
    name: 'CircleCI',
    platform: 'circleci',
    jobs: [],
    stages: [],
    gates: [],
    triggers: [],
    metadata: {},
  };

  let inJobsSection = false;
  let inWorkflowsSection = false;
  let inOrbsSection = false;
  let currentJob: Partial<PipelineJob> | null = null;
  let currentJobInWorkflow: string | null = null;
  let currentIndent = 0;
  let inRequiresSection = false;
  let inFiltersSection = false;
  let inBranchesSection = false;
  const orbs: string[] = [];

  for (const line of lines) {
    const trimmed = line.trim();
    const indent = line.search(/\S/);
    
    // Orbs section
    if (trimmed === 'orbs:') {
      inOrbsSection = true;
      inJobsSection = false;
      inWorkflowsSection = false;
      continue;
    } else if (inOrbsSection && indent === 2 && trimmed.includes(':')) {
      const [name, value] = trimmed.split(':').map(s => s.trim());
      if (value) {
        orbs.push(value);
      }
    }
    
    // Jobs section
    if (trimmed === 'jobs:') {
      inJobsSection = true;
      inOrbsSection = false;
      inWorkflowsSection = false;
      continue;
    }
    
    // Workflows section
    if (trimmed === 'workflows:') {
      inWorkflowsSection = true;
      inJobsSection = false;
      inOrbsSection = false;
      if (currentJob && currentJob.id) {
        pipeline.jobs.push(currentJob as PipelineJob);
        currentJob = null;
      }
      continue;
    }
    
    // Job definition in jobs section
    if (inJobsSection && indent === 2 && trimmed.endsWith(':') && !trimmed.startsWith('-')) {
      if (currentJob && currentJob.id) {
        pipeline.jobs.push(currentJob as PipelineJob);
      }
      const jobId = trimmed.replace(':', '');
      currentJob = {
        id: jobId,
        name: jobId,
        dependsOn: [],
        metadata: {},
      };
      currentIndent = indent;
    }
    
    // Docker executor
    else if (currentJob && trimmed.startsWith('- image:')) {
      const image = trimmed.replace('- image:', '').trim();
      if (currentJob.metadata) {
        currentJob.metadata.executor = `docker: ${image}`;
      }
    }
    
    // Job requires in workflow
    if (inWorkflowsSection && currentJobInWorkflow && trimmed === 'requires:') {
      inRequiresSection = true;
      inFiltersSection = false;
      inBranchesSection = false;
      continue; // Move to next line
    }
    
    if (inWorkflowsSection && inRequiresSection && trimmed.startsWith('- ')) {
      const depJobId = trimmed.replace('- ', '').trim();
      const job = pipeline.jobs.find(j => j.id === currentJobInWorkflow);
      if (job && !job.dependsOn.includes(depJobId)) {
        job.dependsOn.push(depJobId);
      }
      continue; // Move to next line
    }
    
    // Job in workflow (can be "- build" or "- test:")
    if (inWorkflowsSection && !inRequiresSection && !inFiltersSection && trimmed.startsWith('- ')) {
      // Extract job ID, removing leading "- " and trailing ":" if present
      const jobId = trimmed.replace('- ', '').replace(':', '').trim();
      currentJobInWorkflow = jobId;
      inRequiresSection = false;
      inFiltersSection = false;
      inBranchesSection = false;
      
      // Find or create job
      let job = pipeline.jobs.find(j => j.id === jobId);
      if (!job) {
        job = {
          id: jobId,
          name: jobId,
          dependsOn: [],
          metadata: {},
        };
        pipeline.jobs.push(job);
      }
      continue; // Move to next line
    }
    
    // Filters - use separate if checks, not else if
    if (inWorkflowsSection && currentJobInWorkflow && trimmed === 'filters:') {
      inFiltersSection = true;
      inRequiresSection = false;
      inBranchesSection = false;
      const job = pipeline.jobs.find(j => j.id === currentJobInWorkflow);
      if (job) {
        job.metadata.filters = {};
      }
    } 
    
    if (inFiltersSection && trimmed === 'branches:') {
      inBranchesSection = true;
      const job = pipeline.jobs.find(j => j.id === currentJobInWorkflow);
      if (job && job.metadata.filters) {
        job.metadata.filters.branches = {};
      }
    } 
    
    if (inBranchesSection && trimmed.startsWith('only:')) {
      const only = trimmed.replace('only:', '').trim();
      const job = pipeline.jobs.find(j => j.id === currentJobInWorkflow);
      if (job && job.metadata.filters?.branches) {
        job.metadata.filters.branches.only = only;
      }
    }
  }

  // Add last job if still parsing jobs section
  if (currentJob && currentJob.id) {
    pipeline.jobs.push(currentJob as PipelineJob);
  }

  // Add orbs to metadata
  if (orbs.length > 0) {
    pipeline.metadata.orbs = orbs;
  }

  // Create default stage
  if (pipeline.jobs.length > 0) {
    pipeline.stages.push({
      name: 'default',
      jobs: pipeline.jobs.map(j => j.id),
      order: 0,
    });
  }

  return pipeline;
}

/**
 * Convert pipeline to canvas document (DAG visualization)
 * 
 * @param pipeline - Pipeline definition
 * @param config - Parser configuration
 * @param metrics - Optional runtime metrics
 * @returns Canvas document
 * 
 * @example
 * ```typescript
 * const canvas = pipelineToCanvas(pipeline, config);
 * ```
 */
export function pipelineToCanvas(
  pipeline: Pipeline,
  config: PipelineParserConfig,
  metrics?: Map<string, JobMetrics>
): CanvasDocument {
  const elements: Record<string, CanvasNode | CanvasEdge> = {};
  const elementOrder: string[] = [];

  // Layout constants
  const NODE_WIDTH = 180;
  const NODE_HEIGHT = 80;
  const HORIZONTAL_GAP = 100;
  const VERTICAL_GAP = 120;
  const STAGE_GAP = 200;

  // Build dependency graph to determine levels
  const levels = calculateJobLevels(pipeline.jobs);
  const stageGroups = groupJobsByStage(pipeline);

  let currentX = 50;
  let currentY = 50;

  // Create nodes for jobs
  pipeline.jobs.forEach((job) => {
    const level = levels.get(job.id) || 0;
    // Handle both PipelineStage[] and legacy string[] formats via type assertion
    const stages = pipeline.stages as unknown[];
    const stageIndex = stages.findIndex((s) => 
      typeof s === 'string' ? s === job.stage : s?.jobs?.includes?.(job.id)
    );
    
    // Calculate position
    if (config.layout === 'horizontal') {
      currentX = 50 + (level * (NODE_WIDTH + HORIZONTAL_GAP));
      const stageJobs = stageGroups.get(job.stage || 'default') || [];
      const jobIndex = stageJobs.indexOf(job.id);
      currentY = 50 + (jobIndex * (NODE_HEIGHT + VERTICAL_GAP));
    } else {
      currentY = 50 + (level * (NODE_HEIGHT + VERTICAL_GAP));
      currentX = 50 + (stageIndex * (NODE_WIDTH + STAGE_GAP));
    }

    const jobMetric = metrics?.get(job.id);
    const status = jobMetric?.status || 'pending';

    const node: CanvasNode = {
      id: job.id,
      type: 'node',
      nodeType: 'pipeline-job',
      transform: {
        position: { x: currentX, y: currentY },
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: currentX,
        y: currentY,
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 1,
      metadata: {
        jobName: job.name,
        stage: job.stage,
        status,
        duration: jobMetric?.duration,
        platform: pipeline.platform,
      },
      version: '1.0.0',
      createdAt: new Date(),
      updatedAt: new Date(),
      data: {
        label: job.name,
        status,
        duration: jobMetric?.duration ? `${jobMetric.duration}s` : undefined,
      },
      inputs: [],
      outputs: job.dependsOn,
      style: getJobStyle(status),
    };
    elements[job.id] = node;
    elementOrder.push(job.id);
  });

  // Create edges for dependencies
  pipeline.jobs.forEach((job) => {
    job.dependsOn.forEach((depId) => {
      const edgeId = `edge-${depId}-${job.id}`;
      const edge: CanvasEdge = {
        id: edgeId,
        type: 'edge',
        sourceId: depId,
        targetId: job.id,
        transform: {
          position: { x: 0, y: 0 },
          scale: 1,
          rotation: 0,
        },
        bounds: { x: 0, y: 0, width: 0, height: 0 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0,
        metadata: {
          type: 'dependency',
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        path: [],
        style: {
          stroke: '#666',
          strokeWidth: 2,
        },
      };
      elements[edgeId] = edge;
      elementOrder.push(edgeId);
    });
  });

  // Add gate nodes
  pipeline.gates.forEach((gate, index) => {
    const gateX = currentX + 300;
    const gateY = 50 + (index * 150);
    
    const gateNode: CanvasNode = {
      id: gate.id,
      type: 'node',
      nodeType: 'pipeline-gate',
      transform: {
        position: { x: gateX, y: gateY },
        scale: 1,
        rotation: 0,
      },
      bounds: {
        x: gateX,
        y: gateY,
        width: 120,
        height: 60,
      },
      visible: true,
      locked: false,
      selected: false,
      zIndex: 2,
      metadata: {
        gateName: gate.name,
        gateType: gate.type,
        beforeJob: gate.beforeJob,
      },
      version: '1.0.0',
      createdAt: new Date(),
      updatedAt: new Date(),
      data: {
        label: gate.name,
        type: gate.type,
      },
      inputs: [],
      outputs: [gate.beforeJob],
      style: getGateStyle(gate.type),
    };
    elements[gate.id] = gateNode;
    elementOrder.push(gate.id);
  });

  // Add stage labels if enabled
  if (config.showStageLabels && pipeline.stages.length > 0) {
    const stageGroups = new Map<string, PipelineJob[]>();
    
    // Group jobs by stage
    pipeline.jobs.forEach(job => {
      if (job.stage) {
        if (!stageGroups.has(job.stage)) {
          stageGroups.set(job.stage, []);
        }
        stageGroups.get(job.stage)!.push(job);
      }
    });

    // Create label nodes for each stage
    stageGroups.forEach((jobs, stageName) => {
      if (jobs.length === 0) return;

      // Find the leftmost/topmost job in this stage
      const firstJob = jobs[0];
      const jobNode = elements[firstJob.id] as CanvasNode;
      
      if (!jobNode) return;

      // Position label above (vertical) or to the left (horizontal) of the stage
      const labelX = config.layout === 'vertical' 
        ? jobNode.transform.position.x 
        : jobNode.transform.position.x - 150;
      const labelY = config.layout === 'vertical'
        ? jobNode.transform.position.y - 80
        : jobNode.transform.position.y;

      const labelNode: CanvasNode = {
        id: `stage-label-${stageName}`,
        type: 'node',
        nodeType: 'stage-label',
        transform: {
          position: { x: labelX, y: labelY },
          scale: 1,
          rotation: 0,
        },
        bounds: {
          x: labelX,
          y: labelY,
          width: 120,
          height: 40,
        },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 0, // Behind other elements
        metadata: {
          stageName,
          jobCount: jobs.length,
        },
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
        data: {
          label: stageName,
          type: 'stage-label',
        },
        inputs: [],
        outputs: [],
        style: {
          backgroundColor: '#f0f0f0',
          borderColor: '#999',
          borderWidth: 1,
          borderStyle: 'dashed',
          textColor: '#666',
          fontSize: 14,
          fontWeight: 'bold',
        },
      };

      elements[labelNode.id] = labelNode;
      // Add to beginning of order so labels appear behind jobs
      elementOrder.unshift(labelNode.id);
    });
  }

  return {
    id: `pipeline-${pipeline.name}`,
    version: '1.0.0',
    title: `Pipeline: ${pipeline.name}`,
    description: `${pipeline.platform} pipeline visualization`,
    viewport: {
      center: { x: 400, y: 300 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: {
      platform: pipeline.platform,
      jobCount: pipeline.jobs.length,
      stageCount: pipeline.stages.length,
      bounds: {
        x: 0,
        y: 0,
        width: currentX + NODE_WIDTH + 50,
        height: currentY + NODE_HEIGHT + 50,
      },
    },
    capabilities: {
      canEdit: false,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: false,
      canRedo: false,
      canExport: true,
      canImport: false,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

/**
 * Calculate job levels based on dependencies (for DAG layout)
 */
export function calculateJobLevels(jobs: PipelineJob[]): Map<string, number> {
  const levels = new Map<string, number>();
  const visited = new Set<string>();

  /**
   *
   */
  function visit(jobId: string, currentLevel: number): void {
    if (visited.has(jobId)) return;
    visited.add(jobId);

    const existingLevel = levels.get(jobId) || 0;
    levels.set(jobId, Math.max(existingLevel, currentLevel));

    const job = jobs.find(j => j.id === jobId);
    if (job) {
      // Visit jobs that depend on this one
      jobs.forEach(j => {
        if (j.dependsOn.includes(jobId)) {
          visit(j.id, currentLevel + 1);
        }
      });
    }
  }

  // Start with jobs that have no dependencies
  jobs.filter(j => j.dependsOn.length === 0).forEach(j => visit(j.id, 0));

  return levels;
}

/**
 * Group jobs by stage
 */
export function groupJobsByStage(pipeline: Pipeline): Map<string, string[]> {
  const groups = new Map<string, string[]>();
  
  pipeline.jobs.forEach(job => {
    const stage = job.stage || 'default';
    const jobs = groups.get(stage) || [];
    jobs.push(job.id);
    groups.set(stage, jobs);
  });

  return groups;
}

/**
 * Get style for job based on status
 */
export function getJobStyle(status: JobStatus): Record<string, unknown> {
  const statusStyles: Record<JobStatus, Record<string, unknown>> = {
    'pending': { fill: '#808080', stroke: '#666' },
    'running': { fill: '#3b82f6', stroke: '#2563eb' },
    'success': { fill: '#10b981', stroke: '#059669' },
    'failure': { fill: '#ef4444', stroke: '#dc2626' },
    'skipped': { fill: '#6b7280', stroke: '#4b5563' },
    'cancelled': { fill: '#f59e0b', stroke: '#d97706' },
  };

  return statusStyles[status];
}

/**
 * Get style for gate based on type
 */
export function getGateStyle(gateType: GateType): Record<string, unknown> {
  const gateStyles: Record<GateType, Record<string, unknown>> = {
    'manual-approval': { fill: '#fbbf24', stroke: '#f59e0b' },
    'automated-check': { fill: '#60a5fa', stroke: '#3b82f6' },
    'deployment-gate': { fill: '#a78bfa', stroke: '#8b5cf6' },
  };

  return gateStyles[gateType];
}

/**
 * Add runtime metrics to pipeline
 * 
 * @param pipeline - Base pipeline
 * @param execution - Execution data with metrics
 * @returns Updated pipeline with metrics in job metadata
 */
export function addMetricsToPipeline(
  pipeline: Pipeline,
  execution: PipelineExecution
): Pipeline {
  const jobMetricsMap = new Map(
    execution.jobMetrics.map(m => [m.jobId, m])
  );

  return {
    ...pipeline,
    jobs: pipeline.jobs.map(job => ({
      ...job,
      metadata: {
        ...job.metadata,
        metrics: jobMetricsMap.get(job.id),
      },
    })),
  };
}
