package com.ghatana.virtualorg.framework.runtime;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

/**
 * Interface for the agent runtime engine that implements the think-act-observe
 * loop.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for autonomous agent execution. Implementations manage:
 * - Event reception and queuing - LLM-based reasoning (think) - Tool/action
 * execution (act) - Outcome recording (observe) - State transitions
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentRuntime runtime = new DefaultAgentRuntime(config, llmGateway, toolRegistry);
 * runtime.start(agentContext);
 *
 * // Handle incoming event
 * runtime.handleEvent(event);
 *
 * // Check state
 * AgentState state = runtime.getState();
 *
 * // Pause/Resume
 * runtime.pause();
 * runtime.resume();
 *
 * // Stop
 * runtime.stop();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Agent autonomous execution runtime
 * @doc.layer product
 * @doc.pattern State Machine
 */
public interface AgentRuntime {

    /**
     * Starts the agent runtime with the given context.
     *
     * @param context The agent context
     * @return Promise completing when started
     */
    Promise<Void> start(AgentContext context);

    /**
     * Stops the agent runtime.
     *
     * @return Promise completing when stopped
     */
    Promise<Void> stop();

    /**
     * Pauses the agent runtime. The agent will finish current work but not
     * accept new events.
     */
    void pause();

    /**
     * Resumes a paused agent runtime.
     */
    void resume();

    /**
     * Handles an incoming event. The event will be queued and processed
     * according to the agent's state.
     *
     * @param event The event to handle
     * @return Promise completing when the event is queued (not processed)
     */
    Promise<Void> handleEvent(Event event);

    /**
     * Runs a single execution cycle: perceive → think → act → observe.
     *
     * @return Promise completing with the resulting state
     */
    Promise<AgentState> runCycle();

    /**
     * Gets the current agent state.
     *
     * @return The current state
     */
    AgentState getState();

    /**
     * Gets the agent context.
     *
     * @return The agent context
     */
    AgentContext getContext();

    /**
     * Gets the last decision made by the agent.
     *
     * @return The last decision, or null if none
     */
    AgentDecision getLastDecision();

    /**
     * Checks if the agent is running.
     *
     * @return true if the agent is active
     */
    default boolean isRunning() {
        AgentState state = getState();
        return state != AgentState.STOPPED && state != AgentState.PAUSED;
    }

    /**
     * Checks if the agent can accept new events.
     *
     * @return true if events can be queued
     */
    default boolean canAcceptEvents() {
        return getState().canAcceptEvents();
    }

    /**
     * Listener interface for runtime events.
     */
    interface RuntimeListener {

        /**
         * Called when the agent state changes.
         */
        default void onStateChange(AgentState oldState, AgentState newState) {
        }

        /**
         * Called when the agent makes a decision.
         */
        default void onDecision(AgentDecision decision) {
        }

        /**
         * Called when the agent executes an action.
         */
        default void onActionExecuted(AgentDecision.AgentAction action, Object result) {
        }

        /**
         * Called when an error occurs.
         */
        default void onError(Throwable error) {
        }
    }

    /**
     * Adds a runtime listener.
     *
     * @param listener The listener to add
     */
    void addListener(RuntimeListener listener);

    /**
     * Removes a runtime listener.
     *
     * @param listener The listener to remove
     */
    void removeListener(RuntimeListener listener);
}
