package com.ghatana.digitalmarketing.application.customer;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.customer.Customer;
import com.ghatana.digitalmarketing.domain.customer.CustomerProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Service interface for customer account management.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing customer accounts (DMOS-F2-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface CustomerService {

    /**
     * Create a new customer account.
     *
     * @param ctx     operation context
     * @param request customer creation request
     * @return the newly created customer
     */
    Promise<Customer> createCustomer(DmOperationContext ctx, CreateCustomerRequest request);

    /**
     * Fetch a customer by ID.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @return optional customer
     */
    Promise<Optional<Customer>> getCustomer(DmOperationContext ctx, String customerId);

    /**
     * Update customer profile.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @param profile     new profile
     * @return updated customer
     */
    Promise<Customer> updateProfile(DmOperationContext ctx, String customerId, CustomerProfile profile);

    /**
     * Activate a customer account.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @return updated customer
     */
    Promise<Customer> activateCustomer(DmOperationContext ctx, String customerId);

    /**
     * Deactivate a customer account.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @param deactivatedBy user deactivating the account
     * @return updated customer
     */
    Promise<Customer> deactivateCustomer(DmOperationContext ctx, String customerId, String deactivatedBy);

    /**
     * Grant consent for a customer.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @return updated customer
     */
    Promise<Customer> grantConsent(DmOperationContext ctx, String customerId);

    /**
     * Revoke consent for a customer.
     *
     * @param ctx         operation context
     * @param customerId customer ID
     * @return updated customer
     */
    Promise<Customer> revokeConsent(DmOperationContext ctx, String customerId);

    // ── Request types ─────────────────────────────────────────────────────────

    record CreateCustomerRequest(
        String tenantId,
        CustomerProfile profile
    ) {
        public CreateCustomerRequest {
            // Validation logic
        }
    }
}
