/**
 * VirtualAgentService
 * 
 * Manages Virtual Personas (AI Agents) that automatically fill unfilled roles.
 * These agents run background checks, post comments, create tasks, and can block deploys.
 * 
 * @doc.type service
 * @doc.purpose Virtual AI Agent management
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PersonaType } from '../context/PersonaContext';

// Agent action types
export type AgentActionType = 
  | 'comment'
  | 'create_task'
  | 'block_deploy'
  | 'approve'
  | 'warning'
  | 'suggestion'
  | 'auto_fix';

export type AgentActionSeverity = 'info' | 'warning' | 'error' | 'success';

export interface AgentAction {
  id: string;
  agentPersona: PersonaType;
  type: AgentActionType;
  severity: AgentActionSeverity;
  title: string;
  message: string;
  timestamp: Date;
  targetId?: string; // ID of the element/task this action relates to
  targetType?: 'node' | 'edge' | 'task' | 'project' | 'deploy';
  autoFixAvailable?: boolean;
  autoFixAction?: () => Promise<void>;
  dismissed?: boolean;
  requiresHumanOverride?: boolean;
}

export interface AgentCheckResult {
  passed: boolean;
  actions: AgentAction[];
}

export interface VirtualAgentConfig {
  persona: PersonaType;
  enabled: boolean;
  autoRun: boolean;
  checkInterval: number; // ms
  blocksOnError: boolean;
}

// Default agent configurations
const DEFAULT_AGENT_CONFIGS: Record<PersonaType, VirtualAgentConfig> = {
  'product-owner': {
    persona: 'product-owner',
    enabled: true,
    autoRun: true,
    checkInterval: 60000, // 1 minute
    blocksOnError: false,
  },
  'developer': {
    persona: 'developer',
    enabled: true,
    autoRun: true,
    checkInterval: 30000,
    blocksOnError: false,
  },
  'designer': {
    persona: 'designer',
    enabled: true,
    autoRun: true,
    checkInterval: 60000,
    blocksOnError: false,
  },
  'devops': {
    persona: 'devops',
    enabled: true,
    autoRun: true,
    checkInterval: 30000,
    blocksOnError: true,
  },
  'qa': {
    persona: 'qa',
    enabled: true,
    autoRun: true,
    checkInterval: 30000,
    blocksOnError: true,
  },
  'security': {
    persona: 'security',
    enabled: true,
    autoRun: true,
    checkInterval: 15000, // More frequent for security
    blocksOnError: true, // Security issues block deploys
  },
};

// Agent check functions
type AgentCheckFn = (context: AgentCheckContext) => Promise<AgentCheckResult>;

interface AgentCheckContext {
  projectId: string;
  canvasState: unknown;
  lifecyclePhase: string;
  pendingDeploy?: boolean;
}

// Security Agent Checks
async function securityAgentCheck(context: AgentCheckContext): Promise<AgentCheckResult> {
  const actions: AgentAction[] = [];
  const { canvasState, pendingDeploy } = context;

  // Check for exposed secrets in node data
  const secretPatterns = [
    /api[_-]?key/i,
    /secret/i,
    /password/i,
    /token/i,
    /credential/i,
  ];

  const elements = canvasState?.elements || [];
  for (const element of elements) {
    const dataStr = JSON.stringify(element.data || {});
    for (const pattern of secretPatterns) {
      if (pattern.test(dataStr)) {
        actions.push({
          id: `sec-${element.id}-${Date.now()}`,
          agentPersona: 'security',
          type: pendingDeploy ? 'block_deploy' : 'warning',
          severity: 'error',
          title: 'Potential Secret Exposure',
          message: `Element "${element.data?.label || element.id}" may contain sensitive data. Review before deploying.`,
          timestamp: new Date(),
          targetId: element.id,
          targetType: 'node',
          requiresHumanOverride: true,
        });
        break;
      }
    }
  }

  // Check for missing authentication on API nodes
  const apiNodes = elements.filter((el: unknown) => el.type === 'api');
  for (const apiNode of apiNodes) {
    if (!apiNode.data?.authentication) {
      actions.push({
        id: `sec-auth-${apiNode.id}-${Date.now()}`,
        agentPersona: 'security',
        type: 'warning',
        severity: 'warning',
        title: 'Missing Authentication',
        message: `API endpoint "${apiNode.data?.label || apiNode.id}" has no authentication configured.`,
        timestamp: new Date(),
        targetId: apiNode.id,
        targetType: 'node',
      });
    }
  }

  return {
    passed: actions.filter(a => a.severity === 'error').length === 0,
    actions,
  };
}

// QA Agent Checks
async function qaAgentCheck(context: AgentCheckContext): Promise<AgentCheckResult> {
  const actions: AgentAction[] = [];
  const { canvasState } = context;

  const elements = canvasState?.elements || [];

  // Check for components without tests
  const componentNodes = elements.filter((el: unknown) => el.type === 'component');
  for (const node of componentNodes) {
    if (!node.data?.hasTests) {
      actions.push({
        id: `qa-test-${node.id}-${Date.now()}`,
        agentPersona: 'qa',
        type: 'suggestion',
        severity: 'info',
        title: 'Missing Tests',
        message: `Component "${node.data?.label || node.id}" has no associated tests.`,
        timestamp: new Date(),
        targetId: node.id,
        targetType: 'node',
      });
    }
  }

  // Check for orphaned nodes (no connections)
  const connections = canvasState?.connections || [];
  const connectedIds = new Set<string>();
  connections.forEach((conn: unknown) => {
    connectedIds.add(conn.source);
    connectedIds.add(conn.target);
  });

  const orphanedNodes = elements.filter(
    (el: unknown) => el.kind !== 'shape' && !connectedIds.has(el.id)
  );

  if (orphanedNodes.length > 0) {
    actions.push({
      id: `qa-orphan-${Date.now()}`,
      agentPersona: 'qa',
      type: 'warning',
      severity: 'warning',
      title: 'Orphaned Components',
      message: `${orphanedNodes.length} component(s) have no connections. This may indicate incomplete design.`,
      timestamp: new Date(),
    });
  }

  return {
    passed: actions.filter(a => a.severity === 'error').length === 0,
    actions,
  };
}

// DevOps Agent Checks
async function devopsAgentCheck(context: AgentCheckContext): Promise<AgentCheckResult> {
  const actions: AgentAction[] = [];
  const { canvasState, lifecyclePhase, pendingDeploy } = context;

  // Check if ready for deployment
  if (pendingDeploy && lifecyclePhase !== 'RUN') {
    actions.push({
      id: `devops-phase-${Date.now()}`,
      agentPersona: 'devops',
      type: 'block_deploy',
      severity: 'error',
      title: 'Not Ready for Deploy',
      message: `Project is in ${lifecyclePhase} phase. Complete VALIDATE and GENERATE phases before deploying.`,
      timestamp: new Date(),
      requiresHumanOverride: true,
    });
  }

  // Check for environment configuration
  const elements = canvasState?.elements || [];
  const apiNodes = elements.filter((el: unknown) => el.type === 'api');
  
  for (const apiNode of apiNodes) {
    if (!apiNode.data?.environment) {
      actions.push({
        id: `devops-env-${apiNode.id}-${Date.now()}`,
        agentPersona: 'devops',
        type: 'suggestion',
        severity: 'info',
        title: 'Missing Environment Config',
        message: `API "${apiNode.data?.label || apiNode.id}" has no environment configuration.`,
        timestamp: new Date(),
        targetId: apiNode.id,
        targetType: 'node',
      });
    }
  }

  return {
    passed: actions.filter(a => a.severity === 'error').length === 0,
    actions,
  };
}

// Designer Agent Checks
async function designerAgentCheck(context: AgentCheckContext): Promise<AgentCheckResult> {
  const actions: AgentAction[] = [];
  const { canvasState } = context;

  const elements = canvasState?.elements || [];
  const pageNodes = elements.filter((el: unknown) => el.type === 'page');

  // Check for pages without components
  for (const page of pageNodes) {
    if (!page.data?.components || page.data.components.length === 0) {
      actions.push({
        id: `design-empty-${page.id}-${Date.now()}`,
        agentPersona: 'designer',
        type: 'suggestion',
        severity: 'info',
        title: 'Empty Page',
        message: `Page "${page.data?.label || page.id}" has no UI components. Consider adding a layout.`,
        timestamp: new Date(),
        targetId: page.id,
        targetType: 'node',
      });
    }
  }

  // Check for accessibility concerns
  const componentNodes = elements.filter((el: unknown) => el.type === 'component');
  for (const node of componentNodes) {
    if (!node.data?.ariaLabel && node.data?.type === 'button') {
      actions.push({
        id: `design-a11y-${node.id}-${Date.now()}`,
        agentPersona: 'designer',
        type: 'suggestion',
        severity: 'warning',
        title: 'Missing Accessibility Label',
        message: `Button "${node.data?.label || node.id}" is missing an aria-label.`,
        timestamp: new Date(),
        targetId: node.id,
        targetType: 'node',
      });
    }
  }

  return {
    passed: true,
    actions,
  };
}

// Product Owner Agent Checks
async function productOwnerAgentCheck(context: AgentCheckContext): Promise<AgentCheckResult> {
  const actions: AgentAction[] = [];
  const { canvasState, lifecyclePhase } = context;

  const elements = canvasState?.elements || [];

  // Check for components without descriptions/acceptance criteria
  const componentNodes = elements.filter((el: unknown) => 
    el.kind === 'component' || el.kind === 'node'
  );

  const undocumented = componentNodes.filter(
    (node: unknown) => !node.data?.description && !node.data?.acceptanceCriteria
  );

  if (undocumented.length > 0 && lifecyclePhase === 'SHAPE') {
    actions.push({
      id: `po-docs-${Date.now()}`,
      agentPersona: 'product-owner',
      type: 'suggestion',
      severity: 'info',
      title: 'Missing Requirements',
      message: `${undocumented.length} component(s) lack descriptions or acceptance criteria.`,
      timestamp: new Date(),
    });
  }

  return {
    passed: true,
    actions,
  };
}

// Agent check registry
const AGENT_CHECKS: Record<PersonaType, AgentCheckFn> = {
  'security': securityAgentCheck,
  'qa': qaAgentCheck,
  'devops': devopsAgentCheck,
  'designer': designerAgentCheck,
  'product-owner': productOwnerAgentCheck,
  'developer': async () => ({ passed: true, actions: [] }), // Developer agent is passive
};

/**
 * VirtualAgentService class
 */
