package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.memory.TierEntry;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Controller for Memory operations.
 *
 * @doc.type class
 * @doc.purpose Exposes Memory Tier data via API
 * @doc.layer product
 * @doc.pattern Controller
 */
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryTierRouter memoryTierRouter;

    public Promise<List<TierEntry>> getEntries() {
        return Promise.of(List.of());
    }
}

