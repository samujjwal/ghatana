/**
 * React hook for Agent Service integration
 *
 * Provides real-time access to agent metrics, plugins, and command execution.
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, useCallback } from 'react';
import { AgentService, MetricsData, PluginInfo, CommandRequest, CommandResponse } from '../services/agentService';

// Singleton instance
let agentService: AgentService | null = null;

const getAgentService = (): AgentService => {
  if (!agentService) {
    // Default configuration - can be overridden via environment variables
    agentService = new AgentService({
      httpUrl: import.meta.env.VITE_AGENT_HTTP_URL || 'http://localhost:8080',
      wsUrl: import.meta.env.VITE_AGENT_WS_URL || 'ws://localhost:8080',
      apiKey: import.meta.env.VITE_AGENT_API_KEY,
      jwtToken: import.meta.env.VITE_AGENT_JWT_TOKEN,
    });
  }
  return agentService;
};

/**
 * Hook to test agent connection
 */
export const useAgentConnection = () => {
  return useQuery({
    queryKey: ['agent', 'connection'],
    queryFn: () => getAgentService().testConnection(),
    staleTime: 30_000,
    refetchInterval: 60_000, // Recheck every minute
  });
};

/**
 * Hook to get agent metrics (one-time fetch)
 */
export const useAgentMetrics = () => {
  return useQuery({
    queryKey: ['agent', 'metrics'],
    queryFn: () => getAgentService().getMetrics(),
    staleTime: 5_000,
    refetchInterval: 10_000, // Refresh every 10 seconds
  });
};

/**
 * Hook for real-time metrics streaming via WebSocket
 */
export const useAgentMetricsStream = () => {
  const [metrics, setMetrics] = useState<MetricsData | null>(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    const service = getAgentService();

    // Set up WebSocket connection
    const disconnect = service.connectMetricsStream((data) => {
      setMetrics(data);
      setIsConnected(true);
    });

    return () => {
      disconnect();
      setIsConnected(false);
    };
  }, []);

  return { metrics, isConnected };
};

/**
 * Hook to list plugins
 */
export const useAgentPlugins = (status?: string) => {
  return useQuery({
    queryKey: ['agent', 'plugins', status],
    queryFn: () => getAgentService().listPlugins(status),
    staleTime: 15_000,
  });
};

/**
 * Hook to get a specific plugin
 */
export const useAgentPlugin = (pluginId: string) => {
  return useQuery({
    queryKey: ['agent', 'plugin', pluginId],
    queryFn: () => getAgentService().getPlugin(pluginId),
    enabled: !!pluginId,
    staleTime: 15_000,
  });
};

/**
 * Hook to start a plugin
 */
export const useStartPlugin = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (pluginId: string) => getAgentService().startPlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent', 'plugins'] });
    },
  });
};

/**
 * Hook to stop a plugin
 */
export const useStopPlugin = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (pluginId: string) => getAgentService().stopPlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent', 'plugins'] });
    },
  });
};

/**
 * Hook to install a plugin
 */
export const useInstallPlugin = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ source, config }: { source: string; config?: Record<string, any> }) =>
      getAgentService().installPlugin(source, config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent', 'plugins'] });
    },
  });
};

/**
 * Hook to uninstall a plugin
 */
export const useUninstallPlugin = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (pluginId: string) => getAgentService().uninstallPlugin(pluginId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent', 'plugins'] });
    },
  });
};

/**
 * Hook to update a plugin
 */
export const useUpdatePlugin = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ pluginId, config }: { pluginId: string; config: Record<string, any> }) =>
      getAgentService().updatePlugin(pluginId, config),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['agent', 'plugins'] });
    },
  });
};

/**
 * Hook to execute a command via agent
 */
export const useExecuteCommand = () => {
  return useMutation({
    mutationFn: (request: CommandRequest) => getAgentService().executeCommand(request),
  });
};

/**
 * Hook to get agent health status
 */
export const useAgentHealth = () => {
  return useQuery({
    queryKey: ['agent', 'health'],
    queryFn: () => getAgentService().getHealth(),
    staleTime: 10_000,
    refetchInterval: 30_000, // Refresh every 30 seconds
  });
};

/**
 * Hook for agent events stream via WebSocket
 */
export const useAgentEventsStream = () => {
  const [events, setEvents] = useState<any[]>([]);
  const [isConnected, setIsConnected] = useState(false);

  const addEvent = useCallback((event: unknown) => {
    setEvents((prev) => [event, ...prev].slice(0, 100)); // Keep last 100 events
  }, []);

  useEffect(() => {
    const service = getAgentService();

    // Set up WebSocket connection
    const disconnect = service.connectEventStream(addEvent);
    setIsConnected(true);

    return () => {
      disconnect();
      setIsConnected(false);
    };
  }, [addEvent]);

  return { events, isConnected };
};

/**
 * Export the service instance for direct access if needed
 */
export const getAgent = getAgentService;
