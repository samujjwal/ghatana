/**
 * Agent Executor
 * 
 * Executes agents safely within their defined contracts
 * 
 * @doc.type service
 * @doc.purpose Execute agents with contract enforcement
 * @doc.layer product
 * @doc.pattern Service
 */

import {
    AgentExecutionContract,
    AgentExecutionContext,
    AgentExecutionResult,
    AgentAction,
    AuditLogEntry,
    validateAgentExecution,
    createAuditLogEntry,
} from './AgentContract';

// ============================================================================
// Agent Executor
// ============================================================================

export class AgentExecutor {
    private auditLogs: AuditLogEntry[] = [];

    /**
     * Execute an agent within its contract
     */
    async executeAgent(
        contract: AgentExecutionContract,
        context: AgentExecutionContext,
        agentFunction: (ctx: AgentExecutionContext) => Promise<unknown>
    ): Promise<AgentExecutionResult> {
        const startTime = Date.now();

        try {
            // Validate execution context
            const validation = validateAgentExecution(contract, context);
            if (!validation.valid) {
                return {
                    success: false,
                    actions: [],
                    artifacts: {},
                    errors: validation.errors,
                    metadata: {
                        startTime,
                        endTime: Date.now(),
                        duration: Date.now() - startTime,
                    },
                };
            }

            // Execute with timeout
            const timeoutPromise = new Promise((_, reject) => {
                if (contract.maxExecutionTime) {
                    setTimeout(() => {
                        reject(new Error(`Agent execution exceeded ${contract.maxExecutionTime}ms`));
                    }, contract.maxExecutionTime);
                }
            });

            const executionPromise = agentFunction(context);

            const result = await Promise.race([executionPromise, timeoutPromise]);

            const endTime = Date.now();
            const duration = endTime - startTime;

            // Create audit log
            const auditLog = createAuditLogEntry(
                contract,
                'execute' as AgentAction,
                context,
                result,
                true
            );

            if (contract.auditLevel !== 'NONE') {
                this.auditLogs.push(auditLog);
            }

            return {
                success: true,
                actions: contract.allowedActions,
                artifacts: result,
                metadata: {
                    startTime,
                    endTime,
                    duration,
                },
                auditLog: contract.auditLevel === 'FULL' ? [auditLog] : undefined,
            };

        } catch (error) {
            const endTime = Date.now();
            const duration = endTime - startTime;

            // Log error
            const auditLog = createAuditLogEntry(
                contract,
                'execute' as AgentAction,
                context,
                null,
                false,
                error instanceof Error ? error.message : String(error)
            );

            if (contract.auditLevel !== 'NONE') {
                this.auditLogs.push(auditLog);
            }

            return {
                success: false,
                actions: [],
                artifacts: {},
                errors: [error instanceof Error ? error.message : String(error)],
                metadata: {
                    startTime,
                    endTime,
                    duration,
                },
                auditLog: contract.auditLevel === 'FULL' ? [auditLog] : undefined,
            };
        }
    }

    /**
     * Get audit logs
     */
    getAuditLogs(): AuditLogEntry[] {
        return [...this.auditLogs];
    }

    /**
     * Clear audit logs
     */
    clearAuditLogs(): void {
        this.auditLogs = [];
    }

    /**
     * Export audit logs for persistence
     */
    exportAuditLogs(): string {
        return JSON.stringify(this.auditLogs, null, 2);
    }
}

// ============================================================================
// Singleton Instance
// ============================================================================

export const agentExecutor = new AgentExecutor();
