package com.ghatana.datacloud.entity.security;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserContextTest {

    @Test
    void shouldGrantAdminBypassWithoutExplicitPermissions() { 
        UserContext context = UserContext.builder() 
                .userId("admin-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.ADMIN)) 
                .build(); 

        assertThat(context.hasRole(UserRole.ADMIN)).isTrue(); 
        assertThat(context.hasPermission("collection", "delete")).isTrue(); 
        assertThat(context.hasPermission("schema", "write")).isTrue(); 
    }

    @Test
    void shouldUseExplicitPermissionsForNonAdminUsers() { 
        UserContext context = UserContext.builder() 
                .userId("editor-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.EDITOR)) 
                .permissions(Map.of( 
                        "collection", Set.of("read", "write"), 
                        "entity", Set.of("read")
                ))
                .build(); 

        assertThat(context.hasPermission("collection", "read")).isTrue(); 
        assertThat(context.hasPermission("collection", "write")).isTrue(); 
        assertThat(context.hasPermission("collection", "delete")).isFalse(); 
        assertThat(context.hasPermission("entity", "read")).isTrue(); 
        assertThat(context.hasPermission("entity", "write")).isFalse(); 
    }

    @Test
    void shouldExposeImmutableRoleAndPermissionCollections() { 
        UserContext context = UserContext.builder() 
                .userId("viewer-user")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.VIEWER)) 
                .permissions(Map.of("collection", Set.of("read")))
                .build(); 

        assertThatThrownBy(() -> context.getRoles().add(UserRole.ADMIN)) 
                .isInstanceOf(UnsupportedOperationException.class); 
        assertThatThrownBy(() -> context.getPermissions().put("schema", Set.of("write")))
                .isInstanceOf(UnsupportedOperationException.class); 
    }

    @Test
    void shouldTreatIssuedAtZeroAsNonExpiringAndEnforceTtlOtherwise() { 
        UserContext nonExpiring = UserContext.builder() 
                .userId("service-user")
                .tenantId("tenant-a")
                .issuedAtMs(0) 
                .build(); 

        UserContext fresh = UserContext.builder() 
                .userId("fresh-user")
                .tenantId("tenant-a")
                .issuedAtMs(System.currentTimeMillis() - 3_599_000) 
                .build(); 

        UserContext expired = UserContext.builder() 
                .userId("expired-user")
                .tenantId("tenant-a")
                .issuedAtMs(System.currentTimeMillis() - 3_601_000) 
                .build(); 

        assertThat(nonExpiring.isValid()).isTrue(); 
        assertThat(fresh.isValid()).isTrue(); 
        assertThat(expired.isValid()).isFalse(); 
    }

    @Test
    void shouldCompareEqualityByUserAndTenantIdentity() { 
        UserContext first = UserContext.builder() 
                .userId("user-1")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.VIEWER)) 
                .build(); 

        UserContext second = UserContext.builder() 
                .userId("user-1")
                .tenantId("tenant-a")
                .roles(Set.of(UserRole.ADMIN)) 
                .permissions(Map.of("collection", Set.of("delete")))
                .build(); 

        UserContext differentTenant = UserContext.builder() 
                .userId("user-1")
                .tenantId("tenant-b")
                .build(); 

        assertThat(first).isEqualTo(second); 
        assertThat(first.hashCode()).isEqualTo(second.hashCode()); 
        assertThat(first).isNotEqualTo(differentTenant); 
        assertThat(first.toString()).contains("user-1", "tenant-a"); 
    }
}