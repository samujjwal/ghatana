package com.ghatana.platform.security;

import com.ghatana.platform.security.annotation.RequiresPermission;
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
@ExtendWith(MockitoExtension.class)
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
        void shouldReturnFalseWhenUserIsNull() {
            assertThat(SecurityUtils.hasPermission("event:read:all", null, policyService, "events"))
                    .isFalse();
        }

        @Test
        @DisplayName("should return false when policyService is null")
        void shouldReturnFalseWhenPolicyServiceIsNull() {
            assertThat(SecurityUtils.hasPermission("event:read:all", user, null, "events"))
                    .isFalse();
        }

        @Test
        @DisplayName("should delegate to policyService when user and service are valid")
        void shouldDelegateToPolicyService() {
            when(user.getRoles()).thenReturn(Set.of("READER"));
            when(policyService.isAuthorized(any(), eq("event:read:all"), eq("events")))
                    .thenReturn(true);

            assertThat(SecurityUtils.hasPermission("event:read:all", user, policyService, "events"))
                    .isTrue();
        }

        @Test
        @DisplayName("should return false when policy denies access")
        void shouldReturnFalseWhenDenied() {
            when(user.getRoles()).thenReturn(Set.of("GUEST"));
            when(policyService.isAuthorized(any(), eq("event:write:all"), eq("events")))
                    .thenReturn(false);

            assertThat(SecurityUtils.hasPermission("event:write:all", user, policyService, "events"))
                    .isFalse();
        }
    }



    @Nested
    @DisplayName("extractBearerToken")
    class ExtractBearerToken {

        @Test
        @DisplayName("should extract token from valid Bearer header")
        void shouldExtractFromValidHeader() {
            assertThat(SecurityUtils.extractBearerToken("Bearer abc123")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("should return null for null header")
        void shouldReturnNullForNullHeader() {
            assertThat(SecurityUtils.extractBearerToken(null)).isNull();
        }

        @Test
        @DisplayName("should return null for non-Bearer header")
        void shouldReturnNullForNonBearerHeader() {
            assertThat(SecurityUtils.extractBearerToken("Basic abc123")).isNull();
        }

        @Test
        @DisplayName("should handle empty token after Bearer prefix")
        void shouldHandleEmptyToken() {
            assertThat(SecurityUtils.extractBearerToken("Bearer ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("validatePassword")
    class ValidatePassword {

        @Test
        @DisplayName("should validate a strong password")
        void shouldValidateStrongPassword() {
            Map<String, Boolean> result = SecurityUtils.validatePassword("Str0ng!Pass");

            assertThat(result).containsEntry("minLength", true)
                    .containsEntry("hasUppercase", true)
                    .containsEntry("hasLowercase", true)
                    .containsEntry("hasNumber", true)
                    .containsEntry("hasSpecialChar", true);
        }

        @Test
        @DisplayName("should detect short password")
        void shouldDetectShortPassword() {
            Map<String, Boolean> result = SecurityUtils.validatePassword("Ab1!");

            assertThat(result).containsEntry("minLength", false);
        }

        @Test
        @DisplayName("should detect missing uppercase")
        void shouldDetectMissingUppercase() {
            Map<String, Boolean> result = SecurityUtils.validatePassword("lowercase1!");

            assertThat(result).containsEntry("hasUppercase", false);
        }

        @Test
        @DisplayName("should detect missing number")
        void shouldDetectMissingNumber() {
            Map<String, Boolean> result = SecurityUtils.validatePassword("NoNumbers!");

            assertThat(result).containsEntry("hasNumber", false);
        }
    }

    @Nested
    @DisplayName("getRequiredPermission")
    class GetRequiredPermission {

        @Test
        @DisplayName("should return null for null annotations")
        void shouldReturnNullForNullAnnotations() {
            assertThat(SecurityUtils.getRequiredPermission(null)).isNull();
        }

        @Test
        @DisplayName("should return null for empty annotations")
        void shouldReturnNullForEmptyAnnotations() {
            assertThat(SecurityUtils.getRequiredPermission(new Annotation[]{})).isNull();
        }

        @Test
        @DisplayName("should return null when RequiresPermission is absent")
        void shouldReturnNullWhenAbsent() {
            Annotation[] annotations = { new Override() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return Override.class;
                }
            }};
            assertThat(SecurityUtils.getRequiredPermission(annotations)).isNull();
        }
    }



    @Nested
    @DisplayName("permission constants")
    class PermissionConstants {

        @Test
        @DisplayName("should have correct admin wildcard")
        void shouldHaveAdminWildcard() {
            assertThat(SecurityUtils.PERMISSION_ADMIN).isEqualTo("*:*:*");
        }

        @Test
        @DisplayName("should have standard permission format")
        void shouldHaveStandardFormat() {
            assertThat(SecurityUtils.PERMISSION_EVENT_READ).isEqualTo("event:read:all");
            assertThat(SecurityUtils.PERMISSION_EVENT_WRITE).isEqualTo("event:write:all");
            assertThat(SecurityUtils.PERMISSION_USER_READ).isEqualTo("user:read:all");
            assertThat(SecurityUtils.PERMISSION_USER_WRITE).isEqualTo("user:write:all");
        }
    }
}
