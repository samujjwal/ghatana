//! Tauri commands for the AI Voice application.

use crate::error::AppError;
use crate::models::*;
use crate::project_storage::{AudioTrack, Project, ProjectMetadata, ProjectSettings, ProjectStorage};
use crate::python;
use crate::state::AppState;
use crate::audio;
use hound::{SampleFormat, WavSpec, WavWriter};
use serde_json::Value;
use std::f32::consts::PI;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::path::{Path, PathBuf};
use tauri::State;

// ============================================================================
// Audio Commands
// ============================================================================

/// Load an audio file and return its metadata.
#[tauri::command]
pub async fn ai_voice_load_audio(
    path: String,
    _state: State<'_, AppState>,
) -> Result<AudioMetadata, AppError> {
    tracing::info!("Loading audio: {}", path);
    audio::load_audio_metadata(&path)
}

/// Get audio file information.
#[tauri::command]
pub async fn ai_voice_get_audio_info(
    path: String,
) -> Result<AudioMetadata, AppError> {
    audio::load_audio_metadata(&path)
}

/// Play audio file.
#[tauri::command]
pub async fn ai_voice_play_audio(
    path: String,
    state: State<'_, AppState>,
) -> Result<(), AppError> {
    // Stop any existing playback first
    {
        let mut handle = state.playback_handle.write().await;
        if let Some(h) = handle.take() {
            let _ = h.stop();
        }
    }

    let mut playing = state.playing_audio.write().await;
    *playing = Some(path.clone());
    tracing::info!("Playing audio: {}", path);

    let handle = crate::playback::PlaybackHandle::start_default(std::path::PathBuf::from(&path))?;
    let mut playback_handle = state.playback_handle.write().await;
    *playback_handle = Some(handle);

    Ok(())
}

/// Stop audio playback.
#[tauri::command]
pub async fn ai_voice_stop_audio(
    state: State<'_, AppState>,
) -> Result<(), AppError> {
    let mut playing = state.playing_audio.write().await;
    *playing = None;

    let mut handle = state.playback_handle.write().await;
    if let Some(h) = handle.take() {
        let _ = h.stop();
    }

    tracing::info!("Stopped audio playback");
    Ok(())
}

/// Export audio to file.
#[tauri::command]
pub async fn ai_voice_export_audio(
    input_path: String,
    output_path: String,
    format: String,
) -> Result<(), AppError> {
    tracing::info!("Exporting audio: {} -> {} ({})", input_path, output_path, format);
    // Would use speech-audio-rust for format conversion
    std::fs::copy(&input_path, &output_path)?;
    Ok(())
}

// ============================================================================
// Stem Separation Commands
// ============================================================================

