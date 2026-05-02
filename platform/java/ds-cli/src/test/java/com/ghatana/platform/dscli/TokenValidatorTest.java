/*
 * Copyright (c) 2025 Ghatana Platform Contributors 
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

    private final TokenValidator validator = new TokenValidator(); 

    @Nested
    @DisplayName("valid token files")
    class Valid {

        @Test
        @DisplayName("clean color token passes")
        void cleanColorToken() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "primary", Map.of("$value", "#1A73E8", "$type", "color"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues).extracting(ValidationIssue::isError).doesNotContain(true); 
        }

        @Test
        @DisplayName("dimension token passes")
        void dimensionToken() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "spacing-md", Map.of("$value", "16px", "$type", "dimension"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues.stream().filter(ValidationIssue::isError)).isEmpty(); 
        }

        @Test
        @DisplayName("alias reference to existing token passes")
        void aliasReferenceResolved() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "brand", Map.of("$value", "#FF0000", "$type", "color"), 
                    "primary", Map.of("$value", "{brand}", "$type", "color"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues.stream().filter(i -> i.code().equals("BROKEN_ALIAS"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("invalid token files")
    class Invalid {

        @Test
        @DisplayName("invalid color format produces error")
        void invalidColorFormat() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "bad-color", Map.of("$value", "notacolor", "$type", "color"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues).anySatisfy(i -> { 
                assertThat(i.code()).isEqualTo("INVALID_COLOR");
                assertThat(i.isError()).isTrue(); 
            });
        }

        @Test
        @DisplayName("invalid dimension format produces error")
        void invalidDimensionFormat() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "bad-dim", Map.of("$value", "16", "$type", "dimension"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues).anySatisfy(i -> { 
                assertThat(i.code()).isEqualTo("INVALID_DIMENSION");
                assertThat(i.isError()).isTrue(); 
            });
        }

        @Test
        @DisplayName("broken alias reference produces error")
        void brokenAlias() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "primary", Map.of("$value", "{nonexistent.token}", "$type", "color"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues).anySatisfy(i -> 
                    assertThat(i.code()).isEqualTo("BROKEN_ALIAS"));
        }

        @Test
        @DisplayName("unknown type produces warning")
        void unknownType() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "blob", Map.of("$value", "whatever", "$type", "custom-type"))); 

            final List<ValidationIssue> issues = validator.validate(file); 

            assertThat(issues).anySatisfy(i -> 
                    assertThat(i.code()).isEqualTo("UNKNOWN_TYPE"));
        }

        @Test
        @DisplayName("missing $version produces warning")
        void missingVersion() { 
            final TokenFile file = new TokenFile(); 

            final List<ValidationIssue> issues = validator.validate(file); 

            // Default version is set, so no warning expected — override it to null
            final TokenFile noVersion = new TokenFile(); 
            noVersion.setVersion(null); 
            final List<ValidationIssue> noVersionIssues = validator.validate(noVersion); 

            assertThat(noVersionIssues).anySatisfy(i -> 
                    assertThat(i.code()).isEqualTo("MISSING_VERSION"));
        }
    }

    @Nested
    @DisplayName("TokenAuditor - duplicate detection")
    class AuditDuplicates {

        @Test
        @DisplayName("duplicate values across tokens are flagged")
        void duplicateValues() { 
            final TokenFile file = tokenFileWith(Map.of( 
                    "color-a", Map.of("$value", "#FF0000"), 
                    "color-b", Map.of("$value", "#FF0000"))); 

            final var auditor = new com.ghatana.platform.dscli.service.TokenAuditor(); 
            final var loaded = new com.ghatana.platform.dscli.service.TokenFileLoader.LoadedTokenFile( 
                    java.nio.file.Path.of("test.json"), file);
            final List<ValidationIssue> issues = auditor.audit(List.of(loaded)); 

            assertThat(issues).anySatisfy(i -> 
                    assertThat(i.code()).isEqualTo("DUPLICATE_VALUE"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private TokenFile tokenFileWith(final Map<String, Object> tokens) { 
        final TokenFile file = new TokenFile(); 
        tokens.forEach(file::setToken); 
        return file;
    }
}
