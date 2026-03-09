/**
 * SprintPlanningPage
 *
 * @description Sprint planning session page with backlog refinement,
 * capacity planning, and story point estimation.
 *
 * @doc.phase 3
 * @doc.route /projects/:projectId/sprints/:sprintId/planning
 */

import React, { useCallback, useMemo, useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { StoryCard } from '@ghatana/yappc-ui';
import { Spinner as LoadingSpinner } from '@ghatana/ui';
import { ErrorBoundary } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

type StoryStatus = 'backlog' | 'ready' | 'in_progress' | 'review' | 'done';
type StoryPriority = 'critical' | 'high' | 'medium' | 'low';
type StoryType = 'feature' | 'bug' | 'tech_debt' | 'spike';

interface Story {
  id: string;
  title: string;
  description: string;
  type: StoryType;
  status: StoryStatus;
  priority: StoryPriority;
  points?: number;
  assignee?: {
    id: string;
    name: string;
    avatar?: string;
  };
  labels: string[];
  epicId?: string;
  epicName?: string;
  acceptanceCriteria: string[];
  createdAt: string;
}

interface TeamMember {
  id: string;
  name: string;
  avatar?: string;
  role: string;
  capacity: number; // points per sprint
  allocatedPoints: number;
}

interface Sprint {
  id: string;
  name: string;
  number: number;
  goal: string;
  startDate: string;
  endDate: string;
  capacity: number;
  committedPoints: number;
}

interface PlanningSession {
  sprint: Sprint;
  team: TeamMember[];
  backlogStories: Story[];
  plannedStories: Story[];
  votingInProgress?: {
    storyId: string;
    votes: Record<string, number>;
  };
}

// ============================================================================
// API Functions
// ============================================================================

const fetchPlanningSession = async (
  projectId: string,
  sprintId: string
): Promise<PlanningSession> => {
  const response = await fetch(
    `/api/projects/${projectId}/sprints/${sprintId}/planning`
  );
  if (!response.ok) throw new Error('Failed to fetch planning session');
  return response.json();
};

const addStoryToSprint = async (
  projectId: string,
  sprintId: string,
  storyId: string
): Promise<void> => {
  const response = await fetch(
    `/api/projects/${projectId}/sprints/${sprintId}/stories/${storyId}`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error('Failed to add story to sprint');
};

const removeStoryFromSprint = async (
  projectId: string,
  sprintId: string,
  storyId: string
): Promise<void> => {
  const response = await fetch(
    `/api/projects/${projectId}/sprints/${sprintId}/stories/${storyId}`,
    { method: 'DELETE' }
  );
  if (!response.ok) throw new Error('Failed to remove story from sprint');
};

const updateStoryPoints = async (
  projectId: string,
  storyId: string,
  points: number
): Promise<void> => {
  const response = await fetch(`/api/projects/${projectId}/stories/${storyId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ points }),
  });
  if (!response.ok) throw new Error('Failed to update story points');
};

const startSprintPlanning = async (
  projectId: string,
  sprintId: string
): Promise<void> => {
  const response = await fetch(
    `/api/projects/${projectId}/sprints/${sprintId}/start-planning`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error('Failed to start sprint');
};

const submitVote = async (
  projectId: string,
  storyId: string,
  vote: number
): Promise<void> => {
  const response = await fetch(
    `/api/projects/${projectId}/stories/${storyId}/vote`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ vote }),
    }
  );
  if (!response.ok) throw new Error('Failed to submit vote');
};

// ============================================================================
// Utility Functions
// ============================================================================

const FIBONACCI_SEQUENCE = [0, 1, 2, 3, 5, 8, 13, 21];

const getCapacityStatus = (
  committed: number,
  capacity: number
): { status: 'under' | 'optimal' | 'over'; percentage: number } => {
  const percentage = capacity > 0 ? Math.round((committed / capacity) * 100) : 0;
  if (percentage < 70) return { status: 'under', percentage };
  if (percentage <= 100) return { status: 'optimal', percentage };
  return { status: 'over', percentage };
};

// ============================================================================
// Sub-Components
// ============================================================================

interface CapacityBarProps {
  committed: number;
  capacity: number;
}

const CapacityBar: React.FC<CapacityBarProps> = ({ committed, capacity }) => {
  const { status, percentage } = getCapacityStatus(committed, capacity);
  const colors: Record<string, string> = {
    under: '#F59E0B',
    optimal: '#10B981',
    over: '#EF4444',
  };

  return (
    <div className="capacity-bar">
      <div className="capacity-header">
        <span className="capacity-label">Sprint Capacity</span>
        <span className="capacity-value">
          {committed} / {capacity} pts ({percentage}%)
        </span>
      </div>
      <div className="capacity-track">
        <div
          className="capacity-fill"
          style={{
            width: `${Math.min(percentage, 100)}%`,
            background: colors[status],
          }}
        />
        {percentage > 100 && (
          <div
            className="capacity-overflow"
            style={{ width: `${percentage - 100}%` }}
          />
        )}
      </div>
    </div>
  );
};

interface PointingCardProps {
  selected: number | null;
  onSelect: (points: number) => void;
  disabled?: boolean;
}

const PointingCards: React.FC<PointingCardProps> = ({ selected, onSelect, disabled }) => (
  <div className="pointing-cards">
    {FIBONACCI_SEQUENCE.map((points) => (
      <button
        key={points}
        type="button"
        className={`pointing-card ${selected === points ? 'pointing-card--selected' : ''}`}
        onClick={() => onSelect(points)}
        disabled={disabled}
      >
        {points}
      </button>
    ))}
  </div>
);

interface TeamCapacityPanelProps {
  team: TeamMember[];
}

const TeamCapacityPanel: React.FC<TeamCapacityPanelProps> = ({ team }) => (
  <div className="team-capacity-panel">
    <h3 className="panel-title">👥 Team Capacity</h3>
    <ul className="team-list">
      {team.map((member) => {
        const usage = member.capacity > 0
          ? Math.round((member.allocatedPoints / member.capacity) * 100)
          : 0;
        return (
          <li key={member.id} className="team-member">
            <div className="member-info">
              <div className="member-avatar">
                {member.avatar ? (
                  <img src={member.avatar} alt={member.name} />
                ) : (
                  <span>{member.name.charAt(0)}</span>
                )}
              </div>
              <div className="member-details">
                <span className="member-name">{member.name}</span>
                <span className="member-role">{member.role}</span>
              </div>
            </div>
            <div className="member-capacity">
              <span className="capacity-text">
                {member.allocatedPoints}/{member.capacity} pts
              </span>
              <div className="capacity-mini-bar">
                <div
                  className="capacity-mini-fill"
                  style={{
                    width: `${Math.min(usage, 100)}%`,
                    background: usage > 100 ? '#EF4444' : '#10B981',
                  }}
                />
              </div>
            </div>
          </li>
        );
      })}
    </ul>
  </div>
);

// ============================================================================
// Main Component
// ============================================================================

export const SprintPlanningPage: React.FC = () => {
  const { projectId, sprintId } = useParams<{
    projectId: string;
    sprintId: string;
  }>();
  const navigate = useNavigate();

  // State
  const [session, setSession] = useState<PlanningSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedStory, setSelectedStory] = useState<Story | null>(null);
  const [pointingVote, setPointingVote] = useState<number | null>(null);
  const [dragOverPlanned, setDragOverPlanned] = useState(false);

  // Load data
  useEffect(() => {
    const loadSession = async () => {
      if (!projectId || !sprintId) return;

      setLoading(true);
      setError(null);

      try {
        const data = await fetchPlanningSession(projectId, sprintId);
        setSession(data);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load planning session');
      } finally {
        setLoading(false);
      }
    };

    loadSession();
  }, [projectId, sprintId]);

  // Calculated values
  const totalCommitted = useMemo(() => {
    if (!session) return 0;
    return session.plannedStories.reduce((sum, s) => sum + (s.points || 0), 0);
  }, [session]);

  // Handlers
  const handleAddToSprint = useCallback(
    async (story: Story) => {
      if (!projectId || !sprintId || !session) return;

      try {
        await addStoryToSprint(projectId, sprintId, story.id);
        setSession((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            backlogStories: prev.backlogStories.filter((s) => s.id !== story.id),
            plannedStories: [...prev.plannedStories, story],
            sprint: {
              ...prev.sprint,
              committedPoints: prev.sprint.committedPoints + (story.points || 0),
            },
          };
        });
      } catch (err) {
        console.error('Failed to add story:', err);
      }
    },
    [projectId, sprintId, session]
  );

  const handleRemoveFromSprint = useCallback(
    async (story: Story) => {
      if (!projectId || !sprintId || !session) return;

      try {
        await removeStoryFromSprint(projectId, sprintId, story.id);
        setSession((prev) => {
          if (!prev) return prev;
          return {
            ...prev,
            plannedStories: prev.plannedStories.filter((s) => s.id !== story.id),
            backlogStories: [...prev.backlogStories, story],
            sprint: {
              ...prev.sprint,
              committedPoints: prev.sprint.committedPoints - (story.points || 0),
            },
          };
        });
      } catch (err) {
        console.error('Failed to remove story:', err);
      }
    },
    [projectId, sprintId, session]
  );

  const handlePointsUpdate = useCallback(
    async (story: Story, points: number) => {
      if (!projectId) return;

      try {
        await updateStoryPoints(projectId, story.id, points);
        setSession((prev) => {
          if (!prev) return prev;
          const updateStory = (s: Story) =>
            s.id === story.id ? { ...s, points } : s;
          return {
            ...prev,
            backlogStories: prev.backlogStories.map(updateStory),
            plannedStories: prev.plannedStories.map(updateStory),
          };
        });
        setSelectedStory(null);
        setPointingVote(null);
      } catch (err) {
        console.error('Failed to update points:', err);
      }
    },
    [projectId]
  );

  const handleStartSprint = useCallback(async () => {
    if (!projectId || !sprintId) return;

    if (!confirm('Are you sure you want to start this sprint?')) return;

    try {
      await startSprintPlanning(projectId, sprintId);
      navigate(`/projects/${projectId}/sprints/${sprintId}`);
    } catch (err) {
      console.error('Failed to start sprint:', err);
    }
  }, [projectId, sprintId, navigate]);

  const handleDragStart = useCallback((e: React.DragEvent, story: Story) => {
    e.dataTransfer.setData('storyId', story.id);
    e.dataTransfer.setData('storyData', JSON.stringify(story));
  }, []);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    setDragOverPlanned(true);
  }, []);

  const handleDragLeave = useCallback(() => {
    setDragOverPlanned(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragOverPlanned(false);
      const storyData = e.dataTransfer.getData('storyData');
      if (storyData) {
        const story = JSON.parse(storyData) as Story;
        handleAddToSprint(story);
      }
    },
    [handleAddToSprint]
  );

  if (loading) {
    return (
      <div className="sprint-planning-page sprint-planning-page--loading">
        <LoadingSpinner message="Loading planning session..." />
      </div>
    );
  }

  if (error || !session) {
    return (
      <div className="sprint-planning-page sprint-planning-page--error">
        <div className="error-container">
          <h2>Failed to load planning session</h2>
          <p>{error || 'Session not found'}</p>
          <button onClick={() => window.location.reload()}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <div className="sprint-planning-page">
        {/* Header */}
        <header className="page-header">
          <div className="header-main">
            <h1 className="page-title">📋 Sprint Planning</h1>
            <span className="sprint-name">
              Sprint {session.sprint.number}: {session.sprint.name}
            </span>
          </div>
          <div className="header-actions">
            <button
              type="button"
              className="secondary-btn"
              onClick={() => navigate(`/projects/${projectId}/sprints/${sprintId}`)}
            >
              Cancel
            </button>
            <button
              type="button"
              className="primary-btn"
              onClick={handleStartSprint}
              disabled={session.plannedStories.length === 0}
            >
              Start Sprint
            </button>
          </div>
        </header>

        {/* Sprint Goal */}
        <section className="goal-section">
          <label className="goal-label">Sprint Goal</label>
          <p className="goal-text">{session.sprint.goal}</p>
        </section>

        {/* Capacity Bar */}
        <CapacityBar committed={totalCommitted} capacity={session.sprint.capacity} />

        {/* Main Content */}
        <div className="planning-content">
          {/* Backlog Column */}
          <div className="backlog-column">
            <div className="column-header">
              <h2 className="column-title">📦 Product Backlog</h2>
              <span className="column-count">{session.backlogStories.length} stories</span>
            </div>
            <div className="story-list">
              {session.backlogStories.length === 0 ? (
                <div className="empty-list">
                  <p>No stories in backlog</p>
                </div>
              ) : (
                session.backlogStories.map((story) => (
                  <div
                    key={story.id}
                    className="story-item"
                    draggable
                    onDragStart={(e) => handleDragStart(e, story)}
                  >
                    <StoryCard
                      story={story}
                      onClick={() => setSelectedStory(story)}
                      compact
                    />
                    <button
                      type="button"
                      className="add-to-sprint-btn"
                      onClick={() => handleAddToSprint(story)}
                      title="Add to sprint"
                    >
                      →
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Sprint Column */}
          <div
            className={`sprint-column ${dragOverPlanned ? 'sprint-column--drag-over' : ''}`}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            <div className="column-header">
              <h2 className="column-title">🏃 Sprint Backlog</h2>
              <span className="column-count">
                {session.plannedStories.length} stories • {totalCommitted} pts
              </span>
            </div>
            <div className="story-list">
              {session.plannedStories.length === 0 ? (
                <div className="empty-list drop-zone">
                  <p>Drag stories here to add to sprint</p>
                </div>
              ) : (
                session.plannedStories.map((story) => (
                  <div key={story.id} className="story-item story-item--planned">
                    <StoryCard
                      story={story}
                      onClick={() => setSelectedStory(story)}
                      compact
                    />
                    <button
                      type="button"
                      className="remove-from-sprint-btn"
                      onClick={() => handleRemoveFromSprint(story)}
                      title="Remove from sprint"
                    >
                      ×
                    </button>
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Team Capacity Sidebar */}
          <TeamCapacityPanel team={session.team} />
        </div>

        {/* Story Detail / Pointing Modal */}
        {selectedStory && (
          <div className="modal-overlay" onClick={() => setSelectedStory(null)}>
            <div className="story-modal" onClick={(e) => e.stopPropagation()}>
              <div className="modal-header">
                <h3 className="modal-title">{selectedStory.title}</h3>
                <button
                  type="button"
                  className="modal-close"
                  onClick={() => setSelectedStory(null)}
                >
                  ×
                </button>
              </div>
              <div className="modal-body">
                <p className="story-description">{selectedStory.description}</p>
                {selectedStory.acceptanceCriteria.length > 0 && (
                  <div className="acceptance-criteria">
                    <h4>Acceptance Criteria</h4>
                    <ul>
                      {selectedStory.acceptanceCriteria.map((ac, i) => (
                        <li key={i}>{ac}</li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="pointing-section">
                  <h4>Story Points</h4>
                  <p className="pointing-instruction">
                    {selectedStory.points !== undefined
                      ? `Currently estimated at ${selectedStory.points} points`
                      : 'Select points to estimate this story'}
                  </p>
                  <PointingCards
                    selected={pointingVote ?? selectedStory.points ?? null}
                    onSelect={setPointingVote}
                  />
                </div>
              </div>
              <div className="modal-footer">
                <button
                  type="button"
                  className="secondary-btn"
                  onClick={() => {
                    setSelectedStory(null);
                    setPointingVote(null);
                  }}
                >
                  Cancel
                </button>
                <button
                  type="button"
                  className="primary-btn"
                  onClick={() => {
                    if (pointingVote !== null) {
                      handlePointsUpdate(selectedStory, pointingVote);
                    }
                  }}
                  disabled={pointingVote === null}
                >
                  Set Points
                </button>
              </div>
            </div>
          </div>
        )}

        {/* CSS-in-JS Styles */}
        <style>{`
          .sprint-planning-page {
            min-height: 100vh;
            background: #F9FAFB;
            padding: 1.5rem 2rem;
          }

          .sprint-planning-page--loading,
          .sprint-planning-page--error {
            display: flex;
            align-items: center;
            justify-content: center;
          }

          .error-container {
            text-align: center;
            padding: 2rem;
          }

          .error-container h2 {
            margin: 0 0 0.5rem;
            color: #111827;
          }

          .error-container p {
            margin: 0 0 1rem;
            color: #6B7280;
          }

          .error-container button {
            padding: 0.5rem 1rem;
            background: #3B82F6;
            color: #fff;
            border: none;
            border-radius: 6px;
            cursor: pointer;
          }

          .page-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 1.5rem;
          }

          .header-main {
            display: flex;
            align-items: baseline;
            gap: 1rem;
          }

          .page-title {
            margin: 0;
            font-size: 1.5rem;
            font-weight: 700;
            color: #111827;
          }

          .sprint-name {
            font-size: 0.875rem;
            color: #6B7280;
          }

          .header-actions {
            display: flex;
            gap: 0.75rem;
          }

          .primary-btn,
          .secondary-btn {
            padding: 0.625rem 1.25rem;
            border: none;
            border-radius: 8px;
            font-size: 0.875rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .primary-btn {
            background: #3B82F6;
            color: #fff;
          }

          .primary-btn:hover:not(:disabled) {
            background: #2563EB;
          }

          .primary-btn:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          .secondary-btn {
            background: #F3F4F6;
            color: #374151;
          }

          .secondary-btn:hover {
            background: #E5E7EB;
          }

          .goal-section {
            margin-bottom: 1rem;
            padding: 1rem;
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
          }

          .goal-label {
            font-size: 0.75rem;
            font-weight: 600;
            color: #6B7280;
            text-transform: uppercase;
            letter-spacing: 0.05em;
          }

          .goal-text {
            margin: 0.5rem 0 0;
            font-size: 1rem;
            color: #111827;
          }

          .capacity-bar {
            margin-bottom: 1.5rem;
            padding: 1rem;
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
          }

          .capacity-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.5rem;
          }

          .capacity-label {
            font-size: 0.875rem;
            font-weight: 600;
            color: #111827;
          }

          .capacity-value {
            font-size: 0.875rem;
            color: #6B7280;
          }

          .capacity-track {
            height: 12px;
            background: #E5E7EB;
            border-radius: 6px;
            overflow: hidden;
            position: relative;
          }

          .capacity-fill {
            height: 100%;
            border-radius: 6px;
            transition: width 0.3s ease;
          }

          .capacity-overflow {
            position: absolute;
            right: 0;
            top: 0;
            height: 100%;
            background: repeating-linear-gradient(
              45deg,
              #EF4444,
              #EF4444 5px,
              #DC2626 5px,
              #DC2626 10px
            );
            border-radius: 6px;
          }

          .planning-content {
            display: grid;
            grid-template-columns: 1fr 1fr 280px;
            gap: 1.5rem;
          }

          .backlog-column,
          .sprint-column {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            overflow: hidden;
          }

          .sprint-column--drag-over {
            border-color: #3B82F6;
            background: #EFF6FF;
          }

          .column-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1rem;
            border-bottom: 1px solid #E5E7EB;
          }

          .column-title {
            margin: 0;
            font-size: 0.875rem;
            font-weight: 600;
            color: #111827;
          }

          .column-count {
            font-size: 0.75rem;
            color: #6B7280;
          }

          .story-list {
            padding: 0.75rem;
            max-height: calc(100vh - 400px);
            overflow-y: auto;
          }

          .empty-list {
            padding: 2rem;
            text-align: center;
            color: #6B7280;
          }

          .drop-zone {
            border: 2px dashed #E5E7EB;
            border-radius: 8px;
          }

          .story-item {
            position: relative;
            margin-bottom: 0.5rem;
          }

          .story-item:last-child {
            margin-bottom: 0;
          }

          .add-to-sprint-btn,
          .remove-from-sprint-btn {
            position: absolute;
            right: 8px;
            top: 50%;
            transform: translateY(-50%);
            width: 28px;
            height: 28px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #3B82F6;
            color: #fff;
            border: none;
            border-radius: 50%;
            font-size: 1rem;
            cursor: pointer;
            opacity: 0;
            transition: opacity 0.15s ease;
          }

          .story-item:hover .add-to-sprint-btn,
          .story-item:hover .remove-from-sprint-btn {
            opacity: 1;
          }

          .remove-from-sprint-btn {
            background: #EF4444;
          }

          .team-capacity-panel {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            padding: 1rem;
          }

          .panel-title {
            margin: 0 0 1rem;
            font-size: 0.875rem;
            font-weight: 600;
            color: #111827;
          }

          .team-list {
            list-style: none;
            padding: 0;
            margin: 0;
          }

          .team-member {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 0.5rem 0;
            border-bottom: 1px solid #F3F4F6;
          }

          .team-member:last-child {
            border-bottom: none;
          }

          .member-info {
            display: flex;
            align-items: center;
            gap: 0.5rem;
          }

          .member-avatar {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            background: #E5E7EB;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 0.75rem;
            font-weight: 600;
            color: #374151;
          }

          .member-avatar img {
            width: 100%;
            height: 100%;
            border-radius: 50%;
            object-fit: cover;
          }

          .member-details {
            display: flex;
            flex-direction: column;
          }

          .member-name {
            font-size: 0.8125rem;
            font-weight: 500;
            color: #111827;
          }

          .member-role {
            font-size: 0.6875rem;
            color: #6B7280;
          }

          .member-capacity {
            display: flex;
            flex-direction: column;
            align-items: flex-end;
            gap: 0.25rem;
          }

          .capacity-text {
            font-size: 0.6875rem;
            color: #6B7280;
          }

          .capacity-mini-bar {
            width: 60px;
            height: 4px;
            background: #E5E7EB;
            border-radius: 2px;
            overflow: hidden;
          }

          .capacity-mini-fill {
            height: 100%;
            border-radius: 2px;
          }

          .modal-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
          }

          .story-modal {
            width: 100%;
            max-width: 500px;
            background: #fff;
            border-radius: 16px;
            overflow: hidden;
          }

          .modal-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1rem 1.5rem;
            border-bottom: 1px solid #E5E7EB;
          }

          .modal-title {
            margin: 0;
            font-size: 1.125rem;
            font-weight: 600;
            color: #111827;
          }

          .modal-close {
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: none;
            border: none;
            font-size: 1.5rem;
            color: #6B7280;
            cursor: pointer;
          }

          .modal-close:hover {
            color: #111827;
          }

          .modal-body {
            padding: 1.5rem;
          }

          .story-description {
            margin: 0 0 1rem;
            font-size: 0.875rem;
            color: #374151;
            line-height: 1.5;
          }

          .acceptance-criteria {
            margin-bottom: 1.5rem;
          }

          .acceptance-criteria h4 {
            margin: 0 0 0.5rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #111827;
          }

          .acceptance-criteria ul {
            margin: 0;
            padding-left: 1.25rem;
          }

          .acceptance-criteria li {
            font-size: 0.8125rem;
            color: #374151;
            margin-bottom: 0.25rem;
          }

          .pointing-section h4 {
            margin: 0 0 0.5rem;
            font-size: 0.8125rem;
            font-weight: 600;
            color: #111827;
          }

          .pointing-instruction {
            margin: 0 0 0.75rem;
            font-size: 0.8125rem;
            color: #6B7280;
          }

          .pointing-cards {
            display: flex;
            gap: 0.5rem;
            flex-wrap: wrap;
          }

          .pointing-card {
            width: 48px;
            height: 64px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #fff;
            border: 2px solid #E5E7EB;
            border-radius: 8px;
            font-size: 1.25rem;
            font-weight: 700;
            color: #374151;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .pointing-card:hover:not(:disabled) {
            border-color: #3B82F6;
            background: #EFF6FF;
          }

          .pointing-card--selected {
            border-color: #3B82F6;
            background: #3B82F6;
            color: #fff;
          }

          .pointing-card:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          .modal-footer {
            display: flex;
            justify-content: flex-end;
            gap: 0.75rem;
            padding: 1rem 1.5rem;
            border-top: 1px solid #E5E7EB;
          }

          @media (max-width: 1024px) {
            .planning-content {
              grid-template-columns: 1fr 1fr;
            }

            .team-capacity-panel {
              grid-column: 1 / -1;
            }
          }

          @media (max-width: 768px) {
            .planning-content {
              grid-template-columns: 1fr;
            }
          }
        `}</style>
      </div>
    </ErrorBoundary>
  );
};

SprintPlanningPage.displayName = 'SprintPlanningPage';

export default SprintPlanningPage;
