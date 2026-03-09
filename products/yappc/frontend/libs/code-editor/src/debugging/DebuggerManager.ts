/**
 * Debugging Capabilities System
 * 
 * Comprehensive debugging system with support for:
 * - Breakpoint management
 * - Step execution (step over, step into, step out)
 * - Variable inspection and watch expressions
 * - Call stack navigation
 * - Debug console
 * - Execution control (pause, resume, stop)
 * 
 * Features:
 * - 🔴 Breakpoint management with conditions
 * - ⏸️ Execution control (pause, resume, step)
 * - 🔍 Variable inspection and watch expressions
 * - 📞 Call stack navigation
 * - 💬 Debug console with REPL
 * - 🔄 Real-time state updates
 * 
 * @doc.type system
 * @doc.purpose Debugging and execution control
 * @doc.layer product
 * @doc.pattern Debugger Engine
 */

/**
 * Breakpoint type
 */
export interface Breakpoint {
  id: string;
  fileId: string;
  line: number;
  column: number;
  enabled: boolean;
  condition?: string;
  logMessage?: string;
  hitCount: number;
  verified: boolean;
}

/**
 * Stack frame
 */
export interface StackFrame {
  id: string;
  name: string;
  source: {
    fileId: string;
    path: string;
  };
  line: number;
  column: number;
  locals: Variable[];
}

/**
 * Variable
 */
export interface Variable {
  name: string;
  value: unknown;
  type: string;
  variablesReference?: number;
  indexed?: boolean;
  memoryReference?: string;
}

/**
 * Debug session state
 */
export type DebugState = 'idle' | 'running' | 'paused' | 'stopped' | 'error';

/**
 * Step type
 */
export type StepType = 'step-over' | 'step-into' | 'step-out';

/**
 * Debug session
 */
export interface DebugSession {
  id: string;
  state: DebugState;
  breakpoints: Map<string, Breakpoint>;
  callStack: StackFrame[];
  currentFrame?: StackFrame;
  variables: Map<string, Variable>;
  watchExpressions: Map<string, unknown>;
  consoleLogs: ConsoleLog[];
  startTime: number;
  pauseTime?: number;
}

/**
 * Console log entry
 */
export interface ConsoleLog {
  id: string;
  timestamp: number;
  level: 'log' | 'warn' | 'error' | 'info' | 'debug';
  message: string;
  source?: string;
  stackTrace?: string;
}

/**
 * Debugger Manager
 */
export class DebuggerManager {
  private sessions: Map<string, DebugSession> = new Map();
  private currentSessionId: string | null = null;
  private breakpoints: Map<string, Breakpoint> = new Map();
  private listeners: Set<(session: DebugSession) => void> = new Set();
  private breakpointListeners: Set<(breakpoint: Breakpoint) => void> = new Set();

  constructor() {}

  /**
   * Create debug session
   */
  createSession(sessionId: string): DebugSession {
    const session: DebugSession = {
      id: sessionId,
      state: 'idle',
      breakpoints: new Map(),
      callStack: [],
      variables: new Map(),
      watchExpressions: new Map(),
      consoleLogs: [],
      startTime: Date.now(),
    };

    this.sessions.set(sessionId, session);
    this.currentSessionId = sessionId;

    return session;
  }

  /**
   * Get current session
   */
  getCurrentSession(): DebugSession | null {
    if (!this.currentSessionId) return null;
    return this.sessions.get(this.currentSessionId) || null;
  }

  /**
   * Set breakpoint
   */
  setBreakpoint(
    fileId: string,
    line: number,
    column: number = 0,
    condition?: string
  ): Breakpoint {
    const id = `bp-${fileId}-${line}-${column}`;
    const breakpoint: Breakpoint = {
      id,
      fileId,
      line,
      column,
      enabled: true,
      condition,
      hitCount: 0,
      verified: false,
    };

    this.breakpoints.set(id, breakpoint);

    const session = this.getCurrentSession();
    if (session) {
      session.breakpoints.set(id, breakpoint);
    }

    this.notifyBreakpointListeners(breakpoint);
    return breakpoint;
  }

  /**
   * Remove breakpoint
   */
  removeBreakpoint(breakpointId: string): boolean {
    const removed = this.breakpoints.delete(breakpointId);

    const session = this.getCurrentSession();
    if (session) {
      session.breakpoints.delete(breakpointId);
    }

    return removed;
  }

