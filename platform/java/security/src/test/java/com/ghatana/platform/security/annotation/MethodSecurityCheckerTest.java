package com.ghatana.platform.security.annotation;

import com.ghatana.platform.governance.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MethodSecurityChecker.
 *
 * @doc.type class
 * @doc.purpose Tests for method-level security annotation checking
 * @doc.layer security
 */
@DisplayName("MethodSecurityChecker Tests")
class MethodSecurityCheckerTest {

    @Nested
    @DisplayName("Secured Annotation Tests")
    class SecuredAnnotationTests {

        @Test
        @DisplayName("Should deny access when principal is null and @Secured is present")
        void shouldDenyAccessWhenPrincipalNullAndSecuredPresent() throws NoSuchMethodException { 
            MethodSecurityChecker checker = new MethodSecurityChecker(null); 
            Method method = TestHandler.class.getMethod("securedMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("Should grant access when principal is present and @Secured is present")
        void shouldGrantAccessWhenPrincipalPresentAndSecuredPresent() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("securedMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should grant access to class with @Secured when principal is present")
        void shouldGrantAccessToClassWithSecuredWhenPrincipalPresent() { 
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            
            boolean result = checker.checkClassLevelAccess(TestHandler.class); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should deny access to class with @Secured when principal is null")
        void shouldDenyAccessToClassWithSecuredWhenPrincipalNull() { 
            MethodSecurityChecker checker = new MethodSecurityChecker(null); 
            
            boolean result = checker.checkClassLevelAccess(TestHandler.class); 
            
            assertThat(result).isFalse(); 
        }
    }

    @Nested
    @DisplayName("RequiresRole Annotation Tests")
    class RequiresRoleAnnotationTests {

        @Test
        @DisplayName("Should grant access when principal has required role")
        void shouldGrantAccessWhenPrincipalHasRequiredRole() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should deny access when principal lacks required role")
        void shouldDenyAccessWhenPrincipalLacksRequiredRole() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("Should grant access when principal has any of the required roles (OR semantics)")
        void shouldGrantAccessWhenPrincipalHasAnyRequiredRole() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("operatorOrViewerMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should deny access when principal has none of the required roles (OR semantics)")
        void shouldDenyAccessWhenPrincipalHasNoneRequiredRole() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("GUEST"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("operatorOrViewerMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("Should grant access when principal has all required roles (AND semantics)")
        void shouldGrantAccessWhenPrincipalHasAllRequiredRoles() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("ADMIN", "OPERATOR"), "tenant1"); 
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("adminAndOperatorMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should deny access when principal lacks some required roles (AND semantics)")
        void shouldDenyAccessWhenPrincipalLacksSomeRequiredRoles() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("adminAndOperatorMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isFalse(); 
        }

        @Test
        @DisplayName("Should handle case-insensitive role matching")
        void shouldHandleCaseInsensitiveRoleMatching() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("admin"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); 
            
            assertThat(result).isTrue(); 
        }
    }

    @Nested
    @DisplayName("Combined Annotation Tests")
    class CombinedAnnotationTests {

        @Test
        @DisplayName("Should check both class and method level annotations")
        void shouldCheckBothClassAndMethodLevelAnnotations() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("classAdminMethod");
            
            boolean result = checker.checkAccess(TestHandler.class, method); 
            
            assertThat(result).isTrue(); 
        }

        @Test
        @DisplayName("Should deny when method annotation fails even if class annotation passes")
        void shouldDenyWhenMethodAnnotationFailsEvenIfClassPasses() throws NoSuchMethodException { 
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); 
            Method method = TestHandler.class.getMethod("classAdminMethodViewerRequired");
            
            boolean result = checker.checkAccess(TestHandler.class, method); 
            
            assertThat(result).isFalse(); 
        }
    }

    // Test fixture class with annotated methods
    @Secured
    static class TestHandler {

        @Secured
        public void securedMethod() { 
        }

        @RequiresRole("ADMIN")
        public void adminMethod() { 
        }

        @RequiresRole({"OPERATOR", "VIEWER"}) 
        public void operatorOrViewerMethod() { 
        }

        @RequiresRole(value = {"ADMIN", "OPERATOR"}, requireAll = true) 
        public void adminAndOperatorMethod() { 
        }

        @RequiresRole("ADMIN")
        public void classAdminMethod() { 
        }

        @RequiresRole("VIEWER")
        public void classAdminMethodViewerRequired() { 
        }

        @RequiresPermission("entity:read:all")
        public void readPermissionMethod() {
        }

        @RequiresPermission(anyOf = {"entity:read:all", "entity:write:all"})
        public void readOrWritePermissionMethod() {
        }

        @RequiresPermission(value = "event:write:all", requireAll = true,
                anyOf = {"event:write:all", "admin:*:*"})
        public void writeAndAdminPermissionMethod() {
        }
    }

    @Nested
    @DisplayName("RequiresPermission Annotation Tests")
    class RequiresPermissionAnnotationTests {

        @Test
        @DisplayName("Should deny access when principal is null and @RequiresPermission is present")
        void shouldDenyAccessWhenPrincipalNullAndRequiresPermission() throws NoSuchMethodException {
            MethodSecurityChecker checker = new MethodSecurityChecker(null);
            Method method = TestHandler.class.getMethod("readPermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should grant access when principal role matches required permission (role-as-permission)")
        void shouldGrantAccessWhenRoleMatchesPermission() throws NoSuchMethodException {
            Principal principal = new Principal("user1", List.of("entity:read:all"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal);
            Method method = TestHandler.class.getMethod("readPermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should deny access when principal role does not match required permission")
        void shouldDenyAccessWhenRoleDoesNotMatchPermission() throws NoSuchMethodException {
            Principal principal = new Principal("user1", List.of("entity:read:own"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal);
            Method method = TestHandler.class.getMethod("readPermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should grant access when principal has any of the required permissions (OR semantics)")
        void shouldGrantAccessWhenPrincipalHasAnyPermission() throws NoSuchMethodException {
            Principal principal = new Principal("user1", List.of("entity:write:all"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal);
            Method method = TestHandler.class.getMethod("readOrWritePermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should deny access when principal has none of the required permissions (OR semantics)")
        void shouldDenyAccessWhenPrincipalHasNoneOfPermissions() throws NoSuchMethodException {
            Principal principal = new Principal("user1", List.of("entity:delete:all"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal);
            Method method = TestHandler.class.getMethod("readOrWritePermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should be case-insensitive in permission matching")
        void shouldBeCaseInsensitiveInPermissionMatching() throws NoSuchMethodException {
            Principal principal = new Principal("user1", List.of("ENTITY:READ:ALL"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal);
            Method method = TestHandler.class.getMethod("readPermissionMethod");

            boolean result = checker.checkMethodLevelAccess(method);

            assertThat(result).isTrue();
        }
    }
}
