package com.ghatana.platform.security;

import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.PolicyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SecurityUtils}.
 *
 * @doc.type class
 * @doc.purpose SecurityUtils unit tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("SecurityUtils [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class SecurityUtilsTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private User user;

    @Nested
    @DisplayName("hasPermission(String, User, PolicyService, String) [GH-90000]")
    class HasPermissionWithPolicyService {

        @Test
        @DisplayName("should return false when user is null [GH-90000]")
        void shouldReturnFalseWhenUserIsNull() { // GH-90000
            assertThat(SecurityUtils.hasPermission("event:read:all", null, policyService, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should return false when policyService is null [GH-90000]")
        void shouldReturnFalseWhenPolicyServiceIsNull() { // GH-90000
            assertThat(SecurityUtils.hasPermission("event:read:all", user, null, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should delegate to policyService when user and service are valid [GH-90000]")
        void shouldDelegateToPolicyService() { // GH-90000
            when(user.getRoles()).thenReturn(Set.of("READER [GH-90000]"));
            when(policyService.isAuthorized(any(), eq("event:read:all [GH-90000]"), eq("events [GH-90000]")))
                    .thenReturn(true); // GH-90000

            assertThat(SecurityUtils.hasPermission("event:read:all", user, policyService, "events")) // GH-90000
                    .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when policy denies access [GH-90000]")
        void shouldReturnFalseWhenDenied() { // GH-90000
            when(user.getRoles()).thenReturn(Set.of("GUEST [GH-90000]"));
            when(policyService.isAuthorized(any(), eq("event:write:all [GH-90000]"), eq("events [GH-90000]")))
                    .thenReturn(false); // GH-90000

            assertThat(SecurityUtils.hasPermission("event:write:all", user, policyService, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }
    }



    @Nested
    @DisplayName("extractBearerToken [GH-90000]")
    class ExtractBearerToken {

        @Test
        @DisplayName("should extract token from valid Bearer header [GH-90000]")
        void shouldExtractFromValidHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Bearer abc123 [GH-90000]")).isEqualTo("abc123 [GH-90000]");
        }

        @Test
        @DisplayName("should return null for null header [GH-90000]")
        void shouldReturnNullForNullHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken(null)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null for non-Bearer header [GH-90000]")
        void shouldReturnNullForNonBearerHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Basic abc123 [GH-90000]")).isNull();
        }

        @Test
        @DisplayName("should handle empty token after Bearer prefix [GH-90000]")
        void shouldHandleEmptyToken() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Bearer  [GH-90000]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("validatePassword [GH-90000]")
    class ValidatePassword {

        @Test
        @DisplayName("should validate a strong password [GH-90000]")
        void shouldValidateStrongPassword() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("Str0ng!Pass [GH-90000]");

            assertThat(result).containsEntry("minLength", true) // GH-90000
                    .containsEntry("hasUppercase", true) // GH-90000
                    .containsEntry("hasLowercase", true) // GH-90000
                    .containsEntry("hasNumber", true) // GH-90000
                    .containsEntry("hasSpecialChar", true); // GH-90000
        }

        @Test
        @DisplayName("should detect short password [GH-90000]")
        void shouldDetectShortPassword() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("Ab1! [GH-90000]");

            assertThat(result).containsEntry("minLength", false); // GH-90000
        }

        @Test
        @DisplayName("should detect missing uppercase [GH-90000]")
        void shouldDetectMissingUppercase() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("lowercase1! [GH-90000]");

            assertThat(result).containsEntry("hasUppercase", false); // GH-90000
        }

        @Test
        @DisplayName("should detect missing number [GH-90000]")
        void shouldDetectMissingNumber() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("NoNumbers! [GH-90000]");

            assertThat(result).containsEntry("hasNumber", false); // GH-90000
        }
    }

    @Nested
    @DisplayName("getRequiredPermission [GH-90000]")
    class GetRequiredPermission {

        @Test
        @DisplayName("should return null for null annotations [GH-90000]")
        void shouldReturnNullForNullAnnotations() { // GH-90000
            assertThat(SecurityUtils.getRequiredPermission(null)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null for empty annotations [GH-90000]")
        void shouldReturnNullForEmptyAnnotations() { // GH-90000
            assertThat(SecurityUtils.getRequiredPermission(new Annotation[]{})).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null when RequiresPermission is absent [GH-90000]")
        void shouldReturnNullWhenAbsent() { // GH-90000
            Annotation[] annotations = { new Override() { // GH-90000
                @Override
                public Class<? extends Annotation> annotationType() { // GH-90000
                    return Override.class;
                }
            }};
            assertThat(SecurityUtils.getRequiredPermission(annotations)).isNull(); // GH-90000
        }
    }



    @Nested
    @DisplayName("permission constants [GH-90000]")
    class PermissionConstants {

        @Test
        @DisplayName("should have correct admin wildcard [GH-90000]")
        void shouldHaveAdminWildcard() { // GH-90000
            assertThat(SecurityUtils.PERMISSION_ADMIN).isEqualTo("*:*:* [GH-90000]");
        }

        @Test
        @DisplayName("should have standard permission format [GH-90000]")
        void shouldHaveStandardFormat() { // GH-90000
            assertThat(SecurityUtils.PERMISSION_EVENT_READ).isEqualTo("event:read:all [GH-90000]");
            assertThat(SecurityUtils.PERMISSION_EVENT_WRITE).isEqualTo("event:write:all [GH-90000]");
            assertThat(SecurityUtils.PERMISSION_USER_READ).isEqualTo("user:read:all [GH-90000]");
            assertThat(SecurityUtils.PERMISSION_USER_WRITE).isEqualTo("user:write:all [GH-90000]");
        }
    }
}
