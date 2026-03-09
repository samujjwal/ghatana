package com.ghatana.agent.memory.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryRedactionFilter}.
 *
 * @doc.type class
 * @doc.purpose Tests PII and credential redaction patterns
 * @doc.layer agent-memory
 * @doc.pattern Unit Test
 */
@DisplayName("MemoryRedactionFilter")
class MemoryRedactionFilterTest {

    private MemoryRedactionFilter filter;

    @BeforeEach
    void setUp() {
        filter = MemoryRedactionFilter.defaultFilter();
    }

    @Nested
    @DisplayName("PII redaction")
    class PiiRedaction {

        @Test
        @DisplayName("should redact email addresses")
        void shouldRedactEmails() {
            String result = filter.redact("Contact me at john.doe@example.com for details");
            assertThat(result).doesNotContain("john.doe@example.com");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("should redact US phone numbers")
        void shouldRedactPhoneNumbers() {
            String result = filter.redact("Call me at 555-123-4567 or 555.123.4567");
            assertThat(result).doesNotContain("555-123-4567");
            assertThat(result).doesNotContain("555.123.4567");
        }

        @Test
        @DisplayName("should redact SSN")
        void shouldRedactSsn() {
            String result = filter.redact("SSN: 123-45-6789");
            assertThat(result).doesNotContain("123-45-6789");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("should redact multiple PII in same text")
        void shouldRedactMultiplePii() {
            String text = "Email: alice@test.com, Phone: 800-555-1234, SSN: 987-65-4321";
            String result = filter.redact(text);
            assertThat(result).doesNotContain("alice@test.com");
            assertThat(result).doesNotContain("800-555-1234");
            assertThat(result).doesNotContain("987-65-4321");
        }
    }

    @Nested
    @DisplayName("credential redaction")
    class CredentialRedaction {

        @Test
        @DisplayName("should redact API keys")
        void shouldRedactApiKeys() {
            String result = filter.redact("api_key=sk-abc123def456");
            assertThat(result).doesNotContain("sk-abc123def456");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("should redact bearer tokens")
        void shouldRedactBearerTokens() {
            String result = filter.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.test");
            assertThat(result).doesNotContain("eyJhbGciOiJIUzI1NiJ9");
        }

        @Test
        @DisplayName("should redact password values")
        void shouldRedactPasswords() {
            String result = filter.redact("password=MySecretP@ss123");
            assertThat(result).doesNotContain("MySecretP@ss123");
        }
    }

    @Nested
    @DisplayName("no-op cases")
    class NoOp {

        @Test
        @DisplayName("should not modify clean text")
        void shouldNotModifyCleanText() {
            String clean = "This is a regular message with no sensitive data";
            assertThat(filter.redact(clean)).isEqualTo(clean);
        }

        @Test
        @DisplayName("should handle empty string")
        void shouldHandleEmptyString() {
            assertThat(filter.redact("")).isEqualTo("");
        }
    }

    @Nested
    @DisplayName("containsSensitiveContent")
    class ContainsSensitive {

        @Test
        @DisplayName("should detect email as sensitive")
        void shouldDetectEmail() {
            assertThat(filter.containsSensitiveContent("email: bob@company.com")).isTrue();
        }

        @Test
        @DisplayName("should detect API key as sensitive")
        void shouldDetectApiKey() {
            assertThat(filter.containsSensitiveContent("api_key: sk_live_abc")).isTrue();
        }

        @Test
        @DisplayName("should return false for clean text")
        void shouldReturnFalseForClean() {
            assertThat(filter.containsSensitiveContent("just normal text")).isFalse();
        }
    }

    @Nested
    @DisplayName("selective redaction")
    class SelectiveRedaction {

        @Test
        @DisplayName("should only redact PII when credentials disabled")
        void shouldOnlyRedactPii() {
            MemoryRedactionFilter piiOnly = new MemoryRedactionFilter(true, false);
            String text = "Email: alice@test.com, api_key=secret123";

            String result = piiOnly.redact(text);
            assertThat(result).doesNotContain("alice@test.com");
            assertThat(result).contains("api_key=secret123"); // credentials not redacted
        }

        @Test
        @DisplayName("should only redact credentials when PII disabled")
        void shouldOnlyRedactCredentials() {
            MemoryRedactionFilter credOnly = new MemoryRedactionFilter(false, true);
            String text = "Email: alice@test.com, api_key=secret123";

            String result = credOnly.redact(text);
            assertThat(result).contains("alice@test.com"); // PII not redacted
            assertThat(result).doesNotContain("api_key=secret123");
        }
    }

    @Nested
    @DisplayName("RedactionPatternProvider SPI")
    class ProviderSpi {

        @Test
        @DisplayName("should use custom provider patterns")
        void shouldUseCustomProviderPatterns() {
            RedactionPatternProvider customProvider = new RedactionPatternProvider() {
                @Override
                public List<Pattern> piiPatterns() {
                    // Match credit card-like numbers
                    return List.of(Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"));
                }

                @Override
                public List<Pattern> credentialPatterns() {
                    return List.of();
                }

                @Override
                public String providerName() {
                    return "test-custom";
                }
            };

            MemoryRedactionFilter customFilter = new MemoryRedactionFilter(true, true, customProvider);

            String result = customFilter.redact("My card is 4111-1111-1111-1111");
            assertThat(result).doesNotContain("4111-1111-1111-1111");
            assertThat(result).contains("[REDACTED]");
        }

        @Test
        @DisplayName("should aggregate patterns from multiple providers")
        void shouldAggregateMultipleProviders() {
            RedactionPatternProvider provider1 = new RedactionPatternProvider() {
                @Override
                public List<Pattern> piiPatterns() {
                    return List.of(Pattern.compile("CUSTOM_PII_\\w+"));
                }

                @Override
                public List<Pattern> credentialPatterns() {
                    return List.of();
                }
            };

            RedactionPatternProvider provider2 = new RedactionPatternProvider() {
                @Override
                public List<Pattern> piiPatterns() {
                    return List.of();
                }

                @Override
                public List<Pattern> credentialPatterns() {
                    return List.of(Pattern.compile("CUSTOM_CRED_\\w+"));
                }
            };

            MemoryRedactionFilter multiFilter = new MemoryRedactionFilter(true, true, provider1, provider2);

            assertThat(multiFilter.piiPatternCount()).isEqualTo(1);
            assertThat(multiFilter.credentialPatternCount()).isEqualTo(1);
            assertThat(multiFilter.redact("Token: CUSTOM_PII_ABC")).doesNotContain("CUSTOM_PII_ABC");
            assertThat(multiFilter.redact("Key: CUSTOM_CRED_XYZ")).doesNotContain("CUSTOM_CRED_XYZ");
        }

        @Test
        @DisplayName("should use custom replacement token")
        void shouldUseCustomReplacementToken() {
            RedactionPatternProvider provider = new RedactionPatternProvider() {
                @Override
                public List<Pattern> piiPatterns() {
                    return List.of(Pattern.compile("SECRET_\\w+"));
                }

                @Override
                public List<Pattern> credentialPatterns() {
                    return List.of();
                }

                @Override
                public String replacementToken() {
                    return "***MASKED***";
                }
            };

            MemoryRedactionFilter customFilter = new MemoryRedactionFilter(true, false, provider);
            String result = customFilter.redact("Value is SECRET_DATA");
            assertThat(result).contains("***MASKED***");
            assertThat(result).doesNotContain("SECRET_DATA");
            assertThat(customFilter.getReplacementToken()).isEqualTo("***MASKED***");
        }

        @Test
        @DisplayName("should combine default and custom providers")
        void shouldCombineDefaultAndCustomProviders() {
            RedactionPatternProvider extraProvider = new RedactionPatternProvider() {
                @Override
                public List<Pattern> piiPatterns() {
                    // Match UK National Insurance numbers
                    return List.of(Pattern.compile("(?i)[A-Z]{2}\\d{6}[A-Z]"));
                }

                @Override
                public List<Pattern> credentialPatterns() {
                    return List.of();
                }
            };

            MemoryRedactionFilter combined = new MemoryRedactionFilter(true, true,
                    DefaultRedactionPatternProvider.instance(), extraProvider);

            // Default patterns still work
            assertThat(combined.redact("email: test@example.com")).doesNotContain("test@example.com");
            // Custom pattern also works
            assertThat(combined.redact("NI: AB123456C")).doesNotContain("AB123456C");
            // Pattern counts include both
            assertThat(combined.piiPatternCount()).isEqualTo(4); // 3 default + 1 custom
        }

        @Test
        @DisplayName("should expose providers list")
        void shouldExposeProvidersList() {
            MemoryRedactionFilter defaultOnly = MemoryRedactionFilter.defaultFilter();
            assertThat(defaultOnly.getProviders()).hasSize(1);
            assertThat(defaultOnly.getProviders().get(0)).isInstanceOf(DefaultRedactionPatternProvider.class);
        }
    }

    @Nested
    @DisplayName("DefaultRedactionPatternProvider")
    class DefaultProviderTests {

        @Test
        @DisplayName("should be singleton")
        void shouldBeSingleton() {
            assertThat(DefaultRedactionPatternProvider.instance())
                    .isSameAs(DefaultRedactionPatternProvider.instance());
        }

        @Test
        @DisplayName("should provide non-empty pattern lists")
        void shouldProvideNonEmptyPatterns() {
            DefaultRedactionPatternProvider provider = DefaultRedactionPatternProvider.instance();
            assertThat(provider.piiPatterns()).isNotEmpty();
            assertThat(provider.credentialPatterns()).isNotEmpty();
        }

        @Test
        @DisplayName("should have default replacement token")
        void shouldHaveDefaultReplacementToken() {
            assertThat(DefaultRedactionPatternProvider.instance().replacementToken())
                    .isEqualTo("[REDACTED]");
        }
    }

    @Nested
    @DisplayName("YamlRedactionPatternProvider")
    class YamlProviderTests {

        @Test
        @DisplayName("should create provider from pattern lists")
        void shouldCreateFromLists() {
            YamlRedactionPatternProvider provider = YamlRedactionPatternProvider.of(
                    List.of("\\bTEST_PII\\b"),
                    List.of("\\bTEST_CRED\\b"),
                    "<<REMOVED>>");

            assertThat(provider.piiPatterns()).hasSize(1);
            assertThat(provider.credentialPatterns()).hasSize(1);
            assertThat(provider.replacementToken()).isEqualTo("<<REMOVED>>");
            assertThat(provider.providerName()).isEqualTo("yaml:programmatic");

            MemoryRedactionFilter yamlFilter = new MemoryRedactionFilter(true, true, provider);
            assertThat(yamlFilter.redact("Value: TEST_PII here")).contains("<<REMOVED>>");
        }
    }
}
