package com.ghatana.agent.framework.coordination;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Manages task delegation from one agent to another.
 * Handles agent discovery, task routing, and result aggregation.
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Find suitable agents for tasks</li>
 *   <li>Route tasks to agents</li>
 *   <li>Track delegation status</li>
 *   <li>Aggregate results</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>{@code
 * DelegationManager delegationMgr = ...;
 * 
 * // Delegate task to specialist
 * Promise<Result> result = delegationMgr.delegate(
 *     DelegationRequest.builder()
 *         .fromAgent("ProductOwnerAgent")
 *         .toRole("architect")
 *         .task(architectureTask)
 *         .priority(Priority.HIGH)
 *         .build(),
 *     context
 * );
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Agent task delegation management
 * @doc.layer framework
 * @doc.pattern Mediator
 */
public interface DelegationManager {
    
    /**
     * Delegates a task to another agent.
     * 
     * @param request Delegation request
     * @param context Execution context
     * @param <TResult> Result type
     * @return Promise of result
     */
    @NotNull
    <TResult> Promise<TResult> delegate(
        @NotNull DelegationRequest<TResult> request,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Delegates tasks to multiple agents in parallel.
     * 
     * @param requests List of delegation requests
     * @param context Execution context
     * @param <TResult> Result type
     * @return Promise of result list (same order as requests)
     */
    @NotNull
    <TResult> Promise<List<TResult>> delegateParallel(
        @NotNull List<DelegationRequest<TResult>> requests,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Finds agents matching criteria.
     * 
     * @param criteria Search criteria
     * @param context Execution context
     * @return Promise of matching agents
     */
    @NotNull
    Promise<List<AgentInfo>> findAgents(
        @NotNull AgentCriteria criteria,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Gets status of a delegation.
     * 
     * @param delegationId Delegation ID
     * @param context Execution context
     * @return Promise of delegation status
     */
    @NotNull
    Promise<DelegationStatus> getStatus(
        @NotNull String delegationId,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
    
    /**
     * Cancels a pending delegation.
     * 
     * @param delegationId Delegation ID
     * @param context Execution context
     * @return Promise of cancellation result
     */
    @NotNull
    Promise<Boolean> cancel(
        @NotNull String delegationId,
        @NotNull com.ghatana.agent.framework.api.AgentContext context);
}
