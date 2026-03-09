/**
 * Bootstrapping Phase GraphQL Operations
 *
 * @description GraphQL queries, mutations, and subscriptions for the
 * bootstrapping phase including session management, AI conversation,
 * canvas operations, and collaboration features.
 *
 * @doc.type api
 * @doc.purpose Bootstrapping Phase API
 * @doc.layer data-access
 */

import { gql } from '@apollo/client';

// =============================================================================
// FRAGMENTS
// =============================================================================

export const BOOTSTRAP_SESSION_FRAGMENT = gql`
  fragment BootstrapSessionFields on BootstrapSession {
    id
    projectName
    description
    createdAt
    updatedAt
    status
    phase
    validationScore
    collaborators {
      id
      name
      email
      avatar
      role
      status
    }
    metadata {
      domain
      complexity
      estimatedDuration
      aiModel
    }
  }
`;

export const CONVERSATION_TURN_FRAGMENT = gql`
  fragment ConversationTurnFields on ConversationTurn {
    id
    role
    content
    timestamp
    tokens
    processingTime
    metadata {
      intent
      confidence
      suggestedActions
      extractedEntities
      codeBlocks {
        language
        code
        filename
      }
    }
    reactions {
      userId
      type
    }
  }
`;

export const CANVAS_NODE_FRAGMENT = gql`
  fragment CanvasNodeFields on CanvasNode {
    id
    type
    position {
      x
      y
    }
    data {
      label
      description
      icon
      color
      category
      properties
      validation {
        isValid
        errors
        warnings
      }
    }
    connections {
      sourceId
      sourceHandle
      targetId
      targetHandle
      label
    }
    locked
    createdBy
    createdAt
    updatedAt
  }
`;

export const VALIDATION_REPORT_FRAGMENT = gql`
  fragment ValidationReportFields on ValidationReport {
    sessionId
    timestamp
    overallScore
    status
    categories {
      name
      score
      weight
      items {
        id
        type
        severity
        message
        suggestion
        nodeId
        autoFixable
      }
    }
    statistics {
      totalNodes
      totalConnections
      missingFields
      invalidReferences
      optimizationOpportunities
    }
    recommendations {
      priority
      category
      message
      action
      impact
    }
  }
`;

// =============================================================================
// QUERIES
// =============================================================================

export const GET_BOOTSTRAP_SESSION = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  ${CANVAS_NODE_FRAGMENT}
  
  query GetBootstrapSession($id: ID!) {
    bootstrapSession(id: $id) {
      ...BootstrapSessionFields
      canvasState {
        nodes {
          ...CanvasNodeFields
        }
        viewport {
          x
          y
          zoom
        }
      }
    }
  }
