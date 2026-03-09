package com.ghatana.yappc.api.testing.dto;

import java.util.List;

/**
 * CoverageReport.
 *
 * @doc.type record
 * @doc.purpose coverage report
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CoverageReport(
    double overallCoverage,
    List<CoverageGap> gaps,
    int sourceFileCount,
    int testFileCount
) {}

