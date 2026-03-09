/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.scaffold;

import java.nio.file.Path;
import java.util.Map;

/**
 * GenerationContext.
 *
 * @doc.type record
 * @doc.purpose generation context
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record GenerationContext(
    Template template,
    FeaturePack featurePack,
    String projectName,
    String packageName,
    Path outputPath,
    Path projectPath,
    Map<String, Object> configuration
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Template template;
        private FeaturePack featurePack;
        private String projectName;
        private String packageName;
        private Path outputPath;
        private Path projectPath;
        private Map<String, Object> configuration;

        public Builder template(Template template) { this.template = template; return this; }
        public Builder featurePack(FeaturePack featurePack) { this.featurePack = featurePack; return this; }
        public Builder projectName(String projectName) { this.projectName = projectName; return this; }
        public Builder packageName(String packageName) { this.packageName = packageName; return this; }
        public Builder outputPath(Path outputPath) { this.outputPath = outputPath; return this; }
        public Builder projectPath(Path projectPath) { this.projectPath = projectPath; return this; }
        public Builder configuration(Map<String, Object> configuration) { this.configuration = configuration; return this; }
        
        public GenerationContext build() {
            return new GenerationContext(template, featurePack, projectName, packageName, outputPath, projectPath, configuration);
        }
    }
}