export class VirtualAgentService {
  private configs: Map<PersonaType, VirtualAgentConfig> = new Map();
  private actions: AgentAction[] = [];
  private intervals: Map<PersonaType, NodeJS.Timeout> = new Map();
  private listeners: Set<(actions: AgentAction[]) => void> = new Set();
  private checkContext: AgentCheckContext | null = null;

  constructor() {
    // Initialize with default configs
    Object.entries(DEFAULT_AGENT_CONFIGS).forEach(([persona, config]) => {
      this.configs.set(persona as PersonaType, { ...config });
    });
  }

  /**
   * Set the context for agent checks
   */
  setContext(context: AgentCheckContext): void {
    this.checkContext = context;
  }

  /**
   * Enable a virtual agent
   */
  enableAgent(persona: PersonaType): void {
    const config = this.configs.get(persona);
    if (config) {
      config.enabled = true;
      this.configs.set(persona, config);
    }
  }

  /**
   * Disable a virtual agent
   */
  disableAgent(persona: PersonaType): void {
    const config = this.configs.get(persona);
    if (config) {
      config.enabled = false;
      this.configs.set(persona, config);
      this.stopAgentChecks(persona);
    }
  }

  /**
   * Start automatic checks for an agent
   */
  startAgentChecks(persona: PersonaType): void {
    const config = this.configs.get(persona);
    if (!config || !config.enabled || !config.autoRun) return;

    // Clear existing interval
    this.stopAgentChecks(persona);

    // Start new interval
    const interval = setInterval(async () => {
      await this.runAgentCheck(persona);
    }, config.checkInterval);

    this.intervals.set(persona, interval);

    // Run immediately
    this.runAgentCheck(persona);
  }

