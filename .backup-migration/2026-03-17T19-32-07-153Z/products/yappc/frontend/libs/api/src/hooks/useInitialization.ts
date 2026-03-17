/**
 * Initialization Phase React Hooks
 *
 * @description Custom hooks for initialization/setup wizard phase operations
 * including infrastructure provisioning, environment setup, and team invites.
 *
 * @doc.type hooks
 * @doc.purpose Initialization phase data management
 * @doc.layer integration
 * @doc.phase initialization
 */

import { useCallback, useMemo, useEffect } from 'react';
import { useAtom, useAtomValue, useSetAtom } from 'jotai';
import { useQuery, useMutation, useSubscription, useLazyQuery } from '@apollo/client';

import {
  wizardStateAtom,
  currentWizardStepAtom,
  wizardProgressAtom,
  infrastructureStateAtom,
  provisioningStatusAtom,
  environmentsAtom,
  costEstimateAtom,
  teamInvitesAtom,
} from '@ghatana/yappc-canvas';

import {
  GET_WIZARD_STATE,
  GET_INFRASTRUCTURE_OPTIONS,
  GET_ENVIRONMENTS,
  GET_COST_ESTIMATE,
  GET_TEAM_INVITES,
  CREATE_WIZARD_SESSION,
  UPDATE_WIZARD_STEP,
  COMPLETE_WIZARD_STEP,
  SAVE_INFRASTRUCTURE_CONFIG,
  PROVISION_INFRASTRUCTURE,
  CREATE_ENVIRONMENT,
  UPDATE_ENVIRONMENT,
  DELETE_ENVIRONMENT,
  INVITE_TEAM_MEMBER,
  RESEND_INVITE,
  REVOKE_INVITE,
  FINALIZE_SETUP,
  INFRASTRUCTURE_PROVISIONING_SUBSCRIPTION,
  WIZARD_PROGRESS_SUBSCRIPTION,
  type WizardState,
  type WizardStep,
  type InfrastructureConfig,
  type Environment,
  type EnvironmentInput,
  type TeamInvite,
  type CostEstimate,
} from '@ghatana/yappc-api';

// =============================================================================
// Wizard Hooks
// =============================================================================

/**
 * Hook for managing wizard session state
 */
export function useWizard(sessionId?: string) {
  const [wizardState, setWizardState] = useAtom(wizardStateAtom);
  const [currentStep, setCurrentStep] = useAtom(currentWizardStepAtom);
  const [progress, setProgress] = useAtom(wizardProgressAtom);

  const { data, loading, error, refetch } = useQuery(GET_WIZARD_STATE, {
    variables: { sessionId },
    skip: !sessionId,
    onCompleted: (data) => {
      if (data?.wizardState) {
        setWizardState(data.wizardState);
        setCurrentStep(data.wizardState.currentStep);
        setProgress(data.wizardState.progress);
      }
    },
  });

  const [createSession] = useMutation(CREATE_WIZARD_SESSION);
  const [updateStep] = useMutation(UPDATE_WIZARD_STEP);
  const [completeStep] = useMutation(COMPLETE_WIZARD_STEP);
  const [finalize] = useMutation(FINALIZE_SETUP);

  // Subscribe to wizard progress updates
  useSubscription(WIZARD_PROGRESS_SUBSCRIPTION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
      if (data?.data?.wizardProgress) {
        const progressUpdate = data.data.wizardProgress;
        setProgress(progressUpdate.percentage);
        if (progressUpdate.currentStep) {
          setCurrentStep(progressUpdate.currentStep);
        }
      }
    },
  });

  const startWizard = useCallback(
    async (projectId: string, templateId?: string) => {
      const result = await createSession({
        variables: { input: { projectId, templateId } },
      });
      if (result.data?.createWizardSession) {
        setWizardState(result.data.createWizardSession);
        return result.data.createWizardSession;
      }
      return null;
    },
    [createSession, setWizardState]
  );

  const goToStep = useCallback(
    async (stepId: string, data?: Record<string, unknown>) => {
      if (!sessionId) return;
      await updateStep({
        variables: { sessionId, stepId, data },
      });
      setCurrentStep(stepId);
    },
    [sessionId, updateStep, setCurrentStep]
  );

  const completeCurrentStep = useCallback(
    async (stepData: Record<string, unknown>) => {
      if (!sessionId || !currentStep) return;
      const result = await completeStep({
        variables: { sessionId, stepId: currentStep, data: stepData },
      });
      if (result.data?.completeWizardStep) {
        const nextStep = result.data.completeWizardStep.nextStep;
        if (nextStep) {
          setCurrentStep(nextStep);
        }
      }
    },
    [sessionId, currentStep, completeStep, setCurrentStep]
  );

  const finalizeSetup = useCallback(async () => {
    if (!sessionId) return;
    const result = await finalize({ variables: { sessionId } });
    return result.data?.finalizeSetup;
  }, [sessionId, finalize]);

  return {
    wizardState,
    currentStep,
    progress,
    isLoading: loading,
    error,
    startWizard,
    goToStep,
    completeCurrentStep,
    finalizeSetup,
    refetch,
  };
}

