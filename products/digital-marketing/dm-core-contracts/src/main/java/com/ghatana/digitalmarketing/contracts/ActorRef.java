package com.ghatana.digitalmarketing.contracts;

import java.util.Objects;

/**
 * Typed wrapper for an actor reference (principal identity) in DMOS operations.
 *
 * <p>An {@code ActorRef} identifies who performed an action. It is populated from the
 * authenticated principal in the inbound security context and is required for audit,
 * approval routing, and authorization decisions.</p>
 *
 * @doc.type class
 * @doc.purpose Typed actor identity value object for DMOS audit and authorization
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class ActorRef {

    /** Special sentinel value for system-initiated operations with no human actor. */
    public static final ActorRef SYSTEM = new ActorRef("system", ActorType.SYSTEM);

    private final String principalId;
    private final ActorType type;

    private ActorRef(String principalId, ActorType type) {
        this.principalId = principalId;
        this.type = type;
    }

    /**
     * Creates an actor reference for a human user.
     *
     * @param principalId the authenticated principal identifier; must not be blank
     */
    public static ActorRef user(String principalId) {
        Objects.requireNonNull(principalId, "principalId must not be null");
        if (principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        return new ActorRef(principalId, ActorType.USER);
    }

    /**
     * Creates an actor reference for an automation agent.
     *
     * @param agentId the agent identifier; must not be blank
     */
    public static ActorRef agent(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        return new ActorRef(agentId, ActorType.AGENT);
    }

    /** Returns the principal identifier. Never {@code null} or blank. */
    public String getPrincipalId() {
        return principalId;
    }

    /** Returns the actor type. */
    public ActorType getType() {
        return type;
    }

    /**
     * Identifies the kind of actor performing an operation.
     */
    public enum ActorType {
        USER,
        AGENT,
        SYSTEM
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActorRef that = (ActorRef) o;
        return principalId.equals(that.principalId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(principalId, type);
    }

    @Override
    public String toString() {
        return "ActorRef{" + type + ":" + principalId + '}';
    }
}
