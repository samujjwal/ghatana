/**
 * Ansible Playbook Parser
 * 
 * Parses Ansible playbook YAML into Runbook format for execution and visualization.
 */

import * as yaml from 'js-yaml';

import type { Runbook, RunbookStep } from '../types';

/**
 * Parse YAML content safely
 */
function parseYAML(content: string): unknown {
  try {
    return yaml.load(content);
  } catch (error) {
    console.error('Failed to parse YAML:', error);
    throw error;
  }
}

/**
 * Parse Ansible playbook YAML into a Runbook
 * 
 * @param yamlContent - Ansible playbook YAML string or parsed object
 * @returns Runbook representation of the playbook
 * 
 * @example
 * ```typescript
 * const runbook = parseAnsiblePlaybook(playbookYaml);
 * console.log(`Parsed ${runbook.steps.length} steps`);
 * ```
 */
export function parseAnsiblePlaybook(yamlContent: string | Record<string, unknown>): Runbook {
  const playbook = typeof yamlContent === 'string' ? parseYAML(yamlContent) : yamlContent;
  
  if (!Array.isArray(playbook)) {
    throw new Error('Invalid Ansible playbook: expected array of plays');
  }

  const steps: RunbookStep[] = [];
  let stepIndex = 0;

  playbook.forEach((play, playIndex) => {
    const playName = play.name || `Play ${playIndex + 1}`;
    const hosts = play.hosts || 'all';
    
    // Add play start checkpoint
    steps.push({
      id: `play-${playIndex}-start`,
      name: `${playName} (${hosts})`,
      type: 'checkpoint',
      dependsOn: playIndex > 0 ? [`play-${playIndex - 1}-end`] : [],
      metadata: {
        status: 'pending',
      },
    });

    // Parse tasks
    const tasks = play.tasks || [];
    tasks.forEach((task: unknown, taskIndex: number) => {
      const taskName = task.name || `Task ${taskIndex + 1}`;
      const module = Object.keys(task).find(
        k => k !== 'name' && k !== 'when' && k !== 'register' && k !== 'with_items'
      );
      
      const stepId = `step-${stepIndex}`;
      steps.push({
        id: stepId,
        name: taskName,
        type: 'task',
        module,
        args: module ? task[module] : undefined,
        command: module ? `ansible ${module}` : undefined,
        dependsOn: stepIndex > 0 ? [`step-${stepIndex - 1}`] : [`play-${playIndex}-start`],
        condition: task.when,
        timeout: task.async || 300,
        metadata: {
          status: 'pending',
        },
      });
      stepIndex++;
    });

    // Add play end checkpoint
    steps.push({
      id: `play-${playIndex}-end`,
      name: `End ${playName}`,
      type: 'checkpoint',
      dependsOn: tasks.length > 0 ? [`step-${stepIndex - 1}`] : [`play-${playIndex}-start`],
      metadata: {
        status: 'pending',
      },
    });
  });

  return {
    id: `ansible-${Date.now()}`,
    name: playbook[0]?.name || 'Ansible Playbook',
    type: 'ansible',
    description: `Ansible playbook with ${playbook.length} play(s)`,
    version: '1.0.0',
    steps,
    approvalGates: [],
    variables: {},
    metadata: {
      status: 'pending',
      totalSteps: steps.length,
      completedSteps: 0,
      failedSteps: 0,
      skippedSteps: 0,
      rollbackSteps: 0,
    },
  };
}