/// Separate audio into stems.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_separate_stems(
    audio_path: String,
    audio_duration: Option<f64>,
    state: State<'_, AppState>,
) -> Result<StemsOutput, AppError> {
    tracing::info!("Separating stems: {}", audio_path);
    
    let output_dir = state.temp_path(&format!("stems_{}", uuid::Uuid::new_v4()));
    std::fs::create_dir_all(&output_dir)?;
    
    let meta = crate::audio::load_audio_metadata(&audio_path)?;
    tracing::info!(
        "Stem separation duration inputs: meta_duration={}, audio_duration={:?}",
        meta.duration,
        audio_duration
    );

    let mut duration = meta.duration;
    if duration <= 0.0 {
        if let Some(d) = audio_duration {
            duration = d;
            tracing::info!("Using frontend-provided duration (audio_duration): {}", duration);
        } else {
            tracing::warn!(
                "Audio duration unavailable from metadata; defaulting to 0.5s placeholder stems"
            );
        }
    }
    let duration = duration.max(0.5);
    tracing::info!("Stem separation duration resolved: {}", duration);

    let log_wav_duration = |label: &str, path: &str| {
        match crate::audio::load_audio_metadata(path) {
            Ok(m) => tracing::info!("Stem WAV duration on disk: {} duration={}", label, m.duration),
            Err(e) => tracing::warn!("Failed to read stem WAV metadata for {}: {}", label, e),
        }
    };

    // 1) Prefer system Python (external process). This allows real Demucs stems
    // if the user has the deps installed.
    match python::separate_stems_system_python(&audio_path, output_dir.to_str().unwrap_or(""))
        .await
    {
        Ok(result) => {
            tracing::info!("Stem separation used system python");
            tracing::info!(
                "Stem separation result durations: vocals={}, drums={}, bass={}, other={}",
                result.vocals.duration,
                result.drums.duration,
                result.bass.duration,
                result.other.duration
            );
            log_wav_duration("vocals", &result.vocals.path);
            log_wav_duration("drums", &result.drums.path);
            log_wav_duration("bass", &result.bass.path);
            log_wav_duration("other", &result.other.path);
            return Ok(result);
        }
        Err(err) => {
            tracing::warn!("System python stem separation unavailable: {}", err);
        }
    }

    // 2) Fall back to embedded python via pyo3.
    match python::separate_stems(&audio_path, output_dir.to_str().unwrap_or("")) {
        Ok(result) => {
            tracing::info!("Stem separation used embedded python (pyo3)");
            tracing::info!(
                "Stem separation python result durations: vocals={}, drums={}, bass={}, other={}",
                result.vocals.duration,
                result.drums.duration,
                result.bass.duration,
                result.other.duration
            );

            log_wav_duration("vocals", &result.vocals.path);
            log_wav_duration("drums", &result.drums.path);
            log_wav_duration("bass", &result.bass.path);
            log_wav_duration("other", &result.other.path);
            Ok(result)
        }
        Err(AppError::Python(message)) if message.contains("stem_separator_enhanced") => {
            tracing::warn!(
                "Stem separation Python module unavailable, using placeholder stems: {}",
                message
            );

            let vocals_path = output_dir.join("vocals.wav");
            let drums_path = output_dir.join("drums.wav");
            let bass_path = output_dir.join("bass.wav");
            let other_path = output_dir.join("other.wav");

            write_example_wav(&vocals_path, duration, 44_100u32, 440.0)?;
            write_example_wav(&drums_path, duration, 44_100u32, 220.0)?;
            write_example_wav(&bass_path, duration, 44_100u32, 110.0)?;
            write_example_wav(&other_path, duration, 44_100u32, 330.0)?;

            log_wav_duration("vocals", &vocals_path.to_string_lossy());
            log_wav_duration("drums", &drums_path.to_string_lossy());
            log_wav_duration("bass", &bass_path.to_string_lossy());
            log_wav_duration("other", &other_path.to_string_lossy());

            Ok(StemsOutput {
                vocals: StemResult {
                    path: vocals_path.to_string_lossy().to_string(),
                    duration,
                },
                drums: StemResult {
                    path: drums_path.to_string_lossy().to_string(),
                    duration,
                },
                bass: StemResult {
                    path: bass_path.to_string_lossy().to_string(),
                    duration,
                },
                other: StemResult {
                    path: other_path.to_string_lossy().to_string(),
                    duration,
                },
                used_fallback: true,
                warning: Some(
                    "Python stem separation is unavailable. Generated placeholder sine-wave stems (constant tones). Install Python deps (demucs/torch) to get real stems."
                        .to_string(),
                ),
            })
        }
        Err(err) => Err(err),
    }
}

/// Get separation progress.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_separation_progress(
    task_id: String,
    state: State<'_, AppState>,
) -> Result<SeparationTask, AppError> {
    let tasks = state.separation_tasks.read().await;
    tasks
        .get(&task_id)
        .cloned()
        .ok_or_else(|| AppError::NotInitialized(format!("Task not found: {}", task_id)))
}

// ============================================================================
// Phrase Detection Commands
// ============================================================================

/// Detect phrases in vocal audio.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_detect_phrases(
    vocal_path: String,
) -> Result<Vec<DetectedPhrase>, AppError> {
    tracing::info!("Detecting phrases: {}", vocal_path);
    python::detect_phrases(&vocal_path)
}

// ============================================================================
// Voice Conversion Commands
// ============================================================================

