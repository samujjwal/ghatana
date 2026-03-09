/**
 * Domain model for Data Cloud entity management.
 *
 * <p>This package contains the core domain entities, value objects, and
 * aggregates for the Data Cloud platform's entity management subsystem.
 *
 * <h2>Core Entities</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.entity.MetaCollection} - Collection schema definition</li>
 *   <li>{@link com.ghatana.datacloud.entity.MetaField} - Field definition within a collection</li>
 *   <li>{@link com.ghatana.datacloud.entity.DynamicEntity} - Runtime entity instances</li>
 *   <li>{@link com.ghatana.datacloud.entity.DataType} - Supported field data types</li>
 * </ul>
 *
 * <h2>Type-Safe Configurations</h2>
 * <ul>
 *   <li>{@link com.ghatana.datacloud.entity.FieldValidation} - Field validation rules</li>
 *   <li>{@link com.ghatana.datacloud.entity.FieldUiConfig} - UI rendering configuration</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>This package follows hexagonal architecture principles:
 * <ul>
 *   <li>Domain entities are persistence-agnostic (JPA annotations optional)</li>
 *   <li>Business logic is encapsulated within entities</li>
 *   <li>Value objects are immutable records</li>
 * </ul>
 *
 * @see com.ghatana.datacloud.entity.MetaCollection
 * @see com.ghatana.datacloud.entity.MetaField
 * @see com.ghatana.datacloud.entity.DynamicEntity
 */
package com.ghatana.datacloud.entity;
