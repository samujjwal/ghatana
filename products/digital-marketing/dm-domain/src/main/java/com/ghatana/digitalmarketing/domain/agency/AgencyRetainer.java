package com.ghatana.digitalmarketing.domain.agency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a retainer agreement for agency services.
 *
 * @doc.type class
 * @doc.purpose Agency retainer for ongoing service engagements (P3-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class AgencyRetainer {

    private final String id;
    private final String contractId;
    private final String agencyTenantId;
    private final String clientId;
    private final BigDecimal monthlyAmount;
    private final String currency;
    private final LocalDate billingCycleStart;
    private final int billingDayOfMonth;
    private final Map<String, Integer> serviceAllowances;
    private final BigDecimal overageRate;
    private final AgencyRetainerStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgencyRetainer(Builder builder) {
        this.id = builder.id;
        this.contractId = builder.contractId;
        this.agencyTenantId = builder.agencyTenantId;
        this.clientId = builder.clientId;
        this.monthlyAmount = builder.monthlyAmount;
        this.currency = builder.currency;
        this.billingCycleStart = builder.billingCycleStart;
        this.billingDayOfMonth = builder.billingDayOfMonth;
        this.serviceAllowances = Map.copyOf(builder.serviceAllowances);
        this.overageRate = builder.overageRate;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public AgencyRetainer activate() {
        if (status != AgencyRetainerStatus.PENDING) {
            throw new IllegalStateException("Cannot activate retainer in status: " + status);
        }
        return toBuilder()
            .status(AgencyRetainerStatus.ACTIVE)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyRetainer suspend(String reason) {
        if (status != AgencyRetainerStatus.ACTIVE) {
            throw new IllegalStateException("Cannot suspend retainer in status: " + status);
        }
        return toBuilder()
            .status(AgencyRetainerStatus.SUSPENDED)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyRetainer cancel(String reason) {
        if (status == AgencyRetainerStatus.CANCELLED) {
            throw new IllegalStateException("Retainer already cancelled");
        }
        return toBuilder()
            .status(AgencyRetainerStatus.CANCELLED)
            .updatedAt(Instant.now())
            .build();
    }

    public boolean isActive() {
        return status == AgencyRetainerStatus.ACTIVE;
    }

    public boolean hasAllowanceForService(String serviceType) {
        Integer allowance = serviceAllowances.get(serviceType);
        return allowance != null && allowance > 0;
    }

    public String getId() { return id; }
    public String getContractId() { return contractId; }
    public String getAgencyTenantId() { return agencyTenantId; }
    public String getClientId() { return clientId; }
    public BigDecimal getMonthlyAmount() { return monthlyAmount; }
    public String getCurrency() { return currency; }
    public LocalDate getBillingCycleStart() { return billingCycleStart; }
    public int getBillingDayOfMonth() { return billingDayOfMonth; }
    public Map<String, Integer> getServiceAllowances() { return serviceAllowances; }
    public BigDecimal getOverageRate() { return overageRate; }
    public AgencyRetainerStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgencyRetainer)) return false;
        return id.equals(((AgencyRetainer) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "AgencyRetainer{id='" + id + "', contractId='" + contractId + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .contractId(contractId)
            .agencyTenantId(agencyTenantId)
            .clientId(clientId)
            .monthlyAmount(monthlyAmount)
            .currency(currency)
            .billingCycleStart(billingCycleStart)
            .billingDayOfMonth(billingDayOfMonth)
            .serviceAllowances(serviceAllowances)
            .overageRate(overageRate)
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String contractId;
        private String agencyTenantId;
        private String clientId;
        private BigDecimal monthlyAmount;
        private String currency = "USD";
        private LocalDate billingCycleStart;
        private int billingDayOfMonth = 1;
        private Map<String, Integer> serviceAllowances = Map.of();
        private BigDecimal overageRate;
        private AgencyRetainerStatus status = AgencyRetainerStatus.PENDING;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder contractId(String v) { this.contractId = v; return this; }
        public Builder agencyTenantId(String v) { this.agencyTenantId = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder monthlyAmount(BigDecimal v) { this.monthlyAmount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder billingCycleStart(LocalDate v) { this.billingCycleStart = v; return this; }
        public Builder billingDayOfMonth(int v) { this.billingDayOfMonth = v; return this; }
        public Builder serviceAllowances(Map<String, Integer> v) { this.serviceAllowances = v; return this; }
        public Builder overageRate(BigDecimal v) { this.overageRate = v; return this; }
        public Builder status(AgencyRetainerStatus v) { this.status = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AgencyRetainer build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (contractId == null || contractId.isBlank()) throw new IllegalArgumentException("contractId must not be blank");
            if (agencyTenantId == null || agencyTenantId.isBlank()) throw new IllegalArgumentException("agencyTenantId must not be blank");
            if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank");
            if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("monthlyAmount must be non-negative");
            }
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new AgencyRetainer(this);
        }
    }
}