/// Convert a phrase using AI voice.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_convert_phrase(
    phrase_id: String,
    voice_model_id: String,
    start_time: f64,
    end_time: f64,
    state: State<'_, AppState>,
) -> Result<ConversionResult, AppError> {
    tracing::info!("Converting phrase {} with model {}", phrase_id, voice_model_id);
    let duration_seconds = (end_time - start_time).max(0.5).min(20.0);
    
    let models = state.voice_models.read().await;
    let _model = models
        .get(&voice_model_id)
        .ok_or_else(|| AppError::Model(format!("Model not found: {}", voice_model_id)))?;
    
    let output_path = state.temp_path(&format!("converted_{}.wav", phrase_id));

    // Placeholder: generate an audible file so the UI can preview takes.
    // (Real implementation will slice vocals and run RVC conversion.)
    write_example_wav(&output_path, duration_seconds, 44_100u32, 440.0)?;

    Ok(ConversionResult {
        path: output_path.to_string_lossy().to_string(),
        duration: duration_seconds,
    })
}

/// Convert full audio using AI voice.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_convert_full(
    input_path: String,
    voice_model_id: String,
    pitch_shift: f32,
    state: State<'_, AppState>,
) -> Result<ConversionResult, AppError> {
    tracing::info!("Converting full audio with model {}", voice_model_id);
    
    let models = state.voice_models.read().await;
    let model = models
        .get(&voice_model_id)
        .ok_or_else(|| AppError::Model(format!("Model not found: {}", voice_model_id)))?;
    
    let output_path = state.temp_path(&format!("converted_{}.wav", uuid::Uuid::new_v4()));
    
    python::convert_voice(
        &input_path,
        output_path.to_str().unwrap_or(""),
        &model.path,
        pitch_shift,
    )
}

// ============================================================================
// Voice Model Commands
// ============================================================================

/// List available voice models.
#[tauri::command]
pub async fn ai_voice_list_models(
    state: State<'_, AppState>,
) -> Result<Vec<VoiceModel>, AppError> {
    {
        let models = state.voice_models.read().await;
        if !models.is_empty() {
            return Ok(models.values().cloned().collect());
        }
    }

    let mut discovered: Vec<VoiceModel> = Vec::new();
    if let Ok(entries) = std::fs::read_dir(&state.models_dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_file() {
                continue;
            }

            let id = match path.file_stem().and_then(|s| s.to_str()) {
                Some(v) if !v.is_empty() => v.to_string(),
                _ => continue,
            };

            let name = id.clone();
            let created_at = chrono::Utc::now().to_rfc3339();

            discovered.push(VoiceModel {
                id,
                name,
                path: path.to_string_lossy().to_string(),
                created_at,
                training_samples: 0,
                quality: 0.0,
                is_default: false,
            });
        }
    }

    if !discovered.is_empty() {
        let mut models = state.voice_models.write().await;
        for model in &discovered {
            models.insert(model.id.clone(), model.clone());
        }
    }

    Ok(discovered)
}

/// Load a voice model.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_load_model(
    model_id: String,
    state: State<'_, AppState>,
) -> Result<VoiceModel, AppError> {
    let models = state.voice_models.read().await;
    models
        .get(&model_id)
        .cloned()
        .ok_or_else(|| AppError::Model(format!("Model not found: {}", model_id)))
}

/// Delete a voice model.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_delete_model(
    model_id: String,
    state: State<'_, AppState>,
) -> Result<(), AppError> {
    let mut models = state.voice_models.write().await;
    
    if let Some(model) = models.remove(&model_id) {
        let path = std::path::Path::new(&model.path);
        if path.exists() {
            std::fs::remove_file(path)?;
        }
    }
    
    Ok(())
}

/// Test a voice model with sample text.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_test_model(
    model_id: String,
    text: String,
    state: State<'_, AppState>,
) -> Result<String, AppError> {
    tracing::info!("Testing model {} with text: {}", model_id, text);
    
    let models = state.voice_models.read().await;
    let _model = models
        .get(&model_id)
        .ok_or_else(|| AppError::Model(format!("Model not found: {}", model_id)))?;
    
    // In a real implementation, this would synthesize speech
    // and return the path to the audio file
    Ok(state.temp_path("test_output.wav").to_string_lossy().to_string())
}

// ============================================================================
// Training Commands
// ============================================================================

/// Start training a new voice model.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_start_training(
    model_name: String,
    sample_paths: Vec<String>,
    state: State<'_, AppState>,
) -> Result<String, AppError> {
    tracing::info!("Starting training for model: {}", model_name);
    
    let output_path = state.model_path(&model_name);
    let session_id = python::train_voice_model(
        &sample_paths,
        output_path.to_str().unwrap_or(""),
        &model_name,
    )?;
    
    let mut sessions = state.training_sessions.write().await;
    sessions.insert(session_id.clone(), TrainingSession {
        id: session_id.clone(),
        model_name,
        status: TrainingStatus::Pending,
        progress: 0.0,
        started_at: Some(chrono::Utc::now().to_rfc3339()),
        completed_at: None,
        error: None,
    });
    
    Ok(session_id)
}

