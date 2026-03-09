/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for storing consumer offsets in PostgreSQL.
 */
@Entity
@Table(name = "consumer_offsets", indexes = {
    @Index(name = "idx_consumer_offsets_lookup", columnList = "tenant_id, consumer_group, partition_id", unique = true)
})
public class ConsumerOffsetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @Column(name = "partition_id", nullable = false, length = 100)
    private String partitionId;

    @Column(name = "offset_val", nullable = false, length = 255)
    private String offset;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ConsumerOffsetEntity() {
    }

    public ConsumerOffsetEntity(String tenantId, String consumerGroup, String partitionId, String offset) {
        this.tenantId = tenantId;
        this.consumerGroup = consumerGroup;
        this.partitionId = partitionId;
        this.offset = offset;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    @PrePersist
    public void updateTimestamp() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(String partitionId) {
        this.partitionId = partitionId;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

