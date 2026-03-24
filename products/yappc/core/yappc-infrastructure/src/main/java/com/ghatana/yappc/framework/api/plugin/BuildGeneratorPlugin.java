package com.ghatana.yappc.framework.api.plugin;

import com.ghatana.yappc.framework.api.domain.BuildSystemType;

/** Build file generation plugin contract used by framework integration. 
 * @doc.type interface
 * @doc.purpose Defines the contract for build generator plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface BuildGeneratorPlugin extends YappcPlugin {
  default boolean isEnabled() {
    return true;
  }

  default int getPriority(BuildSystemType buildSystemType) {
    return 0;
  }

  default BuildGeneratorCapabilities getCapabilities() {
    return BuildGeneratorCapabilities.empty();
  }

  /** Lightweight capabilities descriptor. */
  final class BuildGeneratorCapabilities {
    private final java.util.Set<String> supportedFeatures;

    public BuildGeneratorCapabilities(java.util.Set<String> supportedFeatures) {
      this.supportedFeatures =
          supportedFeatures == null ? java.util.Set.of() : java.util.Set.copyOf(supportedFeatures);
    }

    public java.util.Set<String> getSupportedFeatures() {
      return supportedFeatures;
    }

    public static BuildGeneratorCapabilities empty() {
      return new BuildGeneratorCapabilities(java.util.Set.of());
    }
  }
}
