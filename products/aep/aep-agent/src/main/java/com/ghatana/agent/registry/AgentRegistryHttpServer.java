package com.ghatana.agent.registry;

/**
 * <b>REMOVED</b> — Agent registry endpoints are now centralized in
 * {@code AepCentralRegistryService} and exposed via the AEP server's
 * {@code AgentController}.
 *
 * <p>This tombstone class exists only to produce a clear compile error if any
 * downstream code still references the old HTTP server. Migrate to
 * {@code AepCentralRegistryService} immediately.
 *
 * @see com.ghatana.aep.runtime.AepCentralRegistryService
 * @see docs/AGENT_REGISTRY_MIGRATION_GUIDE.md
 *
 * @doc.type class
 * @doc.purpose Tombstone for removed agent registry HTTP server
 * @doc.layer product
 * @doc.pattern Tombstone
 */
@Deprecated(since = "2026.3", forRemoval = true)
public final class AgentRegistryHttpServer {
    private AgentRegistryHttpServer() {
        throw new UnsupportedOperationException(
            "AgentRegistryHttpServer has been removed. "
            + "Use AepCentralRegistryService instead. "
            + "See docs/AGENT_REGISTRY_MIGRATION_GUIDE.md");
    }
}
