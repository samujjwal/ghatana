/**
 * Tasks Panel Component
 *
 * Task management panel with filtering, sorting, and phase-based organization.
 * Supports task creation, editing, status updates, and priority management.
 *
 * @doc.type component
 * @doc.purpose Task management and tracking
 * @doc.layer presentation
 */

import React, { useState, useMemo } from "react";
import { useAtomValue } from "jotai";
import {
  chromeCurrentPhaseAtom,
} from "../../chrome";
import { getCanvasConfig, hasCanvasConfig } from '../../core/canvas-config';
import { getCanvasState } from "../../handlers/canvas-handlers";

interface TasksPanelProps {
  onClose: () => void;
}

type TaskStatus = "todo" | "in-progress" | "done" | "blocked";
type TaskPriority = "low" | "medium" | "high" | "critical";

interface Task {
  id: string;
  title: string;
  description?: string;
  status: TaskStatus;
  priority: TaskPriority;
  phase: string;?: string;
  dueDate?: string;
  createdAt: string;
  updatedAt: string;
}

interface TaskGroup {
  phase: string;
  tasks: Task[];
}

const STATUS_ICONS: Record<TaskStatus, string> = {
  todo: "⭕",
  "in-progress": "🔄",
  done: "✅",
  blocked: "🚫",
};

const PRIORITY_COLORS: Record<TaskPriority, string> = {
  low: "#4caf50",
  medium: "#ff9800",
  high: "#f44336",
  critical: "#9c27b0",
};

