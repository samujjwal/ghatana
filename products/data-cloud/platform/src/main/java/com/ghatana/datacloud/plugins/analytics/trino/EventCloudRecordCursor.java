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
import io.airlift.slice.Slice;
import io.airlift.slice.Slices;
import io.trino.spi.connector.RecordCursor;
import io.trino.spi.type.Type;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.trino.spi.type.BigintType.BIGINT;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;

/**
 * Record cursor for reading EventCloud data row by row.
 *
 * <p>
 * This cursor fetches data from EventCloud HTTP API and streams it to Trino
 * workers.</p>
 *
 * @doc.type class
 * @doc.purpose Trino record cursor
 * @doc.layer product
 * @doc.pattern Cursor
 */
public class EventCloudRecordCursor implements RecordCursor {

    private static final int BATCH_SIZE = 1000;

    private final EventCloudConnectorConfig config;
    private final EventCloudSplit split;
    private final EventCloudTableHandle tableHandle;
    private final List<EventCloudColumnHandle> columns;
    private final HttpClient httpClient;

    private Iterator<Map<String, Object>> rowIterator;
    private Map<String, Object> currentRow;
    private long completedRows;
    private long completedBytes;
    private boolean closed;

    /**
     * Creates a new record cursor.
     *
     * @param config Connector configuration
     * @param split The split to read
     * @param tableHandle The table handle
     * @param columns The columns to read
     */
    public EventCloudRecordCursor(
            EventCloudConnectorConfig config,
            EventCloudSplit split,
            EventCloudTableHandle tableHandle,
            List<EventCloudColumnHandle> columns) {
        this.config = config;
        this.split = split;
        this.tableHandle = tableHandle;
        this.columns = columns;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.rowIterator = List.<Map<String, Object>>of().iterator();
        this.completedRows = 0;
        this.completedBytes = 0;
        this.closed = false;
    }

    @Override
    public long getCompletedBytes() {
        return completedBytes;
    }

    @Override
    public long getReadTimeNanos() {
        // Not tracking read time for now
        return 0;
    }

    @Override
    public Type getType(int field) {
        return columns.get(field).getType();
    }

    @Override
    public boolean advanceNextPosition() {
        if (closed) {
            return false;
        }

        // Fetch more rows if needed
        if (!rowIterator.hasNext()) {
            List<Map<String, Object>> batch = fetchNextBatch();
            if (batch.isEmpty()) {
                return false;
            }
            rowIterator = batch.iterator();
        }

        if (rowIterator.hasNext()) {
            currentRow = rowIterator.next();
            completedRows++;
            // Estimate bytes (rough approximation)
            completedBytes += estimateRowBytes(currentRow);
            return true;
        }

        return false;
    }

    private List<Map<String, Object>> fetchNextBatch() {
        // Build API URL for fetching data
        String apiUrl = buildApiUrl();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("X-Tenant-Id", config.getDefaultTenantId().orElse("default"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                // Return empty list on error (could throw exception in production)
                return List.of();
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private String buildApiUrl() {
        StringBuilder url = new StringBuilder(config.getUrl());
        url.append("/api/v1/");

        switch (split.getTableName()) {
            case EventCloudMetadata.EVENTS_TABLE:
                url.append("events?partition=").append(split.getPartitionId());
                url.append("&offset=").append(split.getStartOffset() + completedRows);
                url.append("&limit=").append(BATCH_SIZE);
                break;
            case EventCloudMetadata.EVENT_TYPES_TABLE:
                url.append("event-types?limit=").append(BATCH_SIZE);
                url.append("&offset=").append(completedRows);
                break;
            case EventCloudMetadata.STREAMS_TABLE:
                url.append("streams?limit=").append(BATCH_SIZE);
                url.append("&offset=").append(completedRows);
                break;
            default:
                break;
        }

        return url.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResponse(String json) {
        // Simple JSON parsing - in production use Jackson
        // This is a stub that returns empty for now
        // Real implementation would parse the JSON response
        return new ArrayList<>();
    }

    private long estimateRowBytes(Map<String, Object> row) {
        return row.values().stream()
                .mapToLong(v -> v != null ? v.toString().length() : 0)
                .sum();
    }

    @Override
    public boolean getBoolean(int field) {
        Object value = getFieldValue(field);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @Override
    public long getLong(int field) {
        Object value = getFieldValue(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Override
    public double getDouble(int field) {
        Object value = getFieldValue(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    @Override
    public Slice getSlice(int field) {
        Object value = getFieldValue(field);
        if (value == null) {
            return Slices.EMPTY_SLICE;
        }
        return Slices.utf8Slice(String.valueOf(value));
    }

    @Override
    public Object getObject(int field) {
        return getFieldValue(field);
    }

    @Override
    public boolean isNull(int field) {
        return getFieldValue(field) == null;
    }

    private Object getFieldValue(int field) {
        if (currentRow == null) {
            return null;
        }
        String columnName = columns.get(field).getName();
        return currentRow.get(columnName);
    }

    @Override
    public void close() {
        closed = true;
    }
}
