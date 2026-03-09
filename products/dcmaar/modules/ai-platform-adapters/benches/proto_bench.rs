//! Benchmark for Protocol Buffers encoding/decoding with current contracts

use criterion::{black_box, criterion_group, criterion_main, Criterion};
use prost::Message;

use dcmaar_proto::dcmaar::v1::{
    ActivityType, EnvelopeMeta, Event, EventEnvelope, EventType, EventWithMetadata,
};

fn sample_envelope() -> EventEnvelope {
    EventEnvelope {
        meta: Some(EnvelopeMeta {
            tenant_id: "tenant".into(),
            device_id: "device".into(),
            session_id: "session".into(),
            timestamp: 1_000_000_000_000, // ms
            schema_version: "1.0.0".into(),
            ..Default::default()
        }),
        events: vec![EventWithMetadata {
            event: Some(Event {
                id: "event-1".into(),
                r#type: EventType::EventTypeUser as i32,
                source: "bench".into(),
                timestamp: Some(prost_types::Timestamp {
                    seconds: 1_000_000_000,
                    nanos: 0,
                }),
                data: br#"{"msg":"hello"}"#.to_vec(),
                ..Default::default()
            }),
            activity_type: ActivityType::ActivityUserInteraction as i32,
            ..Default::default()
        }],
        ..Default::default()
    }
}

fn encode_benchmark(c: &mut Criterion) {
    let envelope = sample_envelope();
    c.bench_function("encode_event_envelope", |b| {
        b.iter(|| {
            let bytes = envelope.encode_to_vec();
            black_box(bytes);
        })
    });
}

fn decode_benchmark(c: &mut Criterion) {
    let encoded = sample_envelope().encode_to_vec();
    c.bench_function("decode_event_envelope", |b| {
        b.iter(|| {
            let _decoded = EventEnvelope::decode(black_box(encoded.as_slice())).unwrap();
        })
    });
}

criterion_group!(benches, encode_benchmark, decode_benchmark);
criterion_main!(benches);
