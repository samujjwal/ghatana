/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * Record adapters that bridge the JPA persistence layer and the trait-based
 * domain layer.
 *
 * <p>The data-cloud has two parallel record hierarchies:
 * <ul>
 *   <li><b>JPA layer</b> — mutable POJOs ({@code EntityRecord}, {@code EventRecord})
 *       mapped to database tables via JPA annotations</li>
 *   <li><b>Trait layer</b> — immutable Java records ({@code FullEntityRecord},
 *       {@code ImmutableEventRecord}) composed from domain traits</li>
 * </ul>
 *
 * <p>These adapters convert between the two at the persistence boundary:
 * <pre>
 * Application Service (trait records)
 *         ↕ {@link com.ghatana.datacloud.record.adapter.EntityRecordAdapter}
 *         ↕ {@link com.ghatana.datacloud.record.adapter.EventRecordAdapter}
 * Repository / JPA Layer (JPA entities)
 * </pre>
 *
 * @doc.type package
 * @doc.purpose Record adapters for JPA ↔ trait-based record conversion
 * @doc.layer product
 */
package com.ghatana.datacloud.record.adapter;
