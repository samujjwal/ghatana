//! Benchmarks for storage components

use std::time::Duration;

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use time::OffsetDateTime;
use tokio::runtime::Runtime;

use agent_rs::metrics::{CpuMetrics, MemoryMetrics, NetworkMetrics, SystemDiskMetrics, SystemMetrics};
use agent_rs::storage::{
    AggregationFunction, MetricsQueryBuilder, MetricsStorage, Storage, StorageTrait,
};

/// Create test metrics data
fn create_test_metrics(count: usize) -> Vec<SystemMetrics> {
    let mut metrics = Vec::with_capacity(count);
    let base_timestamp = OffsetDateTime::now_utc().unix_timestamp() as u64;
    
    for i in 0..count {
        metrics.push(SystemMetrics {
            timestamp: base_timestamp + (i as u64 * 60), // One minute apart
            cpu: CpuMetrics {
                usage_percent: (i % 100) as f64,
                core_usage: vec![(i % 100) as f64],
                cores: 4,
                name: "cpu0".to_string(),
                frequency: 2400,
                load_average: None,
            },
            memory: MemoryMetrics {
                total: 1024 * 1024 * 1024,
                used: (512 + i * 10) * 1024 * 1024,
                free: (512 - i * 10) * 1024 * 1024,
                swap_total: 2048 * 1024 * 1024,
                swap_used: (128 + i * 5) * 1024 * 1024,
                swap_free: (1920 - i * 5) * 1024 * 1024,
                usage_percent: (50.0 + i as f64) % 100.0,
                swap_usage_percent: (6.25 + i as f64 * 0.5) % 100.0,
            },
            disk: SystemDiskMetrics::default(),
            network: NetworkMetrics::default(),
            processes: vec![],
        });
    }
    
    metrics
}

/// Setup storage with test data
fn setup_storage(rt: &Runtime, record_count: usize) -> MetricsStorage {
    rt.block_on(async {
        let storage = Storage::memory().await.expect("Failed to create storage");
        let metrics_storage = MetricsStorage::new(storage);
        
        // Initialize storage
        metrics_storage.init().await.expect("Failed to initialize storage");
        
        // Store test metrics
        let hostname = "test-host".to_string();
        let test_metrics = create_test_metrics(record_count);
        metrics_storage.store_metrics_batch(&test_metrics, &hostname).await
            .expect("Failed to store metrics");
            
        metrics_storage
    })
}

fn bench_metrics_query(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    // Create datasets of different sizes
    let small_storage = setup_storage(&rt, 100);
    let medium_storage = setup_storage(&rt, 1_000);
    let large_storage = setup_storage(&rt, 10_000);
    
    let mut group = c.benchmark_group("metrics_query");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark simple queries
    for (name, storage) in [
        ("small_100", &small_storage),
        ("medium_1k", &medium_storage),
        ("large_10k", &large_storage),
    ] {
        group.bench_with_input(
            BenchmarkId::new("simple_query", name),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        let query = MetricsQueryBuilder::new()
                            .metric_type("cpu")
                            .hostname("test-host")
                            .build();
                        
                        black_box(storage.query_metrics(query).await.unwrap())
                    })
                });
            },
        );
    }
    
    // Benchmark filtered queries
    for (name, storage) in [
        ("small_100", &small_storage),
        ("medium_1k", &medium_storage),
        ("large_10k", &large_storage),
    ] {
        group.bench_with_input(
            BenchmarkId::new("filtered_query", name),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        let now = OffsetDateTime::now_utc();
                        let one_hour_ago = now - time::Duration::hours(1);
                        
                        let query = MetricsQueryBuilder::new()
                            .metric_type("cpu")
                            .hostname("test-host")
                            .start_time(one_hour_ago)
                            .end_time(now)
                            .build();
                        
                        black_box(storage.query_metrics(query).await.unwrap())
                    })
                });
            },
        );
    }
    
    // Benchmark paginated queries
    for (name, storage) in [
        ("small_100", &small_storage),
        ("medium_1k", &medium_storage),
        ("large_10k", &large_storage),
    ] {
        group.bench_with_input(
            BenchmarkId::new("paginated_query", name),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        let query_builder = MetricsQueryBuilder::new()
                            .metric_type("cpu")
                            .hostname("test-host")
                            .limit(10)
                            .offset(0)
                            .sort_by_timestamp_desc();
                        
                        black_box(storage.query_metrics_paginated(query_builder).await.unwrap())
                    })
                });
            },
        );
    }
    
    // Benchmark aggregation queries
    for (name, storage) in [
        ("small_100", &small_storage),
        ("medium_1k", &medium_storage),
        ("large_10k", &large_storage),
    ] {
        group.bench_with_input(
            BenchmarkId::new("aggregation_query", name),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        let query = MetricsQueryBuilder::new()
                            .metric_type("cpu")
                            .hostname("test-host")
                            .build();
                        
                        black_box(storage.query_metrics_with_aggregation(
                            &query,
                            &AggregationFunction::Count,
                            &["metric_type".to_string()],
                        ).await.unwrap())
                    })
                });
            },
        );
    }
    
    group.finish();
}

fn bench_metrics_storage(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("metrics_storage");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark batch storage with different batch sizes
    for batch_size in [10, 100, 1000] {
        group.bench_with_input(
            BenchmarkId::new("batch_storage", batch_size),
            &batch_size,
            |b, &batch_size| {
                b.iter(|| {
                    rt.block_on(async {
                        let storage = Storage::memory().await.unwrap();
                        let metrics_storage = MetricsStorage::new(storage);
                        
                        let hostname = "test-host".to_string();
                        let test_metrics = create_test_metrics(batch_size);
                        
                        black_box(metrics_storage.store_metrics_batch(&test_metrics, &hostname).await.unwrap())
                    })
                });
            },
        );
    }
    
    // Benchmark single metric storage
    group.bench_function("single_storage", |b| {
        b.iter(|| {
            rt.block_on(async {
                let storage = Storage::memory().await.unwrap();
                let metrics_storage = MetricsStorage::new(storage);
                
                let hostname = "test-host".to_string();
                let metric = create_test_metrics(1)[0].clone();
                
                black_box(metrics_storage.store_metrics(&metric, &hostname).await.unwrap())
            })
        });
    });
    
    group.finish();
}

fn bench_storage_monitoring(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("storage_monitoring");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark storage stats collection
    for record_count in [100, 1000, 10000] {
        let storage = setup_storage(&rt, record_count);
        
        group.bench_with_input(
            BenchmarkId::new("get_stats", record_count),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        black_box(storage.get_stats().await.unwrap())
                    })
                });
            },
        );
    }
    
    // Benchmark cleanup operation
    for record_count in [100, 1000, 10000] {
        let storage = setup_storage(&rt, record_count);
        
        group.bench_with_input(
            BenchmarkId::new("cleanup", record_count),
            &storage,
            |b, storage| {
                b.iter(|| {
                    rt.block_on(async {
                        black_box(storage.cleanup(Duration::from_secs(3600)).await.unwrap())
                    })
                });
            },
        );
    }
    
    group.finish();
}

criterion_group!(
    benches,
    bench_metrics_query,
    bench_metrics_storage,
    bench_storage_monitoring
);
criterion_main!(benches);
