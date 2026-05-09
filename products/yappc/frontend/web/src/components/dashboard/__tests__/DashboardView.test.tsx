import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { DashboardView } from '../DashboardView';
import type { PriorityTask } from '../PriorityTasksList';
import type { Project, Workflow, Workspace } from '../types';

// ProjectCard uses design-system components — mock at the right level
vi.mock('../../../components/project/ProjectCard', () => ({
  ProjectCard: ({ project, onClick }: { project: Project; onClick?: () => void }) => (
    <div data-testid={`project-card-${project.id}`} onClick={onClick}>
      {project.name}
    </div>
  ),
}));

vi.mock('../PriorityTasksList', () => ({
  PriorityTasksList: ({ tasks, onTaskClick, onViewAll }: {
    tasks: PriorityTask[];
    onTaskClick: (t: PriorityTask) => void;
    onViewAll: () => void;
  }) => (
    <div data-testid="priority-tasks-list">
      {tasks.map(t => (
        <button key={t.id} onClick={() => onTaskClick(t)}>{t.title}</button>
      ))}
      <button onClick={onViewAll}>View all tasks</button>
    </div>
  ),
}));

const makeTask = (id: string): PriorityTask => ({
  id,
  title: `Task ${id}`,
  priority: 'high',
  category: 'requirement',
  dueDate: '2026-05-01',
  status: 'pending',
});

const makeProject = (id: string): Project => ({
  id,
  name: `Project ${id}`,
  type: 'webapp',
  updatedAt: '2026-04-01',
});

const makeWorkflow = (id: string): Workflow => ({
  id,
  name: `Workflow ${id}`,
  status: 'active',
  taskCount: 5,
});

const makeWorkspace = (id: string): Workspace => ({
  id,
  name: `Workspace ${id}`,
});

const defaultProps = {
  userName: 'Alice',
  priorityTasks: [makeTask('t1'), makeTask('t2')],
  recentProjects: [makeProject('p1'), makeProject('p2')],
  recentWorkflows: [makeWorkflow('w1')],
  workspaces: [makeWorkspace('ws1')],
  onTaskClick: vi.fn(),
  onViewAllTasks: vi.fn(),
  onSearchClick: vi.fn(),
  onProjectClick: vi.fn(),
  onCreateProject: vi.fn(),
  onViewAllProjects: vi.fn(),
  onWorkflowClick: vi.fn(),
  onCreateWorkflow: vi.fn(),
  onViewAllWorkflows: vi.fn(),
  onWorkspaceClick: vi.fn(),
  onCreateWorkspace: vi.fn(),
  onViewAllWorkspaces: vi.fn(),
};

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the welcome message with userName', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText(/Welcome back, Alice/i)).toBeInTheDocument();
  });

  it('renders default welcome without userName', () => {
    render(<DashboardView {...defaultProps} userName={undefined} />);
    expect(screen.getByText(/Welcome back/i)).toBeInTheDocument();
  });

  it('renders the search placeholder text', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByPlaceholderText(/Search tasks, projects, code, or get guidance/i)).toBeInTheDocument();
  });

  it('calls onSearchClick when Enter pressed in search input', () => {
    render(<DashboardView {...defaultProps} />);
    const searchInput = screen.getByPlaceholderText(/Search tasks/i);
    fireEvent.keyDown(searchInput, { key: 'Enter' });
    expect(defaultProps.onSearchClick).toHaveBeenCalledTimes(1);
  });

  it('renders the PriorityTasksList with tasks', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByTestId('priority-tasks-list')).toBeInTheDocument();
    expect(screen.getByText('Task t1')).toBeInTheDocument();
    expect(screen.getByText('Task t2')).toBeInTheDocument();
  });

  it('calls onTaskClick when a task is clicked', () => {
    render(<DashboardView {...defaultProps} />);
    fireEvent.click(screen.getByText('Task t1'));
    expect(defaultProps.onTaskClick).toHaveBeenCalledWith(makeTask('t1'));
  });

  it('calls onViewAllTasks when view all tasks button clicked', () => {
    render(<DashboardView {...defaultProps} />);
    fireEvent.click(screen.getByText('View all tasks'));
    expect(defaultProps.onViewAllTasks).toHaveBeenCalledTimes(1);
  });

  it('renders Development Hub heading', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText('Development Hub')).toBeInTheDocument();
  });

  it('renders recent projects', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByTestId('project-card-p1')).toBeInTheDocument();
    expect(screen.getByTestId('project-card-p2')).toBeInTheDocument();
  });

  it('calls onCreateProject when New project button clicked', () => {
    render(<DashboardView {...defaultProps} />);
    const newButtons = screen.getAllByText('New');
    fireEvent.click(newButtons[0]);
    expect(defaultProps.onCreateProject).toHaveBeenCalledTimes(1);
  });

  it('renders persona chip', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText('Product Manager')).toBeInTheDocument();
  });

  it('renders Collaborative IDE section', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText('Collaborative IDE')).toBeInTheDocument();
    expect(screen.getByText('Launch IDE')).toBeInTheDocument();
  });

  it('renders workflow section', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText('Workflow w1')).toBeInTheDocument();
  });

  it('renders workspace section', () => {
    render(<DashboardView {...defaultProps} />);
    expect(screen.getByText('Workspace ws1')).toBeInTheDocument();
  });
});
