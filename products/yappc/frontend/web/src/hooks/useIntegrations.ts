/**
 * Integrations Hook
 *
 * React hook wrapping IntegrationService for CRUD operations
 * on third-party integrations.
 *
 * @doc.type hook
 * @doc.purpose Integration management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';
import {
  getIntegrations,
  registerIntegration,
  connectIntegration,
  disconnectIntegration,
  removeIntegration,
  checkHealth,
  type Integration,
  type IntegrationConfig,
  type IntegrationCategory,
  type IntegrationHealth,
} from '../services/integrations/IntegrationService';

// ============================================================================
// Types
// ============================================================================

export interface UseIntegrationsResult {
  integrations: Integration[];
  register: (
    name: string,
    category: IntegrationCategory,
    description: string,
    config?: IntegrationConfig,
  ) => Integration;
  connect: (id: string, config: IntegrationConfig) => Integration;
  disconnect: (id: string) => Integration;
  remove: (id: string) => void;
  healthCheck: (integration: Integration) => IntegrationHealth;
  refresh: () => void;
}

// ============================================================================
// Hook
// ============================================================================

export function useIntegrations(): UseIntegrationsResult {
  const [integrations, setIntegrations] = useState<Integration[]>(getIntegrations);

  useEffect(() => {
    setIntegrations(getIntegrations());
  }, []);

  const refresh = useCallback(() => {
    setIntegrations(getIntegrations());
  }, []);

  const register = useCallback(
    (
      name: string,
      category: IntegrationCategory,
      description: string,
      config: IntegrationConfig = {},
    ) => {
      const created = registerIntegration({ name, category, description, config });
      setIntegrations(getIntegrations());
      return created;
    },
    [],
  );

  const connect = useCallback((id: string, config: IntegrationConfig) => {
    const updated = connectIntegration(id, config);
    setIntegrations(getIntegrations());
    return updated;
  }, []);

  const disconnect = useCallback((id: string) => {
    const updated = disconnectIntegration(id);
    setIntegrations(getIntegrations());
    return updated;
  }, []);

  const remove = useCallback((id: string) => {
    removeIntegration(id);
    setIntegrations(getIntegrations());
  }, []);

  const healthCheck = useCallback((integration: Integration) => {
    return checkHealth(integration);
  }, []);

  return {
    integrations,
    register,
    connect,
    disconnect,
    remove,
    healthCheck,
    refresh,
  };
}
