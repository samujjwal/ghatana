package com.ghatana.platform.security.rbac.example;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.InMemoryPolicyRepository;
import com.ghatana.platform.security.rbac.Permission;
import com.ghatana.platform.security.rbac.PolicyRepository;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.platform.security.rbac.Role;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Example application that demonstrates how to use the RBAC policy enforcement.
 * This class shows how to create and use policies for role-based access control.
 
 *
 * @doc.type class
 * @doc.purpose Rbac policy example
 * @doc.layer core
 * @doc.pattern Component
*/
public class RbacPolicyExample {

    public static void main(String[] args) {
        // Create a policy repository
        PolicyRepository policyRepository = new InMemoryPolicyRepository();
        
        // Create a policy service
        PolicyService policyService = new PolicyService(policyRepository);
        
        // Create some policies
        createPolicies(policyService);
        
        // Create some principals with different roles
        Principal adminUser = createPrincipal("admin", Role.ADMIN);
        Principal regularUser = createPrincipal("user", Role.USER);
        Principal guestUser = createPrincipal("guest", Role.GUEST);
        
        // Test authorization for different resources and permissions
        testAuthorization(policyService, adminUser, regularUser, guestUser);
    }
    
    private static void createPolicies(PolicyService policyService) {
        System.out.println("Creating policies...");
        
        // Create a policy for admin role with all permissions on all resources
        policyService.createPolicy(
                "Admin Policy",
                "Grants all permissions to admin role",
                Role.ADMIN,
                "*",
                Set.of("*")
        );
        
        // Create a policy for user role with read and write permissions on user resources
        policyService.createPolicy(
                "User Policy - User Resources",
                "Grants read and write permissions to user role on user resources",
                Role.USER,
                "user:*",
                Set.of(Permission.READ, Permission.WRITE)
        );
        
        // Create a policy for user role with read permissions on public resources
        policyService.createPolicy(
                "User Policy - Public Resources",
                "Grants read permissions to user role on public resources",
                Role.USER,
                "public:*",
                Set.of(Permission.READ)
        );
        
        // Create a policy for guest role with read permissions on public resources
        policyService.createPolicy(
                "Guest Policy",
                "Grants read permissions to guest role on public resources",
                Role.GUEST,
                "public:*",
                Set.of(Permission.READ)
        );
        
        System.out.println("Policies created successfully.");
    }
    
    private static Principal createPrincipal(String name, String... roles) {
        List<String> roleList = new java.util.ArrayList<>();
        for (String role : roles) {
            roleList.add(role);
        }
        
        // Principal now requires tenantId and List<String> roles
        return new Principal(name, roleList);
    }
    
    private static void testAuthorization(PolicyService policyService, Principal adminUser, Principal regularUser, Principal guestUser) {
        System.out.println("\nTesting authorization...");
        
        // Test admin user
        testUserAuthorization(policyService, adminUser, "user:profile", Permission.READ);
        testUserAuthorization(policyService, adminUser, "user:profile", Permission.WRITE);
        testUserAuthorization(policyService, adminUser, "user:profile", Permission.DELETE);
        testUserAuthorization(policyService, adminUser, "public:document", Permission.READ);
        testUserAuthorization(policyService, adminUser, "admin:settings", Permission.ADMIN);
        
        // Test regular user
        testUserAuthorization(policyService, regularUser, "user:profile", Permission.READ);
        testUserAuthorization(policyService, regularUser, "user:profile", Permission.WRITE);
        testUserAuthorization(policyService, regularUser, "user:profile", Permission.DELETE);
        testUserAuthorization(policyService, regularUser, "public:document", Permission.READ);
        testUserAuthorization(policyService, regularUser, "public:document", Permission.WRITE);
        testUserAuthorization(policyService, regularUser, "admin:settings", Permission.READ);
        
        // Test guest user
        testUserAuthorization(policyService, guestUser, "user:profile", Permission.READ);
        testUserAuthorization(policyService, guestUser, "public:document", Permission.READ);
        testUserAuthorization(policyService, guestUser, "public:document", Permission.WRITE);
        
        // Test enforcing authorization
        System.out.println("\nTesting enforcement...");
        
        try {
            System.out.print("Enforcing admin user can read user:profile: ");
            policyService.enforceAuthorization(adminUser, Permission.READ, "user:profile");
            System.out.println("Allowed");
        } catch (Exception e) {
            System.out.println("Denied: " + e.getMessage());
        }
        
        try {
            System.out.print("Enforcing regular user can delete user:profile: ");
            policyService.enforceAuthorization(regularUser, Permission.DELETE, "user:profile");
            System.out.println("Allowed");
        } catch (Exception e) {
            System.out.println("Denied: " + e.getMessage());
        }
        
        try {
            System.out.print("Enforcing guest user can read admin:settings: ");
            policyService.enforceAuthorization(guestUser, Permission.READ, "admin:settings");
            System.out.println("Allowed");
        } catch (Exception e) {
            System.out.println("Denied: " + e.getMessage());
        }
    }
    
    private static void testUserAuthorization(PolicyService policyService, Principal user, String resource, String permission) {
        boolean isAuthorized = policyService.isAuthorized(user, permission, resource);
        System.out.printf("User %s has %s permission on %s: %s%n", 
                user.getTenantId(), permission, resource, isAuthorized ? "Yes" : "No");
    }
}
