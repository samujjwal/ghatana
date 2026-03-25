package com.ghatana.flashit.plugin;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.plugin.KernelPlugin;
import com.ghatana.kernel.plugin.PluginManifest;
import io.activej.promise.Promise;

import java.util.Set;

/**
 * FlashIt kernel plugin implementation.
 *
 * <p>Registers FlashIt-specific capabilities with the kernel without creating
 * tight coupling between FlashIt and the kernel.</p>
 *
 * @doc.type class
 * @doc.purpose FlashIt plugin — registers capabilities and lifecycle with kernel
 * @doc.layer product
 * @doc.pattern Service
 */
public class FlashItProductPlugin implements KernelPlugin {

    private static final PluginManifest MANIFEST = PluginManifest.builder()
            .pluginId("flashit")
            .version("1.0.0")
            .description("Personal context capture platform with AI-powered reflection")
            .author("FlashIt Team")
            .capability(new KernelCapability(
                "moment.capture",
                "Moment Capture",
                "Capture and store multimedia moments",
                KernelCapability.CapabilityType.DATA_MANAGEMENT,
                java.util.Map.of(
                    "supported_formats", "text,image,audio,video",
                    "ai_classification", "true",
                    "storage_backend", "multimedia",
                    "required_services", "multimedia_processor,classification_service"
                )
            ))
            .capability(new KernelCapability(
                "reflection.engine",
                "Reflection Engine",
                "AI-powered reflection generation",
                KernelCapability.CapabilityType.AI_ML,
                java.util.Map.of(
                    "ai_models", "gpt-4,claude",
                    "reflection_types", "daily,weekly,monthly",
                    "personalization", "true",
                    "required_services", "ai_service,reflection_processor"
                )
            ))
            .capability(new KernelCapability(
                "context.search",
                "Context Search",
                "Semantic search across captured moments",
                KernelCapability.CapabilityType.AI_ML,
                java.util.Map.of(
                    "search_type", "semantic",
                    "embedding_model", "text-embedding-ada-002",
                    "indexing", "vector",
                    "required_services", "embedding_service,search_service"
                )
            ))
            .dependency(new KernelDependency("data.storage", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))
            .dependency(new KernelDependency("user.authentication", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))
            .dependency(new KernelDependency("ai.ml.framework", "1.0.0", KernelDependency.DependencyType.CAPABILITY, false))
            .dependency(new KernelDependency("openai-api", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, true))
            .dependency(new KernelDependency("anthropic-api", "1.0.0", KernelDependency.DependencyType.EXTERNAL_SERVICE, true))
            .build();
    
    @Override
    public PluginManifest getManifest() {
        return MANIFEST;
    }

    @Override
    public Set<String> getExportedContracts() {
        return Set.of();
    }

    @Override
    public Set<String> getRequiredContracts() {
        return Set.of();
    }

    @Override
    public Promise<Void> install() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> uninstall() {
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return HealthStatus.healthy("FlashIt plugin is operational");
    }

    @Override
    public Promise<Void> start() {
        System.out.println("Starting FlashIt moment capture service...");
        System.out.println("Starting FlashIt reflection engine...");
        System.out.println("Starting FlashIt context search...");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        System.out.println("Stopping FlashIt moment capture service...");
        System.out.println("Stopping FlashIt reflection engine...");
        System.out.println("Stopping FlashIt context search...");
        return Promise.complete();
    }
}
