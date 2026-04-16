/**
 * Initialization Phase React Hooks
 *
 * @description Custom hooks for initialization/setup wizard phase operations
 * including infrastructure provisioning, environment setup, and team invites.
 *
 * @doc.type hooks
 * @doc.purpose Initialization phase data management
 * @doc.layer integration
 */

/* eslint-disable @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-return -- Apollo hook refactor pending */

import {
  useQuery,
  useMutation,
  useSubscription,
} from '@apollo/client/react';
import { useAtom, useSetAtom } from 'jotai';
import { useCallback, useMemo } from 'react';

import {
  wizardStateAtom,
  currentWizardStepAtom,
  wizardProgressAtom,
  infrastructureStateAtom,
  provisioningStatusAtom,
  environmentsAtom,
  costEstimateAtom,
  teamInvitesAtom,
} from '@yappc/state';
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
  type InfrastructureConfig,
} from '@yappc/core/api';

// =============================================================================
// Wizard Hooks
// =============================================================================

/**
 * Hook for managing wizard session state
 */
export function useWizard(sessionId?: string) {
  type WizardQueryData = { wizardState?: WizardState };
  type WizardQueryState = {
    data?: WizardQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type WizardMutationResult = { [key: string]: unknown };
  type WizardMutationState = { loading: boolean; error?: unknown };
  const runWizardQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => WizardQueryState;
  const runWizardMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: unknown) => Promise<{ data?: WizardMutationResult }>,
    WizardMutationState,
  ];

  const [wizardState, setWizardState] = useAtom(wizardStateAtom);
  const [currentStep, setCurrentStep] = useAtom(currentWizardStepAtom);
  const [progress, setProgress] = useAtom(wizardProgressAtom);

  const { loading, error, refetch } = runWizardQuery(GET_WIZARD_STATE, {
    variables: { sessionId },
    skip: !sessionId,
    onCompleted: (queryData: WizardQueryData) => {
      if (queryData?.wizardState) {
        setWizardState(queryData.wizardState);
        setCurrentStep(queryData.wizardState.currentStep);
        setProgress(queryData.wizardState.progress);
      }
    },
  });

  const [createSessionValue] = runWizardMutation(CREATE_WIZARD_SESSION);
  const createSession = createSessionValue as (
    args: unknown
  ) => Promise<{ data?: WizardMutationResult }>;

  const [updateStepValue] = runWizardMutation(UPDATE_WIZARD_STEP);
  const updateStep = updateStepValue as (
    args: unknown
  ) => Promise<{ data?: WizardMutationResult }>;

  const [completeStepValue] = runWizardMutation(
    COMPLETE_WIZARD_STEP
  );
  const completeStep = completeStepValue as (
    args: unknown
  ) => Promise<{ data?: WizardMutationResult }>;

  const [finalizeValue] = runWizardMutation(
    FINALIZE_SETUP
  );
  const finalize = finalizeValue as (
    args: unknown
  ) => Promise<{ data?: WizardMutationResult }>;

  // Subscribe to wizard progress updates
  type WizardProgressPayload = { wizardProgress?: Record<string, unknown> };
  type WizardSubscriptionState = { data?: WizardProgressPayload };
  const runWizardSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => WizardSubscriptionState;

  runWizardSubscription(WIZARD_PROGRESS_SUBSCRIPTION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({ data }: { data: WizardSubscriptionState }) => {
      if (data?.data?.wizardProgress) {
        const progressUpdate = data.data.wizardProgress as Record<
          string,
          unknown
        >;
        setProgress((progressUpdate.percentage ?? 0) as number);
        if (progressUpdate.currentStep) {
          setCurrentStep(progressUpdate.currentStep as string);
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
  type InfrastructureOptionsData = {
    infrastructureOptions?: { [key: string]: unknown };
  };
  type InfrastructureOptionsState = {
    data?: InfrastructureOptionsData;
    loading: boolean;
    error?: unknown;
  };
  type InfrastructureMutationResult = { [key: string]: unknown };
  type InfrastructureMutationState = { loading: boolean; error?: unknown };
  const runInfraQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => InfrastructureOptionsState;
  const runInfraMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: InfrastructureMutationResult }>, InfrastructureMutationState];
  const runInfraSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => { data?: { data?: { infrastructureProvisioning?: Record<string, unknown> } } };

  const [infraState, setInfraState] = useAtom(infrastructureStateAtom);
  const [provisioningStatus, setProvisioningStatus] = useAtom(
    provisioningStatusAtom
  );

  const { data: optionsData, loading: optionsLoading } = runInfraQuery(
    GET_INFRASTRUCTURE_OPTIONS,
    {
      skip: !sessionId,
    }
  );

  const [saveConfigValue] = runInfraMutation(SAVE_INFRASTRUCTURE_CONFIG);
  const saveConfig = saveConfigValue as (
    args: unknown
  ) => Promise<{ data?: InfrastructureMutationResult }>;

  const [provisionValue] = runInfraMutation(PROVISION_INFRASTRUCTURE);
  const provision = provisionValue as (
    args: unknown
  ) => Promise<{ data?: InfrastructureMutationResult }>;

  // Subscribe to provisioning updates
  runInfraSubscription(INFRASTRUCTURE_PROVISIONING_SUBSCRIPTION, {
    variables: { sessionId },
    skip: !sessionId,
    onData: ({
      data,
    }: {
      data?: {
        data?: { infrastructureProvisioning?: Record<string, unknown> };
      };
    }) => {
      if (data?.data?.infrastructureProvisioning) {
        const update = data.data.infrastructureProvisioning;
        setProvisioningStatus({
          status: (update.status ?? 'unknown') as string,
          progress: (update.progress ?? 0) as number,
          currentResource: (update.currentResource ?? null) as unknown,
          completedResources: (update.completedResources ?? []) as unknown,
          totalResources: (update.totalResources ?? 0) as number,
          errors: (update.errors ?? []) as unknown,
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
    setProvisioningStatus((prev) => ({
      ...prev,
      status: 'provisioning',
      progress: 0,
    }));
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
  type CostEstimateData = { costEstimate?: Record<string, unknown> };
  type CostEstimateState = {
    data?: CostEstimateData;
    loading: boolean;
    refetch: (opts?: unknown) => Promise<{ data?: CostEstimateData }>;
  };
  const runCostEstimateQuery = useQuery as unknown as (query: unknown, options: unknown) => CostEstimateState;

  const [costEstimate, setCostEstimate] = useAtom(costEstimateAtom);

  const { data, loading, refetch } = runCostEstimateQuery(GET_COST_ESTIMATE, {
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
  type EnvironmentsData = { environments?: Record<string, unknown>[] };
  type EnvironmentsState = {
    data?: EnvironmentsData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: EnvironmentsData }>;
  };
  const runEnvironmentsQuery = useQuery as unknown as (query: unknown, options: unknown) => EnvironmentsState;

  const [environments, setEnvironments] = useAtom(environmentsAtom);

  const { data, loading, error, refetch } = runEnvironmentsQuery(GET_ENVIRONMENTS, {
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
  type _EnvironmentMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown } };
  const runMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const setEnvironments = useSetAtom(environmentsAtom);

  const [createEnv] = runMutation(CREATE_ENVIRONMENT);
  const [updateEnv] = runMutation(UPDATE_ENVIRONMENT);
  const [deleteEnv] = runMutation(DELETE_ENVIRONMENT);

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
  type TeamInvitesData = { teamInvites?: Record<string, unknown>[] };
  type TeamInvitesState = {
    data?: TeamInvitesData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: TeamInvitesData }>;
  };
  const runTeamInvitesQuery = useQuery as unknown as (query: unknown, options: unknown) => TeamInvitesState;

  type InviteMutationResult = { [key: string]: unknown };
  const runInviteMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<{ data?: InviteMutationResult }>, { loading: boolean }];

  const [invites, setInvites] = useAtom(teamInvitesAtom);

  const { data, loading, error, refetch } = runTeamInvitesQuery(GET_TEAM_INVITES, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.teamInvites) {
        setInvites(data.teamInvites);
      }
    },
  });

  const [inviteMember] = runInviteMutation(INVITE_TEAM_MEMBER);
  const [resendInvite] = runInviteMutation(RESEND_INVITE);
  const [revokeInvite] = runInviteMutation(REVOKE_INVITE);

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
