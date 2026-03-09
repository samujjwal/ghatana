// Prevents additional console window on Windows in release, DO NOT REMOVE!!
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod grpc_client;
mod error_handling;
mod validation;

use grpc_client::{stt, tts};
use error_handling::{UserError, CircuitBreaker, retry_with_backoff, RetryConfig};
use validation::{validate_audio_file, validate_image_file, validate_video_file, validate_text};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Mutex;
use tauri::State;
use tonic::transport::Channel;

// Application state
#[derive(Debug)]
struct AppState {
    services: Mutex<HashMap<String, ServiceStatus>>,
    stt_circuit_breaker: CircuitBreaker,
    tts_circuit_breaker: CircuitBreaker,
    vision_circuit_breaker: CircuitBreaker,
    multimodal_circuit_breaker: CircuitBreaker,
}

impl Default for AppState {
    fn default() -> Self {
        Self {
            services: Mutex::new(HashMap::new()),
            stt_circuit_breaker: CircuitBreaker::default(),
            tts_circuit_breaker: CircuitBreaker::default(),
            vision_circuit_breaker: CircuitBreaker::default(),
            multimodal_circuit_breaker: CircuitBreaker::default(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct ServiceStatus {
    name: String,
    status: String,
    endpoint: String,
    last_check: String,
}

// STT Commands
#[tauri::command]
async fn stt_transcribe(
    audio_data: Vec<u8>,
    language: Option<String>,
    filename: Option<String>,
    state: State<'_, AppState>,
) -> Result<String, String> {
    // Validate audio file
    if let Err(e) = validate_audio_file(&audio_data, filename.as_deref()) {
        let user_error = match e {
            validation::ValidationError::FileTooLarge { size, max } => {
                UserError::file_too_large(max / (1024 * 1024))
            }
            validation::ValidationError::InvalidExtension { .. } => {
                UserError::invalid_file_type("audio (wav, mp3, ogg, flac, m4a)")
            }
            validation::ValidationError::InvalidFileType { .. } => {
                UserError::invalid_file_type("audio file with valid signature")
            }
            _ => UserError::processing_error(&e.to_string()),
        };
        return Err(user_error.to_json());
    }

    let addr = std::env::var("STT_GRPC_URL").unwrap_or_else(|_| "http://localhost:50051".to_string());
    
    // Use circuit breaker and retry logic
    let result = state.stt_circuit_breaker.call_async(|| {
        let addr = addr.clone();
        let audio_data = audio_data.clone();
        let language = language.clone();
        
        Box::pin(async move {
            let config = RetryConfig::default();
            
            retry_with_backoff(
                || {
                    let addr = addr.clone();
                    let audio_data = audio_data.clone();
                    let language = language.clone();
                    
                    Box::pin(async move {
                        let channel = Channel::from_shared(addr.clone())
                            .map_err(|e| e.to_string())?
                            .connect_timeout(std::time::Duration::from_secs(5))
                            .timeout(std::time::Duration::from_secs(30))
                            .connect()
                            .await
                            .map_err(|_| "Failed to connect to STT service".to_string())?;
                        
                        let mut client = stt::stt_service_client::SttServiceClient::new(channel);
                        
                        let request = tonic::Request::new(stt::TranscribeRequest {
                            audio_data,
                            language: language.unwrap_or_default(),
                            profile_id: String::new(),
                        });
                        
                        let response = client.transcribe(request)
                            .await
                            .map_err(|e| format!("Transcription failed: {}", e))?;
                        
                        Ok(response.into_inner().text)
                    })
                },
                &config,
            ).await
        })
    }).await;
    
    match result {
        Ok(text) => {
            // Update service status
            let mut services = state.services.lock().unwrap();
            services.insert("stt".to_string(), ServiceStatus {
                name: "STT".to_string(),
                status: "healthy".to_string(),
                endpoint: addr,
                last_check: chrono::Utc::now().to_rfc3339(),
            });
            Ok(text)
        }
        Err(e) => Err(e.to_json()),
    }
}

#[tauri::command]
async fn stt_get_status(state: State<'_, AppState>) -> Result<ServiceStatus, String> {
    let services = state.services.lock().unwrap();
    Ok(services.get("stt").cloned().unwrap_or(ServiceStatus {
        name: "STT".to_string(),
        status: "unknown".to_string(),
        endpoint: "http://localhost:50051".to_string(),
        last_check: chrono::Utc::now().to_rfc3339(),
    }))
}

// TTS Commands
#[tauri::command]
async fn tts_synthesize(
    text: String,
    voice_id: Option<String>,
    state: State<'_, AppState>,
) -> Result<Vec<u8>, String> {
    // Validate text input
    if let Err(e) = validate_text(&text) {
        let user_error = match e {
            validation::ValidationError::TextTooLong { length, max } => {
                UserError {
                    code: "TEXT_TOO_LONG".to_string(),
                    message: format!("Text length {} exceeds maximum {} characters", length, max),
                    recovery_action: Some("Please shorten your text or split it into smaller chunks.".to_string()),
                    is_retryable: false,
                }
            }
            _ => UserError::processing_error(&e.to_string()),
        };
        return Err(user_error.to_json());
    }

    let addr = std::env::var("TTS_GRPC_URL").unwrap_or_else(|_| "http://localhost:50052".to_string());
    
    // Use circuit breaker and retry logic
    let result = state.tts_circuit_breaker.call_async(|| {
        let addr = addr.clone();
        let text = text.clone();
        let voice_id = voice_id.clone();
        
        Box::pin(async move {
            let config = RetryConfig::default();
            
            retry_with_backoff(
                || {
                    let addr = addr.clone();
                    let text = text.clone();
                    let voice_id = voice_id.clone();
                    
                    Box::pin(async move {
                        let channel = Channel::from_shared(addr.clone())
                            .map_err(|e| e.to_string())?
                            .connect_timeout(std::time::Duration::from_secs(5))
                            .timeout(std::time::Duration::from_secs(30))
                            .connect()
                            .await
                            .map_err(|_| "Failed to connect to TTS service".to_string())?;
                        
                        let mut client = tts::tts_service_client::TtsServiceClient::new(channel);
                        
                        let request = tonic::Request::new(tts::SynthesizeRequest {
                            text,
                            voice_id: voice_id.unwrap_or_default(),
                            profile_id: String::new(),
                            options: None,
                        });
                        
                        let response = client.synthesize(request)
                            .await
                            .map_err(|e| format!("Synthesis failed: {}", e))?;
                        
                        Ok(response.into_inner().audio_data)
                    })
                },
                &config,
            ).await
        })
    }).await;
    
    match result {
        Ok(audio_data) => {
            // Update service status
            let mut services = state.services.lock().unwrap();
            services.insert("tts".to_string(), ServiceStatus {
                name: "TTS".to_string(),
                status: "healthy".to_string(),
                endpoint: addr,
                last_check: chrono::Utc::now().to_rfc3339(),
            });
            Ok(audio_data)
        }
        Err(e) => Err(e.to_json()),
    }
}

#[tauri::command]
async fn tts_get_status(state: State<'_, AppState>) -> Result<ServiceStatus, String> {
    let services = state.services.lock().unwrap();
    Ok(services.get("tts").cloned().unwrap_or(ServiceStatus {
        name: "TTS".to_string(),
        status: "unknown".to_string(),
        endpoint: "http://localhost:50052".to_string(),
        last_check: chrono::Utc::now().to_rfc3339(),
    }))
}

// AI Voice Commands
#[tauri::command]
async fn ai_voice_process(
    text: String,
    task: String,
    state: State<'_, AppState>,
) -> Result<String, String> {
    // TODO: Implement actual AI Voice processing
    println!("AI Voice request: {} chars, task: {}", text.len(), task);
    
    // Mock processing
    let processed = format!("Processed: {} (task: {})", text, task);
    
    // Update service status
    let mut services = state.services.lock().unwrap();
    services.insert("ai-voice".to_string(), ServiceStatus {
        name: "AI Voice".to_string(),
        status: "healthy".to_string(),
        endpoint: "http://localhost:50053".to_string(),
        last_check: chrono::Utc::now().to_rfc3339(),
    });
    
    Ok(processed)
}

// Computer Vision Commands
#[tauri::command]
async fn vision_process(
    image_data: Vec<u8>,
    task: String,
    filename: Option<String>,
    state: State<'_, AppState>,
) -> Result<String, String> {
    // Validate image file
    if let Err(e) = validate_image_file(&image_data, filename.as_deref()) {
        let user_error = match e {
            validation::ValidationError::FileTooLarge { size, max } => {
                UserError::file_too_large(max / (1024 * 1024))
            }
            validation::ValidationError::InvalidExtension { .. } => {
                UserError::invalid_file_type("image (jpg, jpeg, png, gif, webp, bmp)")
            }
            validation::ValidationError::InvalidFileType { .. } => {
                UserError::invalid_file_type("image file with valid signature")
            }
            _ => UserError::processing_error(&e.to_string()),
        };
        return Err(user_error.to_json());
    }

    let addr = std::env::var("VISION_GRPC_URL").unwrap_or_else(|_| "http://localhost:50054".to_string());
    
    let result = state.vision_circuit_breaker.call_async(|| {
        let addr = addr.clone();
        let image_data = image_data.clone();
        
        Box::pin(async move {
            let config = RetryConfig::default();
            
            retry_with_backoff(
                || {
                    let addr = addr.clone();
                    let image_data = image_data.clone();
                    
                    Box::pin(async move {
                        let channel = Channel::from_shared(addr.clone())
                            .map_err(|e| e.to_string())?
                            .connect_timeout(std::time::Duration::from_secs(5))
                            .timeout(std::time::Duration::from_secs(30))
                            .connect()
                            .await
                            .map_err(|_| "Failed to connect to Vision service".to_string())?;
                        
                        let mut client = grpc_client::vision::vision_service_client::VisionServiceClient::new(channel);
                        
                        let request = tonic::Request::new(grpc_client::vision::DetectRequest {
                            image_data: image_data.into(),
                            target_classes: vec![],
                            max_detections: 10,
                            confidence_threshold: 0.5,
                        });
                        
                        let response = client.detect_objects(request)
                            .await
                            .map_err(|e| format!("Vision detection failed: {}", e))?;
                        let result = response.into_inner();
                        
                        // Convert detections to JSON
                        let detections: Vec<serde_json::Value> = result.detections.iter().map(|d| {
                            serde_json::json!({
                                "class": d.class_name,
                                "confidence": d.confidence,
                                "bbox": {
                                    "x": d.bounding_box.as_ref().map(|b| b.x).unwrap_or(0.0),
                                    "y": d.bounding_box.as_ref().map(|b| b.y).unwrap_or(0.0),
                                    "width": d.bounding_box.as_ref().map(|b| b.width).unwrap_or(0.0),
                                    "height": d.bounding_box.as_ref().map(|b| b.height).unwrap_or(0.0),
                                }
                            })
                        }).collect();
                        
                        Ok(serde_json::json!({"objects": detections}).to_string())
                    })
                },
                &config,
            ).await
        })
    }).await;
    
    match result {
        Ok(json_result) => {
            // Update service status
            let mut services = state.services.lock().unwrap();
            services.insert("vision".to_string(), ServiceStatus {
                name: "Vision".to_string(),
                status: "healthy".to_string(),
                endpoint: addr,
                last_check: chrono::Utc::now().to_rfc3339(),
            });
            Ok(json_result)
        }
        Err(e) => Err(e.to_json()),
    }
}

// Multimodal Commands
#[tauri::command]
async fn multimodal_process(
    request: serde_json::Value,
    state: State<'_, AppState>,
) -> Result<String, String> {
    // Extract and validate data from request
    let audio_data = request.get("audio_data")
        .and_then(|v| v.as_str())
        .map(|s| s.as_bytes().to_vec())
        .unwrap_or_default();
    let image_data = request.get("image_data")
        .and_then(|v| v.as_str())
        .map(|s| s.as_bytes().to_vec())
        .unwrap_or_default();
    let video_data = request.get("video_data")
        .and_then(|v| v.as_str())
        .map(|s| s.as_bytes().to_vec())
        .unwrap_or_default();
    let text = request.get("text")
        .and_then(|v| v.as_str())
        .unwrap_or("")
        .to_string();
    
    // Validate inputs if provided
    if !audio_data.is_empty() {
        if let Err(e) = validate_audio_file(&audio_data, None) {
            return Err(UserError::processing_error(&format!("Invalid audio data: {}", e)).to_json());
        }
    }
    if !image_data.is_empty() {
        if let Err(e) = validate_image_file(&image_data, None) {
            return Err(UserError::processing_error(&format!("Invalid image data: {}", e)).to_json());
        }
    }
    if !video_data.is_empty() {
        if let Err(e) = validate_video_file(&video_data, None) {
            return Err(UserError::processing_error(&format!("Invalid video data: {}", e)).to_json());
        }
    }
    if !text.is_empty() {
        if let Err(e) = validate_text(&text) {
            return Err(UserError::processing_error(&format!("Invalid text: {}", e)).to_json());
        }
    }

    let addr = std::env::var("MULTIMODAL_GRPC_URL").unwrap_or_else(|_| "http://localhost:50055".to_string());
    
    // Use circuit breaker and retry logic
    let result = state.multimodal_circuit_breaker.call_async(|| {
        let addr = addr.clone();
        let audio_data = audio_data.clone();
        let image_data = image_data.clone();
        let video_data = video_data.clone();
        let text = text.clone();
        
        Box::pin(async move {
            let config = RetryConfig::default();
            
            retry_with_backoff(
                || {
                    let addr = addr.clone();
                    let audio_data = audio_data.clone();
                    let image_data = image_data.clone();
                    let video_data = video_data.clone();
                    let text = text.clone();
                    
                    Box::pin(async move {
                        let channel = Channel::from_shared(addr.clone())
                            .map_err(|e| e.to_string())?
                            .connect_timeout(std::time::Duration::from_secs(5))
                            .timeout(std::time::Duration::from_secs(60)) // Longer timeout for multimodal
                            .connect()
                            .await
                            .map_err(|_| "Failed to connect to Multimodal service".to_string())?;
                        
                        let mut client = grpc_client::multimodal::multimodal_service_client::MultimodalServiceClient::new(channel);
                        
                        let grpc_request = tonic::Request::new(grpc_client::multimodal::MultimodalRequest {
                            audio_data: audio_data.into(),
                            image_data: image_data.into(),
                            video_data: video_data.into(),
                            text,
                            analysis_types: vec!["combined".to_string()],
                        });
                        
                        let response = client.process_multimodal(grpc_request)
                            .await
                            .map_err(|e| format!("Multimodal processing failed: {}", e))?;
                        let result = response.into_inner();
                        
                        Ok(serde_json::json!({
                            "summary": result.combined_analysis,
                            "confidence": result.audio_analysis.as_ref().map(|a| a.confidence).unwrap_or(0.0),
                            "processing_time_ms": result.processing_time_ms
                        }).to_string())
                    })
                },
                &config,
            ).await
        })
    }).await;
    
    match result {
        Ok(json_result) => {
            // Update service status
            let mut services = state.services.lock().unwrap();
            services.insert("multimodal".to_string(), ServiceStatus {
                name: "Multimodal".to_string(),
                status: "healthy".to_string(),
                endpoint: addr,
                last_check: chrono::Utc::now().to_rfc3339(),
            });
            Ok(json_result)
        }
        Err(e) => Err(e.to_json()),
    }
}

// Application Commands
#[tauri::command]
async fn get_all_services_status(state: State<'_, AppState>) -> Result<Vec<ServiceStatus>, String> {
    let services = state.services.lock().unwrap();
    let mut status_list: Vec<ServiceStatus> = services.values().cloned().collect();
    
    // Ensure all services are represented
    let all_services = vec!["stt", "tts", "ai-voice", "vision", "multimodal"];
    for service_name in all_services {
        if !services.contains_key(service_name) {
            status_list.push(ServiceStatus {
                name: service_name.to_uppercase().to_string(),
                status: "unknown".to_string(),
                endpoint: format!("http://localhost:500{}", match service_name {
                    "stt" => "51",
                    "tts" => "52", 
                    "ai-voice" => "53",
                    "vision" => "54",
                    "multimodal" => "55",
                    _ => "50"
                }),
                last_check: chrono::Utc::now().to_rfc3339(),
            });
        }
    }
    
    Ok(status_list)
}

#[tauri::command]
async fn check_service_health(service: String, state: State<'_, AppState>) -> Result<bool, String> {
    // TODO: Implement actual health check
    println!("Health check for service: {}", service);
    
    // Mock health check - always return true for now
    let mut services = state.services.lock().unwrap();
    services.insert(service.clone(), ServiceStatus {
        name: service.to_uppercase().to_string(),
        status: "healthy".to_string(),
        endpoint: format!("http://localhost:500{}", match service.as_str() {
            "stt" => "51",
            "tts" => "52",
            "ai-voice" => "53", 
            "vision" => "54",
            "multimodal" => "55",
            _ => "50"
        }),
        last_check: chrono::Utc::now().to_rfc3339(),
    });
    
    Ok(true)
}

fn main() {
    tauri::Builder::default()
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            // STT commands
            stt_transcribe,
            stt_get_status,
            // TTS commands
            tts_synthesize,
            tts_get_status,
            // AI Voice commands
            ai_voice_process,
            // Vision commands
            vision_process,
            // Multimodal commands
            multimodal_process,
            // Application commands
            get_all_services_status,
            check_service_health
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
