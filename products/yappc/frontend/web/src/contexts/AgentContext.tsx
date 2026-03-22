import React, { createContext, useContext, useState, useEffect } from 'react';

import { agentService } from '../services/agentService';
import { AgentStatus } from '../types/agent';

import type { Agent, AgentTask } from '../types/agent';
import type { ReactNode } from 'react';

/**
 *
 */
interface AgentContextType {
  agents: Agent[];
  loading: boolean;
  error: string | null;
  selectedAgents: string[];
  tasks: AgentTask[];
  fetchAgents: () => Promise<void>;
  getAgent: (id: string) => Promise<Agent>;
  createAgent: (agent: Omit<Agent, 'id' | 'lastActive'>) => Promise<Agent>;
  updateAgent: (id: string, updates: Partial<Agent>) => Promise<Agent>;
  deleteAgent: (id: string) => Promise<void>;
  searchAgents: (query: string) => Promise<Agent[]>;
  toggleAgentSelection: (id: string) => void;
  executeTask: (taskDescription: string) => Promise<AgentTask>;
  getTask: (taskId: string) => AgentTask | undefined;
}

const AgentContext = createContext<AgentContextType | undefined>(undefined);

/**
 *
 */
interface AgentProviderProps {
  children: ReactNode;
}

export const AgentProvider: React.FC<AgentProviderProps> = ({ children }) => {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [selectedAgents, setSelectedAgents] = useState<string[]>([]);
  const [tasks, setTasks] = useState<AgentTask[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Fetch all agents on mount
  useEffect(() => {
    fetchAgents();
  }, []);

  const fetchAgents = async () => {
    try {
      setLoading(true);
      const data = await agentService.getAgents();
      setAgents(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch agents');
    } finally {
      setLoading(false);
    }
  };

  const getAgent = async (id: string) => {
    try {
      setLoading(true);
      const agent = await agentService.getAgent(id);
      setError(null);
      return agent;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to fetch agent';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const createAgent = async (agent: Omit<Agent, 'id' | 'lastActive'>) => {
    try {
      setLoading(true);
      const newAgent = await agentService.createAgent(agent);
      setAgents(prev => [...prev, newAgent]);
      setError(null);
      return newAgent;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to create agent';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const updateAgent = async (id: string, updates: Partial<Agent>) => {
    try {
      setLoading(true);
      const updatedAgent = await agentService.updateAgent(id, updates);
      setAgents(prev => 
        prev.map(agent => agent.id === id ? updatedAgent : agent)
      );
      setError(null);
      return updatedAgent;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to update agent';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const deleteAgent = async (id: string) => {
    try {
      setLoading(true);
      await agentService.deleteAgent(id);
      setAgents(prev => prev.filter(agent => agent.id !== id));
      setSelectedAgents(prev => prev.filter(agentId => agentId !== id));
      setError(null);
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Failed to delete agent';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const searchAgents = async (query: string) => {
    try {
      setLoading(true);
      const results = await agentService.searchAgents(query);
      setError(null);
      return results;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Search failed';
      setError(errorMessage);
      throw new Error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const toggleAgentSelection = (id: string) => {
    setSelectedAgents(prev => 
      prev.includes(id)
        ? prev.filter(agentId => agentId !== id)
        : [...prev, id]
    );
  };

  const executeTask = async (taskDescription: string): Promise<AgentTask> => {
    if (selectedAgents.length === 0) {
      throw new Error('No agents selected for the task');
    }

    const task: AgentTask = {
      id: `task-${Date.now()}`,
      description: taskDescription,
      status: 'pending',
      agentIds: [...selectedAgents],
      timestamps: {
        createdAt: new Date().toISOString(),
        startedAt: new Date().toISOString(),
      },
    };

    // Add the task to the list
    setTasks(prev => [...prev, task]);

    try {
      // Update task status to in_progress
      const updatedTask = {
        ...task,
        status: 'in_progress' as const,
      };
      setTasks(prev => prev.map(t => t.id === task.id ? updatedTask : t));

      // Execute the task using the agent service
      const result = await agentService.executeTask(taskDescription, selectedAgents);
      
      // Update task status to completed
      const completedTask = {
        ...updatedTask,
        status: 'completed' as const,
        result,
        timestamps: {
          ...updatedTask.timestamps,
          completedAt: new Date().toISOString(),
        },
      };
      
      setTasks(prev => prev.map(t => t.id === task.id ? completedTask : t));
      return completedTask;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Task execution failed';
      
      // Update task status to failed
      const failedTask = {
        ...task,
        status: 'failed' as const,
        error: {
          message: errorMessage,
        },
        timestamps: {
          ...task.timestamps,
          completedAt: new Date().toISOString(),
        },
      };
      
      setTasks(prev => prev.map(t => t.id === task.id ? failedTask : t));
      throw new Error(errorMessage);
    }
  };

  const getTask = (taskId: string) => {
    return tasks.find(task => task.id === taskId);
  };

  return (
    <AgentContext.Provider
      value={{
        agents,
        loading,
        error,
        selectedAgents,
        tasks,
        fetchAgents,
        getAgent,
        createAgent,
        updateAgent,
        deleteAgent,
        searchAgents,
        toggleAgentSelection,
        executeTask,
        getTask,
      }}
    >
      {children}
    </AgentContext.Provider>
  );
};

export const useAgents = (): AgentContextType => {
  const context = useContext(AgentContext);
  if (context === undefined) {
    throw new Error('useAgents must be used within an AgentProvider');
  }
  return context;
};
