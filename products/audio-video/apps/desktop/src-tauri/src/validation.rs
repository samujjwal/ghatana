/// Security validation utilities for file uploads and data processing

use std::path::Path;

/// Maximum file sizes (in bytes)
pub const MAX_AUDIO_SIZE: usize = 100 * 1024 * 1024; // 100MB
pub const MAX_IMAGE_SIZE: usize = 50 * 1024 * 1024;  // 50MB
pub const MAX_VIDEO_SIZE: usize = 500 * 1024 * 1024; // 500MB
pub const MAX_TEXT_LENGTH: usize = 1_000_000; // 1M characters

/// Allowed file extensions
pub const ALLOWED_AUDIO_EXTENSIONS: &[&str] = &["wav", "mp3", "ogg", "flac", "m4a"];
pub const ALLOWED_IMAGE_EXTENSIONS: &[&str] = &["jpg", "jpeg", "png", "gif", "webp", "bmp"];
pub const ALLOWED_VIDEO_EXTENSIONS: &[&str] = &["mp4", "avi", "mov", "mkv", "webm"];

/// Validation error types
#[derive(Debug)]
pub enum ValidationError {
    FileTooLarge { size: usize, max: usize },
    InvalidExtension { extension: String, allowed: Vec<String> },
    InvalidFileType { detected: String, expected: String },
    TextTooLong { length: usize, max: usize },
    InvalidContent(String),
}

impl std::fmt::Display for ValidationError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ValidationError::FileTooLarge { size, max } => {
                write!(f, "File size {} bytes exceeds maximum {} bytes", size, max)
            }
            ValidationError::InvalidExtension { extension, allowed } => {
                write!(f, "Extension '{}' not allowed. Allowed: {:?}", extension, allowed)
            }
            ValidationError::InvalidFileType { detected, expected } => {
                write!(f, "File type '{}' does not match expected '{}'", detected, expected)
            }
            ValidationError::TextTooLong { length, max } => {
                write!(f, "Text length {} exceeds maximum {}", length, max)
            }
            ValidationError::InvalidContent(msg) => write!(f, "Invalid content: {}", msg),
        }
    }
}

/// Validate audio file
pub fn validate_audio_file(data: &[u8], filename: Option<&str>) -> Result<(), ValidationError> {
    // Check size
    if data.len() > MAX_AUDIO_SIZE {
        return Err(ValidationError::FileTooLarge {
            size: data.len(),
            max: MAX_AUDIO_SIZE,
        });
    }
    
    // Check extension if filename provided
    if let Some(name) = filename {
        let extension = Path::new(name)
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("");
        
        if !ALLOWED_AUDIO_EXTENSIONS.contains(&extension.to_lowercase().as_str()) {
            return Err(ValidationError::InvalidExtension {
                extension: extension.to_string(),
                allowed: ALLOWED_AUDIO_EXTENSIONS.iter().map(|s| s.to_string()).collect(),
            });
        }
    }
    
    // Validate file signature (magic bytes)
    validate_audio_signature(data)?;
    
    Ok(())
}

/// Validate image file
pub fn validate_image_file(data: &[u8], filename: Option<&str>) -> Result<(), ValidationError> {
    // Check size
    if data.len() > MAX_IMAGE_SIZE {
        return Err(ValidationError::FileTooLarge {
            size: data.len(),
            max: MAX_IMAGE_SIZE,
        });
    }
    
    // Check extension if filename provided
    if let Some(name) = filename {
        let extension = Path::new(name)
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("");
        
        if !ALLOWED_IMAGE_EXTENSIONS.contains(&extension.to_lowercase().as_str()) {
            return Err(ValidationError::InvalidExtension {
                extension: extension.to_string(),
                allowed: ALLOWED_IMAGE_EXTENSIONS.iter().map(|s| s.to_string()).collect(),
            });
        }
    }
    
    // Validate file signature (magic bytes)
    validate_image_signature(data)?;
    
    Ok(())
}

/// Validate video file
pub fn validate_video_file(data: &[u8], filename: Option<&str>) -> Result<(), ValidationError> {
    // Check size
    if data.len() > MAX_VIDEO_SIZE {
        return Err(ValidationError::FileTooLarge {
            size: data.len(),
            max: MAX_VIDEO_SIZE,
        });
    }
    
    // Check extension if filename provided
    if let Some(name) = filename {
        let extension = Path::new(name)
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("");
        
        if !ALLOWED_VIDEO_EXTENSIONS.contains(&extension.to_lowercase().as_str()) {
            return Err(ValidationError::InvalidExtension {
                extension: extension.to_string(),
                allowed: ALLOWED_VIDEO_EXTENSIONS.iter().map(|s| s.to_string()).collect(),
            });
        }
    }
    
    // Validate file signature (magic bytes)
    validate_video_signature(data)?;
    
    Ok(())
}

