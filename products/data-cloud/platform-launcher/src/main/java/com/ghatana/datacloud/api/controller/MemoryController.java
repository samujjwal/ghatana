package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.memory.MemoryTierRouter;
import com.ghatana.datacloud.memory.TierEntry;
import io.activej.promise.Promise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Memory", description = "Memory tier query endpoints")
public class MemoryController {

    private final MemoryTierRouter<? extends DataRecord> memoryTierRouter;

    @Operation(summary = "List memory entries", description = "Returns memory tier entries available to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Memory entries returned")
    })
    public Promise<List<TierEntry>> getEntries() {
        return Promise.of(List.of());
    }
}

