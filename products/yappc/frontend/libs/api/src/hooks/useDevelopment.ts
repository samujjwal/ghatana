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

/* eslint-disable @typescript-eslint/no-unsafe-assignment,@typescript-eslint/no-unsafe-member-access,@typescript-eslint/no-unsafe-call,@typescript-eslint/no-unsafe-return -- Apollo hook refactor pending */

import {
  useQuery,
  useLazyQuery,
  useMutation,
  useSubscription,
} from '@apollo/client/react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { useCallback, useMemo } from 'react';

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
} from 'yappc-state';
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
} from '../graphql/operations/development.operations';

// =============================================================================
// Sprint Hooks
// =============================================================================

/**
 * Hook for fetching a single sprint
 */
export function useSprint(sprintId: string | undefined) {
  type SprintData = { sprint?: Record<string, unknown> };
  const runSprintQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => {
    data?: SprintData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  const { data, loading, error, refetch } = runSprintQuery(GET_SPRINT, {
    variables: { id: sprintId },
    skip: !sprintId,
  });

  return {
    sprint: data?.sprint,
    loading,
    error,
    refetch: () => refetch(),
  };
}

/**
 * Hook for listing sprints
 */
export function useSprints(
  projectId: string,
  status?: 'planned' | 'active' | 'completed'
) {
  type Sprint = { [key: string]: unknown };
  type SprintsQueryResult = { sprints?: Sprint[] };
  type SprintsQueryState = {
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  const runSprintsQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => SprintsQueryState;
  const [sprints, setSprints] = useAtom(sprintsAtom);

  const { loading, error, refetch } = runSprintsQuery(LIST_SPRINTS, {
    variables: { projectId, status },
    skip: !projectId,
    onCompleted: (queryData: SprintsQueryResult) => {
      if (queryData?.sprints) {
        setSprints(queryData.sprints);
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
  type Sprint = { id?: string; [key: string]: unknown };
  type ActiveSprintQueryResult = { activeSprint?: Sprint };
  type BoardColumn = { [key: string]: unknown };
  type ActiveSprintQueryState = {
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type SprintUpdatePayload = { sprintUpdated?: { sprint?: Sprint } };
  type SprintSubscriptionState = { data?: SprintUpdatePayload };
  const runActiveSprintQuery = useQuery as unknown as (
    query: unknown,
    options: unknown
  ) => ActiveSprintQueryState;
  const runSprintSubscription = useSubscription as unknown as (
    subscription: unknown,
    options: unknown
  ) => SprintSubscriptionState;

  const [activeSprint, setActiveSprint] = useAtom(activeSprintAtom);
  const [boardColumns, setBoardColumns] = useAtom(boardColumnsAtom);

  const { loading, error, refetch } = runActiveSprintQuery(GET_ACTIVE_SPRINT, {
    variables: { projectId },
    skip: !projectId,
    onCompleted: (queryData: ActiveSprintQueryResult) => {
      if (queryData?.activeSprint) {
        setActiveSprint(queryData.activeSprint);
      }
    },
  });

  // Subscribe to sprint updates
  runSprintSubscription(SUBSCRIBE_TO_SPRINT_UPDATES, {
    variables: { sprintId: (activeSprint as Sprint)?.id },
    skip: !(activeSprint as Sprint)?.id,
    onData: ({ data }: { data: SprintSubscriptionState }) => {
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
  type SprintMutationResult = { [key: string]: unknown };
  type MutationState = { loading: boolean };
  const runSprintMutation = useMutation as unknown as (
    mutation: unknown
  ) => [
    (args: unknown) => Promise<{ data?: SprintMutationResult }>,
    MutationState,
  ];

  const [createMutationValue, { loading: creating }] =
    runSprintMutation(CREATE_SPRINT);
  const createMutation = createMutationValue as (
    args: unknown
  ) => Promise<{ data?: SprintMutationResult }>;
  const [updateMutationValue, { loading: updating }] =
    runSprintMutation(UPDATE_SPRINT);
  const updateMutation = updateMutationValue as (
    args: unknown
  ) => Promise<{ data?: SprintMutationResult }>;
  const [startMutationValue, { loading: starting }] =
    runSprintMutation(START_SPRINT);
  const startMutation = startMutationValue as (
    args: unknown
  ) => Promise<{ data?: SprintMutationResult }>;
  const [completeMutationValue, { loading: completing }] =
    runSprintMutation(COMPLETE_SPRINT);
  const completeMutation = completeMutationValue as (
    args: unknown
  ) => Promise<{ data?: SprintMutationResult }>;

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

/**
 * Hook for fetching a single story with details
 */
export function useStory(storyId: string | undefined) {
  type StoryQueryData = { story?: Record<string, unknown> };
  type StoryQueryState = {
    data?: StoryQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type StorySubscriptionState = { data?: { storyUpdates?: Record<string, unknown> } };
  const runStoryQuery = useQuery as unknown as (query: unknown, options: unknown) => StoryQueryState;
  const runStorySubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => StorySubscriptionState;

  const { data, loading, error, refetch } = runStoryQuery(GET_STORY, {
    variables: { id: storyId },
    skip: !storyId,
  });

  // Subscribe to story updates
  runStorySubscription(SUBSCRIBE_TO_STORY_UPDATES, {
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
  type StoriesQueryData = {
    stories?: {
      edges?: Array<{ node?: Record<string, unknown> }>;
      pageInfo?: { hasNextPage?: boolean; endCursor?: string };
    };
  };
  type StoriesQueryState = {
    data?: StoriesQueryData;
    loading: boolean;
    error?: unknown;
    fetchMore: (opts: unknown) => Promise<unknown>;
    refetch: () => Promise<unknown>;
  };
  const runStoriesQuery = useQuery as unknown as (query: unknown, options: unknown) => StoriesQueryState;

  const { filter, pageSize = 50 } = options ?? {};

  const { data, loading, error, fetchMore, refetch } = runStoriesQuery(LIST_STORIES, {
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
  type BoardQueryData = { sprintBoard?: { columns?: Record<string, unknown> } };
  type BoardQueryState = {
    data?: BoardQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type BoardSubscriptionState = { data?: { boardChanged?: Record<string, unknown> } };
  const runBoardQuery = useQuery as unknown as (query: unknown, options: unknown) => BoardQueryState;
  const runBoardSubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => BoardSubscriptionState;

  const [boardColumns, setBoardColumns] = useAtom(boardColumnsAtom);
  const updateStoryStatus = useSetAtom(updateStoryStatusAction);

  const { loading, error, refetch } = runBoardQuery(GET_SPRINT_BOARD, {
    variables: { sprintId },
    skip: !sprintId,
    onCompleted: (data) => {
      if (data?.sprintBoard?.columns) {
        setBoardColumns(data.sprintBoard.columns);
      }
    },
  });

  // Subscribe to board changes
  runBoardSubscription(SUBSCRIBE_TO_BOARD_CHANGES, {
    variables: { sprintId },
    skip: !sprintId,
    onData: ({ data: subData }: { data: BoardSubscriptionState }) => {
      const change = (subData?.data?.boardChanged) as Record<string, unknown> | undefined;
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
  type BacklogQueryData = {
    backlog?: {
      stories?: {
        edges?: Array<{ node?: Record<string, unknown> }>;
        pageInfo?: { hasNextPage?: boolean; endCursor?: string };
      };
      epics?: Record<string, unknown>[];
      statistics?: Record<string, unknown>;
    };
  };
  type BacklogQueryState = {
    data?: BacklogQueryData;
    loading: boolean;
    error?: unknown;
    fetchMore: (opts: unknown) => Promise<unknown>;
    refetch: () => Promise<unknown>;
  };
  const runBacklogQuery = useQuery as unknown as (query: unknown, options: unknown) => BacklogQueryState;

  const { data, loading, error, fetchMore, refetch } = runBacklogQuery(GET_BACKLOG, {
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
  type _StoryMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown }; loading: boolean };
  const runStoryMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const [storiesMap, setStoriesMap] = useAtom(storiesMapAtom);
  const updateStoryStatus = useSetAtom(updateStoryStatusAction);
  const moveStory = useSetAtom(moveStoryToSprintAction);

  const [createMutation, { loading: creating }] = runStoryMutation(CREATE_STORY);
  const [updateMutation, { loading: updating }] = runStoryMutation(UPDATE_STORY);
  const [deleteMutation, { loading: deleting }] = runStoryMutation(DELETE_STORY);
  const [moveMutation, { loading: moving }] = runStoryMutation(MOVE_STORY);
  const [updateStatusMutation, { loading: updatingStatus }] =
    runStoryMutation(UPDATE_STORY_STATUS);
  const [assignMutation, { loading: assigning }] = runStoryMutation(ASSIGN_STORY);
  const [addCommentMutation, { loading: addingComment }] =
    runStoryMutation(ADD_STORY_COMMENT);
  const [logTimeMutation, { loading: loggingTime }] =
    runStoryMutation(LOG_STORY_TIME);
  const [createSubtaskMutation, { loading: creatingSubtask }] =
    runStoryMutation(CREATE_SUBTASK);
  const [toggleSubtaskMutation] = runStoryMutation(TOGGLE_SUBTASK);

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
      const result = await addCommentMutation({
        variables: { storyId, input },
      });
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
      const result = await createSubtaskMutation({
        variables: { storyId, input },
      });
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
  type PRQueryData = { pullRequest?: Record<string, unknown> };
  type PRQueryState = {
    data?: PRQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  type PRSubscriptionState = { data?: { prUpdates?: Record<string, unknown> } };
  const runPRQuery = useQuery as unknown as (query: unknown, options: unknown) => PRQueryState;
  const runPRSubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => PRSubscriptionState;

  const { data, loading, error, refetch } = runPRQuery(GET_PULL_REQUEST, {
    variables: { id: prId },
    skip: !prId,
  });

  // Subscribe to PR updates
  runPRSubscription(SUBSCRIBE_TO_PR_UPDATES, {
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
  type PRsQueryData = {
    pullRequests?: {
      edges?: Array<{ node?: Record<string, unknown> }>;
      pageInfo?: { hasNextPage?: boolean; endCursor?: string };
    };
  };
  type PRsQueryState = {
    data?: PRsQueryData;
    loading: boolean;
    error?: unknown;
    fetchMore: (opts: unknown) => Promise<unknown>;
    refetch: () => Promise<unknown>;
  };
  const runPRsQuery = useQuery as unknown as (query: unknown, options: unknown) => PRsQueryState;

  const { filter, pageSize = 20 } = options ?? {};
  const [pullRequests, setPullRequests] = useAtom(pullRequestsAtom);

  const { data, loading, error, fetchMore, refetch } = runPRsQuery(
    LIST_PULL_REQUESTS,
    {
      variables: {
        projectId,
        filter,
        pagination: { first: pageSize },
      },
      skip: !projectId,
      onCompleted: (data) => {
        const prs =
          data?.pullRequests?.edges?.map((e: unknown) => e.node) ?? [];
        setPullRequests(prs);
      },
    }
  );

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
  type _PRMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown }; loading: boolean };
  const runPRMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const [createMutation, { loading: creating }] =
    runPRMutation(CREATE_PULL_REQUEST);
  const [updateMutation, { loading: updating }] =
    runPRMutation(UPDATE_PULL_REQUEST);
  const [mergeMutation, { loading: merging }] = runPRMutation(MERGE_PULL_REQUEST);
  const [closeMutation, { loading: closing }] = runPRMutation(CLOSE_PULL_REQUEST);
  const [requestReviewMutation, { loading: requestingReview }] =
    runPRMutation(REQUEST_PR_REVIEW);
  const [submitReviewMutation, { loading: submittingReview }] =
    runPRMutation(SUBMIT_PR_REVIEW);

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
  type FFQueryData = {
    featureFlags?: {
      edges?: Array<{ node?: Record<string, unknown> }>;
      pageInfo?: { hasNextPage?: boolean; endCursor?: string };
    };
  };
  type FFQueryState = {
    data?: FFQueryData;
    loading: boolean;
    error?: unknown;
    refetch: () => Promise<unknown>;
  };
  const runFFQuery = useQuery as unknown as (query: unknown, options: unknown) => FFQueryState;

  const [featureFlags, setFeatureFlags] = useAtom(featureFlagsAtom);
  const toggleFlag = useSetAtom(toggleFeatureFlagAction);

  const { loading, error, refetch } = runFFQuery(LIST_FEATURE_FLAGS, {
    variables: { projectId, filter },
    skip: !projectId,
    onCompleted: (data) => {
      if (data?.featureFlags) {
        setFeatureFlags(data.featureFlags);
      }
    },
  });

  // Subscribe to feature flag changes
  type FFSubscriptionState = { data?: { featureFlagChanged?: Record<string, unknown> } };
  const runFFSubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => FFSubscriptionState;

  runFFSubscription(SUBSCRIBE_TO_FEATURE_FLAG_CHANGES, {
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
  type _FFMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown }; loading: boolean };
  const runFFMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const toggleFlagAction = useSetAtom(toggleFeatureFlagAction);

  const [createMutation, { loading: creating }] =
    runFFMutation(CREATE_FEATURE_FLAG);
  const [updateMutation, { loading: updating }] =
    runFFMutation(UPDATE_FEATURE_FLAG);
  const [toggleMutation, { loading: toggling }] =
    runFFMutation(TOGGLE_FEATURE_FLAG);
  const [deleteMutation, { loading: deleting }] =
    runFFMutation(DELETE_FEATURE_FLAG);

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
  type DeploymentsQueryData = {
    deployments?: {
      edges?: Array<{ node?: Record<string, unknown> }>;
      pageInfo?: { hasNextPage?: boolean; endCursor?: string };
    };
  };
  type DeploymentsQueryState = {
    data?: DeploymentsQueryData;
    loading: boolean;
    error?: unknown;
    fetchMore: (opts: unknown) => Promise<unknown>;
    refetch: () => Promise<unknown>;
  };
  const runDeploymentsQuery = useQuery as unknown as (query: unknown, options: unknown) => DeploymentsQueryState;

  const { environment, pageSize = 20 } = options ?? {};
  const [deployments, setDeployments] = useAtom(deploymentsAtom);

  const { data, loading, error, fetchMore, refetch } = runDeploymentsQuery(
    LIST_DEPLOYMENTS,
    {
      variables: {
        projectId,
        environment,
        pagination: { first: pageSize },
      },
      skip: !projectId,
      onCompleted: (data) => {
        const deps =
          data?.deployments?.edges?.map((e: unknown) => e.node) ?? [];
        setDeployments(deps);
      },
    }
  );

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
  type _DeploymentMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown }; loading: boolean };
  const runDeploymentMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const [triggerMutation, { loading: triggering }] =
    runDeploymentMutation(TRIGGER_DEPLOYMENT);
  const [rollbackMutation, { loading: rollingBack }] =
    runDeploymentMutation(ROLLBACK_DEPLOYMENT);
  const [cancelMutation, { loading: canceling }] =
    runDeploymentMutation(CANCEL_DEPLOYMENT);

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
  type DeploymentStatusState = { data?: { deploymentStatusChanged?: Record<string, unknown> } };
  const runDeploymentStatusSubscription = useSubscription as unknown as (subscription: unknown, options: unknown) => DeploymentStatusState;

  const { data } = runDeploymentStatusSubscription(SUBSCRIBE_TO_DEPLOYMENT_STATUS, {
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
  type VelocityQueryData = { velocityData?: Record<string, unknown> };
  type VelocityQueryState = {
    data?: VelocityQueryData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: VelocityQueryData }>;
  };
  const runVelocityQuery = useQuery as unknown as (query: unknown, options: unknown) => VelocityQueryState;

  const [velocityData, setVelocityData] = useAtom(velocityDataAtom);

  const { loading, error, refetch } = runVelocityQuery(GET_VELOCITY_DATA, {
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
  type EpicsQueryData = { epics?: Record<string, unknown>[] };
  type EpicsQueryState = {
    data?: EpicsQueryData;
    loading: boolean;
    error?: unknown;
    refetch: (opts?: unknown) => Promise<{ data?: EpicsQueryData }>;
  };
  const runEpicsQuery = useQuery as unknown as (query: unknown, options: unknown) => EpicsQueryState;

  const [epics, setEpics] = useAtom(epicsAtom);

  const { loading, error, refetch } = runEpicsQuery(LIST_EPICS, {
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
  type _EpicMutationResult = { [key: string]: unknown };
  type MutationState = { data?: { [key: string]: unknown }; loading: boolean };
  const runEpicMutation = useMutation as unknown as (
    mutation: unknown
  ) => [(args: unknown) => Promise<MutationState>, { loading: boolean }];

  const [createMutation, { loading: creating }] = runEpicMutation(CREATE_EPIC);
  const [updateMutation, { loading: updating }] = runEpicMutation(UPDATE_EPIC);
  const [deleteMutation, { loading: deleting }] = runEpicMutation(DELETE_EPIC);

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
