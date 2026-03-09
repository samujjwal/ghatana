/**
 * Represents the status of an agent
 */
export type AgentStatus = 'online' | 'offline' | 'busy' | 'error';

/**
 * Represents the role of an agent
 */
export type AgentRole = 
  | 'developer' 
  | 'designer' 
  | 'qa' 
  | 'product_manager' 
  | 'system_architect' 
  | 'devops' 
  | 'data_scientist' 
  | string;

/**
 * Represents an agent in the system
 */
export interface Agent {
  /** Unique identifier for the agent */
  id: string;
  
  /** Display name of the agent */
  name: string;
  
  /** Role of the agent */
  role: AgentRole;
  
  /** Current status of the agent */
  status: AgentStatus;
  
  /** Skills or capabilities of the agent */
  skills: string[];
  
  /** Timestamp of when the agent was last active */
  lastActive: string;
  
  /** Optional description of the agent */
  description?: string;
  
  /** Configuration specific to the agent's role */
  config?: Record<string, unknown>;
  
  /** Metadata about the agent */
  metadata?: {
    createdAt: string;
    updatedAt: string;
    createdBy?: string;
    version?: string;
  };
}

/**
 * Represents a task that can be executed by agents
 */
export interface AgentTask {
  /** Unique identifier for the task */
  id: string;
  
  /** Description of the task */
  description: string;
  
  /** Current status of the task */
  status: 'pending' | 'in_progress' | 'completed' | 'failed';
  
  /** IDs of agents assigned to this task */
  agentIds: string[];
  
  /** Result of the task execution, if completed */
  result?: unknown;
  
  /** Error information, if the task failed */
  error?: {
    message: string;
    code?: string;
    details?: unknown;
  };
  
  /** Timestamps for task lifecycle */
  timestamps: {
    createdAt: string;
    startedAt?: string;
    completedAt?: string;
  };
  
  /** Metadata about the task */
  metadata?: Record<string, unknown>;
}

/**
 * Represents a message in a conversation between agents
 */
export interface AgentMessage {
  /** Unique identifier for the message */
  id: string;
  
  /** ID of the agent who sent the message */
  senderId: string;
  
  /** ID of the conversation this message belongs to */
  conversationId: string;
  
  /** Content of the message */
  content: string;
  
  /** Type of the message */
  type: 'text' | 'code' | 'data' | 'error' | 'system';
  
  /** Timestamp of when the message was sent */
  timestamp: string;
  
  /** Metadata about the message */
  metadata?: {
    [key: string]: unknown;
  };
}