`;

export const LIST_BOOTSTRAP_SESSIONS = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  
  query ListBootstrapSessions(
    $filter: BootstrapSessionFilter
    $pagination: PaginationInput
    $sort: SortInput
  ) {
    bootstrapSessions(filter: $filter, pagination: $pagination, sort: $sort) {
      edges {
        cursor
        node {
          ...BootstrapSessionFields
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

export const GET_CONVERSATION_HISTORY = gql`
  ${CONVERSATION_TURN_FRAGMENT}
  
  query GetConversationHistory(
    $sessionId: ID!
    $pagination: PaginationInput
  ) {
    conversationHistory(sessionId: $sessionId, pagination: $pagination) {
      edges {
        cursor
        node {
          ...ConversationTurnFields
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

export const GET_CANVAS_STATE = gql`
  ${CANVAS_NODE_FRAGMENT}
  
  query GetCanvasState($sessionId: ID!) {
    canvasState(sessionId: $sessionId) {
      nodes {
        ...CanvasNodeFields
      }
      edges {
        id
        source
        sourceHandle
        target
        targetHandle
        type
        data {
          label
          animated
        }
      }
      viewport {
        x
        y
        zoom
      }
      selectedNodeIds
      history {
        canUndo
        canRedo
        undoStackSize
        redoStackSize
      }
    }
  }
`;

export const GET_VALIDATION_REPORT = gql`
  ${VALIDATION_REPORT_FRAGMENT}
  
  query GetValidationReport($sessionId: ID!) {
    validationReport(sessionId: $sessionId) {
      ...ValidationReportFields
    }
  }
`;

export const GET_AI_SUGGESTIONS = gql`
  query GetAISuggestions($sessionId: ID!, $context: AISuggestionContext!) {
    aiSuggestions(sessionId: $sessionId, context: $context) {
      id
      type
      content
      confidence
      reasoning
      codeSnippet {
        language
        code
        filename
      }
      relatedNodes
      acceptedAt
      dismissedAt
    }
  }
`;

export const GET_SESSION_TEMPLATES = gql`
  query GetSessionTemplates($category: String, $search: String) {
    sessionTemplates(category: $category, search: $search) {
      id
      name
      description
      category
      icon
      preview {
        nodes
        complexity
        estimatedSetupTime
      }
      tags
      usageCount
      rating
    }
  }
`;

// =============================================================================
// MUTATIONS
// =============================================================================

export const CREATE_BOOTSTRAP_SESSION = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  
  mutation CreateBootstrapSession($input: CreateBootstrapSessionInput!) {
    createBootstrapSession(input: $input) {
      session {
        ...BootstrapSessionFields
      }
      welcomeMessage
      suggestedQuestions
    }
  }
`;

export const UPDATE_BOOTSTRAP_SESSION = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  
  mutation UpdateBootstrapSession($id: ID!, $input: UpdateBootstrapSessionInput!) {
    updateBootstrapSession(id: $id, input: $input) {
      ...BootstrapSessionFields
    }
  }
`;

export const DELETE_BOOTSTRAP_SESSION = gql`
  mutation DeleteBootstrapSession($id: ID!) {
    deleteBootstrapSession(id: $id) {
      success
      message
    }
  }
`;

export const SEND_CONVERSATION_MESSAGE = gql`
  ${CONVERSATION_TURN_FRAGMENT}
  
  mutation SendConversationMessage(
    $sessionId: ID!
    $input: ConversationMessageInput!
  ) {
    sendConversationMessage(sessionId: $sessionId, input: $input) {
      userMessage {
        ...ConversationTurnFields
      }
      aiResponse {
        ...ConversationTurnFields
      }
      canvasUpdates {
        type
        nodeId
        data
      }
      suggestedFollowUps
    }
  }
`;

export const REGENERATE_AI_RESPONSE = gql`
  ${CONVERSATION_TURN_FRAGMENT}
  
  mutation RegenerateAIResponse(
    $sessionId: ID!
    $messageId: ID!
    $options: RegenerateOptions
  ) {
    regenerateAIResponse(sessionId: $sessionId, messageId: $messageId, options: $options) {
      newResponse {
        ...ConversationTurnFields
      }
      canvasUpdates {
        type
        nodeId
        data
      }
    }
  }
`;

export const ADD_CANVAS_NODE = gql`
  ${CANVAS_NODE_FRAGMENT}
  
  mutation AddCanvasNode($sessionId: ID!, $input: AddCanvasNodeInput!) {
    addCanvasNode(sessionId: $sessionId, input: $input) {
      node {
        ...CanvasNodeFields
      }
      validation {
        isValid
        errors
        warnings
      }
    }
  }
`;

export const UPDATE_CANVAS_NODE = gql`
  ${CANVAS_NODE_FRAGMENT}
  
  mutation UpdateCanvasNode(
    $sessionId: ID!
    $nodeId: ID!
    $input: UpdateCanvasNodeInput!
  ) {
    updateCanvasNode(sessionId: $sessionId, nodeId: $nodeId, input: $input) {
      node {
        ...CanvasNodeFields
      }
      validation {
        isValid
        errors
        warnings
      }
    }
  }
`;

export const DELETE_CANVAS_NODE = gql`
  mutation DeleteCanvasNode($sessionId: ID!, $nodeId: ID!) {
    deleteCanvasNode(sessionId: $sessionId, nodeId: $nodeId) {
      success
      affectedEdges
    }
  }
`;

export const ADD_CANVAS_EDGE = gql`
  mutation AddCanvasEdge($sessionId: ID!, $input: AddCanvasEdgeInput!) {
    addCanvasEdge(sessionId: $sessionId, input: $input) {
      edge {
        id
        source
        sourceHandle
        target
        targetHandle
        type
        data {
          label
          animated
        }
      }
      validation {
        isValid
        errors
      }
    }
  }
`;

export const DELETE_CANVAS_EDGE = gql`
  mutation DeleteCanvasEdge($sessionId: ID!, $edgeId: ID!) {
    deleteCanvasEdge(sessionId: $sessionId, edgeId: $edgeId) {
      success
    }
  }
`;

export const UNDO_CANVAS_ACTION = gql`
  mutation UndoCanvasAction($sessionId: ID!) {
    undoCanvasAction(sessionId: $sessionId) {
      success
      restoredState {
        type
        data
      }
    }
  }
`;

export const REDO_CANVAS_ACTION = gql`
  mutation RedoCanvasAction($sessionId: ID!) {
    redoCanvasAction(sessionId: $sessionId) {
      success
      restoredState {
        type
        data
      }
    }
  }
`;

export const VALIDATE_SESSION = gql`
  ${VALIDATION_REPORT_FRAGMENT}
  
  mutation ValidateSession($sessionId: ID!, $options: ValidationOptions) {
    validateSession(sessionId: $sessionId, options: $options) {
      ...ValidationReportFields
    }
  }
`;

export const AUTO_FIX_VALIDATION_ISSUES = gql`
  mutation AutoFixValidationIssues($sessionId: ID!, $issueIds: [ID!]!) {
    autoFixValidationIssues(sessionId: $sessionId, issueIds: $issueIds) {
      fixedCount
      failedCount
      details {
        issueId
        success
        message
        nodeId
      }
    }
  }
`;

export const APPLY_SESSION_TEMPLATE = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  ${CANVAS_NODE_FRAGMENT}
  
  mutation ApplySessionTemplate($sessionId: ID!, $templateId: ID!) {
    applySessionTemplate(sessionId: $sessionId, templateId: $templateId) {
      session {
        ...BootstrapSessionFields
      }
      addedNodes {
        ...CanvasNodeFields
      }
    }
  }
`;

export const EXPORT_SESSION = gql`
  mutation ExportSession($sessionId: ID!, $format: ExportFormat!) {
    exportSession(sessionId: $sessionId, format: $format) {
      downloadUrl
      expiresAt
      format
      size
    }
  }
`;

export const IMPORT_SESSION = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  
  mutation ImportSession($input: ImportSessionInput!) {
    importSession(input: $input) {
      session {
        ...BootstrapSessionFields
      }
      warnings
    }
  }
`;

export const INVITE_COLLABORATOR = gql`
  mutation InviteCollaborator($sessionId: ID!, $input: InviteCollaboratorInput!) {
    inviteCollaborator(sessionId: $sessionId, input: $input) {
      collaborator {
        id
        name
        email
        avatar
        role
        status
        invitedAt
      }
      inviteLink
    }
  }
`;

export const REMOVE_COLLABORATOR = gql`
  mutation RemoveCollaborator($sessionId: ID!, $collaboratorId: ID!) {
    removeCollaborator(sessionId: $sessionId, collaboratorId: $collaboratorId) {
      success
    }
  }
`;

export const UPDATE_COLLABORATOR_ROLE = gql`
  mutation UpdateCollaboratorRole(
    $sessionId: ID!
    $collaboratorId: ID!
    $role: CollaboratorRole!
  ) {
    updateCollaboratorRole(
      sessionId: $sessionId
      collaboratorId: $collaboratorId
      role: $role
    ) {
      collaborator {
        id
        role
      }
    }
  }
`;

export const FINALIZE_BOOTSTRAP = gql`
  mutation FinalizeBootstrap($sessionId: ID!) {
    finalizeBootstrap(sessionId: $sessionId) {
      projectId
      initializationTaskId
      estimatedDuration
      nextSteps
    }
  }
`;

// =============================================================================
// SUBSCRIPTIONS
// =============================================================================

export const SUBSCRIBE_TO_SESSION_UPDATES = gql`
  ${BOOTSTRAP_SESSION_FRAGMENT}
  
  subscription OnSessionUpdate($sessionId: ID!) {
    sessionUpdated(sessionId: $sessionId) {
      type
      session {
        ...BootstrapSessionFields
      }
      changedFields
    }
  }
`;

export const SUBSCRIBE_TO_CONVERSATION = gql`
  ${CONVERSATION_TURN_FRAGMENT}
  
  subscription OnConversationUpdate($sessionId: ID!) {
    conversationUpdated(sessionId: $sessionId) {
      type
      message {
        ...ConversationTurnFields
      }
      isStreaming
      streamChunk
    }
  }
`;

export const SUBSCRIBE_TO_CANVAS_CHANGES = gql`
  ${CANVAS_NODE_FRAGMENT}
  
  subscription OnCanvasChange($sessionId: ID!) {
    canvasChanged(sessionId: $sessionId) {
      type
      userId
      userName
      node {
        ...CanvasNodeFields
      }
      edge {
        id
        source
        target
        type
      }
      viewport {
        x
        y
        zoom
      }
    }
  }
`;

export const SUBSCRIBE_TO_COLLABORATOR_PRESENCE = gql`
  subscription OnCollaboratorPresence($sessionId: ID!) {
    collaboratorPresence(sessionId: $sessionId) {
      userId
      userName
      avatar
      status
      cursor {
        x
        y
      }
      selectedNodes
      lastActivity
    }
  }
`;

export const SUBSCRIBE_TO_AI_THINKING = gql`
  subscription OnAIThinking($sessionId: ID!) {
    aiThinking(sessionId: $sessionId) {
      isThinking
      stage
      progress
      currentTask
      estimatedTimeRemaining
    }
  }
`;

export const SUBSCRIBE_TO_VALIDATION_UPDATES = gql`
  ${VALIDATION_REPORT_FRAGMENT}
  
  subscription OnValidationUpdate($sessionId: ID!) {
    validationUpdated(sessionId: $sessionId) {
      ...ValidationReportFields
    }
  }
`;

// =============================================================================
// TYPE DEFINITIONS (for TypeScript)
// =============================================================================

export interface BootstrapSessionFilter {
  status?: string[];
  phase?: string[];
  createdAfter?: string;
  createdBefore?: string;
  search?: string;
}

export interface CreateBootstrapSessionInput {
  projectName: string;
  description?: string;
  templateId?: string;
  domain?: string;
  aiModel?: string;
}

export interface UpdateBootstrapSessionInput {
  projectName?: string;
  description?: string;
  phase?: string;
  metadata?: Record<string, unknown>;
}

export interface ConversationMessageInput {
  content: string;
  attachments?: Array<{
    type: string;
    url: string;
    name: string;
  }>;
  context?: {
    selectedNodeIds?: string[];
    viewportArea?: { x: number; y: number; width: number; height: number };
  };
}

export interface AddCanvasNodeInput {
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
}

export interface UpdateCanvasNodeInput {
  position?: { x: number; y: number };
  data?: Record<string, unknown>;
  locked?: boolean;
}

export interface AddCanvasEdgeInput {
  source: string;
  sourceHandle?: string;
  target: string;
  targetHandle?: string;
  type?: string;
  data?: Record<string, unknown>;
}

export interface InviteCollaboratorInput {
  email: string;
  role: 'viewer' | 'editor' | 'admin';
  message?: string;
}

export interface ValidationOptions {
  strict?: boolean;
  categories?: string[];
  autoFix?: boolean;
}

export interface AISuggestionContext {
  selectedNodeIds?: string[];
  conversationContext?: boolean;
  suggestionTypes?: string[];
}

export interface RegenerateOptions {
  temperature?: number;
  maxTokens?: number;
  style?: string;
}

export interface ImportSessionInput {
  file: File;
  format: 'json' | 'yaml' | 'yappc';
  conflictResolution?: 'skip' | 'overwrite' | 'merge';
}

export type ExportFormat = 'json' | 'yaml' | 'pdf' | 'markdown';
export type CollaboratorRole = 'viewer' | 'editor' | 'admin';
