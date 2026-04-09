/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.contracts;

import com.ghatana.kernel.contracts.KernelContract;
import java.util.Map;
import java.util.Objects;

/**
 * Generic concrete contract implementation for product-level domain contracts.
 *
 * @doc.type class
 * @doc.purpose Concrete contract for domain-specific governance
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class GenericDomainContract extends KernelContract {

    private GenericDomainContract(Builder builder) {
        super(builder.contractId, builder.name, builder.version,
              builder.family, builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String contractId;
        private String name;
        private String version;
        private ContractFamily family;
        private Map<String, String> metadata;

        public Builder contractId(String contractId) {
            this.contractId = Objects.requireNonNull(contractId, "contractId required");
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name required");
            return this;
        }

        public Builder version(String version) {
            this.version = Objects.requireNonNull(version, "version required");
            return this;
        }

        public Builder family(ContractFamily family) {
            this.family = Objects.requireNonNull(family, "family required");
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            return this;
        }

        public GenericDomainContract build() {
            return new GenericDomainContract(this);
        }
    }
}
