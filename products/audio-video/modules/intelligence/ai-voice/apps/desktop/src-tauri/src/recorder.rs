//! Microphone recording utilities.

use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use cpal::{SampleFormat, StreamConfig};
use hound::{SampleFormat as WavSampleFormat, WavSpec, WavWriter};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::{sync::mpsc, thread};

use crate::error::{AppError, AppResult};

pub struct RecordingHandle {
    stop_tx: mpsc::Sender<()>,
    join: Option<thread::JoinHandle<AppResult<f64>>>,
}

impl RecordingHandle {
    pub fn start_default(output_path: PathBuf) -> AppResult<Self> {
        let (stop_tx, stop_rx) = mpsc::channel::<()>();

        let join = thread::spawn(move || {
            let host = cpal::default_host();
            let device = host
                .default_input_device()
                .ok_or_else(|| AppError::Audio("No input device available".to_string()))?;

            let supported_config = device.default_input_config().map_err(|e| {
                AppError::Audio(format!("Failed to get default input config: {}", e))
            })?;

            let sample_format = supported_config.sample_format();
            let config: StreamConfig = supported_config.into();

            let sample_rate = config.sample_rate.0;
            let channels = config.channels as usize;

            tracing::info!(
                "Starting mic recording: device={} sample_rate={} channels={} format={:?}",
                device.name().unwrap_or_default(),
                sample_rate,
                channels,
                sample_format
            );

            let is_recording = Arc::new(AtomicBool::new(true));
            let is_recording_stream = Arc::clone(&is_recording);

            let samples: Arc<Mutex<Vec<i16>>> = Arc::new(Mutex::new(Vec::new()));
            let samples_clone = Arc::clone(&samples);

            let err_fn = |err| {
                tracing::error!("Mic recording stream error: {}", err);
            };

            let stream = match sample_format {
                SampleFormat::F32 => device
                    .build_input_stream(
                        &config,
                        move |data: &[f32], _: &cpal::InputCallbackInfo| {
                            if !is_recording_stream.load(Ordering::SeqCst) {
                                return;
                            }
                            push_mono_samples_f32(data, channels, &samples_clone)
                        },
                        err_fn,
                        None,
                    )
                    .map_err(|e| AppError::Audio(format!("Failed to build input stream: {}", e)))?,
                SampleFormat::I16 => device
                    .build_input_stream(
                        &config,
                        move |data: &[i16], _: &cpal::InputCallbackInfo| {
                            if !is_recording_stream.load(Ordering::SeqCst) {
                                return;
                            }
                            push_mono_samples_i16(data, channels, &samples_clone)
                        },
                        err_fn,
                        None,
                    )
                    .map_err(|e| AppError::Audio(format!("Failed to build input stream: {}", e)))?,
                SampleFormat::U16 => device
                    .build_input_stream(
                        &config,
                        move |data: &[u16], _: &cpal::InputCallbackInfo| {
                            if !is_recording_stream.load(Ordering::SeqCst) {
                                return;
                            }
                            push_mono_samples_u16(data, channels, &samples_clone)
                        },
                        err_fn,
                        None,
                    )
                    .map_err(|e| AppError::Audio(format!("Failed to build input stream: {}", e)))?,
                other => {
                    return Err(AppError::Audio(format!(
                        "Unsupported input sample format: {:?}",
                        other
                    )))
                }
            };

            stream
                .play()
                .map_err(|e| AppError::Audio(format!("Failed to start input stream: {}", e)))?;

            let _ = stop_rx.recv();
            is_recording.store(false, Ordering::SeqCst);
            drop(stream);

            let samples = {
                let guard = samples
                    .lock()
                    .map_err(|_| AppError::Audio("Failed to lock sample buffer".to_string()))?;
                guard.clone()
            };

            if let Some(parent) = output_path.parent() {
                std::fs::create_dir_all(parent).ok();
            }

            let spec = WavSpec {
                channels: 1,
                sample_rate,
                bits_per_sample: 16,
                sample_format: WavSampleFormat::Int,
            };

            let mut writer = WavWriter::create(&output_path, spec)
                .map_err(|e| AppError::Audio(format!("Failed to create WAV writer: {}", e)))?;

            for s in &samples {
                writer
                    .write_sample(*s)
                    .map_err(|e| AppError::Audio(format!("Failed to write WAV sample: {}", e)))?;
            }

            writer
                .finalize()
                .map_err(|e| AppError::Audio(format!("Failed to finalize WAV file: {}", e)))?;

            let duration = samples.len() as f64 / sample_rate as f64;
            Ok(duration)
        });

        Ok(Self {
            stop_tx,
            join: Some(join),
        })
    }

    pub fn stop(mut self) -> AppResult<f64> {
        let _ = self.stop_tx.send(());

        let join = self
            .join
            .take()
            .ok_or_else(|| AppError::Audio("Recording thread not available".to_string()))?;

        join.join()
            .map_err(|_| AppError::Audio("Recording thread panicked".to_string()))?
    }
}

fn push_mono_samples_f32(data: &[f32], channels: usize, buf: &Arc<Mutex<Vec<i16>>>) {
    let mut guard = match buf.lock() {
        Ok(g) => g,
        Err(_) => return,
    };

    for frame in data.chunks(channels.max(1)) {
        let s = frame.first().copied().unwrap_or(0.0);
        let i16_sample = (s * 32767.0).clamp(-32768.0, 32767.0) as i16;
        guard.push(i16_sample);
    }
}

fn push_mono_samples_i16(data: &[i16], channels: usize, buf: &Arc<Mutex<Vec<i16>>>) {
    let mut guard = match buf.lock() {
        Ok(g) => g,
        Err(_) => return,
    };

    for frame in data.chunks(channels.max(1)) {
        let s = frame.first().copied().unwrap_or(0);
        guard.push(s);
    }
}

fn push_mono_samples_u16(data: &[u16], channels: usize, buf: &Arc<Mutex<Vec<i16>>>) {
    let mut guard = match buf.lock() {
        Ok(g) => g,
        Err(_) => return,
    };

    for frame in data.chunks(channels.max(1)) {
        let s = frame.first().copied().unwrap_or(0);
        let i16_sample = (s as i32 - 32768) as i16;
        guard.push(i16_sample);
    }
}
