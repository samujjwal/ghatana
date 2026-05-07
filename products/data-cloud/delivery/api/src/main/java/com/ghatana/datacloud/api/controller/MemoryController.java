package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.memory.MemoryTier;
import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.memory.TierEntry;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
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
@Tag(name = "Memory", description = "Memory tier query endpoints")
public class MemoryController {

    private final MemoryTierRouter<? extends DataRecord> memoryTierRouter;

    @Operation(summary = "List memory entries", description = "Returns memory tier entries available to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Memory entries returned")
    })
    public Promise<List<TierEntry>> getEntries() {
        List<Promise<List<TierEntry>>> tierQueries = java.util.Arrays.stream(MemoryTier.values())
                .sorted(MemoryTier.BY_EVICTION_PRIORITY)
                .map(tier -> memoryTierRouter.listTierEntries(tier, null, Integer.MAX_VALUE))
                .toList();

        return Promises.toList(tierQueries)
                .map(results -> {
                    List<TierEntry> entries = new ArrayList<>();
                    results.forEach(entries::addAll);
                    entries.sort(
                            Comparator.comparing(TierEntry::getCurrentTier, MemoryTier.BY_EVICTION_PRIORITY)
                                    .thenComparing(TierEntry::getLastAccessedAt, Comparator.reverseOrder())
                                    .thenComparing(TierEntry::getRecordId));
                    return entries;
                });
    }
}
