package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.pattern.PatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import io.activej.promise.Promise;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import java.util.Comparator;
import java.util.List;

/**
 * Controller for Pattern operations.
 *
 * @doc.type class
 * @doc.purpose Exposes Pattern Catalog data via API
 * @doc.layer product
 * @doc.pattern Controller
 */
@RequiredArgsConstructor
@Tag(name = "Patterns", description = "Pattern catalog query endpoints")
public class PatternController {

    private final PatternCatalog patternCatalog;

    @Operation(summary = "List patterns", description = "Returns catalogued patterns available to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patterns returned")
    })
    public Promise<List<PatternRecord>> getPatterns() {
        return patternCatalog.listActive("default", Integer.MAX_VALUE)
                .map(patterns -> patterns.stream()
                        .sorted(Comparator.comparing(PatternRecord::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(PatternRecord::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList());
    }
}
