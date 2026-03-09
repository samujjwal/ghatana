/**
 * @ghatana/yappc-ide - Advanced Debugging Tools
 * 
 * Comprehensive debugging system with breakpoints, variable inspection,
 * call stack analysis, and performance profiling.
 * 
 * @doc.type component
 * @doc.purpose Advanced debugging tools for IDE
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, useEffect } from 'react';
import { useToastNotifications } from './Toast';
import { InteractiveButton } from './MicroInteractions';

/**
 * Breakpoint types
 */
export type BreakpointType = 'line' | 'conditional' | 'function' | 'exception';

/**
 * Breakpoint interface
 */
export interface Breakpoint {
  id: string;
  type: BreakpointType;
  file: string;
  line: number;
  column?: number;
  condition?: string;
  enabled: boolean;
  hitCount: number;
  logMessage?: string;
}

/**
 * Variable inspection interface
 */
export interface Variable {
  name: string;
  value: unknown;
  type: string;
  scope: 'local' | 'global' | 'closure' | 'this';
  children?: Variable[];
  expanded?: boolean;
}

/**
 * Call stack frame interface
 */
export interface CallStackFrame {
  id: string;
  function: string;
  file: string;
  line: number;
  column: number;
  scope: string;
}

/**
 * Debug session interface
 */
export interface DebugSession {
  id: string;
  name: string;
  status: 'running' | 'paused' | 'stopped' | 'error';
  currentFile?: string;
  currentLine?: number;
  breakpoints: Breakpoint[];
  variables: Variable[];
  callStack: CallStackFrame[];
  watchExpressions: WatchExpression[];
  startTime: Date;
  pausedTime?: number;
}

/**
 * Watch expression interface
 */
export interface WatchExpression {
  id: string;
  expression: string;
  value: unknown;
  error?: string;
  type: string;
}

/**
 * Performance profile interface
 */
export interface PerformanceProfile {
  id: string;
  name: string;
  duration: number;
  samples: PerformanceSample[];
  memoryUsage: MemoryUsage[];
  flameGraph: FlameGraphData[];
}

/**
 * Performance sample interface
 */
export interface PerformanceSample {
  timestamp: number;
  cpu: number;
  memory: number;
  function: string;
  file: string;
  line: number;
}

/**
 * Memory usage interface
 */
export interface MemoryUsage {
  timestamp: number;
  heapUsed: number;
  heapTotal: number;
  external: number;
  rss: number;
}

/**
 * Flame graph data interface
 */
export interface FlameGraphData {
  name: string;
  value: number;
  children: FlameGraphData[];
}

/**
 * Debugging tools props
 */
export interface DebuggingToolsProps {
  isVisible: boolean;
  onClose: () => void;
  currentFile?: {
    name: string;
    content: string;
    language: string;
  };
  onBreakpointToggle: (breakpoint: Breakpoint) => void;
  onStep: (action: 'over' | 'into' | 'out' | 'continue') => void;
  className?: string;
}

/**
 * Debugging Tools Component
 */