export const TasksPanel: React.FC<TasksPanelProps> = ({ onClose }) => {
  const currentPhase = useAtomValue(chromeCurrentPhaseAtom);
  const [searchQuery, setSearchQuery] = useState("");
  const [filterStatus, setFilterStatus] = useState<TaskStatus | "all">("all");
  const [filterPriority, setFilterPriority] = useState<TaskPriority | "all">(
    "all",
  );
  const [expandedPhases, setExpandedPhases] = useState<Set<string>>(
    new Set([currentPhase]),
  );
  const [showCompleted, setShowCompleted] = useState(true);
  const [isCreating, setIsCreating] = useState(false);
  const [newTaskTitle, setNewTaskTitle] = useState("");

  // Get all task elements from canvas state
  const tasks = useMemo(() => {
    const canvasState = getCanvasState();
    const taskElements = canvasState
      .getAllElements()
      .filter((el) => el.type === "task");

    // Convert canvas elements to Task objects
    return taskElements.map((el) => ({
      id: el.id,
      title: el.data?.label || el.data?.title || "Untitled Task",
      description: el.data?.description,
      status: (el.data?.status as TaskStatus) || "todo",
      priority: (el.data?.priority as TaskPriority) || "medium",
      phase: (el.data?.phase as string) || currentPhase,
      assignee: el.data?.assignee,
      dueDate: el.data?.dueDate,
      createdAt: el.data?.createdAt || new Date().toISOString(),
      updatedAt: el.data?.updatedAt || new Date().toISOString(),
    })) as Task[];
  }, [currentPhase]);

  // Filter tasks
  const filteredTasks = useMemo(() => {
    return tasks.filter((task) => {
      // Search filter
      if (
        searchQuery &&
        !task.title.toLowerCase().includes(searchQuery.toLowerCase())
      ) {
        return false;
      }

      // Status filter
      if (filterStatus !== "all" && task.status !== filterStatus) {
        return false;
      }

      // Priority filter
      if (filterPriority !== "all" && task.priority !== filterPriority) {
        return false;
      }

      // Show completed filter
      if (!showCompleted && task.status === "done") {
        return false;
      }

      return true;
    });
  }, [tasks, searchQuery, filterStatus, filterPriority, showCompleted]);

  // Group tasks by phase
  const tasksByPhase = useMemo(() => {
    const groups: TaskGroup[] = [];
    const phases: string[] = hasCanvasConfig()
      ? Object.keys(getCanvasConfig().phases)
      : [];

    phases.forEach((phase) => {
      const phaseTasks = filteredTasks.filter((t) => t.phase === phase);
      if (phaseTasks.length > 0) {
        groups.push({ phase, tasks: phaseTasks });
      }
    });

    return groups;
  }, [filteredTasks]);

  // Task statistics
  const stats = useMemo(() => {
    const total = filteredTasks.length;
    const completed = filteredTasks.filter((t) => t.status === "done").length;
    const inProgress = filteredTasks.filter(
      (t) => t.status === "in-progress",
    ).length;
    const blocked = filteredTasks.filter((t) => t.status === "blocked").length;

    return { total, completed, inProgress, blocked };
  }, [filteredTasks]);

  const togglePhase = (phase: string) => {
    const newExpanded = new Set(expandedPhases);
    if (newExpanded.has(phase)) {
      newExpanded.delete(phase);
    } else {
      newExpanded.add(phase);
    }
    setExpandedPhases(newExpanded);
  };

  const handleCreateTask = () => {
    if (!newTaskTitle.trim()) return;

    // Dispatch event to create task on canvas
    const event = new CustomEvent("yappc:add-node", {
      detail: {
        type: "task",
        data: {
          label: newTaskTitle,
          status: "todo",
          priority: "medium",
          phase: currentPhase,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        position: { x: 100, y: 100 },
      },
    });
    window.dispatchEvent(event);

    setNewTaskTitle("");
    setIsCreating(false);
  };

  const handleUpdateTaskStatus = (taskId: string, newStatus: TaskStatus) => {
    const event = new CustomEvent("yappc:update-node", {
      detail: {
        id: taskId,
        data: { status: newStatus, updatedAt: new Date().toISOString() },
      },
    });
    window.dispatchEvent(event);
  };

  return (
    <div
      style={{
        width: "320px",
        height: "100%",
        backgroundColor: "#ffffff",
        borderRight: "1px solid #e0e0e0",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      {/* Header */}
      <div
        style={{
          padding: "16px",
          borderBottom: "1px solid #e0e0e0",
          display: "flex",
          flexDirection: "column",
          gap: "12px",
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
          }}
        >
          <h3 style={{ margin: 0, fontSize: "16px", fontWeight: 600 }}>
            Tasks
          </h3>
          <button
            onClick={onClose}
            style={{
              border: "none",
              background: "transparent",
              cursor: "pointer",
              fontSize: "20px",
              padding: "4px",
            }}
            aria-label="Close panel"
          >
            ×
          </button>
        </div>

        {/* Statistics */}
        <div
          style={{
            display: "flex",
            gap: "8px",
            fontSize: "12px",
            color: "#757575",
          }}
        >
          <span>Total: {stats.total}</span>
          <span>•</span>
          <span>Done: {stats.completed}</span>
          <span>•</span>
          <span>In Progress: {stats.inProgress}</span>
          {stats.blocked > 0 && (
            <>
              <span>•</span>
              <span style={{ color: "#f44336" }}>Blocked: {stats.blocked}</span>
            </>
          )}
        </div>

        {/* Search */}
        <input
          type="text"
          placeholder="Search tasks..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{
            padding: "8px 12px",
            border: "1px solid #e0e0e0",
            borderRadius: "6px",
            fontSize: "13px",
            outline: "none",
          }}
        />

        {/* Filters */}
        <div style={{ display: "flex", gap: "8px" }}>
          <select
            value={filterStatus}
            onChange={(e) =>
              setFilterStatus(e.target.value as TaskStatus | "all")
            }
            style={{
              flex: 1,
              padding: "6px 8px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            <option value="all">All Status</option>
            <option value="todo">To Do</option>
            <option value="in-progress">In Progress</option>
            <option value="done">Done</option>
            <option value="blocked">Blocked</option>
          </select>

          <select
            value={filterPriority}
            onChange={(e) =>
              setFilterPriority(e.target.value as TaskPriority | "all")
            }
            style={{
              flex: 1,
              padding: "6px 8px",
              border: "1px solid #e0e0e0",
              borderRadius: "6px",
              fontSize: "12px",
              cursor: "pointer",
            }}
          >
            <option value="all">All Priority</option>
            <option value="low">Low</option>
            <option value="medium">Medium</option>
            <option value="high">High</option>
            <option value="critical">Critical</option>
          </select>
        </div>

        {/* Toggle completed */}
        <label
          style={{
            display: "flex",
            alignItems: "center",
            gap: "8px",
            fontSize: "12px",
            cursor: "pointer",
          }}
        >
          <input
            type="checkbox"
            checked={showCompleted}
            onChange={(e) => setShowCompleted(e.target.checked)}
          />
          Show completed tasks
        </label>

        {/* Create task button */}
        {!isCreating ? (
          <button
            onClick={() => setIsCreating(true)}
            style={{
              padding: "8px 12px",
              border: "1px solid #1976d2",
              borderRadius: "6px",
              background: "#1976d2",
              color: "white",
              cursor: "pointer",
              fontSize: "13px",
              fontWeight: 500,
            }}
          >
            + New Task
          </button>
        ) : (
          <div style={{ display: "flex", gap: "8px" }}>
            <input
              type="text"
              placeholder="Task title..."
              value={newTaskTitle}
              onChange={(e) => setNewTaskTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleCreateTask();
                if (e.key === "Escape") setIsCreating(false);
              }}
              autoFocus
              style={{
                flex: 1,
                padding: "8px 12px",
                border: "1px solid #1976d2",
                borderRadius: "6px",
                fontSize: "13px",
                outline: "none",
              }}
            />
            <button
              onClick={handleCreateTask}
              style={{
                padding: "8px 12px",
                border: "none",
                borderRadius: "6px",
                background: "#1976d2",
                color: "white",
                cursor: "pointer",
                fontSize: "13px",
              }}
            >
              Add
            </button>
            <button
              onClick={() => setIsCreating(false)}
              style={{
                padding: "8px 12px",
                border: "1px solid #e0e0e0",
                borderRadius: "6px",
                background: "white",
                cursor: "pointer",
                fontSize: "13px",
              }}
            >
              Cancel
            </button>
          </div>
        )}
      </div>

      {/* Task List */}
      <div style={{ flex: 1, overflow: "auto", padding: "8px" }}>
        {tasksByPhase.length === 0 ? (
          <div
            style={{
              padding: "32px 16px",
              textAlign: "center",
              color: "#9e9e9e",
              fontSize: "14px",
            }}
          >
            {tasks.length === 0
              ? "No tasks yet. Create one to get started!"
              : "No tasks match your filters."}
          </div>
        ) : (
          tasksByPhase.map((group) => {
            const phaseColor = hasCanvasConfig()
              ? getCanvasConfig().phases[group.phase]?.color ?? { primary: '#1976d2', background: '#e3f2fd', text: '#1565c0' }
              : { primary: '#1976d2', background: '#e3f2fd', text: '#1565c0' };
            const isExpanded = expandedPhases.has(group.phase);

            return (
              <div key={group.phase} style={{ marginBottom: "8px" }}>
                {/* Phase Header */}
                <button
                  onClick={() => togglePhase(group.phase)}
                  style={{
                    width: "100%",
                    padding: "8px 12px",
                    border: "none",
                    borderRadius: "6px",
                    background: phaseColor.background,
                    color: phaseColor.text,
                    cursor: "pointer",
                    fontSize: "13px",
                    fontWeight: 600,
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                    textAlign: "left",
                  }}
                >
                  <span>
                    {isExpanded ? "▼" : "▶"} {group.phase}
                  </span>
                  <span
                    style={{
                      fontSize: "11px",
                      padding: "2px 8px",
                      borderRadius: "10px",
                      background: "rgba(0,0,0,0.1)",
                    }}
                  >
                    {group.tasks.length}
                  </span>
                </button>

                {/* Tasks */}
                {isExpanded && (
                  <div style={{ marginTop: "4px", marginLeft: "8px" }}>
                    {group.tasks.map((task) => (
                      <div
                        key={task.id}
                        style={{
                          padding: "12px",
                          marginBottom: "4px",
                          border: "1px solid #e0e0e0",
                          borderRadius: "6px",
                          background: "white",
                          cursor: "pointer",
                          transition: "all 0.2s",
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.boxShadow =
                            "0 2px 8px rgba(0,0,0,0.1)";
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.boxShadow = "none";
                        }}
                      >
                        {/* Task Header */}
                        <div
                          style={{
                            display: "flex",
                            alignItems: "flex-start",
                            gap: "8px",
                            marginBottom: "8px",
                          }}
                        >
                          <button
                            onClick={() => {
                              const nextStatus: Record<TaskStatus, TaskStatus> =
                                {
                                  todo: "in-progress",
                                  "in-progress": "done",
                                  done: "todo",
                                  blocked: "todo",
                                };
                              handleUpdateTaskStatus(
                                task.id,
                                nextStatus[task.status],
                              );
                            }}
                            style={{
                              border: "none",
                              background: "transparent",
                              cursor: "pointer",
                              fontSize: "16px",
                              padding: 0,
                            }}
                            title={`Status: ${task.status}`}
                          >
                            {STATUS_ICONS[task.status]}
                          </button>
                          <div style={{ flex: 1 }}>
                            <div
                              style={{
                                fontSize: "13px",
                                fontWeight: 500,
                                color:
                                  task.status === "done"
                                    ? "#9e9e9e"
                                    : "#212121",
                                textDecoration:
                                  task.status === "done"
                                    ? "line-through"
                                    : "none",
                              }}
                            >
                              {task.title}
                            </div>
                            {task.description && (
                              <div
                                style={{
                                  fontSize: "11px",
                                  color: "#757575",
                                  marginTop: "4px",
                                }}
                              >
                                {task.description}
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Task Metadata */}
                        <div
                          style={{
                            display: "flex",
                            gap: "8px",
                            fontSize: "11px",
                            flexWrap: "wrap",
                          }}
                        >
                          {/* Priority Badge */}
                          <span
                            style={{
                              padding: "2px 8px",
                              borderRadius: "10px",
                              background: PRIORITY_COLORS[task.priority],
                              color: "white",
                              fontWeight: 500,
                            }}
                          >
                            {task.priority}
                          </span>

                          {/* Assignee */}
                          {task.assignee && (
                            <span
                              style={{
                                padding: "2px 8px",
                                borderRadius: "10px",
                                background: "#f5f5f5",
                                color: "#616161",
                              }}
                            >
                              👤 {task.assignee}
                            </span>
                          )}

                          {/* Due Date */}
                          {task.dueDate && (
                            <span
                              style={{
                                padding: "2px 8px",
                                borderRadius: "10px",
                                background: "#f5f5f5",
                                color: "#616161",
                              }}
                            >
                              📅 {new Date(task.dueDate).toLocaleDateString()}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};