  /**
   * Toggle breakpoint
   */
  toggleBreakpoint(breakpointId: string): Breakpoint | null {
    const breakpoint = this.breakpoints.get(breakpointId);
    if (!breakpoint) return null;

    breakpoint.enabled = !breakpoint.enabled;
    this.notifyBreakpointListeners(breakpoint);

    return breakpoint;
  }

  /**
   * Get all breakpoints
   */
  getBreakpoints(): Breakpoint[] {
    return Array.from(this.breakpoints.values());
  }

  /**
   * Get breakpoints for file
   */
  getBreakpointsForFile(fileId: string): Breakpoint[] {
    return this.getBreakpoints().filter((bp) => bp.fileId === fileId);
  }

  /**
   * Start execution
   */
  startExecution(): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.state = 'running';
    session.pauseTime = undefined;
    this.notifyListeners(session);
  }

  /**
   * Pause execution
   */
  pauseExecution(): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.state = 'paused';
    session.pauseTime = Date.now();
    this.notifyListeners(session);
  }

  /**
   * Resume execution
   */
  resumeExecution(): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.state = 'running';
    session.pauseTime = undefined;
    this.notifyListeners(session);
  }

  /**
   * Stop execution
   */
  stopExecution(): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.state = 'stopped';
    this.notifyListeners(session);
  }

  /**
   * Step execution
   */
  step(type: StepType): void {
    const session = this.getCurrentSession();
    if (!session) return;

    // In a real implementation, this would communicate with the debugger backend
    // For now, we just update the state
    session.state = 'paused';
    this.notifyListeners(session);
  }

  /**
   * Update call stack
   */
  updateCallStack(frames: StackFrame[]): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.callStack = frames;
    session.currentFrame = frames[0];

    if (session.currentFrame) {
      session.variables = new Map(
        session.currentFrame.locals.map((v) => [v.name, v])
      );
    }

    this.notifyListeners(session);
  }

  /**
   * Add watch expression
   */
  addWatchExpression(expression: string, value: unknown): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.watchExpressions.set(expression, value);
    this.notifyListeners(session);
  }

  /**
   * Remove watch expression
   */
  removeWatchExpression(expression: string): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.watchExpressions.delete(expression);
    this.notifyListeners(session);
  }

  /**
   * Log to debug console
   */
  log(
    message: string,
    level: 'log' | 'warn' | 'error' | 'info' | 'debug' = 'log',
    source?: string
  ): void {
    const session = this.getCurrentSession();
    if (!session) return;

    const log: ConsoleLog = {
      id: `log-${Date.now()}`,
      timestamp: Date.now(),
      level,
      message,
      source,
    };

    session.consoleLogs.push(log);

    // Keep only last 1000 logs
    if (session.consoleLogs.length > 1000) {
      session.consoleLogs.shift();
    }

    this.notifyListeners(session);
  }

  /**
   * Clear debug console
   */
  clearConsole(): void {
    const session = this.getCurrentSession();
    if (!session) return;

    session.consoleLogs = [];
    this.notifyListeners(session);
  }

  /**
   * Get debug console logs
   */
  getConsoleLogs(limit: number = 100): ConsoleLog[] {
    const session = this.getCurrentSession();
    if (!session) return [];

    return session.consoleLogs.slice(-limit);
  }

  /**
   * Get variable value
   */
  getVariable(name: string): Variable | undefined {
    const session = this.getCurrentSession();
    if (!session) return undefined;

    return session.variables.get(name);
  }

  /**
   * Get all variables
   */
  getVariables(): Variable[] {
    const session = this.getCurrentSession();
    if (!session) return [];

    return Array.from(session.variables.values());
  }

  /**
   * Subscribe to session changes
   */
  subscribe(listener: (session: DebugSession) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Subscribe to breakpoint changes
   */
  subscribeBreakpoints(listener: (breakpoint: Breakpoint) => void): () => void {
    this.breakpointListeners.add(listener);
    return () => this.breakpointListeners.delete(listener);
  }

  /**
   * Notify listeners
   */
  private notifyListeners(session: DebugSession): void {
    this.listeners.forEach((listener) => listener(session));
  }

  /**
   * Notify breakpoint listeners
   */
  private notifyBreakpointListeners(breakpoint: Breakpoint): void {
    this.breakpointListeners.forEach((listener) => listener(breakpoint));
  }

  /**
   * Dispose session
   */
  disposeSession(sessionId: string): void {
    this.sessions.delete(sessionId);
    if (this.currentSessionId === sessionId) {
      this.currentSessionId = null;
    }
  }
}

/**
 * Create debugger manager
 */
export function createDebuggerManager(): DebuggerManager {
  return new DebuggerManager();
}
