/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.gateway.transform;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RequestBodySizeFilter}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for request body size enforcement (K11-011)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequestBodySizeFilter — Unit Tests")
class RequestBodySizeFilterTest extends EventloopTestBase {

    private static final io.activej.http.AsyncServlet OK_SERVLET = req -> Promise.of(HttpResponse.ok200().build());

    @Test
    @DisplayName("body within limit — forwards to next servlet")
    void bodyWithinLimit_forwards() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(1024);
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals")
                .withHeader(HttpHeaders.of("Content-Length"), "512")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("body exceeds limit — returns 413")
    void bodyExceedsLimit_returns413() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(1024);
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals")
                .withHeader(HttpHeaders.of("Content-Length"), "2048")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(413);
    }

    @Test
    @DisplayName("body exactly at limit — forwards to next")
    void bodyAtLimit_forwards() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(1024);
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals")
                .withHeader(HttpHeaders.of("Content-Length"), "1024")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("missing Content-Length — forwards without rejection")
    void missingContentLength_forwards() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(1024);
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals").build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("malformed Content-Length — returns 400")
    void malformedContentLength_returns400() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(1024);
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals")
                .withHeader(HttpHeaders.of("Content-Length"), "not-a-number")
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("default constructor uses DEFAULT_MAX_BYTES limit")
    void defaultConstructor_usesDefaultLimit() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter();
        assertThat(filter.getMaxBytes()).isEqualTo(RequestBodySizeFilter.DEFAULT_MAX_BYTES);
    }

    @Test
    @DisplayName("zero or negative maxBytes — throws IllegalArgumentException")
    void negativeMaxBytes_throwsIllegalArgument() {
        assertThatThrownBy(() -> new RequestBodySizeFilter(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestBodySizeFilter(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("body size 1 byte over default limit — returns 413")
    void bodyOneByteOverDefaultLimit_returns413() {
        RequestBodySizeFilter filter = new RequestBodySizeFilter();
        long overLimit = RequestBodySizeFilter.DEFAULT_MAX_BYTES + 1;
        HttpRequest request = HttpRequest.post("http://localhost/api/v2/ledger/journals")
                .withHeader(HttpHeaders.of("Content-Length"), String.valueOf(overLimit))
                .build();

        HttpResponse response = runPromise(() -> filter.apply(request, OK_SERVLET));

        assertThat(response.getCode()).isEqualTo(413);
    }
}
