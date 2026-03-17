/**
 * Service mocks for testing
 */

import { vi } from 'vitest';

import type { Project, Task, Workspace } from '@ghatana/yappc-types';

/**
 * Mock workspace service for testing
 */
export const mockWorkspaceService = {
  getWorkspaces: vi.fn(),
  getWorkspace: vi.fn(),
  createWorkspace: vi.fn(),
  updateWorkspace: vi.fn(),
  deleteWorkspace: vi.fn(),
  addMember: vi.fn(),
  removeMember: vi.fn(),
};

/**
 * Mock project service for testing
 */
export const mockProjectService = {
  getProjects: vi.fn(),
  getProject: vi.fn(),
  createProject: vi.fn(),
  updateProject: vi.fn(),
  deleteProject: vi.fn(),
  getProjectsByWorkspace: vi.fn(),
};

/**
 * Mock task service for testing
 */
export const mockTaskService = {
  getTasks: vi.fn(),
  getTask: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  deleteTask: vi.fn(),
  getTasksByProject: vi.fn(),
  assignTask: vi.fn(),
  completeTask: vi.fn(),
};

/**
 * Reset all service mock functions
 */
export function resetServiceMocks() {
  // Reset workspace service mocks
  Object.values(mockWorkspaceService).forEach((mock) => {
    if (typeof mock === 'function' && mock.mockReset) {
      mock.mockReset();
    }
  });

  // Reset project service mocks
  Object.values(mockProjectService).forEach((mock) => {
    if (typeof mock === 'function' && mock.mockReset) {
      mock.mockReset();
    }
  });

  // Reset task service mocks
  Object.values(mockTaskService).forEach((mock) => {
    if (typeof mock === 'function' && mock.mockReset) {
      mock.mockReset();
    }
  });
}

/**
 * Mock workspace service with data
 *
 * @param workspaces - Workspace data to initialize
 */
export function mockWorkspaceServiceWithData(workspaces: Workspace[]) {
  mockWorkspaceService.getWorkspaces.mockResolvedValue(workspaces);

  mockWorkspaceService.getWorkspace.mockImplementation((id: string) => {
    const workspace = workspaces.find((w) => w.id === id);
    return workspace
      ? Promise.resolve(workspace)
      : Promise.reject(new Error('Workspace not found'));
  });

  mockWorkspaceService.createWorkspace.mockImplementation(
    (data: Partial<Workspace>) => {
      const newWorkspace = {
        id: `mock-${Date.now()}`,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        ...data,
      } as Workspace;
      workspaces.push(newWorkspace);
      return Promise.resolve(newWorkspace);
    }
  );

  mockWorkspaceService.updateWorkspace.mockImplementation(
    (id: string, data: Partial<Workspace>) => {
      const index = workspaces.findIndex((w) => w.id === id);
      if (index === -1) {
        return Promise.reject(new Error('Workspace not found'));
      }

      const updatedWorkspace = {
        ...workspaces[index],
        ...data,
        updatedAt: new Date().toISOString(),
      };

      workspaces[index] = updatedWorkspace;
      return Promise.resolve(updatedWorkspace);
    }
  );

  mockWorkspaceService.deleteWorkspace.mockImplementation((id: string) => {
    const index = workspaces.findIndex((w) => w.id === id);
    if (index === -1) {
      return Promise.reject(new Error('Workspace not found'));
    }

    workspaces.splice(index, 1);
    return Promise.resolve(true);
  });
}

/**
 * Mock project service with data
 *
 * @param projects - Project data to initialize
 */