  /**
   * Stop automatic checks for an agent
   */
  stopAgentChecks(persona: PersonaType): void {
    const interval = this.intervals.get(persona);
    if (interval) {
      clearInterval(interval);
      this.intervals.delete(persona);
    }
  }

  /**
   * Run a single check for an agent
   */
  async runAgentCheck(persona: PersonaType): Promise<AgentCheckResult> {
    if (!this.checkContext) {
      return { passed: true, actions: [] };
    }

    const checkFn = AGENT_CHECKS[persona];
    if (!checkFn) {
      return { passed: true, actions: [] };
    }

    try {
      const result = await checkFn(this.checkContext);
      
      // Add new actions (avoid duplicates)
      const existingIds = new Set(this.actions.map(a => a.id));
      const newActions = result.actions.filter(a => !existingIds.has(a.id));
      
      if (newActions.length > 0) {
        this.actions = [...this.actions, ...newActions];
        this.notifyListeners();
      }

      return result;
    } catch (error) {
      console.error(`Agent check failed for ${persona}:`, error);
      return { passed: true, actions: [] };
    }
  }

  /**
   * Run all enabled agent checks
   */
  async runAllChecks(): Promise<Map<PersonaType, AgentCheckResult>> {
    const results = new Map<PersonaType, AgentCheckResult>();

    for (const [persona, config] of this.configs) {
      if (config.enabled) {
        const result = await this.runAgentCheck(persona);
        results.set(persona, result);
      }
    }

    return results;
  }

