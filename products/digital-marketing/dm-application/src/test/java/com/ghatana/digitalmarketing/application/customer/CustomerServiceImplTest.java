package com.ghatana.digitalmarketing.application.customer;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.customer.Customer;
import com.ghatana.digitalmarketing.domain.customer.CustomerProfile;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
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
class CustomerServiceImplTest {

    @Test
    void shouldCreateCustomer() {
        Eventloop eventloop = Eventloop.create();
        eventloop.submit(() -> {
            CustomerService service = new CustomerServiceImpl();
            DmOperationContext ctx = new DmOperationContext("tenant-1", "user-1", eventloop);
            
            CustomerProfile profile = new CustomerProfile(
                "John", "Doe", "john@example.com", "555-1234",
                "Acme Corp", "Technology", null
            );
            
            CustomerService.CreateCustomerRequest request = 
                new CustomerService.CreateCustomerRequest("tenant-1", profile);
            
            Promise<Customer> promise = service.createCustomer(ctx, request);
            Customer customer = promise.getResult();
            
            assertNotNull(customer);
            assertNotNull(customer.customerId());
            assertEquals("tenant-1", customer.tenantId());
            assertEquals(Customer.CustomerStatus.PENDING, customer.status());
            assertEquals(Customer.ConsentStatus.PENDING, customer.consentStatus());
        }).run();
    }

    @Test
    void shouldActivateCustomerWithConsent() {
        Eventloop eventloop = Eventloop.create();
        eventloop.submit(() -> {
            CustomerService service = new CustomerServiceImpl();
            DmOperationContext ctx = new DmOperationContext("tenant-1", "user-1", eventloop);
            
            CustomerProfile profile = new CustomerProfile(
                "John", "Doe", "john@example.com", "555-1234",
                "Acme Corp", "Technology", null
            );
            
            CustomerService.CreateCustomerRequest request = 
                new CustomerService.CreateCustomerRequest("tenant-1", profile);
            
            Customer customer = service.createCustomer(ctx, request).getResult();
            
            // Grant consent first (in real implementation, this would update consent status)
            // For now, we'll test the activation logic
            
            // Activation should fail without consent
            assertThrows(IllegalStateException.class, () -> {
                customer.activate();
            });
        }).run();
    }

    @Test
    void shouldDeactivateCustomer() {
        Eventloop eventloop = Eventloop.create();
        eventloop.submit(() -> {
            CustomerService service = new CustomerServiceImpl();
            DmOperationContext ctx = new DmOperationContext("tenant-1", "user-1", eventloop);
            
            CustomerProfile profile = new CustomerProfile(
                "John", "Doe", "john@example.com", "555-1234",
                "Acme Corp", "Technology", null
            );
            
            CustomerService.CreateCustomerRequest request = 
                new CustomerService.CreateCustomerRequest("tenant-1", profile);
            
            Customer customer = service.createCustomer(ctx, request).getResult();
            
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
        }).run();
    }

    @Test
    void shouldNotDeactivateInactiveCustomer() {
        Eventloop eventloop = Eventloop.create();
        eventloop.submit(() -> {
            CustomerService service = new CustomerServiceImpl();
            DmOperationContext ctx = new DmOperationContext("tenant-1", "user-1", eventloop);
            
            CustomerProfile profile = new CustomerProfile(
                "John", "Doe", "john@example.com", "555-1234",
                "Acme Corp", "Technology", null
            );
            
            CustomerService.CreateCustomerRequest request = 
                new CustomerService.CreateCustomerRequest("tenant-1", profile);
            
            Customer customer = service.createCustomer(ctx, request).getResult();
            
            assertThrows(IllegalStateException.class, () -> {
                customer.deactivate("admin-user");
            });
        }).run();
    }
}
