package com.ghatana.digitalmarketing.domain.agency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable entity representing an agency contract with a client.
 *
 * @doc.type class
 * @doc.purpose Agency contract for client engagements (P3-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class AgencyContract {

    private final String id;
    private final String agencyTenantId;
    private final String clientId;
    private final String contractNumber;
    private final String contractType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal monthlyRetainer;
    private final String currency;
    private final AgencyContractStatus status;
    private final String terms;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgencyContract(Builder builder) {
        this.id = builder.id;
        this.agencyTenantId = builder.agencyTenantId;
        this.clientId = builder.clientId;
        this.contractNumber = builder.contractNumber;
        this.contractType = builder.contractType;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.monthlyRetainer = builder.monthlyRetainer;
        this.currency = builder.currency;
        this.status = builder.status;
        this.terms = builder.terms;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public AgencyContract activate() {
        if (status != AgencyContractStatus.DRAFT && status != AgencyContractStatus.PENDING) {
            throw new IllegalStateException("Cannot activate contract in status: " + status);
        }
        return toBuilder()
            .status(AgencyContractStatus.ACTIVE)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyContract terminate(String reason) {
        if (status != AgencyContractStatus.ACTIVE) {
            throw new IllegalStateException("Cannot terminate contract in status: " + status);
        }
        return toBuilder()
            .status(AgencyContractStatus.TERMINATED)
            .updatedAt(Instant.now())
            .build();
    }

    public AgencyContract renew(LocalDate newEndDate) {
        if (status != AgencyContractStatus.ACTIVE) {
            throw new IllegalStateException("Cannot renew contract in status: " + status);
        }
        return toBuilder()
            .endDate(newEndDate)
            .updatedAt(Instant.now())
            .build();
    }

    public boolean isActive() {
        return status == AgencyContractStatus.ACTIVE;
    }

    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    public String getId() { return id; }
    public String getAgencyTenantId() { return agencyTenantId; }
    public String getClientId() { return clientId; }
    public String getContractNumber() { return contractNumber; }
    public String getContractType() { return contractType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getMonthlyRetainer() { return monthlyRetainer; }
    public String getCurrency() { return currency; }
    public AgencyContractStatus getStatus() { return status; }
    public String getTerms() { return terms; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgencyContract)) return false;
        return id.equals(((AgencyContract) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "AgencyContract{id='" + id + "', contractNumber='" + contractNumber + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .agencyTenantId(agencyTenantId)
            .clientId(clientId)
            .contractNumber(contractNumber)
            .contractType(contractType)
            .startDate(startDate)
            .endDate(endDate)
            .monthlyRetainer(monthlyRetainer)
            .currency(currency)
            .status(status)
            .terms(terms)
            .createdAt(createdAt)
            .updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String agencyTenantId;
        private String clientId;
        private String contractNumber;
        private String contractType;
        private LocalDate startDate;
        private LocalDate endDate;
        private BigDecimal monthlyRetainer;
        private String currency = "USD";
        private AgencyContractStatus status = AgencyContractStatus.DRAFT;
        private String terms;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String v) { this.id = v; return this; }
        public Builder agencyTenantId(String v) { this.agencyTenantId = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder contractNumber(String v) { this.contractNumber = v; return this; }
        public Builder contractType(String v) { this.contractType = v; return this; }
        public Builder startDate(LocalDate v) { this.startDate = v; return this; }
        public Builder endDate(LocalDate v) { this.endDate = v; return this; }
        public Builder monthlyRetainer(BigDecimal v) { this.monthlyRetainer = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder status(AgencyContractStatus v) { this.status = v; return this; }
        public Builder terms(String v) { this.terms = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }

        public AgencyContract build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (agencyTenantId == null || agencyTenantId.isBlank()) throw new IllegalArgumentException("agencyTenantId must not be blank");
            if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("clientId must not be blank");
            if (contractNumber == null || contractNumber.isBlank()) throw new IllegalArgumentException("contractNumber must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new AgencyContract(this);
        }
    }
}