/// Get training status.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_training_status(
    session_id: String,
    state: State<'_, AppState>,
) -> Result<TrainingStatusResponse, AppError> {
    let sessions = state.training_sessions.read().await;
    
    if let Some(session) = sessions.get(&session_id) {
        Ok(TrainingStatusResponse {
            status: session.status,
            progress: session.progress,
            error: session.error.clone(),
        })
    } else {
        python::get_training_status(&session_id)
    }
}

/// Start recording audio.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_start_recording(
    state: State<'_, AppState>,
) -> Result<(), AppError> {
    let mut is_recording = state.is_recording.write().await;
    let mut recording_path = state.recording_path.write().await;
    let mut recording_handle = state.recording_handle.write().await;

    if *is_recording {
        return Err(AppError::InvalidRequest("Recording already in progress".to_string()));
    }

    *is_recording = true;
    *recording_path = Some(state.temp_path(&format!("recording_{}.wav", uuid::Uuid::new_v4())));

    let out_path = recording_path
        .as_ref()
        .ok_or_else(|| AppError::Audio("Failed to allocate recording path".to_string()))?
        .clone();

    let handle = crate::recorder::RecordingHandle::start_default(out_path)?;
    *recording_handle = Some(handle);

    tracing::info!("Started recording to {:?}", *recording_path);
    Ok(())
}

/// Stop recording and return the recorded audio.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_stop_recording(
    state: State<'_, AppState>,
) -> Result<RecordingResult, AppError> {
    let mut is_recording = state.is_recording.write().await;
    let recording_path = state.recording_path.read().await;
    let mut recording_handle = state.recording_handle.write().await;

    if !*is_recording {
        return Err(AppError::NotInitialized("No recording in progress".to_string()));
    }

    *is_recording = false;

    let path = recording_path
        .as_ref()
        .ok_or_else(|| AppError::NotInitialized("No recording in progress".to_string()))?;

    let handle = recording_handle
        .take()
        .ok_or_else(|| AppError::NotInitialized("Recorder not initialized".to_string()))?;

    let duration = handle.stop()?;

    tracing::info!("Stopped recording: {:?}", path);

    Ok(RecordingResult {
        path: path.to_string_lossy().to_string(),
        duration,
    })
}

// ============================================================================
// Model Download Commands
// ============================================================================

/// List available models for download.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_list_available_models(
    state: State<'_, AppState>,
) -> Result<Vec<AvailableModel>, AppError> {
    let is_downloaded = |model_id: &str, file_name: &str| -> bool {
        let manifest_path = state.models_dir.join("manifest.json");
        if let Ok(contents) = std::fs::read_to_string(manifest_path) {
            if let Ok(value) = serde_json::from_str::<Value>(&contents) {
                if value.get(model_id).is_some() {
                    return true;
                }
            }
        }

        state.models_dir.join(file_name).exists()
    };

    Ok(vec![
        AvailableModel {
            id: "demucs-htdemucs".to_string(),
            name: "Demucs HT".to_string(),
            description: "High-quality stem separation model".to_string(),
            size: 2_400 * 1024 * 1024,
            url: "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/955717e8-8726e21a.th".to_string(),
            model_type: "demucs".to_string(),
            is_downloaded: is_downloaded("demucs-htdemucs", "htdemucs-v4.th"),
            download_progress: None,
        },
        AvailableModel {
            id: "demucs-htdemucs_ft".to_string(),
            name: "Demucs HT (Fine-tuned)".to_string(),
            description: "Fine-tuned hybrid transformer stem separation model".to_string(),
            size: 2_400 * 1024 * 1024,
            url: "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/f7e0c4bc-ba3fe64a.th".to_string(),
            model_type: "demucs".to_string(),
            is_downloaded: is_downloaded("demucs-htdemucs_ft", "htdemucs_ft-v4.th"),
            download_progress: None,
        },
        AvailableModel {
            id: "demucs-htdemucs_6s".to_string(),
            name: "Demucs HT (6 stems)".to_string(),
            description: "Hybrid transformer stem separation model (6 stems)".to_string(),
            size: 2_400 * 1024 * 1024,
            url: "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/5c90dfd2-34c22ccb.th".to_string(),
            model_type: "demucs".to_string(),
            is_downloaded: is_downloaded("demucs-htdemucs_6s", "htdemucs_6s-v4.th"),
            download_progress: None,
        },
        AvailableModel {
            id: "rvc-base".to_string(),
            name: "RVC Base".to_string(),
            description: "Base model for voice conversion".to_string(),
            size: 150 * 1024 * 1024,
            url: "".to_string(),
            model_type: "rvc".to_string(),
            is_downloaded: state.models_dir.join("rvc_base.pth").exists(),
            download_progress: None,
        },
        AvailableModel {
            id: "crepe-full".to_string(),
            name: "CREPE Full".to_string(),
            description: "Pitch detection model".to_string(),
            size: 25 * 1024 * 1024,
            url: "".to_string(),
            model_type: "crepe".to_string(),
            is_downloaded: state.models_dir.join("crepe_full.h5").exists(),
            download_progress: None,
        },
    ])
}