/// Validate text input
pub fn validate_text(text: &str) -> Result<(), ValidationError> {
    if text.len() > MAX_TEXT_LENGTH {
        return Err(ValidationError::TextTooLong {
            length: text.len(),
            max: MAX_TEXT_LENGTH,
        });
    }
    
    // Check for null bytes or other invalid characters
    if text.contains('\0') {
        return Err(ValidationError::InvalidContent("Text contains null bytes".to_string()));
    }
    
    Ok(())
}

/// Validate audio file signature (magic bytes)
fn validate_audio_signature(data: &[u8]) -> Result<(), ValidationError> {
    if data.len() < 4 {
        return Err(ValidationError::InvalidContent("File too small".to_string()));
    }
    
    // Check for common audio file signatures
    let is_valid = 
        // WAV: "RIFF"
        (data.starts_with(b"RIFF") && data.len() > 8 && &data[8..12] == b"WAVE") ||
        // MP3: ID3 or FF FB/FF F3/FF F2
        data.starts_with(b"ID3") ||
        (data[0] == 0xFF && (data[1] & 0xE0) == 0xE0) ||
        // OGG: "OggS"
        data.starts_with(b"OggS") ||
        // FLAC: "fLaC"
        data.starts_with(b"fLaC") ||
        // M4A: "ftyp"
        (data.len() > 8 && &data[4..8] == b"ftyp");
    
    if !is_valid {
        return Err(ValidationError::InvalidFileType {
            detected: "unknown".to_string(),
            expected: "audio".to_string(),
        });
    }
    
    Ok(())
}

/// Validate image file signature (magic bytes)
fn validate_image_signature(data: &[u8]) -> Result<(), ValidationError> {
    if data.len() < 4 {
        return Err(ValidationError::InvalidContent("File too small".to_string()));
    }
    
    // Check for common image file signatures
    let is_valid = 
        // JPEG: FF D8 FF
        (data[0] == 0xFF && data[1] == 0xD8 && data[2] == 0xFF) ||
        // PNG: 89 50 4E 47
        data.starts_with(&[0x89, 0x50, 0x4E, 0x47]) ||
        // GIF: "GIF87a" or "GIF89a"
        data.starts_with(b"GIF87a") || data.starts_with(b"GIF89a") ||
        // WebP: "RIFF" + "WEBP"
        (data.starts_with(b"RIFF") && data.len() > 12 && &data[8..12] == b"WEBP") ||
        // BMP: "BM"
        data.starts_with(b"BM");
    
    if !is_valid {
        return Err(ValidationError::InvalidFileType {
            detected: "unknown".to_string(),
            expected: "image".to_string(),
        });
    }
    
    Ok(())
}

/// Validate video file signature (magic bytes)
fn validate_video_signature(data: &[u8]) -> Result<(), ValidationError> {
    if data.len() < 12 {
        return Err(ValidationError::InvalidContent("File too small".to_string()));
    }
    
    // Check for common video file signatures
    let is_valid = 
        // MP4: "ftyp"
        &data[4..8] == b"ftyp" ||
        // AVI: "RIFF" + "AVI "
        (data.starts_with(b"RIFF") && &data[8..12] == b"AVI ") ||
        // MOV: "ftyp" or "moov"
        &data[4..8] == b"moov" ||
        // MKV/WebM: EBML signature
        data.starts_with(&[0x1A, 0x45, 0xDF, 0xA3]);
    
    if !is_valid {
        return Err(ValidationError::InvalidFileType {
            detected: "unknown".to_string(),
            expected: "video".to_string(),
        });
    }
    
    Ok(())
}

/// Sanitize filename to prevent path traversal
pub fn sanitize_filename(filename: &str) -> String {
    filename
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == '.' || *c == '-' || *c == '_')
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_validate_text() {
        assert!(validate_text("Hello, world!").is_ok());
        assert!(validate_text(&"a".repeat(MAX_TEXT_LENGTH + 1)).is_err());
        assert!(validate_text("Text with\0null byte").is_err());
    }
    
    #[test]
    fn test_sanitize_filename() {
        assert_eq!(sanitize_filename("../../../etc/passwd"), "etcpasswd");
        assert_eq!(sanitize_filename("file name.txt"), "filename.txt");
        assert_eq!(sanitize_filename("valid-file_123.mp3"), "valid-file_123.mp3");
    }
}
