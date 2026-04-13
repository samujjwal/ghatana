/**
 * Contract Serialization/Deserialization Test Suite
 *
 * Tests that contract models can be serialized and deserialized correctly.
 *
 * @doc.type test
 * @doc.purpose Serialization validation for contract models
 * @doc.layer platform
 * @doc.pattern UnitTest
 */

package com.ghatana.contracts.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Contract Serialization Tests")
class ContractSerializationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Contract model serializes to JSON")
    void contractModelSerializesToJson() throws Exception {
        String json = objectMapper.writeValueAsString(new TestContract("id", "name", "ACTIVE"));
        assertThat(json).contains("\"id\":\"id\"");
        assertThat(json).contains("\"name\":\"name\"");
        assertThat(json).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    @DisplayName("Contract model deserializes from JSON")
    void contractModelDeserializesFromJson() throws Exception {
        String json = "{\"id\":\"id\",\"name\":\"name\",\"status\":\"ACTIVE\"}";
        TestContract contract = objectMapper.readValue(json, TestContract.class);
        assertThat(contract.id()).isEqualTo("id");
        assertThat(contract.name()).isEqualTo("name");
        assertThat(contract.status()).isEqualTo("ACTIVE");
    }

    record TestContract(String id, String name, String status) {}
}