/// Download a model.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_download_model(
    model_id: String,
    state: State<'_, AppState>,
) -> Result<(), AppError> {
    let cache_dir: PathBuf = state.models_dir.clone();
    tracing::info!(
        model_id = model_id.as_str(),
        cache_dir = cache_dir.to_string_lossy().as_ref(),
        "Downloading model"
    );

    let url = match model_id.as_str() {
        "demucs-htdemucs" => {
            "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/955717e8-8726e21a.th"
        }
        "demucs-htdemucs_ft" => {
            "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/f7e0c4bc-ba3fe64a.th"
        }
        "demucs-htdemucs_6s" => {
            "https://dl.fbaipublicfiles.com/demucs/hybrid_transformer/5c90dfd2-34c22ccb.th"
        }
        _ => "",
    };
    
    let mut downloads = state.download_tasks.write().await;
    downloads.insert(model_id.clone(), DownloadTask {
        id: uuid::Uuid::new_v4().to_string(),
        model_id: model_id.clone(),
        url: url.to_string(),
        progress: 0.0,
        status: DownloadStatus::Downloading,
        error: None,
    });

    drop(downloads);

    let model_id_for_task = model_id.clone();

    let download_result = tokio::task::spawn_blocking(move || {
        let cache_dir_str = cache_dir.to_str().unwrap_or("");
        catch_unwind(AssertUnwindSafe(|| {
            crate::ml_model_bridge::download_ml_model(&model_id_for_task, cache_dir_str)
        }))
        .map_err(|panic_payload| {
            let msg = if let Some(s) = panic_payload.downcast_ref::<&str>() {
                (*s).to_string()
            } else if let Some(s) = panic_payload.downcast_ref::<String>() {
                s.clone()
            } else {
                "unknown panic".to_string()
            };
            anyhow::anyhow!("panic in model download: {}", msg)
        })?
    })
    .await
    .map_err(|e| AppError::Model(format!("Model download task failed: {}", e)))?;

    match download_result {
        Ok(_) => {
            tracing::info!(model_id = model_id.as_str(), "Model download completed");
            let mut downloads = state.download_tasks.write().await;
            if let Some(task) = downloads.get_mut(&model_id) {
                task.status = DownloadStatus::Completed;
                task.progress = 100.0;
                task.error = None;
            }
            Ok(())
        }
        Err(err) => {
            tracing::error!(model_id = model_id.as_str(), error = %err, "Model download failed");
            let mut downloads = state.download_tasks.write().await;
            if let Some(task) = downloads.get_mut(&model_id) {
                task.status = DownloadStatus::Failed;
                task.error = Some(err.to_string());
            }
            Err(AppError::Model(err.to_string()))
        }
    }
}

/// Get download progress.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_download_progress(
    model_id: String,
    state: State<'_, AppState>,
) -> Result<DownloadTask, AppError> {
    let downloads = state.download_tasks.read().await;
    downloads
        .get(&model_id)
        .cloned()
        .ok_or_else(|| AppError::NotInitialized(format!("Download not found: {}", model_id)))
}

