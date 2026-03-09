package com.ghatana.virtualorg.framework.holon;

import com.ghatana.virtualorg.framework.agent.Agent;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for a Holon - a recursive organizational unit.
 *
 * <p><b>Purpose</b><br>
 * A Holon is both a whole (autonomous unit) and a part (of a larger system).
 * This concept, from Holonic Manufacturing Systems, enables organizations
 * to be modeled as recursive, self-similar structures.
 *
 * <p><b>Key Properties</b><br>
 * - **Autonomy**: Can make independent decisions within its authority
 * - **Cooperation**: Can negotiate and contract with other holons
 * - **Self-Organization**: Can reorganize internally in response to changes
 * - **Hierarchy**: Contains sub-holons and belongs to super-holons
 *
 * <p><b>Examples</b><br>
 * - A "Department" is a holon containing "Team" holons
 * - A "Team" is a holon containing "Agent" holons
 * - An "Agent" is a terminal holon
 *
 * @doc.type interface
 * @doc.purpose Holonic organizational unit contract
 * @doc.layer platform
 * @doc.pattern Composite
 */
public interface Holon {

    /**
     * Gets the unique identifier.
     */
    String getId();

    /**
     * Gets the display name.
     */
    String getName();

    /**
     * Gets the holon type.
     */
    HolonType getType();

    /**
     * Gets the parent holon (if any).
     */
    Optional<Holon> getParent();

    /**
     * Gets child holons.
     */
    List<Holon> getChildren();

    /**
     * Gets agents directly in this holon.
     */
    List<Agent> getAgents();

    /**
     * Gets all agents recursively (including sub-holons).
     */
    Promise<List<Agent>> getAllAgentsRecursive();

    /**
     * Adds a child holon.
     */
    Promise<Void> addChild(Holon child);

    /**
     * Removes a child holon.
     */
    Promise<Boolean> removeChild(String childId);

    /**
     * Gets the capabilities of this holon.
     */
    List<String> getCapabilities();

    /**
     * Checks if this holon can handle a task type.
     */
    boolean canHandle(String taskType);

    /**
     * Gets properties/metadata.
     */
    Map<String, Object> getProperties();

    /**
     * Gets a property value.
     */
    Optional<Object> getProperty(String key);

    /**
     * Sets a property.
     */
    void setProperty(String key, Object value);

    /**
     * Gets the current load/utilization (0.0 to 1.0).
     */
    double getLoad();

    /**
     * Gets the health status.
     */
    HolonHealth getHealth();

    /**
     * Holon types.
     */
    enum HolonType {
        ORGANIZATION,   // Top-level holon
        DEPARTMENT,     // Organizational unit
        TEAM,           // Working group
        AGENT           // Individual contributor (terminal holon)
    }

    /**
     * Holon health status.
     */
    enum HolonHealth {
        HEALTHY,        // Operating normally
        DEGRADED,       // Reduced capacity
        OVERLOADED,     // At or over capacity
        OFFLINE         // Not operational
    }
}
