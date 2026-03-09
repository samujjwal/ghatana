//! Audio processing utilities.

use crate::error::{AppError, AppResult};
use crate::models::AudioMetadata;
use hound::{SampleFormat, WavReader, WavSpec, WavWriter};
use speech_audio_rust::{AudioBuffer, AudioConfig, SampleFormat as SpeechSampleFormat};
use std::path::Path;

/// Load audio file and return metadata.
pub fn load_audio_metadata(path: &str) -> AppResult<AudioMetadata> {
    let path = Path::new(path);

    if !path.exists() {
        return Err(AppError::FileNotFound(path.display().to_string()));
    }

    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("unknown")
        .to_string();

    let format = path
        .extension()
        .and_then(|e| e.to_str())
        .unwrap_or("wav")
        .to_lowercase();

    // For WAV files, read directly
    if format == "wav" {
        let reader = WavReader::open(path)
            .map_err(|e| AppError::Audio(format!("Failed to open WAV: {}", e)))?;

        let spec = reader.spec();
        let duration = reader.duration() as f64 / spec.sample_rate as f64;

        return Ok(AudioMetadata {
            path: path.display().to_string(),
            name,
            duration,
            sample_rate: spec.sample_rate,
            channels: spec.channels,
            format,
        });
    }

    // For other formats, use default values (would need ffmpeg for full support)
    Ok(AudioMetadata {
        path: path.display().to_string(),
        name,
        duration: 0.0,
        sample_rate: 44100,
        channels: 2,
        format,
    })
}

/// Get audio duration in seconds.
pub fn get_audio_duration(path: &str) -> AppResult<f64> {
    let metadata = load_audio_metadata(path)?;
    Ok(metadata.duration)
}

/// Extract waveform data for visualization.
pub fn extract_waveform(path: &str, num_samples: usize) -> AppResult<Vec<f32>> {
    let path = Path::new(path);

    if !path.exists() {
        return Err(AppError::FileNotFound(path.display().to_string()));
    }

    let mut reader =
        WavReader::open(path).map_err(|e| AppError::Audio(format!("Failed to open WAV: {}", e)))?;

    let _spec = reader.spec();
    let total_samples = reader.duration() as usize;
    let step = (total_samples / num_samples).max(1);

    let mut waveform = Vec::with_capacity(num_samples);
    let samples: Vec<i32> = reader.samples::<i32>().filter_map(|s| s.ok()).collect();

    for i in 0..num_samples {
        let idx = (i * step).min(samples.len().saturating_sub(1));
        if idx < samples.len() {
            let normalized = samples[idx] as f32 / i32::MAX as f32;
            waveform.push(normalized.abs());
        }
    }

    Ok(waveform)
}

pub fn load_wav_as_audio_buffer(path: &str) -> AppResult<AudioBuffer> {
    let path = Path::new(path);

    if !path.exists() {
        return Err(AppError::FileNotFound(path.display().to_string()));
    }

    let mut reader =
        WavReader::open(path).map_err(|e| AppError::Audio(format!("Failed to open WAV: {}", e)))?;

    let spec = reader.spec();

    let cfg = AudioConfig {
        sample_rate: spec.sample_rate,
        channels: spec.channels,
        sample_format: SpeechSampleFormat::F32,
    };

    let samples: Vec<f32> = match (spec.sample_format, spec.bits_per_sample) {
        (SampleFormat::Int, 8) => reader
            .samples::<i8>()
            .filter_map(|s| s.ok())
            .map(|s| s as f32 / i8::MAX as f32)
            .collect(),
        (SampleFormat::Int, 16) => reader
            .samples::<i16>()
            .filter_map(|s| s.ok())
            .map(|s| s as f32 / i16::MAX as f32)
            .collect(),
        (SampleFormat::Int, 24) | (SampleFormat::Int, 32) => reader
            .samples::<i32>()
            .filter_map(|s| s.ok())
            .map(|s| s as f32 / i32::MAX as f32)
            .collect(),
        (SampleFormat::Float, 32) => reader.samples::<f32>().filter_map(|s| s.ok()).collect(),
        (SampleFormat::Float, 64) => return Err(AppError::Audio(
            "Unsupported WAV format: 64-bit float (please convert to 16-bit PCM or 32-bit float)"
                .to_string(),
        )),
        (fmt, bps) => {
            return Err(AppError::Audio(format!(
                "Unsupported WAV format: {:?} {}-bit",
                fmt, bps
            )))
        }
    };

    Ok(AudioBuffer::new(samples, cfg))
}