// ============================================================================
// Project Commands
// ============================================================================

/// Export project.
#[tauri::command(rename_all = "snake_case")]
pub async fn ai_voice_export_project(
    project_id: String,
    state: State<'_, AppState>,
) -> Result<String, AppError> {
    tracing::info!("Exporting project: {}", project_id);
    
    let output_path = state.projects_dir.join(format!("{}_export.wav", project_id));
    
    // In a real implementation, this would mix all stems and export
    Ok(output_path.to_string_lossy().to_string())
}

fn write_example_wav(path: &Path, duration_seconds: f64, sample_rate: u32, frequency_hz: f32) -> Result<(), AppError> {
    let spec = WavSpec {
        channels: 1,
        sample_rate,
        bits_per_sample: 16,
        sample_format: SampleFormat::Int,
    };

    let mut writer = WavWriter::create(path, spec)
        .map_err(|e| AppError::Audio(format!("Failed to create WAV: {}", e)))?;
    let total_samples = (duration_seconds * sample_rate as f64).round() as usize;

    let amplitude = i16::MAX as f32 * 0.22;
    for i in 0..total_samples {
        let t = i as f32 / sample_rate as f32;
        let sample = (amplitude * (2.0 * PI * frequency_hz * t).sin()) as i16;
        writer
            .write_sample(sample)
            .map_err(|e| AppError::Audio(format!("Failed to write WAV sample: {}", e)))?;
    }

    writer
        .finalize()
        .map_err(|e| AppError::Audio(format!("Failed to finalize WAV: {}", e)))?;
    Ok(())
}

/// Generate example audio files (5-10) for testing.
#[tauri::command]
pub async fn ai_voice_generate_example_audio(
    count: Option<u32>,
    state: State<'_, AppState>,
) -> Result<Vec<AudioMetadata>, AppError> {
    let requested = count.unwrap_or(8);
    let count = requested.clamp(5, 10);

    let sample_dir = state.projects_dir.join("examples");
    std::fs::create_dir_all(&sample_dir)?;

    let sample_rate = 44_100u32;

    let mut outputs = Vec::with_capacity(count as usize);
    for idx in 0..count {
        let i = idx + 1;
        let file_name = format!("example_{:02}.wav", i);
        let file_path = sample_dir.join(&file_name);

        if !file_path.exists() {
            let duration = 3.0 + (i as f64 % 6.0);
            let frequency = 180.0 + (i as f32 * 35.0);
            write_example_wav(&file_path, duration, sample_rate, frequency)?;
        }

        let metadata = audio::load_audio_metadata(file_path.to_str().unwrap_or(""))?;
        outputs.push(metadata);
    }

    Ok(outputs)
}

/// Seed example projects in the project storage directory.
#[tauri::command]
pub async fn ai_voice_seed_example_projects(
    count: Option<u32>,
    state: State<'_, AppState>,
) -> Result<Vec<String>, AppError> {
    let requested = count.unwrap_or(8);
    let count = requested.clamp(5, 10);

    let sample_dir = state.projects_dir.join("examples");
    std::fs::create_dir_all(&sample_dir)?;

    // Ensure example audio exists.
    let mut example_paths: Vec<(String, f64)> = Vec::with_capacity(count as usize);
    for idx in 0..count {
        let i = idx + 1;
        let file_name = format!("example_{:02}.wav", i);
        let file_path = sample_dir.join(&file_name);
        if !file_path.exists() {
            let duration = 3.0 + (i as f64 % 6.0);
            let frequency = 180.0 + (i as f32 * 35.0);
            write_example_wav(&file_path, duration, 44_100u32, frequency)?;
        }

        let meta = audio::load_audio_metadata(file_path.to_str().unwrap_or(""))?;
        example_paths.push((meta.path, meta.duration));
    }

    let storage = ProjectStorage::new(state.projects_dir.clone())
        .map_err(|e| AppError::InvalidRequest(format!("Failed to init project storage: {}", e)))?;

    let mut created_ids: Vec<String> = Vec::with_capacity(count as usize);
    for (idx, (path, duration)) in example_paths.into_iter().enumerate() {
        let now = chrono::Utc::now();
        let project = Project {
            id: uuid::Uuid::new_v4().to_string(),
            name: format!("Example Project {:02}", idx + 1),
            created: now,
            modified: now,
            tracks: vec![AudioTrack {
                id: uuid::Uuid::new_v4().to_string(),
                name: "Track 1".to_string(),
                audio_path: path,
                volume: 1.0,
                pan: 0.0,
                muted: false,
                solo: false,
                color: "#a855f7".to_string(),
                effects: Vec::new(),
                start_time: 0.0,
                duration,
            }],
            settings: ProjectSettings::default(),
            metadata: ProjectMetadata::default(),
        };

        storage
            .save_project(&project)
            .map_err(|e| AppError::InvalidRequest(format!("Failed to save project: {}", e)))?;
        created_ids.push(project.id);
    }

    Ok(created_ids)
}

