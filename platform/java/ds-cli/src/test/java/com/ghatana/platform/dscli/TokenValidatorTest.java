/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
package com.ghatana.platform.dscli;

import com.ghatana.platform.dscli.model.TokenFile;
import com.ghatana.platform.dscli.model.ValidationIssue;
import com.ghatana.platform.dscli.service.TokenValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TokenValidator}.
 */
@DisplayName("TokenValidator")
class TokenValidatorTest {

    private final TokenValidator validator = new TokenValidator(); // GH-90000

    @Nested
    @DisplayName("valid token files")
    class Valid {

        @Test
        @DisplayName("clean color token passes")
        void cleanColorToken() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "primary", Map.of("$value", "#1A73E8", "$type", "color"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues).extracting(ValidationIssue::isError).doesNotContain(true); // GH-90000
        }

        @Test
        @DisplayName("dimension token passes")
        void dimensionToken() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "spacing-md", Map.of("$value", "16px", "$type", "dimension"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues.stream().filter(ValidationIssue::isError)).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("alias reference to existing token passes")
        void aliasReferenceResolved() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "brand", Map.of("$value", "#FF0000", "$type", "color"), // GH-90000
                    "primary", Map.of("$value", "{brand}", "$type", "color"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues.stream().filter(i -> i.code().equals("BROKEN_ALIAS"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("invalid token files")
    class Invalid {

        @Test
        @DisplayName("invalid color format produces error")
        void invalidColorFormat() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "bad-color", Map.of("$value", "notacolor", "$type", "color"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues).anySatisfy(i -> { // GH-90000
                assertThat(i.code()).isEqualTo("INVALID_COLOR");
                assertThat(i.isError()).isTrue(); // GH-90000
            });
        }

        @Test
        @DisplayName("invalid dimension format produces error")
        void invalidDimensionFormat() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "bad-dim", Map.of("$value", "16", "$type", "dimension"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues).anySatisfy(i -> { // GH-90000
                assertThat(i.code()).isEqualTo("INVALID_DIMENSION");
                assertThat(i.isError()).isTrue(); // GH-90000
            });
        }

        @Test
        @DisplayName("broken alias reference produces error")
        void brokenAlias() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "primary", Map.of("$value", "{nonexistent.token}", "$type", "color"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues).anySatisfy(i -> // GH-90000
                    assertThat(i.code()).isEqualTo("BROKEN_ALIAS"));
        }

        @Test
        @DisplayName("unknown type produces warning")
        void unknownType() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "blob", Map.of("$value", "whatever", "$type", "custom-type"))); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            assertThat(issues).anySatisfy(i -> // GH-90000
                    assertThat(i.code()).isEqualTo("UNKNOWN_TYPE"));
        }

        @Test
        @DisplayName("missing $version produces warning")
        void missingVersion() { // GH-90000
            final TokenFile file = new TokenFile(); // GH-90000

            final List<ValidationIssue> issues = validator.validate(file); // GH-90000

            // Default version is set, so no warning expected — override it to null
            final TokenFile noVersion = new TokenFile(); // GH-90000
            noVersion.setVersion(null); // GH-90000
            final List<ValidationIssue> noVersionIssues = validator.validate(noVersion); // GH-90000

            assertThat(noVersionIssues).anySatisfy(i -> // GH-90000
                    assertThat(i.code()).isEqualTo("MISSING_VERSION"));
        }
    }

    @Nested
    @DisplayName("TokenAuditor - duplicate detection")
    class AuditDuplicates {

        @Test
        @DisplayName("duplicate values across tokens are flagged")
        void duplicateValues() { // GH-90000
            final TokenFile file = tokenFileWith(Map.of( // GH-90000
                    "color-a", Map.of("$value", "#FF0000"), // GH-90000
                    "color-b", Map.of("$value", "#FF0000"))); // GH-90000

            final var auditor = new com.ghatana.platform.dscli.service.TokenAuditor(); // GH-90000
            final var loaded = new com.ghatana.platform.dscli.service.TokenFileLoader.LoadedTokenFile( // GH-90000
                    java.nio.file.Path.of("test.json"), file);
            final List<ValidationIssue> issues = auditor.audit(List.of(loaded)); // GH-90000

            assertThat(issues).anySatisfy(i -> // GH-90000
                    assertThat(i.code()).isEqualTo("DUPLICATE_VALUE"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TokenFile tokenFileWith(final Map<String, Object> tokens) { // GH-90000
        final TokenFile file = new TokenFile(); // GH-90000
        tokens.forEach(file::setToken); // GH-90000
        return file;
    }
}
