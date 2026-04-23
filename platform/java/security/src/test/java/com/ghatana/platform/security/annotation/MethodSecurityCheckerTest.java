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
        void shouldDenyAccessWhenPrincipalNullAndSecuredPresent() throws NoSuchMethodException { // GH-90000
            MethodSecurityChecker checker = new MethodSecurityChecker(null); // GH-90000
            Method method = TestHandler.class.getMethod("securedMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should grant access when principal is present and @Secured is present")
        void shouldGrantAccessWhenPrincipalPresentAndSecuredPresent() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("securedMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should grant access to class with @Secured when principal is present")
        void shouldGrantAccessToClassWithSecuredWhenPrincipalPresent() { // GH-90000
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            
            boolean result = checker.checkClassLevelAccess(TestHandler.class); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should deny access to class with @Secured when principal is null")
        void shouldDenyAccessToClassWithSecuredWhenPrincipalNull() { // GH-90000
            MethodSecurityChecker checker = new MethodSecurityChecker(null); // GH-90000
            
            boolean result = checker.checkClassLevelAccess(TestHandler.class); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("RequiresRole Annotation Tests")
    class RequiresRoleAnnotationTests {

        @Test
        @DisplayName("Should grant access when principal has required role")
        void shouldGrantAccessWhenPrincipalHasRequiredRole() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should deny access when principal lacks required role")
        void shouldDenyAccessWhenPrincipalLacksRequiredRole() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should grant access when principal has any of the required roles (OR semantics)")
        void shouldGrantAccessWhenPrincipalHasAnyRequiredRole() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("VIEWER"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("operatorOrViewerMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should deny access when principal has none of the required roles (OR semantics)")
        void shouldDenyAccessWhenPrincipalHasNoneRequiredRole() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("GUEST"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("operatorOrViewerMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should grant access when principal has all required roles (AND semantics)")
        void shouldGrantAccessWhenPrincipalHasAllRequiredRoles() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("ADMIN", "OPERATOR"), "tenant1"); // GH-90000
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("adminAndOperatorMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should deny access when principal lacks some required roles (AND semantics)")
        void shouldDenyAccessWhenPrincipalLacksSomeRequiredRoles() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("adminAndOperatorMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Should handle case-insensitive role matching")
        void shouldHandleCaseInsensitiveRoleMatching() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("admin"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("adminMethod");
            
            boolean result = checker.checkMethodLevelAccess(method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Combined Annotation Tests")
    class CombinedAnnotationTests {

        @Test
        @DisplayName("Should check both class and method level annotations")
        void shouldCheckBothClassAndMethodLevelAnnotations() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("classAdminMethod");
            
            boolean result = checker.checkAccess(TestHandler.class, method); // GH-90000
            
            assertThat(result).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should deny when method annotation fails even if class annotation passes")
        void shouldDenyWhenMethodAnnotationFailsEvenIfClassPasses() throws NoSuchMethodException { // GH-90000
            Principal principal = new Principal("user1", List.of("ADMIN"), "tenant1");
            MethodSecurityChecker checker = new MethodSecurityChecker(principal); // GH-90000
            Method method = TestHandler.class.getMethod("classAdminMethodViewerRequired");
            
            boolean result = checker.checkAccess(TestHandler.class, method); // GH-90000
            
            assertThat(result).isFalse(); // GH-90000
        }
    }

    // Test fixture class with annotated methods
    @Secured
    static class TestHandler {

        @Secured
        public void securedMethod() { // GH-90000
        }

        @RequiresRole("ADMIN")
        public void adminMethod() { // GH-90000
        }

        @RequiresRole({"OPERATOR", "VIEWER"}) // GH-90000
        public void operatorOrViewerMethod() { // GH-90000
        }

        @RequiresRole(value = {"ADMIN", "OPERATOR"}, requireAll = true) // GH-90000
        public void adminAndOperatorMethod() { // GH-90000
        }

        @RequiresRole("ADMIN")
        public void classAdminMethod() { // GH-90000
        }

        @RequiresRole("VIEWER")
        public void classAdminMethodViewerRequired() { // GH-90000
        }
    }
}
