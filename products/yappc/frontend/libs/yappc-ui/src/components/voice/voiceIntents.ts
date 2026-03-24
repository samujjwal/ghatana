/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC UI - Voice Intents to API Actions Mapping
 * 
 * Maps recognized voice intents to concrete API actions,
 * enabling voice control of YAPPC functionality.
 */

import { VoiceCommand, VoiceIntent } from './useVoiceCommands';

export interface VoiceActionResult {
  success: boolean;
  message: string;
  action: string;
  data?: unknown;
}

export type VoiceActionHandler = (
  command: VoiceCommand,
  context: VoiceActionContext
) => Promise<VoiceActionResult> | VoiceActionResult;

export interface VoiceActionContext {
  /** Current project ID */
  projectId?: string;
  /** Current stage */
  currentStage?: string;
  /** Navigate to a route */
  navigate: (path: string) => void;
  /** Call API endpoint */
  apiCall: (endpoint: string, method: string, body?: unknown) => Promise<unknown>;
  /** Show notification */
  notify: (message: string, type: 'success' | 'error' | 'info') => void;
  /** Refresh data */
  refresh: () => void;
}

/**
 * Default voice action handlers
 * 
 * Maps voice intents to YAPPC API calls. These handlers use the
 * same API endpoints as the UI, ensuring voice/UI parity.
 */
