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
import io.trino.spi.connector.ColumnMetadata;
import io.trino.spi.connector.ConnectorMetadata;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTableMetadata;
import io.trino.spi.connector.Constraint;
import io.trino.spi.connector.ConstraintApplicationResult;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.connector.SchemaTablePrefix;
import io.trino.spi.connector.TableColumnsMetadata;
import io.trino.spi.predicate.TupleDomain;
import io.trino.spi.type.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;

/**
 * Metadata provider for EventCloud Trino connector.
 *
 * <p>
 * This class exposes EventCloud schemas and tables to Trino, allowing SQL
 * queries to discover available data.</p>
 *
 * <p>
 * <b>Schema Structure:</b></p>
 * <ul>
 * <li>eventcloud (default schema)</li>
 * <ul>
 * <li>events - Main events table</li>
 * <li>event_types - Event type definitions</li>
 * <li>streams - Stream definitions</li>
 * </ul>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Trino metadata provider
 * @doc.layer product
 * @doc.pattern Metadata
 */
public class EventCloudMetadata implements ConnectorMetadata {

    /**
     * Default schema name.
     */
    public static final String DEFAULT_SCHEMA = "eventcloud";

    /**
     * Events table name.
     */
    public static final String EVENTS_TABLE = "events";

    /**
     * Event types table name.
     */
    public static final String EVENT_TYPES_TABLE = "event_types";

    /**
     * Streams table name.
     */
    public static final String STREAMS_TABLE = "streams";

    private final EventCloudConnectorConfig config;
    private final Map<SchemaTableName, ConnectorTableMetadata> tables;
    private final Map<SchemaTableName, Map<String, EventCloudColumnHandle>> columnHandles;

    /**
     * Creates a new metadata provider.
     *
     * @param config Connector configuration
     */
    public EventCloudMetadata(EventCloudConnectorConfig config) {
        this.config = config;
        this.tables = new HashMap<>();
        this.columnHandles = new HashMap<>();
        initializeTables();
    }

    private void initializeTables() {
        // Events table
        SchemaTableName eventsTableName = new SchemaTableName(DEFAULT_SCHEMA, EVENTS_TABLE);
        List<ColumnMetadata> eventsColumns = List.of(
                // Primary key and identifiers
                new ColumnMetadata("id", VARCHAR),
                new ColumnMetadata("tenant_id", VARCHAR),
                new ColumnMetadata("event_type_name", VARCHAR),
                new ColumnMetadata("event_type_version", VARCHAR),
                // Stream placement
                new ColumnMetadata("stream_name", VARCHAR),
                new ColumnMetadata("partition_id", INTEGER),
                new ColumnMetadata("event_offset", BIGINT),
                // QT-Model timestamps (stored as epoch millis for portability)
                new ColumnMetadata("occurrence_time", BIGINT),
                new ColumnMetadata("detection_time", BIGINT),
                // Headers and payload
                new ColumnMetadata("headers", VARCHAR), // JSON
                new ColumnMetadata("payload", VARCHAR), // JSON

                // Tracing
                new ColumnMetadata("correlation_id", VARCHAR),
                new ColumnMetadata("causation_id", VARCHAR),
                new ColumnMetadata("trace_id", VARCHAR),
                new ColumnMetadata("span_id", VARCHAR),
                // Metadata
                new ColumnMetadata("storage_tier", INTEGER),
                new ColumnMetadata("created_at", BIGINT),
                new ColumnMetadata("updated_at", BIGINT)
        );

        tables.put(eventsTableName, new ConnectorTableMetadata(eventsTableName, eventsColumns));
        buildColumnHandles(eventsTableName, eventsColumns);

        // Event Types table
        SchemaTableName eventTypesTableName = new SchemaTableName(DEFAULT_SCHEMA, EVENT_TYPES_TABLE);
        List<ColumnMetadata> eventTypesColumns = List.of(
                new ColumnMetadata("id", VARCHAR),
                new ColumnMetadata("tenant_id", VARCHAR),
                new ColumnMetadata("name", VARCHAR),
                new ColumnMetadata("namespace", VARCHAR),
                new ColumnMetadata("version", VARCHAR),
                new ColumnMetadata("description", VARCHAR),
                new ColumnMetadata("schema_definition", VARCHAR), // JSON
                new ColumnMetadata("created_at", BIGINT) // epoch millis
        );

        tables.put(eventTypesTableName, new ConnectorTableMetadata(eventTypesTableName, eventTypesColumns));
        buildColumnHandles(eventTypesTableName, eventTypesColumns);

        // Streams table
        SchemaTableName streamsTableName = new SchemaTableName(DEFAULT_SCHEMA, STREAMS_TABLE);
        List<ColumnMetadata> streamsColumns = List.of(
                new ColumnMetadata("id", VARCHAR),
                new ColumnMetadata("tenant_id", VARCHAR),
                new ColumnMetadata("name", VARCHAR),
                new ColumnMetadata("partition_count", INTEGER),
                new ColumnMetadata("replication_factor", INTEGER),
                new ColumnMetadata("retention_hours", INTEGER),
                new ColumnMetadata("created_at", BIGINT) // epoch millis
        );

        tables.put(streamsTableName, new ConnectorTableMetadata(streamsTableName, streamsColumns));
        buildColumnHandles(streamsTableName, streamsColumns);
    }

