/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghatana.datacloud.plugins.trino;

import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.predicate.TupleDomain;

import java.util.Objects;

/**
 * Table handle for EventCloud tables.
 *
 * <p>
 * This class represents a reference to an EventCloud table within Trino. It
 * includes optional constraint information for predicate pushdown.</p>
 *
 * @doc.type class
 * @doc.purpose Trino table handle
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class EventCloudTableHandle implements ConnectorTableHandle {

    private final SchemaTableName tableName;
    private final TupleDomain<ColumnHandle> constraint;

    /**
     * Creates a new table handle without constraints.
     *
     * @param tableName The schema-qualified table name
     */
    public EventCloudTableHandle(SchemaTableName tableName) {
        this(tableName, TupleDomain.all());
    }

    /**
     * Creates a new table handle with constraints.
     *
     * @param tableName The schema-qualified table name
     * @param constraint The pushed-down filter constraints
     */
    public EventCloudTableHandle(SchemaTableName tableName, TupleDomain<ColumnHandle> constraint) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.constraint = Objects.requireNonNull(constraint, "constraint cannot be null");
    }

    /**
     * Gets the schema-qualified table name.
     *
     * @return The table name
     */
    public SchemaTableName getTableName() {
        return tableName;
    }

    /**
     * Gets the schema name.
     *
     * @return The schema name
     */
    public String getSchemaName() {
        return tableName.getSchemaName();
    }

    /**
     * Gets the table name (without schema).
     *
     * @return The table name
     */
    public String getTableNameOnly() {
        return tableName.getTableName();
    }

    /**
     * Gets the pushed-down filter constraints.
     *
     * @return The filter constraints
     */
    public TupleDomain<ColumnHandle> getConstraint() {
        return constraint;
    }

    /**
     * Creates a new handle with additional constraints.
     *
     * @param newConstraint The new constraints to add
     * @return A new table handle with combined constraints
     */
    public EventCloudTableHandle withConstraint(TupleDomain<ColumnHandle> newConstraint) {
        return new EventCloudTableHandle(tableName, constraint.intersect(newConstraint));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        EventCloudTableHandle that = (EventCloudTableHandle) obj;
        return Objects.equals(tableName, that.tableName)
                && Objects.equals(constraint, that.constraint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName, constraint);
    }

    @Override
    public String toString() {
        return "EventCloudTableHandle{"
                + "tableName=" + tableName
                + ", constraint=" + constraint
                + '}';
    }
}