// =============================================================================
// Infrastructure Hooks
// =============================================================================

/**
 * Hook for managing infrastructure configuration
 */
export function useInfrastructure(sessionId?: string) {
  const [infraState, setInfraState] = useAtom(infrastructureStateAtom);
  const [provisioningStatus, setProvisioningStatus] = useAtom(provisioningStatusAtom);

  const { data: optionsData, loading: optionsLoading } = useQuery(GET_INFRASTRUCTURE_OPTIONS, {
    skip: !sessionId,
  });

  const [saveConfig] = useMutation(SAVE_INFRASTRUCTURE_CONFIG);
  const [provision] = useMutation(PROVISION_INFRASTRUCTURE);

  // Subscribe to provisioning updates
  useSubscription(INFRASTRUCTURE_PROVISIONING_SUBSCRIPTION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }) => {
      if (data?.data?.infrastructureProvisioning) {
        const update = data.data.infrastructureProvisioning;
        setProvisioningStatus({
          status: update.status,
          progress: update.progress,
          currentResource: update.currentResource,
          completedResources: update.completedResources,
          totalResources: update.totalResources,
          errors: update.errors,
        });
      }
    },
  });

  const updateConfig = useCallback(
    async (config: Partial<InfrastructureConfig>) => {
      if (!sessionId) return;
      setInfraState((prev) => ({ ...prev, ...config }));
      await saveConfig({
        variables: { sessionId, config },
      });
    },
    [sessionId, setInfraState, saveConfig]
  );

  const startProvisioning = useCallback(async () => {
    if (!sessionId) return;
    setProvisioningStatus((prev) => ({ ...prev, status: 'provisioning', progress: 0 }));
    const result = await provision({ variables: { sessionId } });
    return result.data?.provisionInfrastructure;
  }, [sessionId, setProvisioningStatus, provision]);

  const options = useMemo(
    () => ({
      cloudProviders: optionsData?.infrastructureOptions?.cloudProviders || [],
      regions: optionsData?.infrastructureOptions?.regions || [],
      resourceTypes: optionsData?.infrastructureOptions?.resourceTypes || [],
      tiers: optionsData?.infrastructureOptions?.tiers || [],
    }),
    [optionsData]
  );

  return {
    config: infraState,
    provisioningStatus,
    options,
    isLoading: optionsLoading,
    updateConfig,
    startProvisioning,
  };
}

/**
 * Hook for cost estimation
 */
export function useCostEstimate(config?: InfrastructureConfig) {
  const [costEstimate, setCostEstimate] = useAtom(costEstimateAtom);

  const { data, loading, refetch } = useQuery(GET_COST_ESTIMATE, {
    variables: { config },
    skip: !config,
    onCompleted: (data) => {
      if (data?.costEstimate) {
        setCostEstimate(data.costEstimate);
      }
    },
  });

  const refreshEstimate = useCallback(
    async (newConfig?: InfrastructureConfig) => {
      const result = await refetch({ config: newConfig || config });
      if (result.data?.costEstimate) {
        setCostEstimate(result.data.costEstimate);
      }
      return result.data?.costEstimate;
    },
    [refetch, config, setCostEstimate]
  );

  return {
    estimate: costEstimate || data?.costEstimate,
    isLoading: loading,
    refreshEstimate,
  };
}

