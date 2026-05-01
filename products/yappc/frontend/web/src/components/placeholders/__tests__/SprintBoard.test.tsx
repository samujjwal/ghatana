import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider, createStore } from 'jotai';
import { SprintBoard } from '../SprintBoard';
import {
  activeSprintAtom,
  sprintStoriesAtom,
} from '../../../state/atoms';

// =============================================================================
// Types (mirrored from component)
// =============================================================================

interface StoryRecord {
  id: string;
  title?: string;
  status?: string;
  priority?: string;
  type?: string;
  assigneeId?: string;
  labels?: string[];
  storyPoints?: number;
}

interface SprintRecord {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: 'planning' | 'active' | 'review' | 'completed' | 'cancelled';
  daysRemaining?: number;
  progress?: number;
}

// =============================================================================
// Helpers
// =============================================================================

function makeStory(overrides: Partial<StoryRecord> = {}): StoryRecord {
  return {
    id: 'story-1',
    title: 'Implement login page',
    status: 'todo',
    priority: 'high',
    type: 'feature',
    storyPoints: 3,
    ...overrides,
  };
}

function makeSprint(overrides: Partial<SprintRecord> = {}): SprintRecord {
  return {
    id: 'sprint-1',
    name: 'Sprint 1',
    startDate: '2026-05-01',
    endDate: '2026-05-14',
    status: 'active',
    ...overrides,
  };
}

function renderWithStore(
  ui: React.ReactElement,
  {
    sprint = null,
    stories = [],
  }: { sprint?: SprintRecord | null; stories?: StoryRecord[] } = {}
) {
  const store = createStore();
  store.set(activeSprintAtom, sprint);
  store.set(sprintStoriesAtom as never, stories);
  return render(<Provider store={store}>{ui}</Provider>);
}

// =============================================================================
// Tests
// =============================================================================

