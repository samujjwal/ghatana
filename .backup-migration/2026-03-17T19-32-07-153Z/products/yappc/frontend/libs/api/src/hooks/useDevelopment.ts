/**
 * Development Phase React Hooks
 *
 * @description Custom React hooks for development phase operations
 * including sprints, stories, PRs, feature flags, and deployments.
 *
 * @doc.type hooks
 * @doc.purpose Development Phase Data Access
 * @doc.layer presentation
 */

import { useCallback, useMemo } from 'react';
import {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
} from '@apollo/client';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';

import {
  GET_SPRINT,
  LIST_SPRINTS,
  GET_ACTIVE_SPRINT,
  GET_STORY,
  LIST_STORIES,
  GET_SPRINT_BOARD,
  GET_BACKLOG,
  GET_PULL_REQUEST,
  LIST_PULL_REQUESTS,
  GET_FEATURE_FLAG,
  LIST_FEATURE_FLAGS,
  GET_DEPLOYMENT,
  LIST_DEPLOYMENTS,
  GET_VELOCITY_DATA,
  GET_EPIC,
  LIST_EPICS,
  CREATE_SPRINT,
  UPDATE_SPRINT,
  START_SPRINT,
  COMPLETE_SPRINT,
  CREATE_STORY,
  UPDATE_STORY,
  DELETE_STORY,
  MOVE_STORY,
  UPDATE_STORY_STATUS,
  ASSIGN_STORY,
  ADD_STORY_COMMENT,
  LOG_STORY_TIME,
  CREATE_SUBTASK,
  TOGGLE_SUBTASK,
  CREATE_PULL_REQUEST,
  UPDATE_PULL_REQUEST,
  MERGE_PULL_REQUEST,
  CLOSE_PULL_REQUEST,
  REQUEST_PR_REVIEW,
  SUBMIT_PR_REVIEW,
  CREATE_FEATURE_FLAG,
  UPDATE_FEATURE_FLAG,
  TOGGLE_FEATURE_FLAG,
  DELETE_FEATURE_FLAG,
  TRIGGER_DEPLOYMENT,
  ROLLBACK_DEPLOYMENT,
  CANCEL_DEPLOYMENT,
  CREATE_EPIC,
  UPDATE_EPIC,
  DELETE_EPIC,
  SUBSCRIBE_TO_SPRINT_UPDATES,
  SUBSCRIBE_TO_BOARD_CHANGES,
  SUBSCRIBE_TO_STORY_UPDATES,
  SUBSCRIBE_TO_PR_UPDATES,
  SUBSCRIBE_TO_DEPLOYMENT_STATUS,
  SUBSCRIBE_TO_FEATURE_FLAG_CHANGES,
  type CreateSprintInput,
  type UpdateSprintInput,
  type CompleteSprintInput,
  type CreateStoryInput,
  type UpdateStoryInput,
  type MoveStoryInput,
  type AddCommentInput,
  type LogTimeInput,
  type CreateSubtaskInput,
  type CreatePullRequestInput,
  type UpdatePullRequestInput,
  type SubmitReviewInput,
  type CreateFeatureFlagInput,
  type UpdateFeatureFlagInput,
  type TriggerDeploymentInput,
  type CreateEpicInput,
  type UpdateEpicInput,
  type StoryFilter,
  type PullRequestFilter,
  type FeatureFlagFilter,
  type EpicFilter,
} from '@ghatana/yappc-api';

import {
  sprintsAtom,
  activeSprintAtom,
  storiesMapAtom,
  boardColumnsAtom,
  pullRequestsAtom,
  featureFlagsAtom,
  deploymentsAtom,
  epicsAtom,
  velocityDataAtom,
  updateStoryStatusAction,
  moveStoryToSprintAction,
  toggleFeatureFlagAction,
} from '@ghatana/yappc-canvas';

// =============================================================================
// Sprint Hooks
// =============================================================================

/**
 * Hook for fetching a single sprint
 */
