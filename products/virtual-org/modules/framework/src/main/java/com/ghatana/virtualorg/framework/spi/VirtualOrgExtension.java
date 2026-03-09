package com.ghatana.virtualorg.framework.spi;

import com.ghatana.virtualorg.framework.VirtualOrgContext;
import io.activej.promise.Promise;

/**
 * Service Provider Interface for framework extensions.
 *
 * <p><b>Purpose</b><br>
 * VirtualOrgExtension is the main extension point for adding domain-specific
 * functionality to the Virtual-Org framework. Extensions are discovered via
 * Java ServiceLoader and initialized during framework bootstrap.
 *
 * <p><b>Extension Types</b><br>
 * - Agent factories (domain-specific agent creation)
 * - Ontology definitions (domain vocabulary)
 * - Norms (domain-specific rules)
 * - Task handlers (domain-specific task processing)
 * - Event processors (domain-specific event handling)
 *
 * <p><b>SPI Discovery</b><br>
 * Create a file at:
 * {@code META-INF/services/com.ghatana.virtualorg.framework.spi.VirtualOrgExtension}
 * containing the fully qualified class name of your extension.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class SoftwareOrgExtension implements VirtualOrgExtension {
 *     @Override
 *     public String getName() {
 *         return "software-org";
 *     }
 *
 *     @Override
 *     public Promise<Void> initialize(VirtualOrgContext context) {
 *         // Register agent factories
 *         context.getAgentRegistry().register(new SoftwareAgentFactory());
 *
 *         // Define domain ontology
 *         context.getOntology().define(
 *             Concept.builder("code-review", "CodeReview").parent("review").build()
 *         );
 *
 *         // Register domain norms
 *         context.getNormRegistry().register(
 *             Norm.obligation("respond-pr").action("review").build()
 *         );
 *
 *         return Promise.complete();
 *     }
 * }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose SPI for framework extensions
 * @doc.layer platform
 * @doc.pattern Plugin
 */
public interface VirtualOrgExtension {

    /**
     * Gets the unique name of this extension.
     *
     * @return extension name (e.g., "software-org", "healthcare-org")
     */
    String getName();

    /**
     * Gets the version of this extension.
     *
     * @return version string (e.g., "1.0.0")
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Gets the priority of this extension (higher = loaded first).
     *
     * @return priority (default 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Gets the dependencies of this extension.
     *
     * @return list of extension names this depends on
     */
    default java.util.List<String> getDependencies() {
        return java.util.List.of();
    }

    /**
     * Initializes the extension.
     *
     * <p>This is called during framework bootstrap. Extensions should:
     * <ul>
     *   <li>Register agent factories</li>
     *   <li>Define ontology concepts</li>
     *   <li>Register norms</li>
     *   <li>Set up event handlers</li>
     * </ul>
     *
     * @param context the framework context
     * @return promise completing when initialization is done
     */
    Promise<Void> initialize(VirtualOrgContext context);

    /**
     * Shuts down the extension.
     *
     * <p>Called during framework shutdown. Extensions should clean up resources.
     *
     * @return promise completing when shutdown is done
     */
    default Promise<Void> shutdown() {
        return Promise.complete();
    }

    /**
     * Health check for the extension.
     *
     * @return promise with true if healthy
     */
    default Promise<Boolean> healthCheck() {
        return Promise.of(true);
    }
}
