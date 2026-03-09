package com.ghatana.datacloud.api.controller;

import com.ghatana.datacloud.pattern.PatternCatalog;
import com.ghatana.datacloud.pattern.PatternRecord;
import io.activej.promise.Promise;
import lombok.RequiredArgsConstructor;

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
public class PatternController {

    private final PatternCatalog patternCatalog;

    public Promise<List<PatternRecord>> getPatterns() {
        return Promise.of(List.of());
    }
}