export function useSprint(sprintId: string | undefined) {
  const { data, loading, error, refetch } = useQuery(GET_SPRINT, {
    variables: { id: sprintId },
    skip: !sprintId,
  });

  return {
    sprint: data?.sprint,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for listing sprints
 */
export function useSprints(projectId: string, status?: 'planned' | 'active' | 'completed') {
  const [sprints, setSprints] = useAtom(sprintsAtom);

  const { loading, error, refetch } = useQuery(LIST_SPRINTS, {
    variables: { projectId, status },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.sprints) {
        setSprints(data.sprints);
      }
    },
  });

  return {
    sprints,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for active sprint with stories
 */
export function useActiveSprint(projectId: string) {
  const [activeSprint, setActiveSprint] = useAtom(activeSprintAtom);
  const [boardColumns, setBoardColumns] = useAtom(boardColumnsAtom);

  const { loading, error, refetch } = useQuery(GET_ACTIVE_SPRINT, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.activeSprint) {
        setActiveSprint(data.activeSprint);
      }
    },
  });

  // Subscribe to sprint updates
  useSubscription(SUBSCRIBE_TO_SPRINT_UPDATES, {
    variables: { sprintId: activeSprint?.id },
    skip: !activeSprint?.id,
    onData: ({ data }) => {
      const update = data.data?.sprintUpdated;
      if (update?.sprint) {
        setActiveSprint(update.sprint);
      }
    },
  });

  return {
    sprint: activeSprint,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for sprint management mutations
 */
export function useSprintMutations(projectId: string) {
  const [createMutation, { loading: creating }] = useMutation(CREATE_SPRINT);
  const [updateMutation, { loading: updating }] = useMutation(UPDATE_SPRINT);
  const [startMutation, { loading: starting }] = useMutation(START_SPRINT);
  const [completeMutation, { loading: completing }] = useMutation(COMPLETE_SPRINT);

  const create = useCallback(
    async (input: CreateSprintInput) => {
      const result = await createMutation({ variables: { projectId, input } });
      return result.data?.createSprint;
    },
    [projectId, createMutation]
  );

  const update = useCallback(
    async (id: string, input: UpdateSprintInput) => {
      const result = await updateMutation({ variables: { id, input } });
      return result.data?.updateSprint;
    },
    [updateMutation]
  );

  const start = useCallback(
    async (id: string, goal?: string) => {
      const result = await startMutation({ variables: { id, goal } });
      return result.data?.startSprint;
    },
    [startMutation]
  );

  const complete = useCallback(
    async (id: string, input: CompleteSprintInput) => {
      const result = await completeMutation({ variables: { id, input } });
      return result.data?.completeSprint;
    },
    [completeMutation]
  );

  return {
    create,
    creating,
    update,
    updating,
    start,
    starting,
    complete,
    completing,
  };
}

// =============================================================================
// Story Hooks
// =============================================================================

/**
 * Hook for fetching a single story with details
 */
export function useStory(storyId: string | undefined) {
  const { data, loading, error, refetch } = useQuery(GET_STORY, {
    variables: { id: storyId },
    skip: !storyId,
  });

  // Subscribe to story updates
  useSubscription(SUBSCRIBE_TO_STORY_UPDATES, {
    variables: { storyId },
    skip: !storyId,
  });

  return {
    story: data?.story,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for listing stories with filters
 */
export function useStories(
  projectId: string,
  options?: {
    filter?: StoryFilter;
    pageSize?: number;
  }
) {
  const { filter, pageSize = 50 } = options ?? {};

  const { data, loading, error, fetchMore, refetch } = useQuery(LIST_STORIES, {
    variables: {
      projectId,
      filter,
      pagination: { first: pageSize },
    },
    skip: !projectId,
    notifyOnNetworkStatusChange: true,
  });

  const loadMore = useCallback(() => {
    if (!data?.stories?.pageInfo?.hasNextPage) return;

    fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: data.stories.pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    stories: data?.stories?.edges?.map((e: unknown) => e.node) ?? [],
    pageInfo: data?.stories?.pageInfo,
    loading,
    error,
    loadMore,
    refetch,
  };
}

/**
 * Hook for sprint board
 */
export function useSprintBoard(sprintId: string) {
  const [boardColumns, setBoardColumns] = useAtom(boardColumnsAtom);
  const updateStoryStatus = useSetAtom(updateStoryStatusAction);

  const { loading, error, refetch } = useQuery(GET_SPRINT_BOARD, {
    variables: { sprintId },
    skip: !sprintId,
    onCompleted: (data) => {
      if (data?.sprintBoard?.columns) {
        setBoardColumns(data.sprintBoard.columns);
      }
    },
  });

  // Subscribe to board changes
  useSubscription(SUBSCRIBE_TO_BOARD_CHANGES, {
    variables: { sprintId },
    skip: !sprintId,
    onData: ({ data }) => {
      const change = data.data?.boardChanged;
      if (change?.story) {
        updateStoryStatus({
          storyId: change.story.id,
          status: change.story.status,
        });
      }
    },
  });

  return {
    columns: boardColumns,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for backlog management
 */
export function useBacklog(projectId: string, pageSize: number = 50) {
  const { data, loading, error, fetchMore, refetch } = useQuery(GET_BACKLOG, {
    variables: { projectId, pagination: { first: pageSize } },
    skip: !projectId,
  });

  const loadMore = useCallback(() => {
    const pageInfo = data?.backlog?.stories?.pageInfo;
    if (!pageInfo?.hasNextPage) return;

    fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    stories: data?.backlog?.stories?.edges?.map((e: unknown) => e.node) ?? [],
    epics: data?.backlog?.epics ?? [],
    statistics: data?.backlog?.statistics,
    pageInfo: data?.backlog?.stories?.pageInfo,
    loading,
    error,
    loadMore,
    refetch,
  };
}

/**
 * Hook for story mutations
 */
export function useStoryMutations(projectId: string) {
  const [storiesMap, setStoriesMap] = useAtom(storiesMapAtom);
  const updateStoryStatus = useSetAtom(updateStoryStatusAction);
  const moveStory = useSetAtom(moveStoryToSprintAction);

  const [createMutation, { loading: creating }] = useMutation(CREATE_STORY);
  const [updateMutation, { loading: updating }] = useMutation(UPDATE_STORY);
  const [deleteMutation, { loading: deleting }] = useMutation(DELETE_STORY);
  const [moveMutation, { loading: moving }] = useMutation(MOVE_STORY);
  const [updateStatusMutation, { loading: updatingStatus }] = useMutation(UPDATE_STORY_STATUS);
  const [assignMutation, { loading: assigning }] = useMutation(ASSIGN_STORY);
  const [addCommentMutation, { loading: addingComment }] = useMutation(ADD_STORY_COMMENT);
  const [logTimeMutation, { loading: loggingTime }] = useMutation(LOG_STORY_TIME);
  const [createSubtaskMutation, { loading: creatingSubtask }] = useMutation(CREATE_SUBTASK);
  const [toggleSubtaskMutation] = useMutation(TOGGLE_SUBTASK);

  const create = useCallback(
    async (input: CreateStoryInput) => {
      const result = await createMutation({ variables: { projectId, input } });
      const story = result.data?.createStory;
      if (story) {
        setStoriesMap((prev) => ({ ...prev, [story.id]: story }));
      }
      return story;
    },
    [projectId, createMutation, setStoriesMap]
  );

  const update = useCallback(
    async (id: string, input: UpdateStoryInput) => {
      const result = await updateMutation({ variables: { id, input } });
      const story = result.data?.updateStory;
      if (story) {
        setStoriesMap((prev) => ({ ...prev, [story.id]: story }));
      }
      return story;
    },
    [updateMutation, setStoriesMap]
  );

  const remove = useCallback(
    async (id: string) => {
      const result = await deleteMutation({ variables: { id } });
      if (result.data?.deleteStory?.success) {
        setStoriesMap((prev) => {
          const next = { ...prev };
          delete next[id];
          return next;
        });
      }
      return result.data?.deleteStory?.success;
    },
    [deleteMutation, setStoriesMap]
  );

  const move = useCallback(
    async (id: string, input: MoveStoryInput) => {
      const result = await moveMutation({ variables: { id, input } });
      const story = result.data?.moveStory;
      if (story) {
        moveStory({ storyId: id, sprintId: input.sprintId });
        setStoriesMap((prev) => ({ ...prev, [story.id]: story }));
      }
      return story;
    },
    [moveMutation, moveStory, setStoriesMap]
  );

  const setStatus = useCallback(
    async (id: string, status: string) => {
      const result = await updateStatusMutation({ variables: { id, status } });
      const story = result.data?.updateStoryStatus;
      if (story) {
        updateStoryStatus({ storyId: id, status });
        setStoriesMap((prev) => ({ ...prev, [story.id]: story }));
      }
      return story;
    },
    [updateStatusMutation, updateStoryStatus, setStoriesMap]
  );

  const assign = useCallback(
    async (id: string, assigneeId: string | null) => {
      const result = await assignMutation({ variables: { id, assigneeId } });
      const story = result.data?.assignStory;
      if (story) {
        setStoriesMap((prev) => ({ ...prev, [story.id]: story }));
      }
      return story;
    },
    [assignMutation, setStoriesMap]
  );

  const addComment = useCallback(
    async (storyId: string, input: AddCommentInput) => {
      const result = await addCommentMutation({ variables: { storyId, input } });
      return result.data?.addStoryComment;
    },
    [addCommentMutation]
  );

  const logTime = useCallback(
    async (storyId: string, input: LogTimeInput) => {
      const result = await logTimeMutation({ variables: { storyId, input } });
      return result.data?.logStoryTime;
    },
    [logTimeMutation]
  );

  const createSubtask = useCallback(
    async (storyId: string, input: CreateSubtaskInput) => {
      const result = await createSubtaskMutation({ variables: { storyId, input } });
      return result.data?.createSubtask;
    },
    [createSubtaskMutation]
  );

  const toggleSubtask = useCallback(
    async (id: string) => {
      const result = await toggleSubtaskMutation({ variables: { id } });
      return result.data?.toggleSubtask;
    },
    [toggleSubtaskMutation]
  );

  return {
    create,
    creating,
    update,
    updating,
    remove,
    deleting,
    move,
    moving,
    setStatus,
    updatingStatus,
    assign,
    assigning,
    addComment,
    addingComment,
    logTime,
    loggingTime,
    createSubtask,
    creatingSubtask,
    toggleSubtask,
  };
}

// =============================================================================
// Pull Request Hooks
// =============================================================================

/**
 * Hook for fetching a single PR
 */
export function usePullRequest(prId: string | undefined) {
  const { data, loading, error, refetch } = useQuery(GET_PULL_REQUEST, {
    variables: { id: prId },
    skip: !prId,
  });

  // Subscribe to PR updates
  useSubscription(SUBSCRIBE_TO_PR_UPDATES, {
    variables: { pullRequestId: prId },
    skip: !prId,
  });

  return {
    pullRequest: data?.pullRequest,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for listing pull requests
 */
export function usePullRequests(
  projectId: string,
  options?: {
    filter?: PullRequestFilter;
    pageSize?: number;
  }
) {
  const { filter, pageSize = 20 } = options ?? {};
  const [pullRequests, setPullRequests] = useAtom(pullRequestsAtom);

  const { data, loading, error, fetchMore, refetch } = useQuery(LIST_PULL_REQUESTS, {
    variables: {
      projectId,
      filter,
      pagination: { first: pageSize },
    },
    skip: !projectId,
    onCompleted: (data) => {
      const prs = data?.pullRequests?.edges?.map((e: unknown) => e.node) ?? [];
      setPullRequests(prs);
    },
  });

  const loadMore = useCallback(() => {
    if (!data?.pullRequests?.pageInfo?.hasNextPage) return;

    fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: data.pullRequests.pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    pullRequests,
    pageInfo: data?.pullRequests?.pageInfo,
    loading,
    error,
    loadMore,
    refetch,
  };
}

/**
 * Hook for pull request mutations
 */
export function usePullRequestMutations(projectId: string) {
  const [createMutation, { loading: creating }] = useMutation(CREATE_PULL_REQUEST);
  const [updateMutation, { loading: updating }] = useMutation(UPDATE_PULL_REQUEST);
  const [mergeMutation, { loading: merging }] = useMutation(MERGE_PULL_REQUEST);
  const [closeMutation, { loading: closing }] = useMutation(CLOSE_PULL_REQUEST);
  const [requestReviewMutation, { loading: requestingReview }] = useMutation(REQUEST_PR_REVIEW);
  const [submitReviewMutation, { loading: submittingReview }] = useMutation(SUBMIT_PR_REVIEW);

  const create = useCallback(
    async (input: CreatePullRequestInput) => {
      const result = await createMutation({ variables: { projectId, input } });
      return result.data?.createPullRequest;
    },
    [projectId, createMutation]
  );

  const update = useCallback(
    async (id: string, input: UpdatePullRequestInput) => {
      const result = await updateMutation({ variables: { id, input } });
      return result.data?.updatePullRequest;
    },
    [updateMutation]
  );

  const merge = useCallback(
    async (id: string, method: 'merge' | 'squash' | 'rebase') => {
      const result = await mergeMutation({ variables: { id, method } });
      return result.data?.mergePullRequest;
    },
    [mergeMutation]
  );

  const close = useCallback(
    async (id: string) => {
      const result = await closeMutation({ variables: { id } });
      return result.data?.closePullRequest;
    },
    [closeMutation]
  );

  const requestReview = useCallback(
    async (pullRequestId: string, reviewerIds: string[]) => {
      const result = await requestReviewMutation({
        variables: { pullRequestId, reviewerIds },
      });
      return result.data?.requestPRReview;
    },
    [requestReviewMutation]
  );

  const submitReview = useCallback(
    async (pullRequestId: string, input: SubmitReviewInput) => {
      const result = await submitReviewMutation({
        variables: { pullRequestId, input },
      });
      return result.data?.submitPRReview;
    },
    [submitReviewMutation]
  );

  return {
    create,
    creating,
    update,
    updating,
    merge,
    merging,
    close,
    closing,
    requestReview,
    requestingReview,
    submitReview,
    submittingReview,
  };
}

// =============================================================================
// Feature Flag Hooks
// =============================================================================

/**
 * Hook for feature flags
 */
export function useFeatureFlags(projectId: string, filter?: FeatureFlagFilter) {
  const [featureFlags, setFeatureFlags] = useAtom(featureFlagsAtom);
  const toggleFlag = useSetAtom(toggleFeatureFlagAction);

  const { loading, error, refetch } = useQuery(LIST_FEATURE_FLAGS, {
    variables: { projectId, filter },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.featureFlags) {
        setFeatureFlags(data.featureFlags);
      }
    },
  });

  // Subscribe to feature flag changes
  useSubscription(SUBSCRIBE_TO_FEATURE_FLAG_CHANGES, {
    variables: { projectId },
    skip: !projectId,
    onData: ({ data }) => {
      const change = data.data?.featureFlagChanged;
      if (change?.flag) {
        setFeatureFlags((prev) => {
          const index = prev.findIndex((f) => f.id === change.flag.id);
          if (change.type === 'DELETED') {
            return prev.filter((f) => f.id !== change.flag.id);
          } else if (index >= 0) {
            const next = [...prev];
            next[index] = change.flag;
            return next;
          } else {
            return [...prev, change.flag];
          }
        });
      }
    },
  });

  return {
    flags: featureFlags,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for feature flag mutations
 */
export function useFeatureFlagMutations(projectId: string) {
  const toggleFlagAction = useSetAtom(toggleFeatureFlagAction);

  const [createMutation, { loading: creating }] = useMutation(CREATE_FEATURE_FLAG);
  const [updateMutation, { loading: updating }] = useMutation(UPDATE_FEATURE_FLAG);
  const [toggleMutation, { loading: toggling }] = useMutation(TOGGLE_FEATURE_FLAG);
  const [deleteMutation, { loading: deleting }] = useMutation(DELETE_FEATURE_FLAG);

  const create = useCallback(
    async (input: CreateFeatureFlagInput) => {
      const result = await createMutation({ variables: { projectId, input } });
      return result.data?.createFeatureFlag;
    },
    [projectId, createMutation]
  );

  const update = useCallback(
    async (id: string, input: UpdateFeatureFlagInput) => {
      const result = await updateMutation({ variables: { id, input } });
      return result.data?.updateFeatureFlag;
    },
    [updateMutation]
  );

  const toggle = useCallback(
    async (id: string, enabled: boolean) => {
      const result = await toggleMutation({ variables: { id, enabled } });
      if (result.data?.toggleFeatureFlag) {
        toggleFlagAction(id);
      }
      return result.data?.toggleFeatureFlag;
    },
    [toggleMutation, toggleFlagAction]
  );

  const remove = useCallback(
    async (id: string) => {
      const result = await deleteMutation({ variables: { id } });
      return result.data?.deleteFeatureFlag?.success;
    },
    [deleteMutation]
  );

  return {
    create,
    creating,
    update,
    updating,
    toggle,
    toggling,
    remove,
    deleting,
  };
}

// =============================================================================
// Deployment Hooks
// =============================================================================

/**
 * Hook for deployments
 */
export function useDeployments(
  projectId: string,
  options?: { environment?: string; pageSize?: number }
) {
  const { environment, pageSize = 20 } = options ?? {};
  const [deployments, setDeployments] = useAtom(deploymentsAtom);

  const { data, loading, error, fetchMore, refetch } = useQuery(LIST_DEPLOYMENTS, {
    variables: {
      projectId,
      environment,
      pagination: { first: pageSize },
    },
    skip: !projectId,
    onCompleted: (data) => {
      const deps = data?.deployments?.edges?.map((e: unknown) => e.node) ?? [];
      setDeployments(deps);
    },
  });

  const loadMore = useCallback(() => {
    if (!data?.deployments?.pageInfo?.hasNextPage) return;

    fetchMore({
      variables: {
        pagination: {
          first: pageSize,
          after: data.deployments.pageInfo.endCursor,
        },
      },
    });
  }, [data, fetchMore, pageSize]);

  return {
    deployments,
    pageInfo: data?.deployments?.pageInfo,
    loading,
    error,
    loadMore,
    refetch,
  };
}

/**
 * Hook for deployment mutations
 */
export function useDeploymentMutations(projectId: string) {
  const [triggerMutation, { loading: triggering }] = useMutation(TRIGGER_DEPLOYMENT);
  const [rollbackMutation, { loading: rollingBack }] = useMutation(ROLLBACK_DEPLOYMENT);
  const [cancelMutation, { loading: canceling }] = useMutation(CANCEL_DEPLOYMENT);

  const trigger = useCallback(
    async (input: TriggerDeploymentInput) => {
      const result = await triggerMutation({ variables: { projectId, input } });
      return result.data?.triggerDeployment;
    },
    [projectId, triggerMutation]
  );

  const rollback = useCallback(
    async (id: string, reason?: string) => {
      const result = await rollbackMutation({ variables: { id, reason } });
      return result.data?.rollbackDeployment;
    },
    [rollbackMutation]
  );

  const cancel = useCallback(
    async (id: string) => {
      const result = await cancelMutation({ variables: { id } });
      return result.data?.cancelDeployment?.success;
    },
    [cancelMutation]
  );

  return {
    trigger,
    triggering,
    rollback,
    rollingBack,
    cancel,
    canceling,
  };
}

/**
 * Hook for subscribing to deployment status
 */
export function useDeploymentStatus(deploymentId: string | undefined) {
  const { data } = useSubscription(SUBSCRIBE_TO_DEPLOYMENT_STATUS, {
    variables: { deploymentId },
    skip: !deploymentId,
  });

  return data?.deploymentStatusChanged;
}

// =============================================================================
// Velocity Hooks
// =============================================================================

/**
 * Hook for velocity data
 */
export function useVelocity(projectId: string, sprintCount: number = 10) {
  const [velocityData, setVelocityData] = useAtom(velocityDataAtom);

  const { loading, error, refetch } = useQuery(GET_VELOCITY_DATA, {
    variables: { projectId, sprintCount },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.velocityData) {
        setVelocityData(data.velocityData);
      }
    },
  });

  return {
    data: velocityData,
    loading,
    error,
    refetch,
  };
}

// =============================================================================
// Epic Hooks
// =============================================================================

/**
 * Hook for epics
 */
export function useEpics(projectId: string, filter?: EpicFilter) {
  const [epics, setEpics] = useAtom(epicsAtom);

  const { loading, error, refetch } = useQuery(LIST_EPICS, {
    variables: { projectId, filter },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.epics) {
        setEpics(data.epics);
      }
    },
  });

  return {
    epics,
    loading,
    error,
    refetch,
  };
}

/**
 * Hook for epic mutations
 */
export function useEpicMutations(projectId: string) {
  const [createMutation, { loading: creating }] = useMutation(CREATE_EPIC);
  const [updateMutation, { loading: updating }] = useMutation(UPDATE_EPIC);
  const [deleteMutation, { loading: deleting }] = useMutation(DELETE_EPIC);

  const create = useCallback(
    async (input: CreateEpicInput) => {
      const result = await createMutation({ variables: { projectId, input } });
      return result.data?.createEpic;
    },
    [projectId, createMutation]
  );

  const update = useCallback(
    async (id: string, input: UpdateEpicInput) => {
      const result = await updateMutation({ variables: { id, input } });
      return result.data?.updateEpic;
    },
    [updateMutation]
  );

  const remove = useCallback(
    async (id: string) => {
      const result = await deleteMutation({ variables: { id } });
      return result.data?.deleteEpic;
    },
    [deleteMutation]
  );

  return {
    create,
    creating,
    update,
    updating,
    remove,
    deleting,
  };
}
