/*
 * Copyright (c) 2024 Ghatana, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.pack;

import java.nio.file.Path;
import java.util.Map;

/**
 * Represents a loaded pack with its metadata and template files. Week 2, Day 7 deliverable - Pack
 * representation.
 *
 * @doc.type class
 * @doc.purpose Represents a loaded pack with its metadata and template files. Week 2, Day 7 deliverable - Pack
 * @doc.layer platform
 * @doc.pattern Component
 */
public class Pack {

    private final PackMetadata metadata;
    private final Path packPath;
    private final Map<String, String> templateContents;

    public Pack(PackMetadata metadata, Path packPath, Map<String, String> templateContents) {
        this.metadata = metadata;
        this.packPath = packPath;
        this.templateContents = Map.copyOf(templateContents);
    }

    /**
 * Get pack metadata. */
    public PackMetadata getMetadata() {
        return metadata;
    }

    /**
 * Get pack directory path. */
    public Path getPackPath() {
        return packPath;
    }

    /**
 * Get template content by template name. */
    public String getTemplateContent(String templateName) {
        return templateContents.get(templateName);
    }

    /**
 * Get all template contents. */
    public Map<String, String> getAllTemplateContents() {
        return Map.copyOf(templateContents);
    }

    /**
 * Get pack name. */
    public String getName() {
        return metadata.name();
    }

    /**
 * Get pack version. */
    public String getVersion() {
        return metadata.version();
    }

    /**
 * Get pack type. */
    public PackMetadata.PackType getType() {
        return metadata.type();
    }

    /**
 * Get pack language. */
    public String getLanguage() {
        return metadata.language();
    }

    /**
 * Get pack framework. */
    public String getFramework() {
        return metadata.framework();
    }

    @Override
    public String toString() {
        return String.format(
                "Pack{name='%s', version='%s', type='%s', language='%s'}",
                getName(), getVersion(), getType(), getLanguage());
    }
}
