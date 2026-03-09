//! Comprehensive Performance Benchmark Suite
//! 
//! This benchmark suite measures performance across all critical agent components
//! including IPC transport, metrics collection, storage operations, and policy enforcement.

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};
use std::time::Duration;
use tokio::runtime::Runtime;

// Import all agent crates for comprehensive benchmarking
use agent_ipc::{Transport, TransportConfig, Message, MessagePriority};
use agent_metrics::{MetricsCollector, MetricsConfig};
use agent_storage::{StorageEngine, StorageConfig, QueueConfig};
use agent_security::{SecurityManager, SecurityConfig};
use agent_config::AgentConfig;

/// Benchmark IPC transport performance across different message sizes and priorities
fn benchmark_ipc_transport(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("ipc_transport");
    group.measurement_time(Duration::from_secs(10));
    
    // Test message sizes: 1KB, 10KB, 100KB, 1MB
    let sizes = vec![1024, 10_240, 102_400, 1_048_576];
    let priorities = vec![MessagePriority::Low, MessagePriority::Normal, MessagePriority::High, MessagePriority::Critical];
    
    for size in &sizes {
        for priority in &priorities {
            group.bench_with_input(
                BenchmarkId::new(format!("send_message_{:?}", priority), size),
                size,
                |b, &size| {
                    let config = TransportConfig::default();
                    let data = vec![0u8; size];
                    
                    b.to_async(&rt).iter(|| async {
                        let transport = Transport::tcp(config.clone()).await.unwrap();
                        let message = Message::new(data.clone(), *priority);
                        black_box(transport.send_message(message).await)
                    })
                }
            );
        }
    }
    
    group.finish();
}

/// Benchmark metrics collection performance with various metric types
fn benchmark_metrics_collection(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("metrics_collection");
    group.measurement_time(Duration::from_secs(10));
    
    // Test different metric volumes
    let volumes = vec![100, 1_000, 10_000, 100_000];
    
    for volume in &volumes {
        group.bench_with_input(
            BenchmarkId::new("collect_system_metrics", volume),
            volume,
            |b, &volume| {
                let config = MetricsConfig::default();
                
                b.to_async(&rt).iter(|| async {
                    let collector = MetricsCollector::new(config.clone()).await.unwrap();
                    
                    // Simulate collecting multiple metrics
                    for i in 0..*volume {
                        let metric_name = format!("test_metric_{}", i);
                        let metric_value = (i as f64) * 1.5;
                        black_box(collector.record_gauge(&metric_name, metric_value).await);
                    }
                })
            }
        );
        
        group.bench_with_input(
            BenchmarkId::new("collect_counter_metrics", volume),
            volume,
            |b, &volume| {
                let config = MetricsConfig::default();
                
                b.to_async(&rt).iter(|| async {
                    let collector = MetricsCollector::new(config.clone()).await.unwrap();
                    
                    // Simulate incrementing counters
                    for i in 0..*volume {
                        let counter_name = format!("test_counter_{}", i % 100); // Reuse counter names
                        black_box(collector.increment_counter(&counter_name, 1.0).await);
                    }
                })
            }
        );
    }
    
    group.finish();
}

/// Benchmark storage engine performance for different operations
fn benchmark_storage_operations(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("storage_operations");
    group.measurement_time(Duration::from_secs(10));
    
    // Test different data sizes and operation types
    let data_sizes = vec![1024, 10_240, 102_400]; // 1KB, 10KB, 100KB
    let batch_sizes = vec![1, 10, 100, 1000];
    
    for data_size in &data_sizes {
        for batch_size in &batch_sizes {
            group.bench_with_input(
                BenchmarkId::new(format!("write_batch_{}", batch_size), data_size),
                &(*data_size, *batch_size),
                |b, &(data_size, batch_size)| {
                    let config = StorageConfig::default();
                    
                    b.to_async(&rt).iter(|| async {
                        let storage = StorageEngine::new(config.clone()).await.unwrap();
                        let data = vec![0u8; data_size];
                        
                        // Write batch of items
                        for i in 0..batch_size {
                            let key = format!("benchmark_key_{}", i);
                            black_box(storage.write(&key, &data).await);
                        }
                    })
                }
            );
            
            group.bench_with_input(
                BenchmarkId::new(format!("read_batch_{}", batch_size), data_size),
                &(*data_size, *batch_size),
                |b, &(data_size, batch_size)| {
                    let config = StorageConfig::default();
                    
                    b.to_async(&rt).iter(|| async {
                        let storage = StorageEngine::new(config.clone()).await.unwrap();
                        let data = vec![0u8; data_size];
                        
                        // First write the data
                        for i in 0..batch_size {
                            let key = format!("benchmark_key_{}", i);
                            let _ = storage.write(&key, &data).await;
                        }
                        
                        // Then read it back
                        for i in 0..batch_size {
                            let key = format!("benchmark_key_{}", i);
                            black_box(storage.read(&key).await);
                        }
                    })
                }
            );
        }
    }
    
    group.finish();
}

