/**
 * Development Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * development phase including sprint management, story tracking,
 * pull requests, feature flags, and deployments.
 *
 * @doc.type api
 * @doc.purpose Development Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const SPRINT_FRAGMENT = gql`
  fragment SprintFields on Sprint {
    id
    name
    goal
    startDate
    endDate
    status
    projectId
    velocity {
      planned
      completed
      remaining
    }
    statistics {
      totalStories
      completedStories
      totalPoints
      completedPoints
      burndownData {
        date
        ideal
        actual
      }
    }
  }
`;

export const STORY_FRAGMENT = gql`
  fragment StoryFields on Story {
    id
    title
    description
    type
    status
    priority
    points
    sprintId
    epicId
    assignee {
      id
      name
      email
      avatar
    }
    reporter {
      id
      name
      email
      avatar
    }
    labels {
      id
      name
      color
    }
    createdAt
    updatedAt
    dueDate
    blockedBy {
      id
      title
    }
    blocking {
      id
      title
    }
    subtasks {
      id
      title
      completed
      assigneeId
    }
    attachments {
      id
      name
      url
      type
      size
      uploadedAt
    }
    timeTracking {
      estimated
      logged
      remaining
    }
  }
`;

export const STORY_COMMENT_FRAGMENT = gql`
  fragment StoryCommentFields on StoryComment {
    id
    storyId
    author {
      id
      name
      email
      avatar
    }
    content
    createdAt
    updatedAt
    reactions {
      emoji
      count
      users {
        id
        name
      }
    }
    mentions {
      id
      name
    }
    attachments {
      id
      name
      url
    }
  }
`;

export const PULL_REQUEST_FRAGMENT = gql`
  fragment PullRequestFields on PullRequest {
    id
    number
    title
    description
    status
    author {
      id
      name
      avatar
    }
    sourceBranch
    targetBranch
    createdAt
    updatedAt
    mergedAt
    closedAt
    reviewers {
      id
      name
      avatar
      status
      reviewedAt
    }
    checks {
      name
      status
      description
      url
      completedAt
    }
    comments {
      total
      resolved
    }
    additions
    deletions
    changedFiles
    linkedStories {
      id
      title
    }
    labels {
      id
      name
      color
    }
    mergeable
    draft
  }
`;

export const FEATURE_FLAG_FRAGMENT = gql`
  fragment FeatureFlagFields on FeatureFlag {
    id
    key
    name
    description
    type
    defaultValue
    enabled
    environments {
      name
      enabled
      value
      percentage
      rules {
        id
        attribute
        operator
        value
        variation
      }
    }
    tags
    owner {
      id
      name
    }
    createdAt
    updatedAt
    lastEvaluatedAt
    evaluationStats {
      total
      trueCount
      falseCount
    }
  }
`;

export const DEPLOYMENT_FRAGMENT = gql`
  fragment DeploymentFields on Deployment {
    id
    version
    environment
    status
    startedAt
    completedAt
    duration
    triggeredBy {
      id
      name
      avatar
    }
    commit {
      sha
      message
      author
    }
    pullRequestId
    artifacts {
      name
      url
      size
    }
    logs {
      timestamp
      level
      message
    }
    rollbackOf
    rolledBackBy
    healthCheck {
      status
      lastChecked
      details
    }
  }
`;

export const EPIC_FRAGMENT = gql`
  fragment EpicFields on Epic {
    id
    name
    description
    status
    startDate
    targetDate
    owner {
      id
      name
      avatar
    }
    color
    progress {
      totalStories
      completedStories
      totalPoints
      completedPoints
      percentage
    }
    children {
      id
      title
      status
      points
    }
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_SPRINT = gql`
  ${SPRINT_FRAGMENT}
  
  query GetSprint($id: ID!) {
    sprint(id: $id) {
      ...SprintFields
    }
  }
`;

export const LIST_SPRINTS = gql`
  ${SPRINT_FRAGMENT}
  
  query ListSprints($projectId: ID!, $status: SprintStatus) {
    sprints(projectId: $projectId, status: $status) {
      ...SprintFields
    }
  }
`;

export const GET_ACTIVE_SPRINT = gql`
  ${SPRINT_FRAGMENT}
  ${STORY_FRAGMENT}
  
  query GetActiveSprint($projectId: ID!) {
    activeSprint(projectId: $projectId) {
      ...SprintFields
      stories {
        ...StoryFields
      }
    }
  }
`;

export const GET_STORY = gql`
  ${STORY_FRAGMENT}
  ${STORY_COMMENT_FRAGMENT}
  
  query GetStory($id: ID!) {
    story(id: $id) {
      ...StoryFields
      comments {
        ...StoryCommentFields
      }
      activity {
        id
        type
        actor {
          id
          name
          avatar
        }
        timestamp
        details
      }
    }
  }
`;

export const LIST_STORIES = gql`
  ${STORY_FRAGMENT}
  
  query ListStories(
    $projectId: ID!
    $filter: StoryFilter
    $pagination: PaginationInput
    $sort: SortInput
  ) {
    stories(
      projectId: $projectId
      filter: $filter
      pagination: $pagination
      sort: $sort
    ) {
      edges {
        cursor
        node {
          ...StoryFields
        }
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
        totalCount
      }
    }
  }
`;

export const GET_SPRINT_BOARD = gql`
  ${STORY_FRAGMENT}
  
  query GetSprintBoard($sprintId: ID!) {
    sprintBoard(sprintId: $sprintId) {
      columns {
        id
        name
        status
        wipLimit
        stories {
          ...StoryFields
        }
      }
      swimlanes {
        id
        name
        type
        collapsed
      }
    }
  }
`;

export const GET_BACKLOG = gql`
  ${STORY_FRAGMENT}
  ${EPIC_FRAGMENT}
  
  query GetBacklog($projectId: ID!, $pagination: PaginationInput) {
    backlog(projectId: $projectId, pagination: $pagination) {
      stories {
        edges {
          cursor
          node {
            ...StoryFields
          }
        }
        pageInfo {
          hasNextPage
          endCursor
          totalCount
        }
      }
      epics {
        ...EpicFields
      }
      statistics {
        totalStories
        unestimated
        totalPoints
        byPriority {
          priority
          count
        }
        byType {
          type
          count
        }
      }
    }
  }
`;

export const GET_PULL_REQUEST = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  query GetPullRequest($id: ID!) {
    pullRequest(id: $id) {
      ...PullRequestFields
      diff {
        files {
          path
          status
          additions
          deletions
          patch
        }
      }
      conversations {
        id
        path
        line
        side
        author {
          id
          name
          avatar
        }
        body
        createdAt
        resolved
        replies {
          id
          author {
            id
            name
            avatar
          }
          body
          createdAt
        }
      }
    }
  }
`;

export const LIST_PULL_REQUESTS = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  query ListPullRequests(
    $projectId: ID!
    $filter: PullRequestFilter
    $pagination: PaginationInput
  ) {
    pullRequests(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...PullRequestFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
    }
  }
`;

export const GET_FEATURE_FLAG = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  query GetFeatureFlag($id: ID!) {
    featureFlag(id: $id) {
      ...FeatureFlagFields
      history {
        id
        action
        actor {
          id
          name
        }
        timestamp
        changes {
          field
          oldValue
          newValue
        }
      }
    }
  }
`;

export const LIST_FEATURE_FLAGS = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  query ListFeatureFlags($projectId: ID!, $filter: FeatureFlagFilter) {
    featureFlags(projectId: $projectId, filter: $filter) {
      ...FeatureFlagFields
    }
  }
`;

export const GET_DEPLOYMENT = gql`
  ${DEPLOYMENT_FRAGMENT}
  
  query GetDeployment($id: ID!) {
    deployment(id: $id) {
      ...DeploymentFields
    }
  }
`;

export const LIST_DEPLOYMENTS = gql`
  ${DEPLOYMENT_FRAGMENT}
  
  query ListDeployments(
    $projectId: ID!
    $environment: String
    $pagination: PaginationInput
  ) {
    deployments(
      projectId: $projectId
      environment: $environment
      pagination: $pagination
    ) {
      edges {
        cursor
        node {
          ...DeploymentFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

export const GET_VELOCITY_DATA = gql`
  query GetVelocityData($projectId: ID!, $sprintCount: Int) {
    velocityData(projectId: $projectId, sprintCount: $sprintCount) {
      sprints {
        id
        name
        plannedPoints
        completedPoints
        addedPoints
        removedPoints
      }
      average
      trend
      forecast {
        nextSprint
        confidence
      }
    }
  }
`;

export const GET_EPIC = gql`
  ${EPIC_FRAGMENT}
  ${STORY_FRAGMENT}
  
  query GetEpic($id: ID!) {
    epic(id: $id) {
      ...EpicFields
      stories {
        ...StoryFields
      }
      roadmapItems {
        id
        name
        startDate
        endDate
        status
      }
    }
  }
`;

export const LIST_EPICS = gql`
  ${EPIC_FRAGMENT}
  
  query ListEpics($projectId: ID!, $filter: EpicFilter) {
    epics(projectId: $projectId, filter: $filter) {
      ...EpicFields
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const CREATE_SPRINT = gql`
  ${SPRINT_FRAGMENT}
  
  mutation CreateSprint($projectId: ID!, $input: CreateSprintInput!) {
    createSprint(projectId: $projectId, input: $input) {
      ...SprintFields
    }
  }
`;

export const UPDATE_SPRINT = gql`
  ${SPRINT_FRAGMENT}
  
  mutation UpdateSprint($id: ID!, $input: UpdateSprintInput!) {
    updateSprint(id: $id, input: $input) {
      ...SprintFields
    }
  }
`;

export const START_SPRINT = gql`
  ${SPRINT_FRAGMENT}
  
  mutation StartSprint($id: ID!, $goal: String) {
    startSprint(id: $id, goal: $goal) {
      ...SprintFields
    }
  }
`;

export const COMPLETE_SPRINT = gql`
  mutation CompleteSprint($id: ID!, $input: CompleteSprintInput!) {
    completeSprint(id: $id, input: $input) {
      sprint {
        id
        status
      }
      movedStories {
        id
        sprintId
      }
      retrospectiveId
    }
  }
`;

export const CREATE_STORY = gql`
  ${STORY_FRAGMENT}
  
  mutation CreateStory($projectId: ID!, $input: CreateStoryInput!) {
    createStory(projectId: $projectId, input: $input) {
      ...StoryFields
    }
  }
`;

export const UPDATE_STORY = gql`
  ${STORY_FRAGMENT}
  
  mutation UpdateStory($id: ID!, $input: UpdateStoryInput!) {
    updateStory(id: $id, input: $input) {
      ...StoryFields
    }
  }
`;

export const DELETE_STORY = gql`
  mutation DeleteStory($id: ID!) {
    deleteStory(id: $id) {
      success
    }
  }
`;

export const MOVE_STORY = gql`
  ${STORY_FRAGMENT}
  
  mutation MoveStory($id: ID!, $input: MoveStoryInput!) {
    moveStory(id: $id, input: $input) {
      ...StoryFields
    }
  }
`;

export const UPDATE_STORY_STATUS = gql`
  ${STORY_FRAGMENT}
  
  mutation UpdateStoryStatus($id: ID!, $status: StoryStatus!) {
    updateStoryStatus(id: $id, status: $status) {
      ...StoryFields
    }
  }
`;

export const ASSIGN_STORY = gql`
  ${STORY_FRAGMENT}
  
  mutation AssignStory($id: ID!, $assigneeId: ID) {
    assignStory(id: $id, assigneeId: $assigneeId) {
      ...StoryFields
    }
  }
`;

export const ADD_STORY_COMMENT = gql`
  ${STORY_COMMENT_FRAGMENT}
  
  mutation AddStoryComment($storyId: ID!, $input: AddCommentInput!) {
    addStoryComment(storyId: $storyId, input: $input) {
      ...StoryCommentFields
    }
  }
`;

export const UPDATE_STORY_COMMENT = gql`
  ${STORY_COMMENT_FRAGMENT}
  
  mutation UpdateStoryComment($id: ID!, $content: String!) {
    updateStoryComment(id: $id, content: $content) {
      ...StoryCommentFields
    }
  }
`;

export const DELETE_STORY_COMMENT = gql`
  mutation DeleteStoryComment($id: ID!) {
    deleteStoryComment(id: $id) {
      success
    }
  }
`;

export const ADD_STORY_REACTION = gql`
  mutation AddStoryReaction($commentId: ID!, $emoji: String!) {
    addStoryReaction(commentId: $commentId, emoji: $emoji) {
      reactions {
        emoji
        count
        users {
          id
          name
        }
      }
    }
  }
`;

export const LOG_STORY_TIME = gql`
  mutation LogStoryTime($storyId: ID!, $input: LogTimeInput!) {
    logStoryTime(storyId: $storyId, input: $input) {
      timeTracking {
        estimated
        logged
        remaining
      }
      entry {
        id
        hours
        description
        loggedAt
        loggedBy {
          id
          name
        }
      }
    }
  }
`;

export const CREATE_SUBTASK = gql`
  mutation CreateSubtask($storyId: ID!, $input: CreateSubtaskInput!) {
    createSubtask(storyId: $storyId, input: $input) {
      id
      title
      completed
      assigneeId
    }
  }
`;

export const TOGGLE_SUBTASK = gql`
  mutation ToggleSubtask($id: ID!) {
    toggleSubtask(id: $id) {
      id
      completed
    }
  }
`;

export const CREATE_PULL_REQUEST = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  mutation CreatePullRequest($projectId: ID!, $input: CreatePullRequestInput!) {
    createPullRequest(projectId: $projectId, input: $input) {
      ...PullRequestFields
    }
  }
`;

export const UPDATE_PULL_REQUEST = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  mutation UpdatePullRequest($id: ID!, $input: UpdatePullRequestInput!) {
    updatePullRequest(id: $id, input: $input) {
      ...PullRequestFields
    }
  }
`;

export const MERGE_PULL_REQUEST = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  mutation MergePullRequest($id: ID!, $method: MergeMethod!) {
    mergePullRequest(id: $id, method: $method) {
      ...PullRequestFields
    }
  }
`;

export const CLOSE_PULL_REQUEST = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  mutation ClosePullRequest($id: ID!) {
    closePullRequest(id: $id) {
      ...PullRequestFields
    }
  }
`;

export const REQUEST_PR_REVIEW = gql`
  mutation RequestPRReview($pullRequestId: ID!, $reviewerIds: [ID!]!) {
    requestPRReview(pullRequestId: $pullRequestId, reviewerIds: $reviewerIds) {
      reviewers {
        id
        name
        status
      }
    }
  }
`;

export const SUBMIT_PR_REVIEW = gql`
  mutation SubmitPRReview($pullRequestId: ID!, $input: SubmitReviewInput!) {
    submitPRReview(pullRequestId: $pullRequestId, input: $input) {
      id
      status
      body
      submittedAt
    }
  }
`;

export const CREATE_FEATURE_FLAG = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  mutation CreateFeatureFlag($projectId: ID!, $input: CreateFeatureFlagInput!) {
    createFeatureFlag(projectId: $projectId, input: $input) {
      ...FeatureFlagFields
    }
  }
`;

export const UPDATE_FEATURE_FLAG = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  mutation UpdateFeatureFlag($id: ID!, $input: UpdateFeatureFlagInput!) {
    updateFeatureFlag(id: $id, input: $input) {
      ...FeatureFlagFields
    }
  }
`;

export const TOGGLE_FEATURE_FLAG = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  mutation ToggleFeatureFlag($id: ID!, $enabled: Boolean!) {
    toggleFeatureFlag(id: $id, enabled: $enabled) {
      ...FeatureFlagFields
    }
  }
`;

export const DELETE_FEATURE_FLAG = gql`
  mutation DeleteFeatureFlag($id: ID!) {
    deleteFeatureFlag(id: $id) {
      success
    }
  }
`;

export const TRIGGER_DEPLOYMENT = gql`
  ${DEPLOYMENT_FRAGMENT}
  
  mutation TriggerDeployment($projectId: ID!, $input: TriggerDeploymentInput!) {
    triggerDeployment(projectId: $projectId, input: $input) {
      ...DeploymentFields
    }
  }
`;

export const ROLLBACK_DEPLOYMENT = gql`
  ${DEPLOYMENT_FRAGMENT}
  
  mutation RollbackDeployment($id: ID!, $reason: String) {
    rollbackDeployment(id: $id, reason: $reason) {
      ...DeploymentFields
    }
  }
`;

export const CANCEL_DEPLOYMENT = gql`
  mutation CancelDeployment($id: ID!) {
    cancelDeployment(id: $id) {
      success
      message
    }
  }
`;

export const CREATE_EPIC = gql`
  ${EPIC_FRAGMENT}
  
  mutation CreateEpic($projectId: ID!, $input: CreateEpicInput!) {
    createEpic(projectId: $projectId, input: $input) {
      ...EpicFields
    }
  }
`;

export const UPDATE_EPIC = gql`
  ${EPIC_FRAGMENT}
  
  mutation UpdateEpic($id: ID!, $input: UpdateEpicInput!) {
    updateEpic(id: $id, input: $input) {
      ...EpicFields
    }
  }
`;

export const DELETE_EPIC = gql`
  mutation DeleteEpic($id: ID!) {
    deleteEpic(id: $id) {
      success
      orphanedStories
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_SPRINT_UPDATES = gql`
  ${SPRINT_FRAGMENT}
  
  subscription OnSprintUpdate($sprintId: ID!) {
    sprintUpdated(sprintId: $sprintId) {
      type
      sprint {
        ...SprintFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_BOARD_CHANGES = gql`
  ${STORY_FRAGMENT}
  
  subscription OnBoardChange($sprintId: ID!) {
    boardChanged(sprintId: $sprintId) {
      type
      story {
        ...StoryFields
      }
      fromColumn
      toColumn
      movedBy {
        id
        name
      }
    }
  }
`;

export const SUBSCRIBE_TO_STORY_UPDATES = gql`
  ${STORY_FRAGMENT}
  
  subscription OnStoryUpdate($storyId: ID!) {
    storyUpdated(storyId: $storyId) {
      type
      story {
        ...StoryFields
      }
      changedFields
      changedBy {
        id
        name
      }
    }
  }
`;

export const SUBSCRIBE_TO_PR_UPDATES = gql`
  ${PULL_REQUEST_FRAGMENT}
  
  subscription OnPRUpdate($pullRequestId: ID!) {
    pullRequestUpdated(pullRequestId: $pullRequestId) {
      type
      pullRequest {
        ...PullRequestFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_DEPLOYMENT_STATUS = gql`
  ${DEPLOYMENT_FRAGMENT}
  
  subscription OnDeploymentStatus($deploymentId: ID!) {
    deploymentStatusChanged(deploymentId: $deploymentId) {
      ...DeploymentFields
    }
  }
`;

export const SUBSCRIBE_TO_FEATURE_FLAG_CHANGES = gql`
  ${FEATURE_FLAG_FRAGMENT}
  
  subscription OnFeatureFlagChange($projectId: ID!) {
    featureFlagChanged(projectId: $projectId) {
      type
      flag {
        ...FeatureFlagFields
      }
      changedBy {
        id
        name
      }
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

export interface CreateSprintInput {
  name: string;
  startDate: string;
  endDate: string;
  goal?: string;
}

export interface UpdateSprintInput {
  name?: string;
  startDate?: string;
  endDate?: string;
  goal?: string;
}

export interface CompleteSprintInput {
  moveIncomplete: 'backlog' | 'next_sprint';
  createRetrospective: boolean;
}

export interface CreateStoryInput {
  title: string;
  description?: string;
  type: 'feature' | 'bug' | 'task' | 'spike' | 'chore';
  priority?: 'critical' | 'high' | 'medium' | 'low';
  points?: number;
  sprintId?: string;
  epicId?: string;
  assigneeId?: string;
  labels?: string[];
  dueDate?: string;
}

export interface UpdateStoryInput {
  title?: string;
  description?: string;
  type?: string;
  priority?: string;
  points?: number;
  dueDate?: string;
  labels?: string[];
  epicId?: string;
}

export interface MoveStoryInput {
  sprintId?: string | null;
  status?: string;
  position?: number;
}

export interface AddCommentInput {
  content: string;
  attachments?: Array<{ name: string; url: string }>;
  mentions?: string[];
}

export interface LogTimeInput {
  hours: number;
  description?: string;
  date?: string;
}

export interface CreateSubtaskInput {
  title: string;
  assigneeId?: string;
}

export interface CreatePullRequestInput {
  title: string;
  description?: string;
  sourceBranch: string;
  targetBranch: string;
  linkedStoryIds?: string[];
  reviewerIds?: string[];
  draft?: boolean;
}

export interface UpdatePullRequestInput {
  title?: string;
  description?: string;
  targetBranch?: string;
  linkedStoryIds?: string[];
  draft?: boolean;
}

export interface SubmitReviewInput {
  status: 'approve' | 'request_changes' | 'comment';
  body?: string;
  comments?: Array<{
    path: string;
    line: number;
    body: string;
  }>;
}

export interface CreateFeatureFlagInput {
  key: string;
  name: string;
  description?: string;
  type: 'boolean' | 'string' | 'number' | 'json';
  defaultValue: unknown;
  tags?: string[];
}

export interface UpdateFeatureFlagInput {
  name?: string;
  description?: string;
  defaultValue?: unknown;
  tags?: string[];
  environments?: Array<{
    name: string;
    enabled: boolean;
    value?: unknown;
    percentage?: number;
  }>;
}

export interface TriggerDeploymentInput {
  environment: string;
  version?: string;
  commit?: string;
  pullRequestId?: string;
  force?: boolean;
}

export interface CreateEpicInput {
  name: string;
  description?: string;
  startDate?: string;
  targetDate?: string;
  color?: string;
}

export interface UpdateEpicInput {
  name?: string;
  description?: string;
  status?: string;
  startDate?: string;
  targetDate?: string;
  color?: string;
}

export interface StoryFilter {
  status?: string[];
  type?: string[];
  priority?: string[];
  assigneeId?: string;
  epicId?: string;
  sprintId?: string;
  labels?: string[];
  search?: string;
}

export interface PullRequestFilter {
  status?: string[];
  authorId?: string;
  reviewerId?: string;
  labels?: string[];
  search?: string;
}

export interface FeatureFlagFilter {
  enabled?: boolean;
  tags?: string[];
  search?: string;
}

export interface EpicFilter {
  status?: string[];
  ownerId?: string;
  search?: string;
}

export type SprintStatus = 'planned' | 'active' | 'completed';
export type StoryStatus = 'backlog' | 'todo' | 'in_progress' | 'review' | 'done';
export type MergeMethod = 'merge' | 'squash' | 'rebase';
