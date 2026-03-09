package com.ghatana.platform.plugin.spi;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.types.identity.Offset;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * SPI for Storage Plugins.
 *
 * @param <T> The type of record stored (e.g., EventRecord, EntityRecord)
 *
 * @doc.type interface
 * @doc.purpose Storage abstraction
 * @doc.layer core
 */
public interface StoragePlugin<T> extends Plugin {

    /**
     * Writes a record to storage.
     *
     * @param record The record to write
     * @param tenantId The tenant ID
     * @return A Promise resolving to the offset of the written record
     */
    @NotNull
    Promise<Offset> write(@NotNull T record, @NotNull TenantId tenantId);

    /**
     * Reads records from storage.
     *
     * @param stream The stream/collection name
     * @param tenantId The tenant ID
     * @param offset The starting offset
     * @param limit The maximum number of records to return
     * @return A Promise resolving to a list of records
     */
    @NotNull
    Promise<List<T>> read(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset, int limit);

    /**
     * Reads records by time range.
     *
     * @param stream The stream/collection name
     * @param tenantId The tenant ID
     * @param startTime The start time (inclusive)
     * @param endTime The end time (exclusive)
     * @param limit The maximum number of records to return
     * @return A Promise resolving to a list of records
     */
    @NotNull
    default Promise<List<T>> readByTime(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Instant startTime, @NotNull Instant endTime, int limit) {
        return Promise.ofException(new UnsupportedOperationException("Time-based read not supported"));
    }

    /**
     * Deletes records up to a specific offset (retention).
     *
     * @param stream The stream/collection name
     * @param tenantId The tenant ID
     * @param offset The offset up to which records should be deleted
     * @return A Promise resolving when deletion is complete
     */
    @NotNull
    Promise<Void> delete(@NotNull String stream, @NotNull TenantId tenantId, @NotNull Offset offset);
}
