/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.yappc.api.deployment.dto;

/**
 * Environment record.
 * 
 * @param id Environment ID
 * @param name Environment name
 * @param description Environment description
 * @param clusterUrl Kubernetes cluster URL
 * 
 * @doc.type record
 * @doc.purpose Environment DTO
 * @doc.layer product
 * @doc.pattern DTO
 */
public record Environment(
        String id,
        String name,
        String description,
        String clusterUrl
) {}
