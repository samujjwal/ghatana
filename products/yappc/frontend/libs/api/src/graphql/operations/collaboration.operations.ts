/**
 * Collaboration Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * collaboration phase including team management, calendar, knowledge base,
 * standups, and messaging.
 *
 * @doc.type api
 * @doc.purpose Collaboration Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const TEAM_FRAGMENT = gql`
  fragment TeamFields on Team {
    id
    name
    description
    avatar
    createdAt
    memberCount
    settings {
      visibility
      joinPolicy
      notificationPreferences {
        channel
        enabled
        types
      }
    }
    leads {
      id
      name
      email
      avatar
    }
  }
`;

export const TEAM_MEMBER_FRAGMENT = gql`
  fragment TeamMemberFields on TeamMember {
    id
    user {
      id
      name
      email
      avatar
      timezone
      title
    }
    role
    status
    joinedAt
    lastActiveAt
    skills {
      name
      level
    }
    workload {
      assignedStories
      inProgressStories
      capacity
    }
  }
`;

export const CALENDAR_EVENT_FRAGMENT = gql`
  fragment CalendarEventFields on CalendarEvent {
    id
    title
    description
    type
    startTime
    endTime
    allDay
    location
    virtualMeetingUrl
    recurrence {
      frequency
      interval
      until
      daysOfWeek
    }
    attendees {
      id
      name
      email
      avatar
      status
      required
    }
    organizer {
      id
      name
      email
      avatar
    }
    reminders {
      method
      minutes
    }
    status
    visibility
    color
    attachments {
      id
      name
      url
    }
    createdAt
    updatedAt
  }
`;

export const KNOWLEDGE_ARTICLE_FRAGMENT = gql`
  fragment KnowledgeArticleFields on KnowledgeArticle {
    id
    title
    slug
    content
    excerpt
    category {
      id
      name
      icon
      path
    }
    author {
      id
      name
      avatar
    }
    contributors {
      id
      name
      avatar
    }
    tags
    status
    visibility
    createdAt
    updatedAt
    publishedAt
    version
    viewCount
    helpfulCount
    notHelpfulCount
    relatedArticles {
      id
      title
      slug
    }
    tableOfContents {
      id
      title
      level
      anchor
    }
  }
`;

export const STANDUP_FRAGMENT = gql`
  fragment StandupFields on Standup {
    id
    date
    team {
      id
      name
    }
    status
    startedAt
    completedAt
    participants {
      id
      name
      avatar
      submitted
      submittedAt
    }
    entries {
      id
      member {
        id
        name
        avatar
      }
      yesterday
      today
      blockers
      mood
      submittedAt
    }
    summary {
      totalParticipants
      submittedCount
      blockersCount
      aiSummary
    }
  }
`;

export const CHANNEL_FRAGMENT = gql`
  fragment ChannelFields on Channel {
    id
    name
    description
    type
    visibility
    createdAt
    createdBy {
      id
      name
    }
    memberCount
    lastMessageAt
    pinned
    muted
    unreadCount
    lastReadMessageId
    topic
    settings {
      allowThreads
      allowReactions
      allowFiles
      retentionDays
    }
  }
`;

export const MESSAGE_FRAGMENT = gql`
  fragment MessageFields on Message {
    id
    channelId
    threadId
    author {
      id
      name
      avatar
      status
    }
    content
    contentType
    timestamp
    editedAt
    deleted
    pinned
    reactions {
      emoji
      count
      users {
        id
        name
      }
      reacted
    }
    attachments {
      id
      type
      name
      url
      size
      thumbnail
    }
    mentions {
      id
      name
      type
    }
    replyCount
    lastReplyAt
  }
`;

export const ACTIVITY_ITEM_FRAGMENT = gql`
  fragment ActivityItemFields on ActivityItem {
    id
    type
    actor {
      id
      name
      avatar
    }
    target {
      type
      id
      title
      url
    }
    action
    metadata
    timestamp
    read
  }
`;

export const TEAM_GOAL_FRAGMENT = gql`
  fragment TeamGoalFields on TeamGoal {
    id
    title
    description
    team {
      id
      name
    }
    type
    status
    startDate
    targetDate
    owner {
      id
      name
      avatar
    }
    progress {
      current
      target
      percentage
    }
    keyResults {
      id
      title
      current
      target
      unit
      status
    }
    alignedTo {
      id
      title
      type
    }
    createdAt
    updatedAt
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_TEAM = gql`
  ${TEAM_FRAGMENT}
  ${TEAM_MEMBER_FRAGMENT}
  
  query GetTeam($id: ID!) {
    team(id: $id) {
      ...TeamFields
      members {
        ...TeamMemberFields
      }
    }
  }
`;

export const LIST_TEAMS = gql`
  ${TEAM_FRAGMENT}
  
  query ListTeams($projectId: ID!, $filter: TeamFilter) {
    teams(projectId: $projectId, filter: $filter) {
      ...TeamFields
    }
  }
`;

export const GET_TEAM_MEMBER = gql`
  ${TEAM_MEMBER_FRAGMENT}
  
  query GetTeamMember($teamId: ID!, $memberId: ID!) {
    teamMember(teamId: $teamId, memberId: $memberId) {
      ...TeamMemberFields
      recentActivity {
        type
        description
        timestamp
        link
      }
      contributions {
        storiesCompleted
        reviewsPerformed
        documentsCreated
        incidentsResolved
      }
    }
  }
`;

export const GET_TEAM_WORKLOAD = gql`
  query GetTeamWorkload($teamId: ID!, $sprintId: ID) {
    teamWorkload(teamId: $teamId, sprintId: $sprintId) {
      members {
        id
        name
        avatar
        capacity
        assigned
        inProgress
        completed
        availability
      }
      totalCapacity
      totalAssigned
      balance
      recommendations {
        type
        message
        fromMemberId
        toMemberId
        storyId
      }
    }
  }
`;

export const GET_CALENDAR_EVENTS = gql`
  ${CALENDAR_EVENT_FRAGMENT}
  
  query GetCalendarEvents(
    $projectId: ID!
    $startDate: String!
    $endDate: String!
    $filter: CalendarFilter
  ) {
    calendarEvents(
      projectId: $projectId
      startDate: $startDate
      endDate: $endDate
      filter: $filter
    ) {
      ...CalendarEventFields
    }
  }
`;

export const GET_CALENDAR_EVENT = gql`
  ${CALENDAR_EVENT_FRAGMENT}
  
  query GetCalendarEvent($id: ID!) {
    calendarEvent(id: $id) {
      ...CalendarEventFields
      comments {
        id
        author {
          id
          name
          avatar
        }
        content
        timestamp
      }
    }
  }
`;

export const GET_AVAILABILITY = gql`
  query GetAvailability($userIds: [ID!]!, $startDate: String!, $endDate: String!) {
    availability(userIds: $userIds, startDate: $startDate, endDate: $endDate) {
      userId
      slots {
        start
        end
        available
        reason
      }
    }
  }
`;

export const FIND_MEETING_TIMES = gql`
  query FindMeetingTimes(
    $attendeeIds: [ID!]!
    $duration: Int!
    $startDate: String!
    $endDate: String!
    $preferences: MeetingPreferences
  ) {
    findMeetingTimes(
      attendeeIds: $attendeeIds
      duration: $duration
      startDate: $startDate
      endDate: $endDate
      preferences: $preferences
    ) {
      start
      end
      score
      conflicts {
        userId
        reason
      }
    }
  }
`;

export const GET_KNOWLEDGE_ARTICLE = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  query GetKnowledgeArticle($id: ID!) {
    knowledgeArticle(id: $id) {
      ...KnowledgeArticleFields
      history {
        version
        author {
          id
          name
          avatar
        }
        timestamp
        changes
      }
    }
  }
`;

export const LIST_KNOWLEDGE_ARTICLES = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  query ListKnowledgeArticles(
    $projectId: ID!
    $filter: ArticleFilter
    $pagination: PaginationInput
    $sort: SortInput
  ) {
    knowledgeArticles(
      projectId: $projectId
      filter: $filter
      pagination: $pagination
      sort: $sort
    ) {
      edges {
        cursor
        node {
          ...KnowledgeArticleFields
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

export const SEARCH_KNOWLEDGE_BASE = gql`
  query SearchKnowledgeBase($projectId: ID!, $query: String!, $filters: ArticleFilter) {
    searchKnowledgeBase(projectId: $projectId, query: $query, filters: $filters) {
      results {
        article {
          id
          title
          slug
          excerpt
          category {
            id
            name
          }
          updatedAt
        }
        score
        highlights {
          field
          fragments
        }
      }
      suggestions {
        text
        score
      }
      totalCount
    }
  }
`;

export const GET_KNOWLEDGE_CATEGORIES = gql`
  query GetKnowledgeCategories($projectId: ID!) {
    knowledgeCategories(projectId: $projectId) {
      id
      name
      description
      icon
      color
      parentId
      path
      articleCount
      children {
        id
        name
        articleCount
      }
    }
  }
`;

export const GET_STANDUP = gql`
  ${STANDUP_FRAGMENT}
  
  query GetStandup($id: ID!) {
    standup(id: $id) {
      ...StandupFields
    }
  }
`;

export const LIST_STANDUPS = gql`
  ${STANDUP_FRAGMENT}
  
  query ListStandups(
    $teamId: ID!
    $startDate: String
    $endDate: String
    $pagination: PaginationInput
  ) {
    standups(
      teamId: $teamId
      startDate: $startDate
      endDate: $endDate
      pagination: $pagination
    ) {
      edges {
        cursor
        node {
          ...StandupFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

export const GET_TODAY_STANDUP = gql`
  ${STANDUP_FRAGMENT}
  
  query GetTodayStandup($teamId: ID!) {
    todayStandup(teamId: $teamId) {
      ...StandupFields
    }
  }
`;

export const GET_CHANNEL = gql`
  ${CHANNEL_FRAGMENT}
  
  query GetChannel($id: ID!) {
    channel(id: $id) {
      ...ChannelFields
      members {
        id
        name
        avatar
        role
        status
      }
      pinnedMessages {
        id
        content
        author {
          id
          name
        }
        timestamp
      }
    }
  }
`;

export const LIST_CHANNELS = gql`
  ${CHANNEL_FRAGMENT}
  
  query ListChannels($projectId: ID!, $filter: ChannelFilter) {
    channels(projectId: $projectId, filter: $filter) {
      ...ChannelFields
    }
  }
`;

export const GET_MESSAGES = gql`
  ${MESSAGE_FRAGMENT}
  
  query GetMessages(
    $channelId: ID!
    $pagination: CursorPaginationInput
    $threadId: ID
  ) {
    messages(channelId: $channelId, pagination: $pagination, threadId: $threadId) {
      edges {
        cursor
        node {
          ...MessageFields
        }
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
      }
    }
  }
`;

export const SEARCH_MESSAGES = gql`
  ${MESSAGE_FRAGMENT}
  
  query SearchMessages(
    $projectId: ID!
    $query: String!
    $filter: MessageSearchFilter
    $pagination: PaginationInput
  ) {
    searchMessages(
      projectId: $projectId
      query: $query
      filter: $filter
      pagination: $pagination
    ) {
      results {
        message {
          ...MessageFields
        }
        channel {
          id
          name
        }
        score
        highlights
      }
      totalCount
    }
  }
`;

export const GET_ACTIVITY_FEED = gql`
  ${ACTIVITY_ITEM_FRAGMENT}
  
  query GetActivityFeed(
    $projectId: ID!
    $filter: ActivityFilter
    $pagination: PaginationInput
  ) {
    activityFeed(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...ActivityItemFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
      unreadCount
    }
  }
`;

export const GET_TEAM_GOALS = gql`
  ${TEAM_GOAL_FRAGMENT}
  
  query GetTeamGoals($teamId: ID!, $filter: GoalFilter) {
    teamGoals(teamId: $teamId, filter: $filter) {
      ...TeamGoalFields
    }
  }
`;

export const GET_TEAM_GOAL = gql`
  ${TEAM_GOAL_FRAGMENT}
  
  query GetTeamGoal($id: ID!) {
    teamGoal(id: $id) {
      ...TeamGoalFields
      updates {
        id
        author {
          id
          name
          avatar
        }
        content
        progressChange
        timestamp
      }
      alignedGoals {
        id
        title
        team {
          id
          name
        }
        progress {
          percentage
        }
      }
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const CREATE_TEAM = gql`
  ${TEAM_FRAGMENT}
  
  mutation CreateTeam($projectId: ID!, $input: CreateTeamInput!) {
    createTeam(projectId: $projectId, input: $input) {
      ...TeamFields
    }
  }
`;

export const UPDATE_TEAM = gql`
  ${TEAM_FRAGMENT}
  
  mutation UpdateTeam($id: ID!, $input: UpdateTeamInput!) {
    updateTeam(id: $id, input: $input) {
      ...TeamFields
    }
  }
`;

export const DELETE_TEAM = gql`
  mutation DeleteTeam($id: ID!) {
    deleteTeam(id: $id) {
      success
    }
  }
`;

export const ADD_TEAM_MEMBER = gql`
  ${TEAM_MEMBER_FRAGMENT}
  
  mutation AddTeamMember($teamId: ID!, $input: AddTeamMemberInput!) {
    addTeamMember(teamId: $teamId, input: $input) {
      ...TeamMemberFields
    }
  }
`;

export const UPDATE_TEAM_MEMBER_ROLE = gql`
  ${TEAM_MEMBER_FRAGMENT}
  
  mutation UpdateTeamMemberRole($teamId: ID!, $memberId: ID!, $role: TeamMemberRole!) {
    updateTeamMemberRole(teamId: $teamId, memberId: $memberId, role: $role) {
      ...TeamMemberFields
    }
  }
`;

export const REMOVE_TEAM_MEMBER = gql`
  mutation RemoveTeamMember($teamId: ID!, $memberId: ID!) {
    removeTeamMember(teamId: $teamId, memberId: $memberId) {
      success
    }
  }
`;

export const UPDATE_MEMBER_STATUS = gql`
  mutation UpdateMemberStatus($status: UserStatus!, $message: String, $until: String) {
    updateMemberStatus(status: $status, message: $message, until: $until) {
      id
      status
      statusMessage
      statusUntil
    }
  }
`;

export const CREATE_CALENDAR_EVENT = gql`
  ${CALENDAR_EVENT_FRAGMENT}
  
  mutation CreateCalendarEvent($projectId: ID!, $input: CreateCalendarEventInput!) {
    createCalendarEvent(projectId: $projectId, input: $input) {
      ...CalendarEventFields
    }
  }
`;

export const UPDATE_CALENDAR_EVENT = gql`
  ${CALENDAR_EVENT_FRAGMENT}
  
  mutation UpdateCalendarEvent(
    $id: ID!
    $input: UpdateCalendarEventInput!
    $updateMode: RecurrenceUpdateMode
  ) {
    updateCalendarEvent(id: $id, input: $input, updateMode: $updateMode) {
      ...CalendarEventFields
    }
  }
`;

export const DELETE_CALENDAR_EVENT = gql`
  mutation DeleteCalendarEvent($id: ID!, $deleteMode: RecurrenceDeleteMode) {
    deleteCalendarEvent(id: $id, deleteMode: $deleteMode) {
      success
    }
  }
`;

export const RESPOND_TO_CALENDAR_EVENT = gql`
  mutation RespondToCalendarEvent($eventId: ID!, $response: AttendeeResponse!) {
    respondToCalendarEvent(eventId: $eventId, response: $response) {
      id
      attendees {
        id
        status
      }
    }
  }
`;

export const CREATE_KNOWLEDGE_ARTICLE = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  mutation CreateKnowledgeArticle($projectId: ID!, $input: CreateArticleInput!) {
    createKnowledgeArticle(projectId: $projectId, input: $input) {
      ...KnowledgeArticleFields
    }
  }
`;

export const UPDATE_KNOWLEDGE_ARTICLE = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  mutation UpdateKnowledgeArticle($id: ID!, $input: UpdateArticleInput!) {
    updateKnowledgeArticle(id: $id, input: $input) {
      ...KnowledgeArticleFields
    }
  }
`;

export const DELETE_KNOWLEDGE_ARTICLE = gql`
  mutation DeleteKnowledgeArticle($id: ID!) {
    deleteKnowledgeArticle(id: $id) {
      success
    }
  }
`;

export const PUBLISH_KNOWLEDGE_ARTICLE = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  mutation PublishKnowledgeArticle($id: ID!) {
    publishKnowledgeArticle(id: $id) {
      ...KnowledgeArticleFields
    }
  }
`;

export const UNPUBLISH_KNOWLEDGE_ARTICLE = gql`
  ${KNOWLEDGE_ARTICLE_FRAGMENT}
  
  mutation UnpublishKnowledgeArticle($id: ID!) {
    unpublishKnowledgeArticle(id: $id) {
      ...KnowledgeArticleFields
    }
  }
`;

export const RATE_KNOWLEDGE_ARTICLE = gql`
  mutation RateKnowledgeArticle($id: ID!, $helpful: Boolean!) {
    rateKnowledgeArticle(id: $id, helpful: $helpful) {
      id
      helpfulCount
      notHelpfulCount
    }
  }
`;

export const CREATE_KNOWLEDGE_CATEGORY = gql`
  mutation CreateKnowledgeCategory($projectId: ID!, $input: CreateCategoryInput!) {
    createKnowledgeCategory(projectId: $projectId, input: $input) {
      id
      name
      description
      icon
      color
      parentId
      path
    }
  }
`;

export const UPDATE_KNOWLEDGE_CATEGORY = gql`
  mutation UpdateKnowledgeCategory($id: ID!, $input: UpdateCategoryInput!) {
    updateKnowledgeCategory(id: $id, input: $input) {
      id
      name
      description
      icon
      color
    }
  }
`;

export const DELETE_KNOWLEDGE_CATEGORY = gql`
  mutation DeleteKnowledgeCategory($id: ID!, $moveArticlesTo: ID) {
    deleteKnowledgeCategory(id: $id, moveArticlesTo: $moveArticlesTo) {
      success
      movedArticles
    }
  }
`;

export const CREATE_STANDUP = gql`
  ${STANDUP_FRAGMENT}
  
  mutation CreateStandup($teamId: ID!, $date: String) {
    createStandup(teamId: $teamId, date: $date) {
      ...StandupFields
    }
  }
`;

export const SUBMIT_STANDUP_ENTRY = gql`
  mutation SubmitStandupEntry($standupId: ID!, $input: StandupEntryInput!) {
    submitStandupEntry(standupId: $standupId, input: $input) {
      id
      member {
        id
        name
      }
      yesterday
      today
      blockers
      mood
      submittedAt
    }
  }
`;

export const UPDATE_STANDUP_ENTRY = gql`
  mutation UpdateStandupEntry($entryId: ID!, $input: StandupEntryInput!) {
    updateStandupEntry(entryId: $entryId, input: $input) {
      id
      yesterday
      today
      blockers
      mood
    }
  }
`;

export const COMPLETE_STANDUP = gql`
  ${STANDUP_FRAGMENT}
  
  mutation CompleteStandup($standupId: ID!) {
    completeStandup(standupId: $standupId) {
      ...StandupFields
    }
  }
`;

export const CREATE_CHANNEL = gql`
  ${CHANNEL_FRAGMENT}
  
  mutation CreateChannel($projectId: ID!, $input: CreateChannelInput!) {
    createChannel(projectId: $projectId, input: $input) {
      ...ChannelFields
    }
  }
`;

export const UPDATE_CHANNEL = gql`
  ${CHANNEL_FRAGMENT}
  
  mutation UpdateChannel($id: ID!, $input: UpdateChannelInput!) {
    updateChannel(id: $id, input: $input) {
      ...ChannelFields
    }
  }
`;

export const DELETE_CHANNEL = gql`
  mutation DeleteChannel($id: ID!) {
    deleteChannel(id: $id) {
      success
    }
  }
`;

export const JOIN_CHANNEL = gql`
  mutation JoinChannel($channelId: ID!) {
    joinChannel(channelId: $channelId) {
      success
    }
  }
`;

export const LEAVE_CHANNEL = gql`
  mutation LeaveChannel($channelId: ID!) {
    leaveChannel(channelId: $channelId) {
      success
    }
  }
`;

export const ADD_CHANNEL_MEMBERS = gql`
  mutation AddChannelMembers($channelId: ID!, $memberIds: [ID!]!) {
    addChannelMembers(channelId: $channelId, memberIds: $memberIds) {
      addedCount
    }
  }
`;

export const REMOVE_CHANNEL_MEMBER = gql`
  mutation RemoveChannelMember($channelId: ID!, $memberId: ID!) {
    removeChannelMember(channelId: $channelId, memberId: $memberId) {
      success
    }
  }
`;

export const SEND_MESSAGE = gql`
  ${MESSAGE_FRAGMENT}
  
  mutation SendMessage($channelId: ID!, $input: SendMessageInput!) {
    sendMessage(channelId: $channelId, input: $input) {
      ...MessageFields
    }
  }
`;

export const UPDATE_MESSAGE = gql`
  ${MESSAGE_FRAGMENT}
  
  mutation UpdateMessage($id: ID!, $content: String!) {
    updateMessage(id: $id, content: $content) {
      ...MessageFields
    }
  }
`;

export const DELETE_MESSAGE = gql`
  mutation DeleteMessage($id: ID!) {
    deleteMessage(id: $id) {
      success
    }
  }
`;

export const ADD_REACTION = gql`
  mutation AddReaction($messageId: ID!, $emoji: String!) {
    addReaction(messageId: $messageId, emoji: $emoji) {
      reactions {
        emoji
        count
        users {
          id
          name
        }
        reacted
      }
    }
  }
`;

export const REMOVE_REACTION = gql`
  mutation RemoveReaction($messageId: ID!, $emoji: String!) {
    removeReaction(messageId: $messageId, emoji: $emoji) {
      reactions {
        emoji
        count
        users {
          id
          name
        }
        reacted
      }
    }
  }
`;

export const PIN_MESSAGE = gql`
  mutation PinMessage($messageId: ID!) {
    pinMessage(messageId: $messageId) {
      id
      pinned
    }
  }
`;

export const UNPIN_MESSAGE = gql`
  mutation UnpinMessage($messageId: ID!) {
    unpinMessage(messageId: $messageId) {
      id
      pinned
    }
  }
`;

export const MARK_CHANNEL_READ = gql`
  mutation MarkChannelRead($channelId: ID!, $messageId: ID) {
    markChannelRead(channelId: $channelId, messageId: $messageId) {
      unreadCount
      lastReadMessageId
    }
  }
`;

export const MARK_ACTIVITY_READ = gql`
  mutation MarkActivityRead($activityIds: [ID!]) {
    markActivityRead(activityIds: $activityIds) {
      success
      unreadCount
    }
  }
`;

export const CREATE_TEAM_GOAL = gql`
  ${TEAM_GOAL_FRAGMENT}
  
  mutation CreateTeamGoal($teamId: ID!, $input: CreateGoalInput!) {
    createTeamGoal(teamId: $teamId, input: $input) {
      ...TeamGoalFields
    }
  }
`;

export const UPDATE_TEAM_GOAL = gql`
  ${TEAM_GOAL_FRAGMENT}
  
  mutation UpdateTeamGoal($id: ID!, $input: UpdateGoalInput!) {
    updateTeamGoal(id: $id, input: $input) {
      ...TeamGoalFields
    }
  }
`;

export const DELETE_TEAM_GOAL = gql`
  mutation DeleteTeamGoal($id: ID!) {
    deleteTeamGoal(id: $id) {
      success
    }
  }
`;

export const ADD_GOAL_UPDATE = gql`
  mutation AddGoalUpdate($goalId: ID!, $input: GoalUpdateInput!) {
    addGoalUpdate(goalId: $goalId, input: $input) {
      id
      content
      progressChange
      timestamp
      author {
        id
        name
        avatar
      }
    }
  }
`;

export const UPDATE_KEY_RESULT = gql`
  mutation UpdateKeyResult($goalId: ID!, $keyResultId: ID!, $current: Float!) {
    updateKeyResult(goalId: $goalId, keyResultId: $keyResultId, current: $current) {
      id
      keyResults {
        id
        current
        status
      }
      progress {
        current
        target
        percentage
      }
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_TEAM_UPDATES = gql`
  ${TEAM_FRAGMENT}
  ${TEAM_MEMBER_FRAGMENT}
  
  subscription OnTeamUpdate($teamId: ID!) {
    teamUpdated(teamId: $teamId) {
      type
      team {
        ...TeamFields
      }
      member {
        ...TeamMemberFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_PRESENCE = gql`
  subscription OnPresenceChange($projectId: ID!) {
    presenceChanged(projectId: $projectId) {
      userId
      status
      statusMessage
      lastActiveAt
    }
  }
`;

export const SUBSCRIBE_TO_CALENDAR_UPDATES = gql`
  ${CALENDAR_EVENT_FRAGMENT}
  
  subscription OnCalendarUpdate($projectId: ID!) {
    calendarUpdated(projectId: $projectId) {
      type
      event {
        ...CalendarEventFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_MESSAGES = gql`
  ${MESSAGE_FRAGMENT}
  
  subscription OnMessage($channelId: ID!) {
    messageReceived(channelId: $channelId) {
      type
      message {
        ...MessageFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_TYPING = gql`
  subscription OnTyping($channelId: ID!) {
    typing(channelId: $channelId) {
      userId
      userName
      isTyping
    }
  }
`;

export const SUBSCRIBE_TO_CHANNEL_UPDATES = gql`
  ${CHANNEL_FRAGMENT}
  
  subscription OnChannelUpdate($projectId: ID!) {
    channelUpdated(projectId: $projectId) {
      type
      channel {
        ...ChannelFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_ACTIVITY = gql`
  ${ACTIVITY_ITEM_FRAGMENT}
  
  subscription OnActivity($projectId: ID!) {
    activityAdded(projectId: $projectId) {
      ...ActivityItemFields
    }
  }
`;

export const SUBSCRIBE_TO_STANDUP_UPDATES = gql`
  ${STANDUP_FRAGMENT}
  
  subscription OnStandupUpdate($standupId: ID!) {
    standupUpdated(standupId: $standupId) {
      ...StandupFields
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

export interface CreateTeamInput {
  name: string;
  description?: string;
  avatar?: string;
  leadIds?: string[];
  settings?: {
    visibility?: 'public' | 'private';
    joinPolicy?: 'open' | 'request' | 'invite_only';
  };
}

export interface UpdateTeamInput {
  name?: string;
  description?: string;
  avatar?: string;
  leadIds?: string[];
  settings?: {
    visibility?: string;
    joinPolicy?: string;
  };
}

export interface AddTeamMemberInput {
  userId: string;
  role?: 'member' | 'lead' | 'admin';
}

export interface CreateCalendarEventInput {
  title: string;
  description?: string;
  type: 'meeting' | 'review' | 'standup' | 'planning' | 'retrospective' | 'other';
  startTime: string;
  endTime: string;
  allDay?: boolean;
  location?: string;
  virtualMeetingUrl?: string;
  attendeeIds?: string[];
  recurrence?: {
    frequency: 'daily' | 'weekly' | 'monthly' | 'yearly';
    interval: number;
    until?: string;
    daysOfWeek?: number[];
  };
  reminders?: Array<{
    method: 'email' | 'push' | 'sms';
    minutes: number;
  }>;
  visibility?: 'public' | 'private';
  color?: string;
}

export interface UpdateCalendarEventInput {
  title?: string;
  description?: string;
  type?: string;
  startTime?: string;
  endTime?: string;
  allDay?: boolean;
  location?: string;
  virtualMeetingUrl?: string;
  attendeeIds?: string[];
  recurrence?: {
    frequency: string;
    interval: number;
    until?: string;
    daysOfWeek?: number[];
  };
  reminders?: Array<{
    method: string;
    minutes: number;
  }>;
  visibility?: string;
  color?: string;
}

export interface CreateArticleInput {
  title: string;
  content: string;
  categoryId: string;
  tags?: string[];
  visibility?: 'public' | 'team' | 'private';
}

export interface UpdateArticleInput {
  title?: string;
  content?: string;
  categoryId?: string;
  tags?: string[];
  visibility?: string;
}

export interface CreateCategoryInput {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
  parentId?: string;
}

export interface UpdateCategoryInput {
  name?: string;
  description?: string;
  icon?: string;
  color?: string;
}

export interface StandupEntryInput {
  yesterday: string;
  today: string;
  blockers?: string;
  mood?: 'great' | 'good' | 'okay' | 'struggling';
}

export interface CreateChannelInput {
  name: string;
  description?: string;
  type: 'public' | 'private' | 'direct';
  memberIds?: string[];
  topic?: string;
}

export interface UpdateChannelInput {
  name?: string;
  description?: string;
  topic?: string;
  settings?: {
    allowThreads?: boolean;
    allowReactions?: boolean;
    allowFiles?: boolean;
    retentionDays?: number;
  };
}

export interface SendMessageInput {
  content: string;
  contentType?: 'text' | 'markdown' | 'code';
  threadId?: string;
  attachments?: Array<{
    type: string;
    url: string;
    name: string;
    size?: number;
  }>;
  mentions?: string[];
}

export interface CreateGoalInput {
  title: string;
  description?: string;
  type: 'objective' | 'key_result' | 'milestone';
  startDate: string;
  targetDate: string;
  ownerId?: string;
  keyResults?: Array<{
    title: string;
    target: number;
    unit?: string;
  }>;
  alignedToId?: string;
}

export interface UpdateGoalInput {
  title?: string;
  description?: string;
  status?: 'not_started' | 'on_track' | 'at_risk' | 'behind' | 'completed';
  targetDate?: string;
  ownerId?: string;
}

export interface GoalUpdateInput {
  content: string;
  progressChange?: number;
}

export interface MeetingPreferences {
  workingHoursOnly?: boolean;
  preferredTimes?: Array<{ start: string; end: string }>;
  minimumNotice?: number;
  bufferBetweenMeetings?: number;
}

export interface TeamFilter {
  visibility?: string;
  search?: string;
}

export interface CalendarFilter {
  types?: string[];
  attendeeId?: string;
  organizerId?: string;
}

export interface ArticleFilter {
  categoryId?: string;
  status?: string;
  authorId?: string;
  tags?: string[];
  search?: string;
}

export interface ChannelFilter {
  type?: string[];
  joined?: boolean;
  search?: string;
}

export interface MessageSearchFilter {
  channelId?: string;
  authorId?: string;
  hasAttachments?: boolean;
  dateRange?: { start: string; end: string };
}

export interface ActivityFilter {
  types?: string[];
  actorId?: string;
  unreadOnly?: boolean;
}

export interface GoalFilter {
  status?: string[];
  type?: string[];
  ownerId?: string;
}

export interface CursorPaginationInput {
  first?: number;
  after?: string;
  last?: number;
  before?: string;
}

export type TeamMemberRole = 'member' | 'lead' | 'admin';
export type UserStatus = 'online' | 'away' | 'busy' | 'offline';
export type AttendeeResponse = 'accepted' | 'declined' | 'tentative' | 'pending';
export type RecurrenceUpdateMode = 'this' | 'this_and_following' | 'all';
export type RecurrenceDeleteMode = 'this' | 'this_and_following' | 'all';
