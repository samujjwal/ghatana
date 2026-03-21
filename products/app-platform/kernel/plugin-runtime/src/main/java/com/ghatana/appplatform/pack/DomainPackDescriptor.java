package com.ghatana.appplatform.pack;

/**
 * Implemented by every domain pack to declare its canonical manifest.
 *
 * <p>The runtime pack registry discovers all {@code DomainPackDescriptor} implementations
 * on the classpath (via Java ServiceLoader or DI scanning) and uses the returned
 * {@link DomainPackManifest} to:</p>
 * <ul>
 *   <li>Validate required kernel capabilities are available before activation</li>
 *   <li>Register exported contracts with the platform contract registry</li>
 *   <li>Wire declared workflows into the workflow engine</li>
 *   <li>Register UI contributions with the shell</li>
 *   <li>Enforce activation constraints per deployment mode</li>
 * </ul>
 *
 * <p>Implementations MUST be stateless — {@link #getManifest()} is called once at
 * startup and the result is cached by the registry.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * public class HealthcareDomainPackDescriptor implements DomainPackDescriptor {
 *     @Override
 *     public DomainPackManifest getManifest() { ... }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Descriptor interface — every domain pack must implement (Backlog D1)
 * @doc.layer product
 * @doc.pattern Strategy, ServiceLoader SPI
 * @author Ghatana AppPlatform Team
 * @since 2026.3.0
 */
public interface DomainPackDescriptor {

    /**
     * Returns the canonical manifest for this domain pack.
     *
     * <p>Must return a fully-populated, non-null {@link DomainPackManifest}.
     * The manifest is treated as immutable after first retrieval.</p>
     *
     * @return the domain pack manifest
     */
    DomainPackManifest getManifest();

    /**
     * Convenience accessor for the pack identity.
     *
     * @return the identity section from the manifest
     */
    default DomainPackManifest.Identity getIdentity() {
        return getManifest().identity();
    }
}
