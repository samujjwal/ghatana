/**
 * Operations Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * operations phase including incidents, alerts, logs, dashboards,
 * runbooks, and service health monitoring.
 *
 * @doc.type api
 * @doc.purpose Operations Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const INCIDENT_FRAGMENT = gql`
  fragment IncidentFields on Incident {
    id
    title
    description
    severity
    status
    priority
    createdAt
    updatedAt
    acknowledgedAt
    resolvedAt
    commander {
      id
      name
      email
      avatar
    }
    assignees {
      id
      name
      email
      avatar
      role
    }
    affectedServices {
      id
      name
      status
    }
    timeline {
      id
      timestamp
      type
      actor {
        id
        name
        avatar
      }
      content
      metadata
    }
    metrics {
      timeToAcknowledge
      timeToResolve
      timeToMitigate
      customerImpact
    }
    tags {
      key
      value
    }
    linkedAlerts {
      id
      name
      severity
    }
    postmortemId
  }
`;

export const ALERT_FRAGMENT = gql`
  fragment AlertFields on Alert {
    id
    name
    message
    severity
    status
    source
    createdAt
    acknowledgedAt
    resolvedAt
    acknowledgedBy {
      id
      name
    }
    service {
      id
      name
    }
    metric {
      name
      value
      threshold
      unit
    }
    labels {
      key
      value
    }
    annotations {
      key
      value
    }
    silenced
    silencedUntil
    linkedIncidentId
  }
`;

export const DASHBOARD_FRAGMENT = gql`
  fragment DashboardFields on Dashboard {
    id
    name
    description
    owner {
      id
      name
    }
    isDefault
    isShared
    createdAt
    updatedAt
    widgets {
      id
      type
      title
      position {
        x
        y
        width
        height
      }
      config {
        query
        visualization
        thresholds {
          value
          color
          label
        }
        refreshInterval
      }
    }
    variables {
      name
      type
      defaultValue
      options
    }
    tags
  }
`;

export const RUNBOOK_FRAGMENT = gql`
  fragment RunbookFields on Runbook {
    id
    name
    description
    category
    trigger
    owner {
      id
      name
    }
    steps {
      id
      order
      type
      title
      description
      command
      expectedOutput
      timeout
      onFailure
      conditions {
        field
        operator
        value
      }
    }
    parameters {
      name
      type
      required
      defaultValue
      description
    }
    linkedServices {
      id
      name
    }
    lastExecutedAt
    executionCount
    averageExecutionTime
    successRate
    tags
    version
  }
`;

export const SERVICE_FRAGMENT = gql`
  fragment ServiceFields on Service {
    id
    name
    description
    type
    status
    owner {
      id
      name
    }
    team {
      id
      name
    }
    repository {
      url
      branch
    }
    endpoints {
      name
      url
      healthCheck
      status
    }
    dependencies {
      id
      name
      type
      status
    }
    metrics {
      uptime
      latencyP50
      latencyP95
      latencyP99
      errorRate
      requestsPerSecond
    }
    slos {
      id
      name
      target
      current
      status
    }
    alerts {
      active
      critical
      warning
    }
    lastDeployedAt
    lastIncidentAt
    tags
  }
`;

export const LOG_ENTRY_FRAGMENT = gql`
  fragment LogEntryFields on LogEntry {
    id
    timestamp
    level
    message
    service
    host
    traceId
    spanId
    attributes {
      key
      value
    }
    context {
      userId
      requestId
      sessionId
    }
  }
`;

export const ON_CALL_SCHEDULE_FRAGMENT = gql`
  fragment OnCallScheduleFields on OnCallSchedule {
    id
    name
    team {
      id
      name
    }
    timezone
    rotations {
      id
      name
      users {
        id
        name
        email
        avatar
      }
      startDate
      handoffTime
      rotationType
      restrictions {
        startDay
        endDay
        startTime
        endTime
      }
    }
    currentOnCall {
      id
      name
      email
      avatar
      until
    }
    escalationPolicy {
      levels {
        order
        delay
        targets {
          type
          id
          name
        }
      }
    }
    overrides {
      id
      user {
        id
        name
      }
      startTime
      endTime
      reason
    }
  }
`;

export const POSTMORTEM_FRAGMENT = gql`
  fragment PostmortemFields on Postmortem {
    id
    incidentId
    title
    status
    summary
    impact {
      duration
      affectedUsers
      affectedServices
      financialImpact
    }
    timeline {
      timestamp
      event
      actor
    }
    rootCauses {
      id
      description
      category
      contributingFactors
    }
    actionItems {
      id
      title
      description
      assignee {
        id
        name
      }
      priority
      status
      dueDate
      completedAt
    }
    lessons {
      id
      category
      description
      recommendation
    }
    authors {
      id
      name
      avatar
    }
    reviewers {
      id
      name
      avatar
      approved
    }
    createdAt
    publishedAt
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_INCIDENT = gql`
  ${INCIDENT_FRAGMENT}
  
  query GetIncident($id: ID!) {
    incident(id: $id) {
      ...IncidentFields
      warRoom {
        enabled
        participants {
          id
          name
          avatar
          role
        }
        messages {
          id
          author {
            id
            name
            avatar
          }
          content
          timestamp
          type
        }
      }
    }
  }
`;

export const LIST_INCIDENTS = gql`
  ${INCIDENT_FRAGMENT}
  
  query ListIncidents(
    $projectId: ID!
    $filter: IncidentFilter
    $pagination: PaginationInput
    $sort: SortInput
  ) {
    incidents(
      projectId: $projectId
      filter: $filter
      pagination: $pagination
      sort: $sort
    ) {
      edges {
        cursor
        node {
          ...IncidentFields
        }
      }
      pageInfo {
        hasNextPage
        hasPreviousPage
        startCursor
        endCursor
        totalCount
      }
      statistics {
        total
        bySeverity {
          severity
          count
        }
        byStatus {
          status
          count
        }
        mttr
        mtta
      }
    }
  }
`;

export const GET_ACTIVE_INCIDENTS = gql`
  ${INCIDENT_FRAGMENT}
  
  query GetActiveIncidents($projectId: ID!) {
    activeIncidents(projectId: $projectId) {
      ...IncidentFields
    }
  }
`;

export const GET_ALERT = gql`
  ${ALERT_FRAGMENT}
  
  query GetAlert($id: ID!) {
    alert(id: $id) {
      ...AlertFields
      history {
        timestamp
        status
        actor {
          id
          name
        }
        note
      }
    }
  }
`;

export const LIST_ALERTS = gql`
  ${ALERT_FRAGMENT}
  
  query ListAlerts(
    $projectId: ID!
    $filter: AlertFilter
    $pagination: PaginationInput
  ) {
    alerts(projectId: $projectId, filter: $filter, pagination: $pagination) {
      edges {
        cursor
        node {
          ...AlertFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
        totalCount
      }
      statistics {
        total
        firing
        acknowledged
        resolved
        bySeverity {
          severity
          count
        }
      }
    }
  }
`;

export const GET_DASHBOARD = gql`
  ${DASHBOARD_FRAGMENT}
  
  query GetDashboard($id: ID!) {
    dashboard(id: $id) {
      ...DashboardFields
    }
  }
`;

export const LIST_DASHBOARDS = gql`
  ${DASHBOARD_FRAGMENT}
  
  query ListDashboards($projectId: ID!, $filter: DashboardFilter) {
    dashboards(projectId: $projectId, filter: $filter) {
      ...DashboardFields
    }
  }
`;

export const QUERY_METRICS = gql`
  query QueryMetrics($projectId: ID!, $query: MetricQueryInput!) {
    queryMetrics(projectId: $projectId, query: $query) {
      series {
        name
        labels {
          key
          value
        }
        values {
          timestamp
          value
        }
      }
      metadata {
        startTime
        endTime
        step
        totalPoints
      }
    }
  }
`;

export const GET_RUNBOOK = gql`
  ${RUNBOOK_FRAGMENT}
  
  query GetRunbook($id: ID!) {
    runbook(id: $id) {
      ...RunbookFields
      executions {
        id
        status
        startedAt
        completedAt
        triggeredBy {
          id
          name
        }
        parameters
        stepResults {
          stepId
          status
          output
          duration
          error
        }
      }
    }
  }
`;

export const LIST_RUNBOOKS = gql`
  ${RUNBOOK_FRAGMENT}
  
  query ListRunbooks($projectId: ID!, $filter: RunbookFilter) {
    runbooks(projectId: $projectId, filter: $filter) {
      ...RunbookFields
    }
  }
`;

export const GET_SERVICE = gql`
  ${SERVICE_FRAGMENT}
  
  query GetService($id: ID!) {
    service(id: $id) {
      ...ServiceFields
      recentIncidents {
        id
        title
        severity
        status
        createdAt
      }
      deployments {
        id
        version
        status
        deployedAt
      }
    }
  }
`;

export const LIST_SERVICES = gql`
  ${SERVICE_FRAGMENT}
  
  query ListServices($projectId: ID!, $filter: ServiceFilter) {
    services(projectId: $projectId, filter: $filter) {
      ...ServiceFields
    }
  }
`;

export const GET_SERVICE_MAP = gql`
  query GetServiceMap($projectId: ID!) {
    serviceMap(projectId: $projectId) {
      nodes {
        id
        name
        type
        status
        position {
          x
          y
        }
        metrics {
          requestsPerSecond
          errorRate
          latency
        }
      }
      edges {
        source
        target
        metrics {
          requestsPerSecond
          errorRate
          latency
        }
      }
    }
  }
`;

export const SEARCH_LOGS = gql`
  ${LOG_ENTRY_FRAGMENT}
  
  query SearchLogs($projectId: ID!, $query: LogQueryInput!) {
    searchLogs(projectId: $projectId, query: $query) {
      entries {
        ...LogEntryFields
      }
      histogram {
        bucket
        count
        levels {
          level
          count
        }
      }
      facets {
        field
        values {
          value
          count
        }
      }
      totalCount
      scannedCount
    }
  }
`;

export const GET_LOG_CONTEXT = gql`
  ${LOG_ENTRY_FRAGMENT}
  
  query GetLogContext($id: ID!, $before: Int, $after: Int) {
    logContext(id: $id, before: $before, after: $after) {
      before {
        ...LogEntryFields
      }
      entry {
        ...LogEntryFields
      }
      after {
        ...LogEntryFields
      }
    }
  }
`;

export const GET_ON_CALL_SCHEDULE = gql`
  ${ON_CALL_SCHEDULE_FRAGMENT}
  
  query GetOnCallSchedule($id: ID!) {
    onCallSchedule(id: $id) {
      ...OnCallScheduleFields
    }
  }
`;

export const LIST_ON_CALL_SCHEDULES = gql`
  ${ON_CALL_SCHEDULE_FRAGMENT}
  
  query ListOnCallSchedules($projectId: ID!) {
    onCallSchedules(projectId: $projectId) {
      ...OnCallScheduleFields
    }
  }
`;

export const GET_POSTMORTEM = gql`
  ${POSTMORTEM_FRAGMENT}
  
  query GetPostmortem($id: ID!) {
    postmortem(id: $id) {
      ...PostmortemFields
    }
  }
`;

export const LIST_POSTMORTEMS = gql`
  ${POSTMORTEM_FRAGMENT}
  
  query ListPostmortems($projectId: ID!, $pagination: PaginationInput) {
    postmortems(projectId: $projectId, pagination: $pagination) {
      edges {
        cursor
        node {
          ...PostmortemFields
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
`;

export const GET_OPS_OVERVIEW = gql`
  query GetOpsOverview($projectId: ID!, $timeRange: TimeRangeInput!) {
    opsOverview(projectId: $projectId, timeRange: $timeRange) {
      incidents {
        total
        open
        mttr
        mtta
        trend
      }
      alerts {
        total
        firing
        acknowledged
      }
      services {
        total
        healthy
        degraded
        down
      }
      slos {
        total
        met
        atRisk
        breached
      }
      availability {
        current
        target
        trend
      }
      topIssues {
        service
        issue
        count
        impact
      }
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const CREATE_INCIDENT = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation CreateIncident($projectId: ID!, $input: CreateIncidentInput!) {
    createIncident(projectId: $projectId, input: $input) {
      ...IncidentFields
    }
  }
`;

export const UPDATE_INCIDENT = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation UpdateIncident($id: ID!, $input: UpdateIncidentInput!) {
    updateIncident(id: $id, input: $input) {
      ...IncidentFields
    }
  }
`;

export const UPDATE_INCIDENT_STATUS = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation UpdateIncidentStatus($id: ID!, $status: IncidentStatus!, $note: String) {
    updateIncidentStatus(id: $id, status: $status, note: $note) {
      ...IncidentFields
    }
  }
`;

export const ACKNOWLEDGE_INCIDENT = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation AcknowledgeIncident($id: ID!) {
    acknowledgeIncident(id: $id) {
      ...IncidentFields
    }
  }
`;

export const RESOLVE_INCIDENT = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation ResolveIncident($id: ID!, $resolution: String!) {
    resolveIncident(id: $id, resolution: $resolution) {
      ...IncidentFields
    }
  }
`;

export const ASSIGN_INCIDENT_COMMANDER = gql`
  ${INCIDENT_FRAGMENT}
  
  mutation AssignIncidentCommander($id: ID!, $commanderId: ID!) {
    assignIncidentCommander(id: $id, commanderId: $commanderId) {
      ...IncidentFields
    }
  }
`;

export const ADD_INCIDENT_TIMELINE_EVENT = gql`
  mutation AddIncidentTimelineEvent($incidentId: ID!, $input: TimelineEventInput!) {
    addIncidentTimelineEvent(incidentId: $incidentId, input: $input) {
      id
      timestamp
      type
      content
      actor {
        id
        name
      }
    }
  }
`;

export const SEND_WAR_ROOM_MESSAGE = gql`
  mutation SendWarRoomMessage($incidentId: ID!, $content: String!, $type: MessageType) {
    sendWarRoomMessage(incidentId: $incidentId, content: $content, type: $type) {
      id
      content
      timestamp
      type
      author {
        id
        name
        avatar
      }
    }
  }
`;

export const ACKNOWLEDGE_ALERT = gql`
  ${ALERT_FRAGMENT}
  
  mutation AcknowledgeAlert($id: ID!, $note: String) {
    acknowledgeAlert(id: $id, note: $note) {
      ...AlertFields
    }
  }
`;

export const RESOLVE_ALERT = gql`
  ${ALERT_FRAGMENT}
  
  mutation ResolveAlert($id: ID!, $note: String) {
    resolveAlert(id: $id, note: $note) {
      ...AlertFields
    }
  }
`;

export const SILENCE_ALERT = gql`
  ${ALERT_FRAGMENT}
  
  mutation SilenceAlert($id: ID!, $duration: Int!, $reason: String) {
    silenceAlert(id: $id, duration: $duration, reason: $reason) {
      ...AlertFields
    }
  }
`;

export const LINK_ALERT_TO_INCIDENT = gql`
  mutation LinkAlertToIncident($alertId: ID!, $incidentId: ID!) {
    linkAlertToIncident(alertId: $alertId, incidentId: $incidentId) {
      alert {
        id
        linkedIncidentId
      }
      incident {
        id
        linkedAlerts {
          id
        }
      }
    }
  }
`;

export const CREATE_DASHBOARD = gql`
  ${DASHBOARD_FRAGMENT}
  
  mutation CreateDashboard($projectId: ID!, $input: CreateDashboardInput!) {
    createDashboard(projectId: $projectId, input: $input) {
      ...DashboardFields
    }
  }
`;

export const UPDATE_DASHBOARD = gql`
  ${DASHBOARD_FRAGMENT}
  
  mutation UpdateDashboard($id: ID!, $input: UpdateDashboardInput!) {
    updateDashboard(id: $id, input: $input) {
      ...DashboardFields
    }
  }
`;

export const DELETE_DASHBOARD = gql`
  mutation DeleteDashboard($id: ID!) {
    deleteDashboard(id: $id) {
      success
    }
  }
`;

export const ADD_DASHBOARD_WIDGET = gql`
  mutation AddDashboardWidget($dashboardId: ID!, $input: WidgetInput!) {
    addDashboardWidget(dashboardId: $dashboardId, input: $input) {
      id
      type
      title
      position {
        x
        y
        width
        height
      }
      config
    }
  }
`;

export const UPDATE_DASHBOARD_WIDGET = gql`
  mutation UpdateDashboardWidget($widgetId: ID!, $input: WidgetInput!) {
    updateDashboardWidget(widgetId: $widgetId, input: $input) {
      id
      type
      title
      position {
        x
        y
        width
        height
      }
      config
    }
  }
`;

export const DELETE_DASHBOARD_WIDGET = gql`
  mutation DeleteDashboardWidget($widgetId: ID!) {
    deleteDashboardWidget(widgetId: $widgetId) {
      success
    }
  }
`;

export const CREATE_RUNBOOK = gql`
  ${RUNBOOK_FRAGMENT}
  
  mutation CreateRunbook($projectId: ID!, $input: CreateRunbookInput!) {
    createRunbook(projectId: $projectId, input: $input) {
      ...RunbookFields
    }
  }
`;

export const UPDATE_RUNBOOK = gql`
  ${RUNBOOK_FRAGMENT}
  
  mutation UpdateRunbook($id: ID!, $input: UpdateRunbookInput!) {
    updateRunbook(id: $id, input: $input) {
      ...RunbookFields
    }
  }
`;

export const DELETE_RUNBOOK = gql`
  mutation DeleteRunbook($id: ID!) {
    deleteRunbook(id: $id) {
      success
    }
  }
`;

export const EXECUTE_RUNBOOK = gql`
  mutation ExecuteRunbook($id: ID!, $parameters: JSON, $dryRun: Boolean) {
    executeRunbook(id: $id, parameters: $parameters, dryRun: $dryRun) {
      executionId
      status
      startedAt
    }
  }
`;

export const CANCEL_RUNBOOK_EXECUTION = gql`
  mutation CancelRunbookExecution($executionId: ID!) {
    cancelRunbookExecution(executionId: $executionId) {
      success
      message
    }
  }
`;

export const CREATE_SERVICE = gql`
  ${SERVICE_FRAGMENT}
  
  mutation CreateService($projectId: ID!, $input: CreateServiceInput!) {
    createService(projectId: $projectId, input: $input) {
      ...ServiceFields
    }
  }
`;

export const UPDATE_SERVICE = gql`
  ${SERVICE_FRAGMENT}
  
  mutation UpdateService($id: ID!, $input: UpdateServiceInput!) {
    updateService(id: $id, input: $input) {
      ...ServiceFields
    }
  }
`;

export const DELETE_SERVICE = gql`
  mutation DeleteService($id: ID!) {
    deleteService(id: $id) {
      success
    }
  }
`;

export const CREATE_ON_CALL_OVERRIDE = gql`
  mutation CreateOnCallOverride($scheduleId: ID!, $input: OnCallOverrideInput!) {
    createOnCallOverride(scheduleId: $scheduleId, input: $input) {
      id
      user {
        id
        name
      }
      startTime
      endTime
      reason
    }
  }
`;

export const DELETE_ON_CALL_OVERRIDE = gql`
  mutation DeleteOnCallOverride($overrideId: ID!) {
    deleteOnCallOverride(overrideId: $overrideId) {
      success
    }
  }
`;

export const CREATE_POSTMORTEM = gql`
  ${POSTMORTEM_FRAGMENT}
  
  mutation CreatePostmortem($incidentId: ID!) {
    createPostmortem(incidentId: $incidentId) {
      ...PostmortemFields
    }
  }
`;

export const UPDATE_POSTMORTEM = gql`
  ${POSTMORTEM_FRAGMENT}
  
  mutation UpdatePostmortem($id: ID!, $input: UpdatePostmortemInput!) {
    updatePostmortem(id: $id, input: $input) {
      ...PostmortemFields
    }
  }
`;

export const PUBLISH_POSTMORTEM = gql`
  ${POSTMORTEM_FRAGMENT}
  
  mutation PublishPostmortem($id: ID!) {
    publishPostmortem(id: $id) {
      ...PostmortemFields
    }
  }
`;

export const ADD_POSTMORTEM_ACTION_ITEM = gql`
  mutation AddPostmortemActionItem($postmortemId: ID!, $input: ActionItemInput!) {
    addPostmortemActionItem(postmortemId: $postmortemId, input: $input) {
      id
      title
      description
      assignee {
        id
        name
      }
      priority
      status
      dueDate
    }
  }
`;

export const UPDATE_ACTION_ITEM_STATUS = gql`
  mutation UpdateActionItemStatus($id: ID!, $status: ActionItemStatus!) {
    updateActionItemStatus(id: $id, status: $status) {
      id
      status
      completedAt
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_INCIDENT_UPDATES = gql`
  ${INCIDENT_FRAGMENT}
  
  subscription OnIncidentUpdate($incidentId: ID!) {
    incidentUpdated(incidentId: $incidentId) {
      type
      incident {
        ...IncidentFields
      }
      changedFields
    }
  }
`;

export const SUBSCRIBE_TO_WAR_ROOM = gql`
  subscription OnWarRoomMessage($incidentId: ID!) {
    warRoomMessage(incidentId: $incidentId) {
      id
      author {
        id
        name
        avatar
      }
      content
      timestamp
      type
    }
  }
`;

export const SUBSCRIBE_TO_ALERTS = gql`
  ${ALERT_FRAGMENT}
  
  subscription OnAlertChange($projectId: ID!) {
    alertChanged(projectId: $projectId) {
      type
      alert {
        ...AlertFields
      }
    }
  }
`;

export const SUBSCRIBE_TO_SERVICE_STATUS = gql`
  ${SERVICE_FRAGMENT}
  
  subscription OnServiceStatusChange($projectId: ID!) {
    serviceStatusChanged(projectId: $projectId) {
      ...ServiceFields
    }
  }
`;

export const SUBSCRIBE_TO_METRICS = gql`
  subscription OnMetricUpdate($projectId: ID!, $queries: [String!]!) {
    metricUpdated(projectId: $projectId, queries: $queries) {
      query
      value
      timestamp
      labels {
        key
        value
      }
    }
  }
`;

export const SUBSCRIBE_TO_LOG_STREAM = gql`
  ${LOG_ENTRY_FRAGMENT}
  
  subscription OnLogEntry($projectId: ID!, $filter: LogFilterInput) {
    logEntry(projectId: $projectId, filter: $filter) {
      ...LogEntryFields
    }
  }
`;

export const SUBSCRIBE_TO_RUNBOOK_EXECUTION = gql`
  subscription OnRunbookExecutionUpdate($executionId: ID!) {
    runbookExecutionUpdated(executionId: $executionId) {
      executionId
      status
      currentStep
      stepResults {
        stepId
        status
        output
        duration
        error
      }
      completedAt
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS
// =============================================================================

export interface CreateIncidentInput {
  title: string;
  description?: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  affectedServiceIds?: string[];
  commanderId?: string;
  tags?: Array<{ key: string; value: string }>;
}

export interface UpdateIncidentInput {
  title?: string;
  description?: string;
  severity?: string;
  priority?: string;
  affectedServiceIds?: string[];
  tags?: Array<{ key: string; value: string }>;
}

export interface TimelineEventInput {
  type: 'note' | 'status_change' | 'assignment' | 'action' | 'external';
  content: string;
  metadata?: Record<string, unknown>;
}

export interface CreateDashboardInput {
  name: string;
  description?: string;
  widgets?: WidgetInput[];
  variables?: Array<{
    name: string;
    type: string;
    defaultValue: string;
    options?: string[];
  }>;
  tags?: string[];
}

export interface UpdateDashboardInput {
  name?: string;
  description?: string;
  isDefault?: boolean;
  isShared?: boolean;
  variables?: Array<{
    name: string;
    type: string;
    defaultValue: string;
    options?: string[];
  }>;
  tags?: string[];
}

export interface WidgetInput {
  type: 'line_chart' | 'bar_chart' | 'gauge' | 'stat' | 'table' | 'heatmap' | 'logs';
  title: string;
  position: { x: number; y: number; width: number; height: number };
  config: {
    query: string;
    visualization?: Record<string, unknown>;
    thresholds?: Array<{ value: number; color: string; label?: string }>;
    refreshInterval?: number;
  };
}

export interface CreateRunbookInput {
  name: string;
  description?: string;
  category?: string;
  trigger?: 'manual' | 'alert' | 'schedule' | 'incident';
  steps: Array<{
    type: 'manual' | 'command' | 'script' | 'api' | 'approval';
    title: string;
    description?: string;
    command?: string;
    expectedOutput?: string;
    timeout?: number;
    onFailure?: 'stop' | 'continue' | 'skip';
  }>;
  parameters?: Array<{
    name: string;
    type: 'string' | 'number' | 'boolean' | 'select';
    required: boolean;
    defaultValue?: string;
    description?: string;
  }>;
  linkedServiceIds?: string[];
  tags?: string[];
}

export interface UpdateRunbookInput {
  name?: string;
  description?: string;
  category?: string;
  trigger?: string;
  steps?: Array<{
    id?: string;
    type: string;
    title: string;
    description?: string;
    command?: string;
    expectedOutput?: string;
    timeout?: number;
    onFailure?: string;
  }>;
  parameters?: Array<{
    name: string;
    type: string;
    required: boolean;
    defaultValue?: string;
    description?: string;
  }>;
  linkedServiceIds?: string[];
  tags?: string[];
}

export interface CreateServiceInput {
  name: string;
  description?: string;
  type: 'api' | 'web' | 'worker' | 'database' | 'cache' | 'queue' | 'external';
  ownerId?: string;
  teamId?: string;
  repository?: { url: string; branch?: string };
  endpoints?: Array<{
    name: string;
    url: string;
    healthCheck?: string;
  }>;
  dependencyIds?: string[];
  tags?: string[];
}

export interface UpdateServiceInput {
  name?: string;
  description?: string;
  type?: string;
  ownerId?: string;
  teamId?: string;
  repository?: { url: string; branch?: string };
  endpoints?: Array<{
    name: string;
    url: string;
    healthCheck?: string;
  }>;
  dependencyIds?: string[];
  tags?: string[];
}

export interface OnCallOverrideInput {
  userId: string;
  startTime: string;
  endTime: string;
  reason?: string;
}

export interface UpdatePostmortemInput {
  title?: string;
  summary?: string;
  impact?: {
    duration?: string;
    affectedUsers?: number;
    affectedServices?: string[];
    financialImpact?: number;
  };
  rootCauses?: Array<{
    id?: string;
    description: string;
    category: string;
    contributingFactors?: string[];
  }>;
  lessons?: Array<{
    id?: string;
    category: string;
    description: string;
    recommendation?: string;
  }>;
}

export interface ActionItemInput {
  title: string;
  description?: string;
  assigneeId?: string;
  priority: 'high' | 'medium' | 'low';
  dueDate?: string;
}

export interface MetricQueryInput {
  query: string;
  startTime: string;
  endTime: string;
  step?: string;
  aggregation?: 'avg' | 'sum' | 'min' | 'max' | 'count';
}

export interface LogQueryInput {
  query: string;
  startTime: string;
  endTime: string;
  services?: string[];
  levels?: string[];
  limit?: number;
  offset?: number;
}

export interface LogFilterInput {
  services?: string[];
  levels?: string[];
  search?: string;
}

export interface TimeRangeInput {
  start: string;
  end: string;
}

export interface IncidentFilter {
  status?: string[];
  severity?: string[];
  commanderId?: string;
  affectedServiceId?: string;
  search?: string;
  dateRange?: TimeRangeInput;
}

export interface AlertFilter {
  status?: string[];
  severity?: string[];
  serviceId?: string;
  silenced?: boolean;
  search?: string;
}

export interface DashboardFilter {
  ownerId?: string;
  isShared?: boolean;
  tags?: string[];
  search?: string;
}

export interface RunbookFilter {
  category?: string;
  trigger?: string;
  serviceId?: string;
  tags?: string[];
  search?: string;
}

export interface ServiceFilter {
  type?: string[];
  status?: string[];
  teamId?: string;
  tags?: string[];
  search?: string;
}

export type IncidentStatus = 'triggered' | 'acknowledged' | 'investigating' | 'mitigating' | 'resolved';
export type MessageType = 'message' | 'status_update' | 'action' | 'system';
export type ActionItemStatus = 'open' | 'in_progress' | 'completed' | 'cancelled';