export const DebuggingTools: React.FC<DebuggingToolsProps> = ({
  isVisible,
  onClose,
  onBreakpointToggle,
  onStep,
  className = '',
}) => {
  const [activeTab, setActiveTab] = useState<'breakpoints' | 'variables' | 'callstack' | 'watch' | 'console' | 'performance'>('breakpoints');
  const [session, setSession] = useState<DebugSession | null>(null);
  const [isDebugging, setIsDebugging] = useState(false);
  const [selectedFrame, setSelectedFrame] = useState<string | null>(null);
  const [newWatchExpression, setNewWatchExpression] = useState('');
  const [isProfiling, setIsProfiling] = useState(false);
  const { success, info } = useToastNotifications();

  /**
   * Initialize debug session
   */
  const initializeSession = useCallback(() => {
    const newSession: DebugSession = {
      id: Math.random().toString(36).substr(2, 9),
      name: `Debug Session ${new Date().toLocaleTimeString()}`,
      status: 'running',
      breakpoints: [],
      variables: [],
      callStack: [],
      watchExpressions: [],
      startTime: new Date(),
    };
    setSession(newSession);
    setIsDebugging(true);
    success('Debug session started');
  }, [success]);

  /**
   * Stop debug session
   */
  const stopSession = useCallback(() => {
    setSession(null);
    setIsDebugging(false);
    setSelectedFrame(null);
    setIsProfiling(false);
    info('Debug session stopped');
  }, [info]);

  /**
   * Toggle breakpoint
   */
  const toggleBreakpoint = useCallback((breakpoint: Breakpoint) => {
    if (!session) return;

    const updatedBreakpoints = session.breakpoints.map(bp =>
      bp.id === breakpoint.id ? { ...bp, enabled: !bp.enabled } : bp
    );

    setSession(prev => prev ? { ...prev, breakpoints: updatedBreakpoints } : null);
    onBreakpointToggle(breakpoint);
    info(`Breakpoint ${breakpoint.enabled ? 'disabled' : 'enabled'}`);
  }, [session, onBreakpointToggle, info]);


  /**
   * Remove breakpoint
   */
  const removeBreakpoint = useCallback((breakpointId: string) => {
    if (!session) return;

    const updatedBreakpoints = session.breakpoints.filter(bp => bp.id !== breakpointId);
    setSession(prev => prev ? { ...prev, breakpoints: updatedBreakpoints } : null);
    info('Breakpoint removed');
  }, [session, info]);

  /**
   * Add watch expression
   */
  const addWatchExpression = useCallback(() => {
    if (!session || !newWatchExpression.trim()) return;

    const expression: WatchExpression = {
      id: Math.random().toString(36).substr(2, 9),
      expression: newWatchExpression,
      value: evaluateExpression(newWatchExpression),
      type: typeof evaluateExpression(newWatchExpression),
    };

    const updatedExpressions = [...session.watchExpressions, expression];
    setSession(prev => prev ? { ...prev, watchExpressions: updatedExpressions } : null);
    setNewWatchExpression('');
    success('Watch expression added');
  }, [session, newWatchExpression, success]);

  /**
   * Remove watch expression
   */
  const removeWatchExpression = useCallback((expressionId: string) => {
    if (!session) return;

    const updatedExpressions = session.watchExpressions.filter(expr => expr.id !== expressionId);
    setSession(prev => prev ? { ...prev, watchExpressions: updatedExpressions } : null);
    info('Watch expression removed');
  }, [session, info]);

  /**
   * Evaluate expression (mock implementation)
   */
  const evaluateExpression = (expression: string): unknown => {
    // Mock evaluation - in real implementation, this would use the debug adapter
    try {
      // Simple mock evaluation
      if (expression === 'true') return true;
      if (expression === 'false') return false;
      if (expression === 'null') return null;
      if (expression === 'undefined') return undefined;
      if (/^\d+$/.test(expression)) return parseInt(expression);
      if (/^\d+\.\d+$/.test(expression)) return parseFloat(expression);
      if (expression.startsWith('"') && expression.endsWith('"')) return expression.slice(1, -1);
      return `[${expression}]`;
    } catch {
      return 'Error evaluating expression';
    }
  };

  /**
   * Toggle variable expansion
   */
  const toggleVariableExpansion = useCallback((variablePath: string) => {
    if (!session) return;

    const updateVariableExpansion = (variables: Variable[]): Variable[] => {
      return variables.map(variable => {
        if (variable.name === variablePath) {
          return { ...variable, expanded: !variable.expanded };
        }
        if (variable.children) {
          return { ...variable, children: updateVariableExpansion(variable.children) };
        }
        return variable;
      });
    };

    setSession(prev => prev ? { ...prev, variables: updateVariableExpansion(prev.variables) } : null);
  }, [session]);

  /**
   * Start performance profiling
   */
  const startProfiling = useCallback(() => {
    setIsProfiling(true);
    info('Performance profiling started');
  }, [info]);

  /**
   * Stop performance profiling
   */
  const stopProfiling = useCallback(() => {
    setIsProfiling(false);
    success('Performance profiling completed');
  }, [success]);

  /**
   * Generate mock variables for demonstration
   */
  const generateMockVariables = (): Variable[] => [
    {
      name: 'this',
      value: { state: { count: 0 }, props: {} },
      type: 'Object',
      scope: 'this',
      expanded: false,
      children: [
        {
          name: 'state',
          value: { count: 0 },
          type: 'Object',
          scope: 'this',
          children: [
            {
              name: 'count',
              value: 0,
              type: 'number',
              scope: 'this',
            },
          ],
        },
        {
          name: 'props',
          value: {},
          type: 'Object',
          scope: 'this',
        },
      ],
    },
    {
      name: 'count',
      value: 42,
      type: 'number',
      scope: 'local',
    },
    {
      name: 'message',
      value: 'Hello, World!',
      type: 'string',
      scope: 'local',
    },
    {
      name: 'items',
      value: [1, 2, 3],
      type: 'Array',
      scope: 'local',
      expanded: false,
      children: [
        { name: '0', value: 1, type: 'number', scope: 'local' },
        { name: '1', value: 2, type: 'number', scope: 'local' },
        { name: '2', value: 3, type: 'number', scope: 'local' },
      ],
    },
  ];

  /**
   * Generate mock call stack
   */
  const generateMockCallStack = (): CallStackFrame[] => [
    {
      id: '1',
      function: 'handleClick',
      file: 'Button.tsx',
      line: 23,
      column: 15,
      scope: 'Component',
    },
    {
      id: '2',
      function: 'render',
      file: 'Button.tsx',
      line: 18,
      column: 5,
      scope: 'Component',
    },
    {
      id: '3',
      function: 'App',
      file: 'App.tsx',
      line: 45,
      column: 10,
      scope: 'Component',
    },
  ];

  // Update session with mock data when debugging
  useEffect(() => {
    if (isDebugging && session) {
      const timer = setTimeout(() => {
        setSession(prev => prev ? {
          ...prev,
          variables: generateMockVariables(),
          callStack: generateMockCallStack(),
        } : null);
      }, 500);
      return () => clearTimeout(timer);
    }
  }, [isDebugging, session]);

  if (!isVisible) return null;

  return (
    <div className={`fixed right-4 top-20 bottom-4 w-96 bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg shadow-2xl flex flex-col ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center space-x-2">
          <span className="text-xl">🐛</span>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            Debug Tools
          </h3>
          {session && (
            <div className={`w-2 h-2 rounded-full ${session.status === 'running' ? 'bg-green-500' :
                session.status === 'paused' ? 'bg-yellow-500' :
                  session.status === 'error' ? 'bg-red-500' : 'bg-gray-500'
              }`} />
          )}
        </div>
        <div className="flex items-center space-x-2">
          {isDebugging ? (
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={stopSession}
            >
              Stop
            </InteractiveButton>
          ) : (
            <InteractiveButton
              variant="primary"
              size="sm"
              onClick={initializeSession}
            >
              Start
            </InteractiveButton>
          )}
          <InteractiveButton
            variant="ghost"
            size="sm"
            onClick={onClose}
          >
            ×
          </InteractiveButton>
        </div>
      </div>

      {/* Session Info */}
      {session && (
        <div className="p-4 border-b border-gray-200 dark:border-gray-700">
          <div className="flex items-center justify-between mb-2">
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {session.name}
            </span>
            <span className="text-xs px-2 py-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 rounded">
              {session.status}
            </span>
          </div>

          {/* Debug Controls */}
          <div className="flex space-x-2">
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onStep('continue')}
              disabled={session.status !== 'paused'}
            >
              ▶️ Continue
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onStep('over')}
              disabled={session.status !== 'paused'}
            >
              ↗️ Step Over
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onStep('into')}
              disabled={session.status !== 'paused'}
            >
              ↘️ Step Into
            </InteractiveButton>
            <InteractiveButton
              variant="secondary"
              size="sm"
              onClick={() => onStep('out')}
              disabled={session.status !== 'paused'}
            >
              ↖️ Step Out
            </InteractiveButton>
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className="flex border-b border-gray-200 dark:border-gray-700">
        {[
          { id: 'breakpoints', label: 'Breakpoints', count: session?.breakpoints.length || 0 },
          { id: 'variables', label: 'Variables', count: session?.variables.length || 0 },
          { id: 'callstack', label: 'Call Stack', count: session?.callStack.length || 0 },
          { id: 'watch', label: 'Watch', count: session?.watchExpressions.length || 0 },
          { id: 'performance', label: 'Performance', count: isProfiling ? 1 : 0 },
        ].map(tab => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as 'breakpoints' | 'variables' | 'callstack' | 'watch' | 'console' | 'performance')}
            className={`
              flex-1 px-3 py-2 text-sm font-medium border-b-2 transition-colors
              ${activeTab === tab.id
                ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
              }
            `}
          >
            {tab.label}
            {tab.count > 0 && (
              <span className="ml-1 text-xs bg-gray-100 dark:bg-gray-800 px-1 rounded">
                {tab.count}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {activeTab === 'breakpoints' && (
          <div className="space-y-2">
            {!isDebugging ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-4xl mb-4">🎯</div>
                <p>Start debugging to manage breakpoints</p>
              </div>
            ) : (!session || session.breakpoints.length === 0) ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <p>No breakpoints set</p>
                <p className="text-sm mt-1">Click on line numbers to add breakpoints</p>
              </div>
            ) : (
              session.breakpoints.map(breakpoint => (
                <div
                  key={breakpoint.id}
                  className="flex items-center justify-between p-2 border border-gray-200 dark:border-gray-700 rounded"
                >
                  <div className="flex items-center space-x-2">
                    <input
                      type="checkbox"
                      checked={breakpoint.enabled}
                      onChange={() => toggleBreakpoint(breakpoint)}
                      className="rounded border-gray-300 dark:border-gray-600"
                    />
                    <div>
                      <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                        {breakpoint.file}:{breakpoint.line}
                      </div>
                      {breakpoint.condition && (
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          Condition: {breakpoint.condition}
                        </div>
                      )}
                      {breakpoint.hitCount > 0 && (
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          Hit count: {breakpoint.hitCount}
                        </div>
                      )}
                    </div>
                  </div>
                  <InteractiveButton
                    variant="ghost"
                    size="sm"
                    onClick={() => removeBreakpoint(breakpoint.id)}
                  >
                    ×
                  </InteractiveButton>
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === 'variables' && (
          <div className="space-y-2">
            {!isDebugging ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-4xl mb-4">📊</div>
                <p>Start debugging to inspect variables</p>
              </div>
            ) : (!session || session.variables.length === 0) ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <p>No variables in current scope</p>
              </div>
            ) : (
              <VariableTree
                variables={session.variables}
                onToggleExpansion={toggleVariableExpansion}
              />
            )}
          </div>
        )}

        {activeTab === 'callstack' && (
          <div className="space-y-2">
            {!isDebugging ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-4xl mb-4">📚</div>
                <p>Start debugging to view call stack</p>
              </div>
            ) : (!session || session.callStack.length === 0) ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <p>Call stack is empty</p>
              </div>
            ) : (
              session.callStack.map(frame => (
                <div
                  key={frame.id}
                  className={`p-3 border border-gray-200 dark:border-gray-700 rounded cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800 ${selectedFrame === frame.id ? 'bg-blue-50 dark:bg-blue-900/20 border-blue-500' : ''
                    }`}
                  onClick={() => setSelectedFrame(frame.id)}
                >
                  <div className="font-medium text-gray-900 dark:text-gray-100">
                    {frame.function}
                  </div>
                  <div className="text-sm text-gray-500 dark:text-gray-400">
                    {frame.file}:{frame.line}:{frame.column}
                  </div>
                  <div className="text-xs text-gray-400 dark:text-gray-500">
                    {frame.scope}
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {activeTab === 'watch' && (
          <div className="space-y-4">
            {!isDebugging ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-4xl mb-4">👁️</div>
                <p>Start debugging to add watch expressions</p>
              </div>
            ) : (
              <>
                {/* Add Watch Expression */}
                <div className="flex space-x-2">
                  <input
                    type="text"
                    value={newWatchExpression}
                    onChange={(e) => setNewWatchExpression(e.target.value)}
                    placeholder="Enter expression to watch..."
                    className="flex-1 px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-gray-800 dark:text-gray-100"
                    onKeyPress={(e) => e.key === 'Enter' && addWatchExpression()}
                  />
                  <InteractiveButton
                    variant="primary"
                    size="sm"
                    onClick={addWatchExpression}
                    disabled={!newWatchExpression.trim()}
                  >
                    Add
                  </InteractiveButton>
                </div>

                {/* Watch Expressions */}
                <div className="space-y-2">
                  {!session || session.watchExpressions.length === 0 ? (
                    <div className="text-center text-gray-500 dark:text-gray-400 py-4">
                      <p>No watch expressions</p>
                    </div>
                  ) : (
                    session.watchExpressions.map(expression => (
                      <div
                        key={expression.id}
                        className="p-3 border border-gray-200 dark:border-gray-700 rounded"
                      >
                        <div className="flex items-center justify-between mb-1">
                          <span className="font-mono text-sm text-gray-900 dark:text-gray-100">
                            {expression.expression}
                          </span>
                          <InteractiveButton
                            variant="ghost"
                            size="sm"
                            onClick={() => removeWatchExpression(expression.id)}
                          >
                            ×
                          </InteractiveButton>
                        </div>
                        {expression.error ? (
                          <div className="text-sm text-red-600 dark:text-red-400">
                            Error: {expression.error}
                          </div>
                        ) : (
                          <div className="text-sm text-gray-700 dark:text-gray-300">
                            <span className="text-gray-500 dark:text-gray-400">
                              {expression.type}:
                            </span>{' '}
                            {String(expression.value)}
                          </div>
                        )}
                      </div>
                    ))
                  )}
                </div>
              </>
            )}
          </div>
        )}

        {activeTab === 'performance' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h4 className="font-medium text-gray-900 dark:text-gray-100">Performance Profiling</h4>
              <InteractiveButton
                variant={isProfiling ? 'secondary' : 'primary'}
                size="sm"
                onClick={isProfiling ? stopProfiling : startProfiling}
              >
                {isProfiling ? 'Stop Profiling' : 'Start Profiling'}
              </InteractiveButton>
            </div>

            {!isProfiling ? (
              <div className="text-center text-gray-500 dark:text-gray-400 py-8">
                <div className="text-4xl mb-4">📈</div>
                <p>Start profiling to analyze performance</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
                  <div className="flex items-center space-x-2">
                    <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse" />
                    <span className="text-sm font-medium text-blue-900 dark:text-blue-100">
                      Profiling in progress...
                    </span>
                  </div>
                </div>

                {/* Mock Performance Metrics */}
                <div className="space-y-3">
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600 dark:text-gray-400">CPU Usage:</span>
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">45%</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600 dark:text-gray-400">Memory Usage:</span>
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">128 MB</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-gray-600 dark:text-gray-400">Execution Time:</span>
                    <span className="text-sm font-medium text-gray-900 dark:text-gray-100">2.3s</span>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

/**
 * Variable Tree Component
 */
const VariableTree: React.FC<{
  variables: Variable[];
  onToggleExpansion: (path: string) => void;
  level?: number;
}> = ({ variables, onToggleExpansion, level = 0 }) => {
  return (
    <div className={`space-y-1 ${level > 0 ? 'ml-4' : ''}`}>
      {variables.map(variable => (
        <div key={variable.name}>
          <div
            className="flex items-center space-x-2 p-1 hover:bg-gray-50 dark:hover:bg-gray-800 rounded cursor-pointer"
            onClick={() => {
              if (variable.children && variable.children.length > 0) {
                onToggleExpansion(variable.name);
              }
            }}
          >
            {variable.children && variable.children.length > 0 && (
              <span className="text-gray-400">
                {variable.expanded ? '▼' : '▶'}
              </span>
            )}
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {variable.name}
            </span>
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {variable.type}
            </span>
            <span className="text-sm text-gray-700 dark:text-gray-300 truncate">
              = {String(variable.value)}
            </span>
          </div>
          {variable.expanded && variable.children && (
            <VariableTree
              variables={variable.children}
              onToggleExpansion={onToggleExpansion}
              level={level + 1}
            />
          )}
        </div>
      ))}
    </div>
  );
};

/**
 * Debugging tools hook
 */
export const useDebuggingTools = () => {
  const [isVisible, setIsVisible] = useState(false);
  const [currentFile, setCurrentFile] = useState<{
    name: string;
    content: string;
    language: string;
    path: string;
  } | null>(null);

  const openTools = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    path: string;
  }) => {
    setCurrentFile(file ?? null);
    setIsVisible(true);
  }, []);

  const closeTools = useCallback(() => {
    setIsVisible(false);
  }, []);

  const toggleTools = useCallback((file?: {
    name: string;
    content: string;
    language: string;
    path: string;
  }) => {
    if (isVisible) {
      closeTools();
    } else {
      openTools(file);
    }
  }, [isVisible, openTools, closeTools]);

  return {
    isVisible,
    currentFile,
    openTools,
    closeTools,
    toggleTools,
  };
};

export default {
  DebuggingTools,
  useDebuggingTools,
};