// ============================================================================
// Voice Cloning Commands
// ============================================================================

/// Clone a voice from audio samples.
#[tauri::command]
pub async fn ai_voice_clone_voice(
    voice_name: String,
    audio_paths: Vec<String>,
    epochs: Option<u32>,
    learning_rate: Option<f32>,
    _state: State<'_, AppState>,
) -> Result<VoiceCloningResult, AppError> {
    tracing::info!("Cloning voice: {} from {} samples", voice_name, audio_paths.len());

    // Call Python voice cloning via python module
    let result = python::call_voice_cloning(
        &voice_name,
        &audio_paths,
        epochs.unwrap_or(100),
        learning_rate.unwrap_or(1e-4),
    )
    .await
    .map_err(|e| AppError::PythonError(format!("Voice cloning failed: {}", e)))?;

    tracing::info!("Voice cloning complete: {}", result.voice_id);
    Ok(result)
}

/// List all cloned voices.
#[tauri::command]
pub async fn ai_voice_list_cloned_voices(
    _state: State<'_, AppState>,
) -> Result<Vec<ClonedVoiceInfo>, AppError> {
    tracing::info!("Listing cloned voices");

    let voices = python::list_cloned_voices()
        .await
        .map_err(|e| AppError::PythonError(format!("Failed to list voices: {}", e)))?;

    Ok(voices)
}

/// Load a cloned voice for synthesis.
#[tauri::command]
pub async fn ai_voice_load_cloned_voice(
    voice_id: String,
    _state: State<'_, AppState>,
) -> Result<(), AppError> {
    tracing::info!("Loading cloned voice: {}", voice_id);

    python::load_cloned_voice(&voice_id)
        .await
        .map_err(|e| AppError::PythonError(format!("Failed to load voice: {}", e)))?;

    Ok(())
}

/// Synthesize text with a cloned voice.
#[tauri::command]
pub async fn ai_voice_synthesize_with_voice(
    text: String,
    voice_id: String,
    speed: Option<f32>,
    pitch: Option<f32>,
    output_path: String,
    _state: State<'_, AppState>,
) -> Result<SynthesisResult, AppError> {
    tracing::info!("Synthesizing with voice: {} ({})", voice_id, text.len());

    let result = python::synthesize_with_cloned_voice(
        &text,
        &voice_id,
        speed.unwrap_or(1.0),
        pitch.unwrap_or(0.0),
        &output_path,
    )
    .await
    .map_err(|e| AppError::PythonError(format!("Synthesis failed: {}", e)))?;

    tracing::info!("Synthesis complete: {:.2}s audio", result.duration_seconds);
    Ok(result)
}

/// Delete a cloned voice.
#[tauri::command]
pub async fn ai_voice_delete_cloned_voice(
    voice_id: String,
    _state: State<'_, AppState>,
) -> Result<(), AppError> {
    tracing::info!("Deleting cloned voice: {}", voice_id);

    python::delete_cloned_voice(&voice_id)
        .await
        .map_err(|e| AppError::PythonError(format!("Failed to delete voice: {}", e)))?;

    Ok(())
}

/// Extract speaker embedding from audio.
#[tauri::command]
pub async fn ai_voice_extract_speaker_embedding(
    audio_path: String,
    _state: State<'_, AppState>,
) -> Result<SpeakerEmbeddingResult, AppError> {
    tracing::info!("Extracting speaker embedding from: {}", audio_path);

    let result = python::extract_speaker_embedding(&audio_path)
        .await
        .map_err(|e| AppError::PythonError(format!("Embedding extraction failed: {}", e)))?;

    Ok(result)
}

