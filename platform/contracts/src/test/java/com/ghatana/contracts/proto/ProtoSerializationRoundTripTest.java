package com.ghatana.contracts.proto;

import com.ghatana.contracts.event.v1.EventProto;
import com.ghatana.contracts.event.v1.UuidProto;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures protobuf payloads remain wire and JSON compatible for client/server flows.
 *
 * @doc.type class
 * @doc.purpose Protobuf serialization and JSON round-trip compatibility test
 * @doc.layer platform
 * @doc.pattern Test, Contract
 */
@DisplayName("Proto Serialization Round-Trip Compatibility")
class ProtoSerializationRoundTripTest {

    @Test
    @DisplayName("EventProto must support binary serialize/parse round-trip")
    void eventProtoBinaryRoundTrip() throws Exception {
        EventProto source = EventProto.newBuilder()
                .setId(UuidProto.newBuilder().setValue("evt-123").build())
                .setTenantId("tenant-alpha")
                .setType("user.created")
                .setTypeVersion("1.0.0")
                .setCorrelationId("corr-001")
            .setPayloadJson("{\"user_id\":\"u-1\",\"active\":true}")
                .build();

        byte[] bytes = source.toByteArray();
        EventProto parsed = EventProto.parseFrom(bytes);

        assertThat(parsed.getId().getValue()).isEqualTo("evt-123");
        assertThat(parsed.getTenantId()).isEqualTo("tenant-alpha");
        assertThat(parsed.getType()).isEqualTo("user.created");
        assertThat(parsed.getCorrelationId()).isEqualTo("corr-001");
        assertThat(parsed.getPayloadJson()).contains("user_id", "active");
    }

    @Test
    @DisplayName("EventProto must support JSON print/parse round-trip")
    void eventProtoJsonRoundTrip() throws Exception {
        EventProto source = EventProto.newBuilder()
                .setId(UuidProto.newBuilder().setValue("evt-999").build())
                .setTenantId("tenant-beta")
                .setType("order.placed")
                .setTypeVersion("2.1.0")
                .setCorrelationId("corr-xyz")
            .setPayloadJson("{\"order_id\":\"o-123\",\"amount\":49.99}")
                .build();

        String json = JsonFormat.printer().print(source);

        EventProto.Builder rebuiltBuilder = EventProto.newBuilder();
        JsonFormat.parser().ignoringUnknownFields().merge(json, rebuiltBuilder);
        EventProto rebuilt = rebuiltBuilder.build();

        assertThat(rebuilt.getId().getValue()).isEqualTo(source.getId().getValue());
        assertThat(rebuilt.getTenantId()).isEqualTo(source.getTenantId());
        assertThat(rebuilt.getType()).isEqualTo(source.getType());
        assertThat(rebuilt.getPayloadJson()).isEqualTo(source.getPayloadJson());
    }
}
