/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
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

package com.ghatana.datacloud.pattern;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a version of a learned pattern with its evolution history.
 *
 * <p>Patterns evolve over time as more data is observed. Each version
 * captures a snapshot of the pattern's state at a point in time.
 *
 * <h2>Version Semantics</h2>
 * <ul>
 *   <li><b>Major</b>: Breaking change in pattern structure</li>
 *   <li><b>Minor</b>: Compatible additions or refinements</li>
 *   <li><b>Patch</b>: Bug fixes or confidence adjustments</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Pattern version tracking
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
@Value
@Builder(toBuilder = true)
public class PatternVersion {

    /**
     * Major version number.
     */
    @Builder.Default
    int major = 1;

    /**
     * Minor version number.
     */
    @Builder.Default
    int minor = 0;

    /**
     * Patch version number.
     */
    @Builder.Default
    int patch = 0;

    /**
     * When this version was created.
     */
    @Builder.Default
    Instant createdAt = Instant.now();

    /**
     * Description of changes in this version.
     */
    String changelog;

    /**
     * Previous version (null for initial version).
     */
    String previousVersionId;

    /**
     * Number of records used to learn this version.
     */
    @Builder.Default
    long learningRecordCount = 0;

    /**
     * Confidence improvement from previous version.
     */
    @Builder.Default
    float confidenceDelta = 0.0f;

    /**
     * Tags indicating what changed.
     */
    @Builder.Default
    List<String> changeTags = List.of();

    /**
     * Additional version metadata.
     */
    @Builder.Default
    Map<String, Object> metadata = Map.of();

    /**
     * Creates the initial version (1.0.0).
     *
     * @param recordCount number of records used for initial learning
     * @return the initial version
     */
    public static PatternVersion initial(long recordCount) {
        return PatternVersion.builder()
                .major(1)
                .minor(0)
                .patch(0)
                .learningRecordCount(recordCount)
                .changelog("Initial pattern discovery")
                .changeTags(List.of("initial"))
                .build();
    }

    /**
     * Gets the semantic version string.
     *
     * @return version string in "major.minor.patch" format
     */
    public String getVersionString() {
        return major + "." + minor + "." + patch;
    }

    /**
     * Creates a new major version.
     *
     * @param changelog description of changes
     * @param recordCount records used for learning
     * @param confidenceDelta confidence change
     * @return new major version
     */
    public PatternVersion incrementMajor(String changelog, long recordCount, float confidenceDelta) {
        return PatternVersion.builder()
                .major(major + 1)
                .minor(0)
                .patch(0)
                .previousVersionId(getVersionString())
                .changelog(changelog)
                .learningRecordCount(recordCount)
                .confidenceDelta(confidenceDelta)
                .changeTags(List.of("major", "breaking"))
                .build();
    }

    /**
     * Creates a new minor version.
     *
     * @param changelog description of changes
     * @param recordCount records used for learning
     * @param confidenceDelta confidence change
     * @return new minor version
     */
    public PatternVersion incrementMinor(String changelog, long recordCount, float confidenceDelta) {
        return PatternVersion.builder()
                .major(major)
                .minor(minor + 1)
                .patch(0)
                .previousVersionId(getVersionString())
                .changelog(changelog)
                .learningRecordCount(recordCount)
                .confidenceDelta(confidenceDelta)
                .changeTags(List.of("minor", "enhancement"))
                .build();
    }

    /**
     * Creates a new patch version.
     *
     * @param changelog description of changes
     * @param confidenceDelta confidence change
     * @return new patch version
     */
    public PatternVersion incrementPatch(String changelog, float confidenceDelta) {
        return PatternVersion.builder()
                .major(major)
                .minor(minor)
                .patch(patch + 1)
                .previousVersionId(getVersionString())
                .changelog(changelog)
                .learningRecordCount(0)
                .confidenceDelta(confidenceDelta)
                .changeTags(List.of("patch", "fix"))
                .build();
    }

    /**
     * Checks if this version is newer than another.
     *
     * @param other the other version
     * @return true if this version is newer
     */
    public boolean isNewerThan(PatternVersion other) {
        if (major != other.major) return major > other.major;
        if (minor != other.minor) return minor > other.minor;
        return patch > other.patch;
    }

    /**
     * Checks if this version is compatible with another.
     *
     * <p>Two versions are compatible if they have the same major version.
     *
     * @param other the other version
     * @return true if compatible
     */
    public boolean isCompatibleWith(PatternVersion other) {
        return major == other.major;
    }
}
