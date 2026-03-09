package com.ghatana.platform.governance;

import com.ghatana.platform.governance.RetentionPolicy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Defines governance policies for an event type, including access control,
 * data classification, and retention policies.
 *
 * @doc.type class
 * @doc.purpose Event governance policies (access control, classification, retention)
 * @doc.layer core
 * @doc.pattern Policy Object
 */
public final class Governance {
    private final String owner;
    private final DataClassification classification;
    private final Set<String> authorizedProducers;
    private final Set<String> authorizedConsumers;
    private final boolean requiresApproval;
    private final String approvalWorkflow;
    private final RetentionPolicy retentionPolicy;

    private Governance(Builder builder) {
        this.owner = builder.owner;
        this.classification = builder.classification;
        this.authorizedProducers = Collections.unmodifiableSet(new HashSet<>(builder.authorizedProducers));
        this.authorizedConsumers = Collections.unmodifiableSet(new HashSet<>(builder.authorizedConsumers));
        this.requiresApproval = builder.requiresApproval;
        this.approvalWorkflow = builder.approvalWorkflow;
        this.retentionPolicy = builder.retentionPolicy;
    }

    public String getOwner() {
        return owner;
    }

    public DataClassification getClassification() {
        return classification;
    }

    public Set<String> getAuthorizedProducers() {
        return authorizedProducers;
    }

    public Set<String> getAuthorizedConsumers() {
        return authorizedConsumers;
    }

    public boolean isApprovalRequired() {
        return requiresApproval;
    }

    public String getApprovalWorkflow() {
        return approvalWorkflow;
    }

    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Governance that = (Governance) o;
        return requiresApproval == that.requiresApproval &&
            Objects.equals(owner, that.owner) &&
            classification == that.classification &&
            authorizedProducers.equals(that.authorizedProducers) &&
            authorizedConsumers.equals(that.authorizedConsumers) &&
            Objects.equals(approvalWorkflow, that.approvalWorkflow) &&
            Objects.equals(retentionPolicy, that.retentionPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, classification, authorizedProducers, authorizedConsumers,
            requiresApproval, approvalWorkflow, retentionPolicy);
    }

    @Override
    public String toString() {
        return "Governance{" +
            "owner='" + owner + '\'' +
            ", classification=" + classification +
            ", authorizedProducers=" + authorizedProducers +
            ", authorizedConsumers=" + authorizedConsumers +
            ", requiresApproval=" + requiresApproval +
            ", approvalWorkflow='" + approvalWorkflow + '\'' +
            ", retentionPolicy=" + retentionPolicy +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Governance defaults(String owner) {
        return builder()
            .withOwner(owner)
            .withClassification(DataClassification.INTERNAL)
            .withApprovalRequired(false)
            .withRetentionPolicy(RetentionPolicy.defaults())
            .build();
    }

    public static final class Builder {
        private String owner;
        private DataClassification classification = DataClassification.INTERNAL;
        private final Set<String> authorizedProducers = new HashSet<>();
        private final Set<String> authorizedConsumers = new HashSet<>();
        private boolean requiresApproval;
        private String approvalWorkflow;
        private RetentionPolicy retentionPolicy = RetentionPolicy.defaults();

        private Builder() {
        }

        public Builder withOwner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder withClassification(DataClassification classification) {
            this.classification = Objects.requireNonNull(classification, "Classification cannot be null");
            return this;
        }

        public Builder addAuthorizedProducer(String principal) {
            this.authorizedProducers.add(Objects.requireNonNull(principal, "Principal cannot be null"));
            return this;
        }

        public Builder addAuthorizedProducers(Iterable<String> principals) {
            for (String principal : principals) {
                addAuthorizedProducer(principal);
            }
            return this;
        }

        public Builder addAuthorizedConsumer(String principal) {
            this.authorizedConsumers.add(Objects.requireNonNull(principal, "Principal cannot be null"));
            return this;
        }

        public Builder addAuthorizedConsumers(Iterable<String> principals) {
            for (String principal : principals) {
                addAuthorizedConsumer(principal);
            }
            return this;
        }

        public Builder withApprovalRequired(boolean requiresApproval) {
            this.requiresApproval = requiresApproval;
            return this;
        }

        public Builder withApprovalWorkflow(String approvalWorkflow) {
            this.approvalWorkflow = approvalWorkflow;
            return this;
        }

        public Builder withRetentionPolicy(RetentionPolicy retentionPolicy) {
            this.retentionPolicy = Objects.requireNonNull(retentionPolicy, "Retention policy cannot be null");
            return this;
        }

        public Governance build() {
            if (owner == null || owner.trim().isEmpty()) {
                throw new IllegalStateException("Owner must be specified");
            }
            return new Governance(this);
        }
    }
}