  /**
   * Check if deploy is blocked by any agent
   */
  async checkDeployBlocked(): Promise<{ blocked: boolean; blockers: AgentAction[] }> {
    if (this.checkContext) {
      this.checkContext.pendingDeploy = true;
    }

    await this.runAllChecks();

    const blockers = this.actions.filter(
      a => a.type === 'block_deploy' && !a.dismissed
    );

    return {
      blocked: blockers.length > 0,
      blockers,
    };
  }

  /**
   * Get all actions
   */
  getActions(): AgentAction[] {
    return [...this.actions];
  }

  /**
   * Get actions by persona
   */
  getActionsByPersona(persona: PersonaType): AgentAction[] {
    return this.actions.filter(a => a.agentPersona === persona);
  }

  /**
   * Get undismissed actions
   */
  getActiveActions(): AgentAction[] {
    return this.actions.filter(a => !a.dismissed);
  }

  /**
   * Dismiss an action
   */
  dismissAction(actionId: string): void {
    const action = this.actions.find(a => a.id === actionId);
    if (action) {
      action.dismissed = true;
      this.notifyListeners();
    }
  }

  /**
   * Override a blocking action (human override)
   */
  overrideAction(actionId: string): void {
    const action = this.actions.find(a => a.id === actionId);
    if (action && action.requiresHumanOverride) {
      action.dismissed = true;
      this.notifyListeners();
    }
  }

  /**
   * Clear all actions
   */
  clearActions(): void {
    this.actions = [];
    this.notifyListeners();
  }

  /**
   * Subscribe to action updates
   */
  subscribe(listener: (actions: AgentAction[]) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Notify all listeners
   */
  private notifyListeners(): void {
    const actions = this.getActions();
    this.listeners.forEach(listener => listener(actions));
  }

  /**
   * Cleanup
   */
  destroy(): void {
    this.intervals.forEach((_, persona) => this.stopAgentChecks(persona));
    this.listeners.clear();
    this.actions = [];
  }
}

// Singleton instance
let virtualAgentServiceInstance: VirtualAgentService | null = null;

export function getVirtualAgentService(): VirtualAgentService {
  if (!virtualAgentServiceInstance) {
    virtualAgentServiceInstance = new VirtualAgentService();
  }
  return virtualAgentServiceInstance;
}

export default VirtualAgentService;