/// Benchmark security operations including encryption, decryption, and certificate validation
fn benchmark_security_operations(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("security_operations");
    group.measurement_time(Duration::from_secs(10));
    
    // Test different payload sizes for encryption
    let payload_sizes = vec![1024, 10_240, 102_400, 1_048_576]; // 1KB to 1MB
    
    for size in &payload_sizes {
        group.bench_with_input(
            BenchmarkId::new("encrypt_decrypt", size),
            size,
            |b, &size| {
                let config = SecurityConfig::default();
                let data = vec![0u8; size];
                
                b.to_async(&rt).iter(|| async {
                    let security_manager = SecurityManager::new(config.clone()).await.unwrap();
                    
                    // Encrypt the data
                    let encrypted = security_manager.encrypt(&data).await.unwrap();
                    
                    // Decrypt it back
                    let decrypted = security_manager.decrypt(&encrypted).await.unwrap();
                    
                    black_box(decrypted);
                })
            }
        );
        
        group.bench_with_input(
            BenchmarkId::new("hash_validation", size),
            size,
            |b, &size| {
                let config = SecurityConfig::default();
                let data = vec![0u8; size];
                
                b.to_async(&rt).iter(|| async {
                    let security_manager = SecurityManager::new(config.clone()).await.unwrap();
                    
                    // Generate hash
                    let hash = security_manager.generate_hash(&data).await.unwrap();
                    
                    // Validate hash
                    let is_valid = security_manager.validate_hash(&data, &hash).await.unwrap();
                    
                    black_box(is_valid);
                })
            }
        );
    }
    
    group.finish();
}

/// Benchmark queue operations with different queue configurations
fn benchmark_queue_operations(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("queue_operations");
    group.measurement_time(Duration::from_secs(10));
    
    // Test different queue configurations
    let queue_sizes = vec![100, 1_000, 10_000];
    let message_sizes = vec![1024, 10_240]; // 1KB, 10KB
    
    for queue_size in &queue_sizes {
        for message_size in &message_sizes {
            group.bench_with_input(
                BenchmarkId::new(format!("enqueue_dequeue_{}kb", message_size / 1024), queue_size),
                &(*queue_size, *message_size),
                |b, &(queue_size, message_size)| {
                    let config = QueueConfig {
                        max_size: queue_size,
                        max_memory_usage: queue_size * message_size,
                        ..Default::default()
                    };
                    
                    b.to_async(&rt).iter(|| async {
                        use agent_storage::Queue;
                        let queue = Queue::new(config.clone()).await.unwrap();
                        let data = vec![0u8; message_size];
                        
                        // Fill queue to capacity
                        for i in 0..(queue_size / 2) {
                            let item_id = format!("item_{}", i);
                            black_box(queue.enqueue(item_id, &data).await);
                        }
                        
                        // Dequeue half the items
                        for _ in 0..(queue_size / 4) {
                            black_box(queue.dequeue().await);
                        }
                    })
                }
            );
        }
    }
    
    group.finish();
}

/// Benchmark end-to-end agent performance with realistic workloads
fn benchmark_end_to_end_performance(c: &mut Criterion) {
    let rt = Runtime::new().unwrap();
    
    let mut group = c.benchmark_group("end_to_end_performance");
    group.measurement_time(Duration::from_secs(15));
    
    // Simulate different workload intensities
    let workload_intensities = vec![10, 50, 100, 500]; // requests per second
    
    for intensity in &workload_intensities {
        group.bench_with_input(
            BenchmarkId::new("full_agent_pipeline", intensity),
            intensity,
            |b, &intensity| {
                b.to_async(&rt).iter(|| async {
                    // Simulate full agent pipeline:
                    // 1. Receive message via IPC
                    // 2. Process through security layer
                    // 3. Store in storage engine
                    // 4. Collect metrics
                    // 5. Send response
                    
                    let ipc_config = TransportConfig::default();
                    let security_config = SecurityConfig::default();
                    let storage_config = StorageConfig::default();
                    let metrics_config = MetricsConfig::default();
                    
                    let transport = Transport::tcp(ipc_config).await.unwrap();
                    let security_manager = SecurityManager::new(security_config).await.unwrap();
                    let storage = StorageEngine::new(storage_config).await.unwrap();
                    let metrics_collector = MetricsCollector::new(metrics_config).await.unwrap();
                    
                    // Simulate processing multiple messages concurrently
                    let mut handles = Vec::new();
                    
                    for i in 0..intensity {
                        let transport = transport.clone();
                        let security_manager = security_manager.clone();
                        let storage = storage.clone();
                        let metrics_collector = metrics_collector.clone();
                        
                        let handle = tokio::spawn(async move {
                            let data = format!("benchmark_message_{}", i).into_bytes();
                            
                            // 1. Receive via IPC
                            let message = Message::new(data.clone(), MessagePriority::Normal);
                            let _ = transport.send_message(message).await;
                            
                            // 2. Security processing
                            let encrypted = security_manager.encrypt(&data).await.unwrap();
                            let _ = security_manager.decrypt(&encrypted).await.unwrap();
                            
                            // 3. Storage operation
                            let key = format!("msg_{}", i);
                            let _ = storage.write(&key, &data).await;
                            let _ = storage.read(&key).await;
                            
                            // 4. Metrics collection
                            let _ = metrics_collector.increment_counter("messages_processed", 1.0).await;
                            let _ = metrics_collector.record_gauge("message_size", data.len() as f64).await;
                        });
                        
                        handles.push(handle);
                    }
                    
                    // Wait for all messages to be processed
                    for handle in handles {
                        black_box(handle.await);
                    }
                })
            }
        );
    }
    
    group.finish();
}

criterion_group!(
    performance_benches,
    benchmark_ipc_transport,
    benchmark_metrics_collection,
    benchmark_storage_operations,
    benchmark_security_operations,
    benchmark_queue_operations,
    benchmark_end_to_end_performance
);

criterion_main!(performance_benches);