/// Resample audio to target sample rate.
pub fn resample_audio(
    input_path: &str,
    output_path: &str,
    target_sample_rate: u32,
) -> AppResult<()> {
    let input = Path::new(input_path);
    let output = Path::new(output_path);

    let mut reader = WavReader::open(input)
        .map_err(|e| AppError::Audio(format!("Failed to open input: {}", e)))?;

    let spec = reader.spec();

    if spec.sample_rate == target_sample_rate {
        // No resampling needed, just copy
        std::fs::copy(input, output)?;
        return Ok(());
    }

    // Read all samples
    let samples: Vec<f32> = match spec.sample_format {
        SampleFormat::Int => reader
            .samples::<i32>()
            .filter_map(|s| s.ok())
            .map(|s| s as f32 / i32::MAX as f32)
            .collect(),
        SampleFormat::Float => reader.samples::<f32>().filter_map(|s| s.ok()).collect(),
    };

    // Simple linear interpolation resampling
    let ratio = target_sample_rate as f64 / spec.sample_rate as f64;
    let new_len = (samples.len() as f64 * ratio) as usize;
    let mut resampled = Vec::with_capacity(new_len);

    for i in 0..new_len {
        let src_idx = i as f64 / ratio;
        let idx0 = src_idx.floor() as usize;
        let idx1 = (idx0 + 1).min(samples.len() - 1);
        let frac = src_idx - idx0 as f64;

        let sample = samples[idx0] * (1.0 - frac as f32) + samples[idx1] * frac as f32;
        resampled.push(sample);
    }

    // Write output
    let out_spec = WavSpec {
        channels: spec.channels,
        sample_rate: target_sample_rate,
        bits_per_sample: 32,
        sample_format: SampleFormat::Float,
    };

    let mut writer = WavWriter::create(output, out_spec)
        .map_err(|e| AppError::Audio(format!("Failed to create output: {}", e)))?;

    for sample in resampled {
        writer
            .write_sample(sample)
            .map_err(|e| AppError::Audio(format!("Failed to write sample: {}", e)))?;
    }

    writer
        .finalize()
        .map_err(|e| AppError::Audio(format!("Failed to finalize: {}", e)))?;

    Ok(())
}

/// Slice audio between start and end times.
pub fn slice_audio(
    input_path: &str,
    output_path: &str,
    start_time: f64,
    end_time: f64,
) -> AppResult<()> {
    let input = Path::new(input_path);
    let output = Path::new(output_path);

    let mut reader = WavReader::open(input)
        .map_err(|e| AppError::Audio(format!("Failed to open input: {}", e)))?;

    let spec = reader.spec();
    let start_sample = (start_time * spec.sample_rate as f64) as usize * spec.channels as usize;
    let end_sample = (end_time * spec.sample_rate as f64) as usize * spec.channels as usize;

    let samples: Vec<i32> = reader
        .samples::<i32>()
        .filter_map(|s| s.ok())
        .skip(start_sample)
        .take(end_sample - start_sample)
        .collect();

    let mut writer = WavWriter::create(output, spec)
        .map_err(|e| AppError::Audio(format!("Failed to create output: {}", e)))?;

    for sample in samples {
        writer
            .write_sample(sample)
            .map_err(|e| AppError::Audio(format!("Failed to write sample: {}", e)))?;
    }

    writer
        .finalize()
        .map_err(|e| AppError::Audio(format!("Failed to finalize: {}", e)))?;

    Ok(())
}

/// Mix multiple audio files together.
pub fn mix_audio(input_paths: &[&str], volumes: &[f32], output_path: &str) -> AppResult<()> {
    if input_paths.is_empty() {
        return Err(AppError::InvalidRequest("No input files".to_string()));
    }

    if input_paths.len() != volumes.len() {
        return Err(AppError::InvalidRequest(
            "Volumes count mismatch".to_string(),
        ));
    }

    // Read first file to get spec
    let first_reader = WavReader::open(input_paths[0])
        .map_err(|e| AppError::Audio(format!("Failed to open first input: {}", e)))?;
    let spec = first_reader.spec();
    drop(first_reader);

    // Read all files
    let mut all_samples: Vec<Vec<f32>> = Vec::new();
    let mut max_len = 0;

    for path in input_paths {
        let mut reader = WavReader::open(path)
            .map_err(|e| AppError::Audio(format!("Failed to open {}: {}", path, e)))?;

        let samples: Vec<f32> = reader
            .samples::<i32>()
            .filter_map(|s| s.ok())
            .map(|s| s as f32 / i32::MAX as f32)
            .collect();

        max_len = max_len.max(samples.len());
        all_samples.push(samples);
    }

    // Mix
    let mut mixed = vec![0.0f32; max_len];
    for (samples, &volume) in all_samples.iter().zip(volumes.iter()) {
        for (i, &sample) in samples.iter().enumerate() {
            mixed[i] += sample * volume;
        }
    }

    // Normalize to prevent clipping
    let max_val = mixed.iter().map(|s| s.abs()).fold(0.0f32, f32::max);
    if max_val > 1.0 {
        for sample in &mut mixed {
            *sample /= max_val;
        }
    }

    // Write output
    let out_spec = WavSpec {
        channels: spec.channels,
        sample_rate: spec.sample_rate,
        bits_per_sample: 32,
        sample_format: SampleFormat::Float,
    };

    let mut writer = WavWriter::create(output_path, out_spec)
        .map_err(|e| AppError::Audio(format!("Failed to create output: {}", e)))?;

    for sample in mixed {
        writer
            .write_sample(sample)
            .map_err(|e| AppError::Audio(format!("Failed to write sample: {}", e)))?;
    }

    writer
        .finalize()
        .map_err(|e| AppError::Audio(format!("Failed to finalize: {}", e)))?;

    Ok(())
}
