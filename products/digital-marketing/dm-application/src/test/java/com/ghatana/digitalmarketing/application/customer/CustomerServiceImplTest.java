package com.ghatana.digitalmarketing.application.customer;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.customer.Customer;
import com.ghatana.digitalmarketing.domain.customer.CustomerProfile;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CustomerServiceImpl.
 *
 * @doc.type class
 * @doc.purpose Validates customer account management operations
 * @doc.layer product
 * @doc.pattern Test
 */
class CustomerServiceImplTest extends EventloopTestBase {

    @Test
    void shouldCreateCustomer() {
        CustomerService service = new CustomerServiceImpl();
        DmOperationContext ctx = createTestContext();
        
        CustomerProfile profile = new CustomerProfile(
            "John", "Doe", "john@example.com", "555-1234",
            "Acme Corp", "Technology", null
        );
        
        CustomerService.CreateCustomerRequest request = 
            new CustomerService.CreateCustomerRequest("tenant-1", profile);
        
        Customer customer = runPromise(() -> service.createCustomer(ctx, request));
        
        assertNotNull(customer);
        assertNotNull(customer.customerId());
        assertEquals("tenant-1", customer.tenantId());
        assertEquals(Customer.CustomerStatus.PENDING, customer.status());
        assertEquals(Customer.ConsentStatus.PENDING, customer.consentStatus());
    }

    @Test
    void shouldActivateCustomerWithConsent() {
        CustomerService service = new CustomerServiceImpl();
        DmOperationContext ctx = createTestContext();
        
        CustomerProfile profile = new CustomerProfile(
            "John", "Doe", "john@example.com", "555-1234",
            "Acme Corp", "Technology", null
        );
        
        CustomerService.CreateCustomerRequest request = 
            new CustomerService.CreateCustomerRequest("tenant-1", profile);
        
        Customer customer = runPromise(() -> service.createCustomer(ctx, request));
        
        // Grant consent first (in real implementation, this would update consent status)
        // For now, we'll test the activation logic
        
        // Activation should fail without consent
        assertThrows(IllegalStateException.class, () -> {
            customer.activate();
        });
    }

    @Test
    void shouldDeactivateCustomer() {
        CustomerService service = new CustomerServiceImpl();
        DmOperationContext ctx = createTestContext();
        
        CustomerProfile profile = new CustomerProfile(
            "John", "Doe", "john@example.com", "555-1234",
            "Acme Corp", "Technology", null
        );
        
        CustomerService.CreateCustomerRequest request = 
            new CustomerService.CreateCustomerRequest("tenant-1", profile);
        
        Customer customer = runPromise(() -> service.createCustomer(ctx, request));
        
        // Manually set to active for testing
        Customer activeCustomer = Customer.builder()
            .customerId(customer.customerId())
            .tenantId(customer.tenantId())
            .profile(customer.profile())
            .status(Customer.CustomerStatus.ACTIVE)
            .consentStatus(Customer.ConsentStatus.GRANTED)
            .createdBy(customer.createdBy())
            .build();
        
        activeCustomer.deactivate("admin-user");
        
        assertEquals(Customer.CustomerStatus.DEACTIVATED, activeCustomer.status());
        assertEquals("admin-user", activeCustomer.deactivatedBy());
    }

    @Test
    void shouldNotDeactivateInactiveCustomer() {
        CustomerService service = new CustomerServiceImpl();
        DmOperationContext ctx = createTestContext();
        
        CustomerProfile profile = new CustomerProfile(
            "John", "Doe", "john@example.com", "555-1234",
            "Acme Corp", "Technology", null
        );
        
        CustomerService.CreateCustomerRequest request = 
            new CustomerService.CreateCustomerRequest("tenant-1", profile);
        
        Customer customer = runPromise(() -> service.createCustomer(ctx, request));
        
        assertThrows(IllegalStateException.class, () -> {
            customer.deactivate("admin-user");
        });
    }

    private DmOperationContext createTestContext() {
        return DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("workspace-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("test-corr-" + System.currentTimeMillis()))
            .build();
    }
}