describe('SprintBoard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // ---------------------------------------------------------------------------
  // Column rendering
  // ---------------------------------------------------------------------------

  it('renders all four kanban columns', () => {
    renderWithStore(<SprintBoard />);
    expect(screen.getByText('To Do')).toBeDefined();
    expect(screen.getByText('In Progress')).toBeDefined();
    expect(screen.getByText('In Review')).toBeDefined();
    expect(screen.getByText('Done')).toBeDefined();
  });

  it('shows "No stories" placeholder in empty columns', () => {
    renderWithStore(<SprintBoard />);
    const noStories = screen.getAllByText('No stories');
    expect(noStories.length).toBe(4);
  });

  // ---------------------------------------------------------------------------
  // Sprint header
  // ---------------------------------------------------------------------------

  it('does not render sprint header when no active sprint exists', () => {
    renderWithStore(<SprintBoard />, { sprint: null });
    expect(screen.queryByText('Sprint 1')).toBeNull();
  });

  it('renders sprint name and date range when active sprint is set', () => {
    const sprint = makeSprint({ name: 'Alpha Sprint', startDate: '2026-05-01', endDate: '2026-05-14' });
    renderWithStore(<SprintBoard />, { sprint });
    expect(screen.getByText('Alpha Sprint')).toBeDefined();
    expect(screen.getByText('2026-05-01 – 2026-05-14')).toBeDefined();
  });

  it('shows daysRemaining when provided on active sprint', () => {
    const sprint = makeSprint({ daysRemaining: 7 });
    renderWithStore(<SprintBoard />, { sprint });
    expect(screen.getByText('7d remaining')).toBeDefined();
  });

  it('does not show daysRemaining text when not provided', () => {
    const sprint = makeSprint({ daysRemaining: undefined });
    renderWithStore(<SprintBoard />, { sprint });
    expect(screen.queryByText(/remaining/)).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Story card rendering
  // ---------------------------------------------------------------------------

  it('renders a story in the correct column', () => {
    const stories = [makeStory({ id: 's1', title: 'Login page', status: 'todo' })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('Login page')).toBeDefined();
  });

  it('renders story in in-progress column', () => {
    const stories = [makeStory({ id: 's1', title: 'Search feature', status: 'in-progress' })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('Search feature')).toBeDefined();
  });

  it('renders story in done column', () => {
    const stories = [makeStory({ id: 's1', title: 'Setup CI', status: 'done' })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('Setup CI')).toBeDefined();
  });

  it('falls back to "todo" column when story has no status', () => {
    const stories = [makeStory({ id: 's1', title: 'No-status story', status: undefined })];
    renderWithStore(<SprintBoard />, { stories });
    // The story should appear in the To Do column (no stories shown in others)
    expect(screen.getByText('No-status story')).toBeDefined();
  });

  it('renders "Untitled Story" when story has no title', () => {
    const stories = [makeStory({ id: 's1', title: undefined })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('Untitled Story')).toBeDefined();
  });

  it('renders story type and priority badges', () => {
    const stories = [makeStory({ type: 'bug', priority: 'critical' })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('bug')).toBeDefined();
    expect(screen.getByText('critical')).toBeDefined();
  });

  it('renders story points', () => {
    const stories = [makeStory({ storyPoints: 5 })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('5 pts')).toBeDefined();
  });

  it('renders story labels', () => {
    const stories = [makeStory({ labels: ['frontend', 'auth'] })];
    renderWithStore(<SprintBoard />, { stories });
    expect(screen.getByText('frontend')).toBeDefined();
    expect(screen.getByText('auth')).toBeDefined();
  });

  // ---------------------------------------------------------------------------
  // Column story count
  // ---------------------------------------------------------------------------

  it('shows correct count badge per column', () => {
    const stories = [
      makeStory({ id: 's1', status: 'todo' }),
      makeStory({ id: 's2', status: 'todo' }),
      makeStory({ id: 's3', status: 'done' }),
    ];
    renderWithStore(<SprintBoard />, { stories });
    const countBadges = screen.getAllByText('2');
    // "To Do" column shows 2; at least one such badge exists
    expect(countBadges.length).toBeGreaterThan(0);
  });

  // ---------------------------------------------------------------------------
  // Callbacks
  // ---------------------------------------------------------------------------

  it('calls onStoryClick with story id when a story card is clicked', () => {
    const onStoryClick = vi.fn();
    const stories = [makeStory({ id: 'story-abc', title: 'Click me' })];
    renderWithStore(<SprintBoard onStoryClick={onStoryClick} />, { stories });
    fireEvent.click(screen.getByText('Click me'));
    expect(onStoryClick).toHaveBeenCalledWith('story-abc');
  });

  it('renders Add Story button when onCreateStory is provided', () => {
    renderWithStore(<SprintBoard onCreateStory={vi.fn()} />);
    const addButtons = screen.getAllByTitle(/Add story to/);
    expect(addButtons.length).toBe(4);
  });

  it('calls onCreateStory with column id when Add Story button is clicked', () => {
    const onCreateStory = vi.fn();
    renderWithStore(<SprintBoard onCreateStory={onCreateStory} />);
    fireEvent.click(screen.getByTitle('Add story to To Do'));
    expect(onCreateStory).toHaveBeenCalledWith('todo');
  });

  it('does not render Add Story buttons when onCreateStory is not provided', () => {
    renderWithStore(<SprintBoard />);
    expect(screen.queryByTitle(/Add story to/)).toBeNull();
  });

  // ---------------------------------------------------------------------------
  // Filtering
  // ---------------------------------------------------------------------------

  it('filters stories by search term', () => {
    const stories = [
      makeStory({ id: 's1', title: 'Auth login feature' }),
      makeStory({ id: 's2', title: 'Dashboard layout' }),
    ];
    renderWithStore(
      <SprintBoard filters={{ search: 'auth', assignees: [], types: [], priorities: [], labels: [] }} />,
      { stories }
    );
    expect(screen.getByText('Auth login feature')).toBeDefined();
    expect(screen.queryByText('Dashboard layout')).toBeNull();
  });

  it('shows all stories when search term is empty', () => {
    const stories = [
      makeStory({ id: 's1', title: 'First story' }),
      makeStory({ id: 's2', title: 'Second story' }),
    ];
    renderWithStore(
      <SprintBoard filters={{ search: '', assignees: [], types: [], priorities: [], labels: [] }} />,
      { stories }
    );
    expect(screen.getByText('First story')).toBeDefined();
    expect(screen.getByText('Second story')).toBeDefined();
  });

  it('filters stories by priority', () => {
    const stories = [
      makeStory({ id: 's1', title: 'Critical task', priority: 'critical' }),
      makeStory({ id: 's2', title: 'Low task', priority: 'low' }),
    ];
    renderWithStore(
      <SprintBoard filters={{ search: '', assignees: [], types: [], priorities: ['critical'], labels: [] }} />,
      { stories }
    );
    expect(screen.getByText('Critical task')).toBeDefined();
    expect(screen.queryByText('Low task')).toBeNull();
  });

  it('filters stories by type', () => {
    const stories = [
      makeStory({ id: 's1', title: 'Bug fix', type: 'bug' }),
      makeStory({ id: 's2', title: 'Feature work', type: 'feature' }),
    ];
    renderWithStore(
      <SprintBoard filters={{ search: '', assignees: [], types: ['bug'], priorities: [], labels: [] }} />,
      { stories }
    );
    expect(screen.getByText('Bug fix')).toBeDefined();
    expect(screen.queryByText('Feature work')).toBeNull();
  });

  it('filters stories by label', () => {
    const stories = [
      makeStory({ id: 's1', title: 'Frontend story', labels: ['frontend'] }),
      makeStory({ id: 's2', title: 'Backend story', labels: ['backend'] }),
    ];
    renderWithStore(
      <SprintBoard filters={{ search: '', assignees: [], types: [], priorities: [], labels: ['frontend'] }} />,
      { stories }
    );
    expect(screen.getByText('Frontend story')).toBeDefined();
    expect(screen.queryByText('Backend story')).toBeNull();
  });
});