export function mockProjectServiceWithData(projects: Project[]) {
  mockProjectService.getProjects.mockResolvedValue(projects);

  mockProjectService.getProject.mockImplementation((id: string) => {
    const project = projects.find((p) => p.id === id);
    return project
      ? Promise.resolve(project)
      : Promise.reject(new Error('Project not found'));
  });

  mockProjectService.getProjectsByWorkspace.mockImplementation(
    (workspaceId: string) => {
      const workspaceProjects = projects.filter(
        (p) => p.workspaceId === workspaceId
      );
      return Promise.resolve(workspaceProjects);
    }
  );

  mockProjectService.createProject.mockImplementation(
    (data: Partial<Project>) => {
      const newProject = {
        id: `mock-${Date.now()}`,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        ...data,
      } as Project;
      projects.push(newProject);
      return Promise.resolve(newProject);
    }
  );

  mockProjectService.updateProject.mockImplementation(
    (id: string, data: Partial<Project>) => {
      const index = projects.findIndex((p) => p.id === id);
      if (index === -1) {
        return Promise.reject(new Error('Project not found'));
      }

      const updatedProject = {
        ...projects[index],
        ...data,
        updatedAt: new Date().toISOString(),
      };

      projects[index] = updatedProject;
      return Promise.resolve(updatedProject);
    }
  );

  mockProjectService.deleteProject.mockImplementation((id: string) => {
    const index = projects.findIndex((p) => p.id === id);
    if (index === -1) {
      return Promise.reject(new Error('Project not found'));
    }

    projects.splice(index, 1);
    return Promise.resolve(true);
  });
}

/**
 * Mock task service with data
 *
 * @param tasks - Task data to initialize
 */
export function mockTaskServiceWithData(tasks: Task[]) {
  mockTaskService.getTasks.mockResolvedValue(tasks);

  mockTaskService.getTask.mockImplementation((id: string) => {
    const task = tasks.find((t) => t.id === id);
    return task
      ? Promise.resolve(task)
      : Promise.reject(new Error('Task not found'));
  });

  mockTaskService.getTasksByProject.mockImplementation((projectId: string) => {
    const projectTasks = tasks.filter((t) => t.projectId === projectId);
    return Promise.resolve(projectTasks);
  });

  mockTaskService.createTask.mockImplementation((data: Partial<Task>) => {
    const newTask = {
      id: `mock-${Date.now()}`,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      ...data,
    } as Task;
    tasks.push(newTask);
    return Promise.resolve(newTask);
  });

  mockTaskService.updateTask.mockImplementation(
    (id: string, data: Partial<Task>) => {
      const index = tasks.findIndex((t) => t.id === id);
      if (index === -1) {
        return Promise.reject(new Error('Task not found'));
      }

      const updatedTask = {
        ...tasks[index],
        ...data,
        updatedAt: new Date().toISOString(),
      } as unknown as Task;

      // ensure status is narrowed to TaskStatus if present
      if ((updatedTask as unknown).status) {
        (updatedTask as unknown).status = (updatedTask as unknown).status as unknown;
      }

      tasks[index] = updatedTask;
      return Promise.resolve(updatedTask);
    }
  );

  mockTaskService.deleteTask.mockImplementation((id: string) => {
    const index = tasks.findIndex((t) => t.id === id);
    if (index === -1) {
      return Promise.reject(new Error('Task not found'));
    }

    tasks.splice(index, 1);
    return Promise.resolve(true);
  });

  mockTaskService.assignTask.mockImplementation(
    (id: string, assigneeId: string) => {
      const index = tasks.findIndex((t) => t.id === id);
      if (index === -1) {
        return Promise.reject(new Error('Task not found'));
      }

      const updatedTask = {
        ...tasks[index],
        assigneeId,
        updatedAt: new Date().toISOString(),
      };

      tasks[index] = updatedTask;
      return Promise.resolve(updatedTask);
    }
  );

  mockTaskService.completeTask.mockImplementation((id: string) => {
    const index = tasks.findIndex((t) => t.id === id);
    if (index === -1) {
      return Promise.reject(new Error('Task not found'));
    }

    const updatedTask = {
      ...tasks[index],
      status: 'done',
      updatedAt: new Date().toISOString(),
    } as unknown;

    // ensure status value conforms to TaskStatus union
    const normalizedStatus: string =
      updatedTask.status === 'done' ? 'done' : updatedTask.status;
    updatedTask.status = normalizedStatus;

    tasks[index] = updatedTask as Task;
    return Promise.resolve(updatedTask as Task);
  });
}
