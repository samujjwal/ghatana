package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.MemoryItem;
import com.ghatana.agent.memory.store.MemoryQuery;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * JDBC-based persistence interface for memory items.
 *
 * @doc.type interface
 * @doc.purpose Memory item persistence SPI
 * @doc.layer agent-memory
 */
public interface MemoryItemRepository {

    @NotNull Promise<MemoryItem> save(@NotNull MemoryItem item);

    @NotNull Promise<@Nullable MemoryItem> findById(@NotNull String id);

    @NotNull Promise<List<MemoryItem>> findByQuery(@NotNull MemoryQuery query);

    @NotNull Promise<Void> delete(@NotNull String id);

    @NotNull Promise<Void> softDelete(@NotNull String id);
}
