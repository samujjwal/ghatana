package com.ghatana.virtualorg.framework.holon;

import com.ghatana.virtualorg.framework.agent.Agent;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base implementation of the Holon interface.
 *
 * <p><b>Purpose</b><br>
 * Provides a reusable base for holonic organizational units.
 * Handles the recursive structure, agent management, and basic
 * properties. Subclasses add domain-specific behavior.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * AbstractHolon department = new AbstractHolon("eng", "Engineering", HolonType.DEPARTMENT) {};
 * AbstractHolon team = new AbstractHolon("platform", "Platform Team", HolonType.TEAM) {};
 *
 * department.addChild(team).getResult();
 *
 * Agent alice = Agent.builder().id("alice").name("Alice").build();
 * team.addAgent(alice);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Base holonic unit implementation
 * @doc.layer platform
 * @doc.pattern Template Method
 */
public abstract class AbstractHolon implements Holon {

    private final String id;
    private final String name;
    private final HolonType type;
    private final AtomicReference<Holon> parent = new AtomicReference<>();
    private final List<Holon> children = new CopyOnWriteArrayList<>();
    private final List<Agent> agents = new CopyOnWriteArrayList<>();
    private final List<String> capabilities = new CopyOnWriteArrayList<>();
    private final Map<String, Object> properties = new LinkedHashMap<>();

    protected AbstractHolon(String id, String name, HolonType type) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public HolonType getType() {
        return type;
    }

    @Override
    public Optional<Holon> getParent() {
        return Optional.ofNullable(parent.get());
    }

    /**
     * Sets the parent holon.
     */
    public void setParent(Holon parent) {
        this.parent.set(parent);
    }

    @Override
    public List<Holon> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public List<Agent> getAgents() {
        return Collections.unmodifiableList(agents);
    }

    /**
     * Adds an agent to this holon.
     */
    public void addAgent(Agent agent) {
        agents.add(agent);
    }

    /**
     * Removes an agent from this holon.
     */
    public boolean removeAgent(String agentId) {
        return agents.removeIf(a -> a.getId().equals(agentId));
    }

    @Override
    public Promise<List<Agent>> getAllAgentsRecursive() {
        List<Agent> allAgents = new ArrayList<>(agents);
        for (Holon child : children) {
            // Synchronous call since we're in the same eventloop context
            allAgents.addAll(child.getAgents());
            if (child instanceof AbstractHolon) {
                allAgents.addAll(((AbstractHolon) child).getAllAgentsRecursive().getResult());
            }
        }
        return Promise.of(allAgents);
    }

    @Override
    public Promise<Void> addChild(Holon child) {
        children.add(child);
        if (child instanceof AbstractHolon) {
            ((AbstractHolon) child).setParent(this);
        }
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> removeChild(String childId) {
        return Promise.of(children.removeIf(c -> c.getId().equals(childId)));
    }

    @Override
    public List<String> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    /**
     * Adds a capability.
     */
    public void addCapability(String capability) {
        if (!capabilities.contains(capability)) {
            capabilities.add(capability);
        }
    }

    @Override
    public boolean canHandle(String taskType) {
        // Check own capabilities
        if (capabilities.contains(taskType)) {
            return true;
        }
        // Check agent capabilities
        for (Agent agent : agents) {
            for (String cap : agent.getFrameworkCapabilities()) {
                if (cap.equals(taskType)) {
                    return true;
                }
            }
        }
        // Check children
        for (Holon child : children) {
            if (child.canHandle(taskType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public Optional<Object> getProperty(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    @Override
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    @Override
    public double getLoad() {
        if (agents.isEmpty() && children.isEmpty()) {
            return 0.0;
        }

        // Calculate average load across agents
        double agentLoad = agents.stream()
                .mapToDouble(a -> a.getCurrentWorkload() / 10.0) // Normalize to 0-1
                .average()
                .orElse(0.0);

        // Calculate average load across children
        double childLoad = children.stream()
                .mapToDouble(Holon::getLoad)
                .average()
                .orElse(0.0);

        // Weighted average (if both present)
        if (!agents.isEmpty() && !children.isEmpty()) {
            return (agentLoad + childLoad) / 2.0;
        } else if (!agents.isEmpty()) {
            return agentLoad;
        } else {
            return childLoad;
        }
    }

    @Override
    public HolonHealth getHealth() {
        double load = getLoad();
        if (load >= 0.9) {
            return HolonHealth.OVERLOADED;
        } else if (load >= 0.7) {
            return HolonHealth.DEGRADED;
        } else {
            return HolonHealth.HEALTHY;
        }
    }

    /**
     * Checks if this holon is a root (has no parent).
     *
     * @return true if this holon has no parent
     */
    public boolean isRoot() {
        return parent.get() == null;
    }

    /**
     * Finds a holon by ID recursively.
     *
     * @param holonId the ID to search for
     * @return optional holon if found
     */
    public Optional<Holon> findById(String holonId) {
        if (id.equals(holonId)) {
            return Optional.of(this);
        }
        for (Holon child : children) {
            if (child.getId().equals(holonId)) {
                return Optional.of(child);
            }
            if (child instanceof AbstractHolon) {
                Optional<Holon> found = ((AbstractHolon) child).findById(holonId);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets all descendant holons recursively.
     *
     * @return list of all descendants
     */
    public List<Holon> getAllDescendants() {
        List<Holon> descendants = new ArrayList<>();
        collectDescendants(this, descendants);
        return descendants;
    }

    private void collectDescendants(Holon holon, List<Holon> list) {
        for (Holon child : holon.getChildren()) {
            list.add(child);
            if (child instanceof AbstractHolon) {
                collectDescendants(child, list);
            }
        }
    }

    /**
     * Gets the depth of this holon in the hierarchy.
     *
     * @return depth (0 for root)
     */
    public int getDepth() {
        int depth = 0;
        Holon current = parent.get();
        while (current != null) {
            depth++;
            if (current instanceof AbstractHolon) {
                current = ((AbstractHolon) current).parent.get();
            } else {
                current = current.getParent().orElse(null);
            }
        }
        return depth;
    }

    @Override
    public String toString() {
        return String.format("%s[%s:%s, agents=%d, children=%d]",
                getClass().getSimpleName(), type, name, agents.size(), children.size());
    }
}
