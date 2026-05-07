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

import com.ghatana.datacloud.plugins.trino.EventCloudMetadata.EventCloudColumnHandle;
import io.trino.spi.connector.ColumnHandle;
import io.trino.spi.connector.ConnectorRecordSetProvider;
import io.trino.spi.connector.ConnectorSession;
import io.trino.spi.connector.ConnectorSplit;
import io.trino.spi.connector.ConnectorTableHandle;
import io.trino.spi.connector.ConnectorTransactionHandle;
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.connector.RecordSet;
import io.trino.spi.type.Type;

import java.util.List;

/**
 * Record set provider for EventCloud Trino connector.
 *
 * <p>
 * This class creates record sets for reading data from EventCloud splits.</p>
 *
 * @doc.type class
 * @doc.purpose Trino record set provider
 * @doc.layer product
 * @doc.pattern RecordSetProvider
 */
public class EventCloudRecordSetProvider implements ConnectorRecordSetProvider {

    private final EventCloudConnectorConfig config;

    /**
     * Creates a new record set provider.
     *
     * @param config Connector configuration
     */
    public EventCloudRecordSetProvider(EventCloudConnectorConfig config) {
        this.config = config;
    }

    @Override
    public RecordSet getRecordSet(
            ConnectorTransactionHandle transaction,
            ConnectorSession session,
            ConnectorSplit split,
            ConnectorTableHandle table,
            List<? extends ColumnHandle> columns) {

        EventCloudSplit eventCloudSplit = (EventCloudSplit) split;
        EventCloudTableHandle tableHandle = (EventCloudTableHandle) table;

        @SuppressWarnings("unchecked")
        List<EventCloudColumnHandle> eventCloudColumns = (List<EventCloudColumnHandle>) (List<?>) columns;

        return new EventCloudRecordSet(
                config,
                eventCloudSplit,
                tableHandle,
                eventCloudColumns
        );
    }

    /**
     * Record set implementation for EventCloud data.
     */
    static class EventCloudRecordSet implements RecordSet {

        private final EventCloudConnectorConfig config;
        private final EventCloudSplit split;
        private final EventCloudTableHandle tableHandle;
        private final List<EventCloudColumnHandle> columns;
        private final List<Type> columnTypes;

        EventCloudRecordSet(
                EventCloudConnectorConfig config,
                EventCloudSplit split,
                EventCloudTableHandle tableHandle,
                List<EventCloudColumnHandle> columns) {
            this.config = config;
            this.split = split;
            this.tableHandle = tableHandle;
            this.columns = columns;
            this.columnTypes = columns.stream()
                    .map(EventCloudColumnHandle::getType)
                    .toList();
        }

        @Override
        public List<Type> getColumnTypes() {
            return columnTypes;
        }

        @Override
        public RecordCursor cursor() {
            return new EventCloudRecordCursor(
                    config,
                    split,
                    tableHandle,
                    columns
            );
        }
    }
}
