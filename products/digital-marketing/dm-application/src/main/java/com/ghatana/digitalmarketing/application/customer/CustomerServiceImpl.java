package com.ghatana.digitalmarketing.application.customer;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.customer.Customer;
import com.ghatana.digitalmarketing.domain.customer.CustomerProfile;
import io.activej.promise.Promise;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of CustomerService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides customer account management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class CustomerServiceImpl implements CustomerService {

    private final ConcurrentMap<String, Customer> customers = new ConcurrentHashMap<>();

    @Override
    public Promise<Customer> createCustomer(DmOperationContext ctx, CreateCustomerRequest request) {
        String customerId = Customer.generateCustomerId();
        Customer customer = Customer.builder()
            .customerId(customerId)
            .tenantId(request.tenantId())
            .profile(request.profile())
            .status(Customer.CustomerStatus.PENDING)
            .consentStatus(Customer.ConsentStatus.PENDING)
            .createdBy(ctx.userId())
            .build();

        customers.put(customerId, customer);
        return Promise.complete(customer);
    }

    @Override
    public Promise<Optional<Customer>> getCustomer(DmOperationContext ctx, String customerId) {
        return Promise.complete(Optional.ofNullable(customers.get(customerId)));
    }

    @Override
    public Promise<Customer> updateProfile(DmOperationContext ctx, String customerId, CustomerProfile profile) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            return Promise.ofException(new IllegalArgumentException("Customer not found: " + customerId));
        }
        customer.updateProfile(profile);
        return Promise.complete(customer);
    }

    @Override
    public Promise<Customer> activateCustomer(DmOperationContext ctx, String customerId) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            return Promise.ofException(new IllegalArgumentException("Customer not found: " + customerId));
        }
        customer.activate();
        return Promise.complete(customer);
    }

    @Override
    public Promise<Customer> deactivateCustomer(DmOperationContext ctx, String customerId, String deactivatedBy) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            return Promise.ofException(new IllegalArgumentException("Customer not found: " + customerId));
        }
        customer.deactivate(deactivatedBy);
        return Promise.complete(customer);
    }

    @Override
    public Promise<Customer> grantConsent(DmOperationContext ctx, String customerId) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            return Promise.ofException(new IllegalArgumentException("Customer not found: " + customerId));
        }
        // In a real implementation, this would update the consent status
        return Promise.complete(customer);
    }

    @Override
    public Promise<Customer> revokeConsent(DmOperationContext ctx, String customerId) {
        Customer customer = customers.get(customerId);
        if (customer == null) {
            return Promise.ofException(new IllegalArgumentException("Customer not found: " + customerId));
        }
        // In a real implementation, this would update the consent status
        return Promise.complete(customer);
    }
}
