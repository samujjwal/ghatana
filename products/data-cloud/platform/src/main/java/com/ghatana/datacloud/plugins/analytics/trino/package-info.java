/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 *
 * package-info.java for EventCloud Trino Connector.
 */

/**
 * Trino SQL Connector for EventCloud.
 *
 * <p>This package provides a Trino connector that enables SQL queries across
 * all EventCloud storage tiers, including:</p>
 * <ul>
 *   <li>L0 (Hot) - Redis/Memory cache</li>
 *   <li>L1 (Warm) - PostgreSQL</li>
 *   <li>L2 (Cool) - Delta Lake/Iceberg</li>
 *   <li>L4 (Cold) - S3/Glacier archive</li>
 * </ul>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Predicate pushdown for efficient queries</li>
 *   <li>Partition pruning based on tenant_id and time</li>
 *   <li>Query result caching</li>
 *   <li>Multi-tenant isolation enforcement</li>
 * </ul>
 *
 * @doc.type package
 * @doc.purpose SQL federation connector for EventCloud
 * @doc.layer product
 * @doc.pattern Connector
 */
package com.ghatana.datacloud.plugins.trino;
