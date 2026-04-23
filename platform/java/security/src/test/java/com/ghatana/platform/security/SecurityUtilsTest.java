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
@DisplayName("SecurityUtils")
@ExtendWith(MockitoExtension.class) // GH-90000
class SecurityUtilsTest {

    @Mock
    private PolicyService policyService;

    @Mock
    private User user;

    @Nested
    @DisplayName("hasPermission(String, User, PolicyService, String)")
    class HasPermissionWithPolicyService {

        @Test
        @DisplayName("should return false when user is null")
        void shouldReturnFalseWhenUserIsNull() { // GH-90000
            assertThat(SecurityUtils.hasPermission("event:read:all", null, policyService, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should return false when policyService is null")
        void shouldReturnFalseWhenPolicyServiceIsNull() { // GH-90000
            assertThat(SecurityUtils.hasPermission("event:read:all", user, null, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should delegate to policyService when user and service are valid")
        void shouldDelegateToPolicyService() { // GH-90000
            when(user.getRoles()).thenReturn(Set.of("READER"));
            when(policyService.isAuthorized(any(), eq("event:read:all"), eq("events")))
                    .thenReturn(true); // GH-90000

            assertThat(SecurityUtils.hasPermission("event:read:all", user, policyService, "events")) // GH-90000
                    .isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when policy denies access")
        void shouldReturnFalseWhenDenied() { // GH-90000
            when(user.getRoles()).thenReturn(Set.of("GUEST"));
            when(policyService.isAuthorized(any(), eq("event:write:all"), eq("events")))
                    .thenReturn(false); // GH-90000

            assertThat(SecurityUtils.hasPermission("event:write:all", user, policyService, "events")) // GH-90000
                    .isFalse(); // GH-90000
        }
    }



    @Nested
    @DisplayName("extractBearerToken")
    class ExtractBearerToken {

        @Test
        @DisplayName("should extract token from valid Bearer header")
        void shouldExtractFromValidHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Bearer abc123")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("should return null for null header")
        void shouldReturnNullForNullHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken(null)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null for non-Bearer header")
        void shouldReturnNullForNonBearerHeader() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Basic abc123")).isNull();
        }

        @Test
        @DisplayName("should handle empty token after Bearer prefix")
        void shouldHandleEmptyToken() { // GH-90000
            assertThat(SecurityUtils.extractBearerToken("Bearer ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("validatePassword")
    class ValidatePassword {

        @Test
        @DisplayName("should validate a strong password")
        void shouldValidateStrongPassword() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("Str0ng!Pass");

            assertThat(result).containsEntry("minLength", true) // GH-90000
                    .containsEntry("hasUppercase", true) // GH-90000
                    .containsEntry("hasLowercase", true) // GH-90000
                    .containsEntry("hasNumber", true) // GH-90000
                    .containsEntry("hasSpecialChar", true); // GH-90000
        }

        @Test
        @DisplayName("should detect short password")
        void shouldDetectShortPassword() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("Ab1!");

            assertThat(result).containsEntry("minLength", false); // GH-90000
        }

        @Test
        @DisplayName("should detect missing uppercase")
        void shouldDetectMissingUppercase() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("lowercase1!");

            assertThat(result).containsEntry("hasUppercase", false); // GH-90000
        }

        @Test
        @DisplayName("should detect missing number")
        void shouldDetectMissingNumber() { // GH-90000
            Map<String, Boolean> result = SecurityUtils.validatePassword("NoNumbers!");

            assertThat(result).containsEntry("hasNumber", false); // GH-90000
        }
    }

    @Nested
    @DisplayName("getRequiredPermission")
    class GetRequiredPermission {

        @Test
        @DisplayName("should return null for null annotations")
        void shouldReturnNullForNullAnnotations() { // GH-90000
            assertThat(SecurityUtils.getRequiredPermission(null)).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null for empty annotations")
        void shouldReturnNullForEmptyAnnotations() { // GH-90000
            assertThat(SecurityUtils.getRequiredPermission(new Annotation[]{})).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should return null when RequiresPermission is absent")
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
    @DisplayName("permission constants")
    class PermissionConstants {

        @Test
        @DisplayName("should have correct admin wildcard")
        void shouldHaveAdminWildcard() { // GH-90000
            assertThat(SecurityUtils.PERMISSION_ADMIN).isEqualTo("*:*:*");
        }

        @Test
        @DisplayName("should have standard permission format")
        void shouldHaveStandardFormat() { // GH-90000
            assertThat(SecurityUtils.PERMISSION_EVENT_READ).isEqualTo("event:read:all");
            assertThat(SecurityUtils.PERMISSION_EVENT_WRITE).isEqualTo("event:write:all");
            assertThat(SecurityUtils.PERMISSION_USER_READ).isEqualTo("user:read:all");
            assertThat(SecurityUtils.PERMISSION_USER_WRITE).isEqualTo("user:write:all");
        }
    }
}
