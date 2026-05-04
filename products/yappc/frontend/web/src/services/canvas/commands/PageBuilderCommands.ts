/**
 * Page Builder Commands
 *
 * Semantic page-builder commands with undo/redo, audit, telemetry, validation, and autosave.
 *
 * @doc.type service
 * @doc.purpose Page builder command system
 * @doc.layer product
 */

export type CommandType = 
  | 'add-node'
  | 'remove-node'
  | 'update-node'
  | 'add-edge'
  | 'remove-edge'
  | 'move-node'
  | 'resize-node'
  | 'duplicate-node'
  | 'group-nodes'
  | 'ungroup-nodes'
  | 'change-layer'
  | 'align-nodes'
  | 'distribute-nodes'
  | 'import-document'
  | 'export-document'
  | 'validate-document'
  | 'save-document'
  | 'autosave-document';

export interface Command {
  id: string;
  type: CommandType;
  timestamp: string;
  userId?: string;
  nodeId?: string;
  data: unknown;
}

export interface CommandResult {
  success: boolean;
  error?: string;
  undoId?: string;
  validationErrors?: string[];
}

export class PageBuilderCommands {
  private commandHistory: Command[] = [];
  private undoStack: Command[] = [];
  private redoStack: Command[] = [];
  private autosaveTimer: NodeJS.Timeout | null = null;
  private autosaveInterval = 30000; // 30 seconds

  constructor(
    private onExecute: (command: Command) => Promise<CommandResult>,
    private onAudit: (command: Command, result: CommandResult) => void,
    private onTelemetry: (event: string, data: unknown) => void
  ) {}

  /**
   * Execute a command with full observability
   */
  async execute(command: Command): Promise<CommandResult> {
    const startTime = Date.now();
    
    try {
      const result = await this.onExecute(command);
      
      // Record command in history
      this.commandHistory.push(command);
      this.undoStack.push(command);
      this.redoStack = []; // Clear redo stack on new command
      
      // Audit logging
      this.onAudit(command, result);
      
      // Telemetry
      this.onTelemetry('command_executed', {
        type: command.type,
        duration: Date.now() - startTime,
        success: result.success,
      });
      
      // Trigger autosave
      this.scheduleAutosave();
      
      return result;
    } catch (error) {
      this.onTelemetry('command_failed', {
        type: command.type,
        error: error instanceof Error ? error.message : String(error),
      });
      
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Undo the last command
   */
  async undo(): Promise<CommandResult> {
    const command = this.undoStack.pop();
    if (!command) {
      return { success: false, error: 'No command to undo' };
    }

    const undoCommand: Command = {
      ...command,
      id: `undo-${command.id}`,
      timestamp: new Date().toISOString(),
    };

    const result = await this.execute(undoCommand);
    this.redoStack.push(command);

    this.onTelemetry('command_undone', {
      originalType: command.type,
      success: result.success,
    });

    return result;
  }

  /**
   * Redo the last undone command
   */
  async redo(): Promise<CommandResult> {
    const command = this.redoStack.pop();
    if (!command) {
      return { success: false, error: 'No command to redo' };
    }

    const result = await this.execute(command);
    this.undoStack.push(command);

    this.onTelemetry('command_redone', {
      type: command.type,
      success: result.success,
    });

    return result;
  }

  /**
   * Check if undo is available
   */
  canUndo(): boolean {
    return this.undoStack.length > 0;
  }

  /**
   * Check if redo is available
   */
  canRedo(): boolean {
    return this.redoStack.length > 0;
  }

  /**
   * Get command history
   */
  getHistory(): Command[] {
    return [...this.commandHistory];
  }

  /**
   * Clear command history
   */
  clearHistory(): void {
    this.commandHistory = [];
    this.undoStack = [];
    this.redoStack = [];
  }

  /**
   * Schedule autosave
   */
  private scheduleAutosave(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
    }

    this.autosaveTimer = setTimeout(() => {
      this.performAutosave();
    }, this.autosaveInterval);
  }

  /**
   * Perform autosave
   */
  private async performAutosave(): Promise<void> {
    const autosaveCommand: Command = {
      id: `autosave-${Date.now()}`,
      type: 'autosave-document',
      timestamp: new Date().toISOString(),
      data: { history: this.commandHistory },
    };

    try {
      await this.execute(autosaveCommand);
      this.onTelemetry('autosave_completed', {
        commandCount: this.commandHistory.length,
      });
    } catch (error) {
      this.onTelemetry('autosave_failed', {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  /**
   * Cleanup resources
   */
  destroy(): void {
    if (this.autosaveTimer) {
      clearTimeout(this.autosaveTimer);
      this.autosaveTimer = null;
    }
  }
}

export default PageBuilderCommands;