export const defaultVoiceActions: Record<VoiceIntent, VoiceActionHandler> = {
  create_project: async (command, context) => {
    const projectName = command.entities.projectName;
    if (!projectName) {
      return { success: false, action: 'create_project', message: 'Please specify a project name' };
    }

    try {
      const result = await context.apiCall('/projects', 'POST', {
        name: projectName,
        description: `Created via voice command`,
      }) as { id: string };

      context.notify(`Created project: ${projectName}`, 'success');
      context.navigate(`/projects/${result.id}`);

      return {
        success: true,
        action: 'create_project',
        message: `Created project: ${projectName}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'create_project',
        message: `Failed to create project: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  open_project: async (command, context) => {
    const projectName = command.entities.projectName;
    if (!projectName) {
      return { success: false, action: 'open_project', message: 'Please specify a project name' };
    }

    // Search for project by name
    try {
      const projects = await context.apiCall(`/projects?search=${encodeURIComponent(projectName)}`, 'GET') as Array<{ id: string; name: string }>;
      
      if (projects.length === 0) {
        return { success: false, action: 'open_project', message: `No project found matching: ${projectName}` };
      }

      const project = projects[0];
      context.navigate(`/projects/${project.id}`);

      return {
        success: true,
        action: 'open_project',
        message: `Opened project: ${project.name}`,
        data: project,
      };
    } catch (error) {
      return {
        success: false,
        action: 'open_project',
        message: `Failed to open project: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  advance_stage: async (_command, context) => {
    if (!context.projectId) {
      return { success: false, action: 'advance_stage', message: 'No project is currently open' };
    }

    try {
      const result = await context.apiCall(`/projects/${context.projectId}/advance`, 'POST') as { currentStage: string };
      context.refresh();
      context.notify(`Advanced to stage: ${result.currentStage}`, 'success');

      return {
        success: true,
        action: 'advance_stage',
        message: `Advanced to stage: ${result.currentStage}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'advance_stage',
        message: `Cannot advance stage: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  go_back_stage: async (_command, context) => {
    if (!context.projectId) {
      return { success: false, action: 'go_back_stage', message: 'No project is currently open' };
    }

    try {
      const result = await context.apiCall(`/projects/${context.projectId}/revert`, 'POST') as { currentStage: string };
      context.refresh();
      context.notify(`Reverted to stage: ${result.currentStage}`, 'success');

      return {
        success: true,
        action: 'go_back_stage',
        message: `Reverted to stage: ${result.currentStage}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'go_back_stage',
        message: `Cannot revert stage: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  create_task: async (command, context) => {
    if (!context.projectId) {
      return { success: false, action: 'create_task', message: 'No project is currently open' };
    }

    const taskTitle = command.entities.taskTitle;
    if (!taskTitle) {
      return { success: false, action: 'create_task', message: 'Please specify a task title' };
    }

    try {
      const result = await context.apiCall(`/tasks`, 'POST', {
        projectId: context.projectId,
        title: taskTitle,
        description: 'Created via voice command',
        stage: context.currentStage || 'execute',
      }) as { id: string; title: string };

      context.refresh();
      context.notify(`Created task: ${result.title}`, 'success');

      return {
        success: true,
        action: 'create_task',
        message: `Created task: ${result.title}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'create_task',
        message: `Failed to create task: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  assign_task: async (command, context) => {
    const taskName = command.entities.taskName;
    const assignee = command.entities.assignee;

    if (!taskName || !assignee) {
      return { success: false, action: 'assign_task', message: 'Please specify both task and assignee' };
    }

    // First find the task
    try {
      const tasks = await context.apiCall(`/tasks?search=${encodeURIComponent(taskName)}`, 'GET') as Array<{ id: string; title: string }>;
      
      if (tasks.length === 0) {
        return { success: false, action: 'assign_task', message: `No task found matching: ${taskName}` };
      }

      const task = tasks[0];
      const result = await context.apiCall(`/tasks/${task.id}/assign`, 'POST', {
        agentId: `agent.yappc.${assignee.replace(/\s+/g, '-')}`,
      }) as { title: string; assignedAgentId: string };

      context.refresh();
      context.notify(`Assigned ${result.title} to ${assignee}`, 'success');

      return {
        success: true,
        action: 'assign_task',
        message: `Assigned ${result.title} to ${assignee}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'assign_task',
        message: `Failed to assign task: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  complete_task: async (command, context) => {
    const taskName = command.entities.taskName;
    if (!taskName) {
      return { success: false, action: 'complete_task', message: 'Please specify a task name' };
    }

    try {
      const tasks = await context.apiCall(`/tasks?search=${encodeURIComponent(taskName)}`, 'GET') as Array<{ id: string; title: string }>;
      
      if (tasks.length === 0) {
        return { success: false, action: 'complete_task', message: `No task found matching: ${taskName}` };
      }

      const task = tasks[0];
      const result = await context.apiCall(`/tasks/${task.id}/status`, 'PATCH', {
        status: 'COMPLETED',
      }) as { title: string; status: string };

      context.refresh();
      context.notify(`Completed task: ${result.title}`, 'success');

      return {
        success: true,
        action: 'complete_task',
        message: `Completed task: ${result.title}`,
        data: result,
      };
    } catch (error) {
      return {
        success: false,
        action: 'complete_task',
        message: `Failed to complete task: ${error instanceof Error ? error.message : 'Unknown error'}`,
      };
    }
  },

  show_tasks: (_command, context) => {
    if (!context.projectId) {
      return { success: false, action: 'show_tasks', message: 'No project is currently open' };
    }

    context.navigate(`/projects/${context.projectId}/tasks`);

    return {
      success: true,
      action: 'show_tasks',
      message: 'Showing tasks',
    };
  },

  show_metrics: (_command, context) => {
    if (!context.projectId) {
      return { success: false, action: 'show_metrics', message: 'No project is currently open' };
    }

    context.navigate(`/projects/${context.projectId}/metrics`);

    return {
      success: true,
      action: 'show_metrics',
      message: 'Showing metrics',
    };
  },

  help: (_command, _context) => {
    return {
      success: true,
      action: 'help',
      message: 'Voice commands help displayed',
    };
  },

  cancel: (_command, _context) => {
    return {
      success: true,
      action: 'cancel',
      message: 'Cancelled',
    };
  },

  confirm: (_command, _context) => {
    return {
      success: true,
      action: 'confirm',
      message: 'Confirmed',
    };
  },

  unknown: (_command, _context) => {
    return {
      success: false,
      action: 'unknown',
      message: "I didn't understand that command. Say 'help' for available commands.",
    };
  },
};

/**
 * Execute a voice command with the provided context
 */
export async function executeVoiceCommand(
  command: VoiceCommand,
  context: VoiceActionContext,
  customActions?: Partial<Record<VoiceIntent, VoiceActionHandler>>
): Promise<VoiceActionResult> {
  const handler = customActions?.[command.intent] || defaultVoiceActions[command.intent];
  
  if (!handler) {
    return {
      success: false,
      action: command.intent,
      message: `No handler for intent: ${command.intent}`,
    };
  }

  try {
    const result = await handler(command, context);
    return result;
  } catch (error) {
    return {
      success: false,
      action: command.intent,
      message: `Error executing command: ${error instanceof Error ? error.message : 'Unknown error'}`,
    };
  }
}
