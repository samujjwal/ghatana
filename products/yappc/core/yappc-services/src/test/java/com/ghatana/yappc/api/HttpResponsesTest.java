package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ghatana.yappc.common.JsonMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponsesTest {

    @Test
    void badRequestUsesProblemDetailEnvelope() throws Exception {
        HttpResponse response = HttpResponses.badRequest400("projectId is required");

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        JsonNode body = JsonMapper.getMapper().readTree(response.getBody().asString(StandardCharsets.UTF_8));
        assertThat(body.get("type").asText()).isEqualTo("https://yappc.ghatana.com/problems/bad-request");
        assertThat(body.get("title").asText()).isEqualTo("Bad Request");
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("detail").asText()).isEqualTo("projectId is required");
        assertThat(body.get("correlationId").asText()).isNotBlank();
        assertThat(body.get("error").asText()).isEqualTo("projectId is required");
    }

    @Test
    void internalErrorUsesProblemDetailEnvelope() throws Exception {
        HttpResponse response = HttpResponses.error500("Internal server error");

        assertThat(response.getCode()).isEqualTo(500);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
        JsonNode body = JsonMapper.getMapper().readTree(response.getBody().asString(StandardCharsets.UTF_8));
        assertThat(body.get("type").asText()).isEqualTo("https://yappc.ghatana.com/problems/internal-server-error");
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.get("detail").asText()).isEqualTo("Internal server error");
        assertThat(body.get("correlationId").asText()).isNotBlank();
    }
}
