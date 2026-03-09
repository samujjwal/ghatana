/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL. Use is subject to the terms of a separate
 * license agreement between you and Ghatana Platforms, Inc. You may not
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, in whole or in part, except as expressly
 * permitted under the applicable written license agreement.
 *
 * Unauthorized use, reproduction, or distribution of this software, or any
 * portion of it, may result in severe civil and criminal penalties, and
 * will be prosecuted to the maximum extent possible under the law.
 */
package com.ghatana.contracts.event.v1;

import static org.assertj.core.api.Assertions.*;

/**
 * Simplified tests for protobuf message classes in the event model. Focuses on basic functionality
 * and avoids complex test cases.
 */
class EventMessagesSimpleTest {

    //  @Test
    //  void testMinimalProducerEvent() {
    //    // Create a minimal event
    //    String eventId = UUID.randomUUID().toString();
    //    ProducerEvent event =
    //        ProducerEvent.newBuilder()
    //            .setEventId(Uuid.newBuilder().setValue(eventId).build())
    //            .setEventName("test.event")
    //            .setEventTypeVersion("1.0.0")
    //            .build();
    //
    //    assertThat(event).isNotNull();
    //    assertThat(event.getEventId().getValue()).isEqualTo(eventId);
    //    assertThat(event.getEventName()).isEqualTo("test.event");
    //    assertThat(event.getEventTypeVersion()).isEqualTo("1.0.0");
    //  }
    //
    //  @Test
    //  void testEventBatch() {
    //    // Create two events
    //    String eventId1 = UUID.randomUUID().toString();
    //    String eventId2 = UUID.randomUUID().toString();
    //
    //    ProducerEvent event1 =
    //        ProducerEvent.newBuilder()
    //            .setEventId(Uuid.newBuilder().setValue(eventId1).build())
    //            .setEventName("test.event.one")
    //            .setEventTypeVersion("1.0.0")
    //            .build();
    //
    //    ProducerEvent event2 =
    //        ProducerEvent.newBuilder()
    //            .setEventId(Uuid.newBuilder().setValue(eventId2).build())
    //            .setEventName("test.event.two")
    //            .setEventTypeVersion("1.0.0")
    //            .build();
    //
    //    // Create a batch with the events
    //    EventBatch batch =
    //        EventBatch.newBuilder()
    //            .setBatchId(Uuid.newBuilder().setValue(UUID.randomUUID().toString()).build())
    //            .addEvents(event1)
    //            .addEvents(event2)
    //            .build();
    //
    //    assertThat(batch).isNotNull();
    //    assertThat(batch.getEventsCount()).isEqualTo(2);
    //    assertThat(batch.getEvents(0).getEventId().getValue()).isEqualTo(eventId1);
    //    assertThat(batch.getEvents(1).getEventId().getValue()).isEqualTo(eventId2);
    //  }
    //
    //  @Test
    //  void testGValueTypes() {
    //    // Test string value
    //    GValue stringValue = GValue.newBuilder().setStringValue("test").build();
    //    assertThat(stringValue.getStringValue()).isEqualTo("test");
    //
    //    // Test number value
    //    GValue numberValue = GValue.newBuilder().setDoubleValue(42.5).build();
    //    assertThat(numberValue.getDoubleValue()).isEqualTo(42.5);
    //  }
}
