//! Benchmarks for compression components

use std::time::Duration;

use criterion::{black_box, criterion_group, criterion_main, Criterion, BenchmarkId};

use agent_rs::storage::{
    CompressionAlgorithm, CompressionConfig, CompressionLevel, CompressionUtils,
    compress, decompress, compress_object, decompress_object,
};

/// Create test data with different compressibility characteristics
fn create_test_data(size: usize, compressibility: &str) -> Vec<u8> {
    match compressibility {
        "high" => {
            // Highly compressible data (repeating pattern)
            let pattern = b"abcdefghijklmnopqrstuvwxyz";
            let mut data = Vec::with_capacity(size);
            while data.len() < size {
                data.extend_from_slice(pattern);
            }
            data.truncate(size);
            data
        }
        "medium" => {
            // Medium compressibility (some structure but not highly repetitive)
            let mut data = Vec::with_capacity(size);
            for i in 0..size {
                data.push(((i / 100) % 256) as u8);
            }
            data
        }
        "low" => {
            // Low compressibility (pseudo-random data)
            let mut data = Vec::with_capacity(size);
            let mut x = 123456789u32;
            for _ in 0..size {
                x = x.wrapping_mul(1103515245).wrapping_add(12345);
                data.push((x % 256) as u8);
            }
            data
        }
        _ => panic!("Unknown compressibility: {}", compressibility),
    }
}

/// Create a serializable test object
#[derive(serde::Serialize, serde::Deserialize)]
struct TestObject {
    name: String,
    value: i32,
    data: Vec<u8>,
}

fn bench_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("compression");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark compression with different algorithms and data characteristics
    for size in [1_000, 10_000, 100_000] {
        for compressibility in ["high", "medium", "low"] {
            let data = create_test_data(size, compressibility);
            
            // Gzip compression
            let gzip_config = CompressionConfig {
                algorithm: CompressionAlgorithm::Gzip,
                level: CompressionLevel::Default,
            };
            
            group.bench_with_input(
                BenchmarkId::new(
                    format!("gzip_compress_{}", compressibility),
                    size,
                ),
                &data,
                |b, data| {
                    b.iter(|| {
                        black_box(compress(black_box(data), black_box(gzip_config)).unwrap())
                    });
                },
            );
            
            // Zlib compression
            let zlib_config = CompressionConfig {
                algorithm: CompressionAlgorithm::Zlib,
                level: CompressionLevel::Default,
            };
            
            group.bench_with_input(
                BenchmarkId::new(
                    format!("zlib_compress_{}", compressibility),
                    size,
                ),
                &data,
                |b, data| {
                    b.iter(|| {
                        black_box(compress(black_box(data), black_box(zlib_config)).unwrap())
                    });
                },
            );
        }
    }
    
    group.finish();
}

fn bench_decompression(c: &mut Criterion) {
    let mut group = c.benchmark_group("decompression");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark decompression with different algorithms and data characteristics
    for size in [1_000, 10_000, 100_000] {
        for compressibility in ["high", "medium", "low"] {
            let data = create_test_data(size, compressibility);
            
            // Gzip compression/decompression
            let gzip_config = CompressionConfig {
                algorithm: CompressionAlgorithm::Gzip,
                level: CompressionLevel::Default,
            };
            let gzip_compressed = compress(&data, gzip_config).unwrap();
            
            group.bench_with_input(
                BenchmarkId::new(
                    format!("gzip_decompress_{}", compressibility),
                    size,
                ),
                &gzip_compressed,
                |b, compressed| {
                    b.iter(|| {
                        black_box(decompress(
                            black_box(compressed),
                            black_box(CompressionAlgorithm::Gzip),
                        ).unwrap())
                    });
                },
            );
            
            // Zlib compression/decompression
            let zlib_config = CompressionConfig {
                algorithm: CompressionAlgorithm::Zlib,
                level: CompressionLevel::Default,
            };
            let zlib_compressed = compress(&data, zlib_config).unwrap();
            
            group.bench_with_input(
                BenchmarkId::new(
                    format!("zlib_decompress_{}", compressibility),
                    size,
                ),
                &zlib_compressed,
                |b, compressed| {
                    b.iter(|| {
                        black_box(decompress(
                            black_box(compressed),
                            black_box(CompressionAlgorithm::Zlib),
                        ).unwrap())
                    });
                },
            );
        }
    }
    
    group.finish();
}