// =============================================================================
// Environment Hooks
// =============================================================================

/**
 * Hook for managing environments
 */
export function useEnvironments(projectId?: string) {
  const [environments, setEnvironments] = useAtom(environmentsAtom);

  const { data, loading, error, refetch } = useQuery(GET_ENVIRONMENTS, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.environments) {
        setEnvironments(data.environments);
      }
    },
  });

  return {
    environments: environments || data?.environments || [],
    isLoading: loading,
    error,
    refetch,
  };
}

/**
 * Hook for environment mutations
 */
export function useEnvironmentMutations(projectId?: string) {
  const setEnvironments = useSetAtom(environmentsAtom);

  const [createEnv] = useMutation(CREATE_ENVIRONMENT);
  const [updateEnv] = useMutation(UPDATE_ENVIRONMENT);
  const [deleteEnv] = useMutation(DELETE_ENVIRONMENT);

  const createEnvironment = useCallback(
    async (input: EnvironmentInput) => {
      if (!projectId) return null;
      const result = await createEnv({
        variables: { projectId, input },
      });
      if (result.data?.createEnvironment) {
        setEnvironments((prev) => [...prev, result.data.createEnvironment]);
        return result.data.createEnvironment;
      }
      return null;
    },
    [projectId, createEnv, setEnvironments]
  );

  const updateEnvironment = useCallback(
    async (envId: string, input: Partial<EnvironmentInput>) => {
      const result = await updateEnv({
        variables: { environmentId: envId, input },
      });
      if (result.data?.updateEnvironment) {
        setEnvironments((prev) =>
          prev.map((env) =>
            env.id === envId ? result.data.updateEnvironment : env
          )
        );
        return result.data.updateEnvironment;
      }
      return null;
    },
    [updateEnv, setEnvironments]
  );

  const deleteEnvironment = useCallback(
    async (envId: string) => {
      await deleteEnv({ variables: { environmentId: envId } });
      setEnvironments((prev) => prev.filter((env) => env.id !== envId));
    },
    [deleteEnv, setEnvironments]
  );

  return {
    createEnvironment,
    updateEnvironment,
    deleteEnvironment,
  };
}

// =============================================================================
// Team Invite Hooks
// =============================================================================

/**
 * Hook for managing team invitations
 */
export function useTeamInvites(projectId?: string) {
  const [invites, setInvites] = useAtom(teamInvitesAtom);

  const { data, loading, error, refetch } = useQuery(GET_TEAM_INVITES, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.teamInvites) {
        setInvites(data.teamInvites);
      }
    },
  });

  const [inviteMember] = useMutation(INVITE_TEAM_MEMBER);
  const [resendInvite] = useMutation(RESEND_INVITE);
  const [revokeInvite] = useMutation(REVOKE_INVITE);

  const sendInvite = useCallback(
    async (email: string, role: string, message?: string) => {
      if (!projectId) return null;
      const result = await inviteMember({
        variables: { projectId, input: { email, role, message } },
      });
      if (result.data?.inviteTeamMember) {
        setInvites((prev) => [...prev, result.data.inviteTeamMember]);
        return result.data.inviteTeamMember;
      }
      return null;
    },
    [projectId, inviteMember, setInvites]
  );

  const resend = useCallback(
    async (inviteId: string) => {
      const result = await resendInvite({ variables: { inviteId } });
      if (result.data?.resendInvite) {
        setInvites((prev) =>
          prev.map((invite) =>
            invite.id === inviteId
              ? { ...invite, resentAt: new Date().toISOString() }
              : invite
          )
        );
      }
      return result.data?.resendInvite;
    },
    [resendInvite, setInvites]
  );

  const revoke = useCallback(
    async (inviteId: string) => {
      await revokeInvite({ variables: { inviteId } });
      setInvites((prev) => prev.filter((invite) => invite.id !== inviteId));
    },
    [revokeInvite, setInvites]
  );

  return {
    invites: invites || data?.teamInvites || [],
    isLoading: loading,
    error,
    sendInvite,
    resend,
    revoke,
    refetch,
  };
}

export default {
  useWizard,
  useInfrastructure,
  useCostEstimate,
  useEnvironments,
  useEnvironmentMutations,
  useTeamInvites,
};