    private void buildColumnHandles(SchemaTableName tableName, List<ColumnMetadata> columns) {
        Map<String, EventCloudColumnHandle> handles = new HashMap<>();
        int index = 0;
        for (ColumnMetadata column : columns) {
            handles.put(column.getName(), new EventCloudColumnHandle(
                    column.getName(),
                    column.getType(),
                    index++
            ));
        }
        columnHandles.put(tableName, handles);
    }

    @Override
    public List<String> listSchemaNames(ConnectorSession session) {
        return List.of(DEFAULT_SCHEMA);
    }

    @Override
    public List<SchemaTableName> listTables(ConnectorSession session, Optional<String> schemaName) {
        if (schemaName.isPresent() && !schemaName.get().equals(DEFAULT_SCHEMA)) {
            return List.of();
        }
        return new ArrayList<>(tables.keySet());
    }

    @Override
    public ConnectorTableHandle getTableHandle(ConnectorSession session, SchemaTableName tableName) {
        if (!tables.containsKey(tableName)) {
            return null;
        }
        return new EventCloudTableHandle(tableName);
    }

    @Override
    public ConnectorTableMetadata getTableMetadata(
            ConnectorSession session,
            ConnectorTableHandle tableHandle) {
        EventCloudTableHandle eventCloudHandle = (EventCloudTableHandle) tableHandle;
        return tables.get(eventCloudHandle.getTableName());
    }

    @Override
    public Map<String, ColumnHandle> getColumnHandles(
            ConnectorSession session,
            ConnectorTableHandle tableHandle) {
        EventCloudTableHandle eventCloudHandle = (EventCloudTableHandle) tableHandle;
        return new HashMap<>(columnHandles.get(eventCloudHandle.getTableName()));
    }

    @Override
    public ColumnMetadata getColumnMetadata(
            ConnectorSession session,
            ConnectorTableHandle tableHandle,
            ColumnHandle columnHandle) {
        EventCloudColumnHandle eventCloudColumn = (EventCloudColumnHandle) columnHandle;
        return new ColumnMetadata(eventCloudColumn.getName(), eventCloudColumn.getType());
    }

    @Override
    public Iterator<TableColumnsMetadata> streamTableColumns(
            ConnectorSession session,
            SchemaTablePrefix prefix) {
        List<TableColumnsMetadata> result = new ArrayList<>();
        for (Map.Entry<SchemaTableName, ConnectorTableMetadata> entry : tables.entrySet()) {
            if (prefix.matches(entry.getKey())) {
                result.add(TableColumnsMetadata.forTable(
                        entry.getKey(),
                        entry.getValue().getColumns()
                ));
            }
        }
        return result.iterator();
    }

    @Override
    public Optional<ConstraintApplicationResult<ConnectorTableHandle>> applyFilter(
            ConnectorSession session,
            ConnectorTableHandle handle,
            Constraint constraint) {

        EventCloudTableHandle tableHandle = (EventCloudTableHandle) handle;
        TupleDomain<ColumnHandle> currentDomain = tableHandle.getConstraint();
        TupleDomain<ColumnHandle> newDomain = constraint.getSummary();

        // Intersect with existing constraints
        TupleDomain<ColumnHandle> combinedDomain = currentDomain.intersect(newDomain);

        if (combinedDomain.equals(currentDomain)) {
            // No new constraints to push down
            return Optional.empty();
        }

        // Create new handle with pushed-down constraints
        EventCloudTableHandle newHandle = new EventCloudTableHandle(
                tableHandle.getTableName(),
                combinedDomain
        );

        return Optional.of(new ConstraintApplicationResult<>(
                newHandle,
                TupleDomain.all(), // remaining constraint after pushdown
                constraint.getExpression(),
                false
        ));
    }

    /**
     * Column handle for EventCloud columns.
     */
    public record EventCloudColumnHandle(
            String name,
            Type type,
            int ordinalPosition
    ) implements ColumnHandle {

        

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public int getOrdinalPosition() {
        return ordinalPosition;
    }
}
}