fn bench_compression_levels(c: &mut Criterion) {
    let mut group = c.benchmark_group("compression_levels");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark different compression levels
    let size = 100_000;
    let data = create_test_data(size, "medium");
    
    for level in [
        CompressionLevel::Fast,
        CompressionLevel::Default,
        CompressionLevel::Best,
    ] {
        // Gzip compression
        let gzip_config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level,
        };
        
        group.bench_with_input(
            BenchmarkId::new("gzip", format!("{:?}", level)),
            &data,
            |b, data| {
                b.iter(|| {
                    black_box(compress(black_box(data), black_box(gzip_config)).unwrap())
                });
            },
        );
        
        // Zlib compression
        let zlib_config = CompressionConfig {
            algorithm: CompressionAlgorithm::Zlib,
            level,
        };
        
        group.bench_with_input(
            BenchmarkId::new("zlib", format!("{:?}", level)),
            &data,
            |b, data| {
                b.iter(|| {
                    black_box(compress(black_box(data), black_box(zlib_config)).unwrap())
                });
            },
        );
    }
    
    group.finish();
}

fn bench_object_compression(c: &mut Criterion) {
    let mut group = c.benchmark_group("object_compression");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Create test objects of different sizes
    for size in [1_000, 10_000, 100_000] {
        let obj = TestObject {
            name: "test".to_string(),
            value: 42,
            data: create_test_data(size, "medium"),
        };
        
        // Gzip object compression
        let gzip_config = CompressionConfig {
            algorithm: CompressionAlgorithm::Gzip,
            level: CompressionLevel::Default,
        };
        
        group.bench_with_input(
            BenchmarkId::new("gzip_object_compress", size),
            &obj,
            |b, obj| {
                b.iter(|| {
                    black_box(compress_object(black_box(obj), black_box(gzip_config)).unwrap())
                });
            },
        );
        
        // Gzip object decompression
        let compressed = compress_object(&obj, gzip_config).unwrap();
        
        group.bench_with_input(
            BenchmarkId::new("gzip_object_decompress", size),
            &compressed,
            |b, compressed| {
                b.iter(|| {
                    black_box(decompress_object::<TestObject>(
                        black_box(compressed),
                        black_box(CompressionAlgorithm::Gzip),
                    ).unwrap())
                });
            },
        );
    }
    
    group.finish();
}

fn bench_compression_utils(c: &mut Criterion) {
    let mut group = c.benchmark_group("compression_utils");
    group.sample_size(10);
    group.measurement_time(Duration::from_secs(5));
    
    // Benchmark utility functions
    let size = 100_000;
    let data = create_test_data(size, "medium");
    
    // Default compression
    group.bench_with_input(
        BenchmarkId::new("compress_default", size),
        &data,
        |b, data| {
            b.iter(|| {
                black_box(CompressionUtils::compress_default(black_box(data)).unwrap())
            });
        },
    );
    
    // Fast compression
    group.bench_with_input(
        BenchmarkId::new("compress_fast", size),
        &data,
        |b, data| {
            b.iter(|| {
                black_box(CompressionUtils::compress_fast(black_box(data)).unwrap())
            });
        },
    );
    
    // Gzip decompression
    let compressed = CompressionUtils::compress_default(&data).unwrap();
    
    group.bench_with_input(
        BenchmarkId::new("decompress_gzip", size),
        &compressed,
        |b, compressed| {
            b.iter(|| {
                black_box(CompressionUtils::decompress_gzip(black_box(compressed)).unwrap())
            });
        },
    );
    
    group.finish();
}

criterion_group!(
    benches,
    bench_compression,
    bench_decompression,
    bench_compression_levels,
    bench_object_compression,
    bench_compression_utils
);
criterion_main!(benches);
