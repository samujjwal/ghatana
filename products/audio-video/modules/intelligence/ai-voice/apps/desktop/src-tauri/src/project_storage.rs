/**
 * Project Storage Backend
 *
 * Handles project persistence:
 * - Save/load projects
 * - Export mixdowns
 * - Cloud sync (optional)
 * - Version control
 *
 * @doc.type module
 * @doc.purpose Project persistence
 * @doc.layer backend
 */

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use chrono::{DateTime, Utc};
use uuid::Uuid;
use anyhow::{Context, Result};
use serde_json;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AudioTrack {
    pub id: String,
    pub name: String,
    pub audio_path: String,
    pub volume: f32,       // 0.0-1.0
    pub pan: f32,          // -1.0 to 1.0
    pub muted: bool,
    pub solo: bool,
    pub color: String,
    pub effects: Vec<Effect>,
    pub start_time: f64,   // seconds
    pub duration: f64,     // seconds
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Effect {
    pub id: String,
    pub effect_type: String,
    pub enabled: bool,
    pub params: serde_json::Value,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProjectSettings {
    pub sample_rate: u32,
    pub bit_depth: u32,
    pub tempo: f32,
    pub time_signature: (u32, u32),
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProjectMetadata {
    pub author: String,
    pub description: String,
    pub tags: Vec<String>,
    pub version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Project {
    pub id: String,
    pub name: String,
    pub created: DateTime<Utc>,
    pub modified: DateTime<Utc>,
    pub tracks: Vec<AudioTrack>,
    pub settings: ProjectSettings,
    pub metadata: ProjectMetadata,
}

impl Default for ProjectSettings {
    fn default() -> Self {
        Self {
            sample_rate: 44100,
            bit_depth: 24,
            tempo: 120.0,
            time_signature: (4, 4),
        }
    }
}

impl Default for ProjectMetadata {
    fn default() -> Self {
        Self {
            author: String::from("Unknown"),
            description: String::new(),
            tags: Vec::new(),
            version: String::from("1.0.0"),
        }
    }
}

impl Project {
    pub fn new(name: String) -> Self {
        let now = Utc::now();
        Self {
            id: Uuid::new_v4().to_string(),
            name,
            created: now,
            modified: now,
            tracks: Vec::new(),
            settings: ProjectSettings::default(),
            metadata: ProjectMetadata::default(),
        }
    }

    pub fn add_track(&mut self, track: AudioTrack) {
        self.tracks.push(track);
        self.modified = Utc::now();
    }

    pub fn remove_track(&mut self, track_id: &str) {
        self.tracks.retain(|t| t.id != track_id);
        self.modified = Utc::now();
    }

    pub fn update_track(&mut self, track: AudioTrack) {
        if let Some(existing) = self.tracks.iter_mut().find(|t| t.id == track.id) {
            *existing = track;
            self.modified = Utc::now();
        }
    }
}

pub struct ProjectStorage {
    storage_dir: PathBuf,
}

impl ProjectStorage {
    pub fn new(storage_dir: PathBuf) -> Result<Self> {
        fs::create_dir_all(&storage_dir)
            .context("Failed to create storage directory")?;

        Ok(Self { storage_dir })
    }

    pub fn save_project(&self, project: &Project) -> Result<PathBuf> {
        let project_dir = self.storage_dir.join(&project.id);
        fs::create_dir_all(&project_dir)
            .context("Failed to create project directory")?;

        // Save project metadata
        let project_file = project_dir.join("project.json");
        let json = serde_json::to_string_pretty(project)
            .context("Failed to serialize project")?;

        fs::write(&project_file, json)
            .context("Failed to write project file")?;

        // Copy audio files to project directory
        let audio_dir = project_dir.join("audio");
        fs::create_dir_all(&audio_dir)?;

        for track in &project.tracks {
            if !track.audio_path.is_empty() {
                let source = Path::new(&track.audio_path);
                if source.exists() {
                    let filename = source.file_name().unwrap_or_default();
                    let dest = audio_dir.join(filename);

                    if !dest.exists() {
                        fs::copy(source, &dest)
                            .context(format!("Failed to copy audio file: {:?}", source))?;
                    }
                }
            }
        }

        Ok(project_file)
    }

    pub fn load_project(&self, project_id: &str) -> Result<Project> {
        let project_file = self.storage_dir
            .join(project_id)
            .join("project.json");

        let json = fs::read_to_string(&project_file)
            .context("Failed to read project file")?;

        let mut project: Project = serde_json::from_str(&json)
            .context("Failed to parse project file")?;

        // Update audio paths to absolute paths
        let audio_dir = self.storage_dir
            .join(project_id)
            .join("audio");

        for track in &mut project.tracks {
            if !track.audio_path.is_empty() {
                let filename = Path::new(&track.audio_path)
                    .file_name()
                    .unwrap_or_default();
                let absolute_path = audio_dir.join(filename);
                track.audio_path = absolute_path.to_string_lossy().to_string();
            }
        }

        Ok(project)
    }

    pub fn list_projects(&self) -> Result<Vec<ProjectInfo>> {
        let mut projects = Vec::new();

        for entry in fs::read_dir(&self.storage_dir)? {
            let entry = entry?;
            let path = entry.path();

            if path.is_dir() {
                let project_file = path.join("project.json");
                if project_file.exists() {
                    let json = fs::read_to_string(&project_file)?;
                    let project: Project = serde_json::from_str(&json)?;

                    projects.push(ProjectInfo {
                        id: project.id,
                        name: project.name,
                        created: project.created,
                        modified: project.modified,
                        track_count: project.tracks.len(),
                    });
                }
            }
        }

        // Sort by modified date (newest first)
        projects.sort_by(|a, b| b.modified.cmp(&a.modified));

        Ok(projects)
    }

    pub fn delete_project(&self, project_id: &str) -> Result<()> {
        let project_dir = self.storage_dir.join(project_id);

        if project_dir.exists() {
            fs::remove_dir_all(&project_dir)
                .context("Failed to delete project directory")?;
        }

        Ok(())
    }

    pub fn export_project(
        &self,
        project_id: &str,
        export_path: &Path,
    ) -> Result<PathBuf> {
        let project = self.load_project(project_id)?;

        // Create temporary directory for rendering
        let temp_dir = std::env::temp_dir().join(format!("export_{}", project_id));
        fs::create_dir_all(&temp_dir)?;

        // TODO: Implement actual mixing/rendering
        // For now, just copy the first track
        if !project.tracks.is_empty() {
            let first_track = &project.tracks[0];
            if !first_track.audio_path.is_empty() {
                let output_path = export_path.join(format!("{}.wav", project.name));
                fs::copy(&first_track.audio_path, &output_path)?;
                return Ok(output_path);
            }
        }

        anyhow::bail!("No tracks to export")
    }

    pub fn get_storage_size(&self) -> Result<u64> {
        fn dir_size(path: &Path) -> Result<u64> {
            let mut size = 0;
            for entry in fs::read_dir(path)? {
                let entry = entry?;
                let metadata = entry.metadata()?;
                if metadata.is_dir() {
                    size += dir_size(&entry.path())?;
                } else {
                    size += metadata.len();
                }
            }
            Ok(size)
        }

        let total = dir_size(&self.storage_dir)?;

        Ok(total)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProjectInfo {
    pub id: String,
    pub name: String,
    pub created: DateTime<Utc>,
    pub modified: DateTime<Utc>,
    pub track_count: usize,
}

// Tauri command handlers
#[tauri::command]
pub async fn save_project(
    project: Project,
    storage_dir: String,
) -> Result<String, String> {
    let storage = ProjectStorage::new(PathBuf::from(storage_dir))
        .map_err(|e| e.to_string())?;

    let project_file = storage.save_project(&project)
        .map_err(|e| e.to_string())?;

    Ok(project_file.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn load_project(
    project_id: String,
    storage_dir: String,
) -> Result<Project, String> {
    let storage = ProjectStorage::new(PathBuf::from(storage_dir))
        .map_err(|e| e.to_string())?;

    storage.load_project(&project_id)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn list_projects(
    storage_dir: String,
) -> Result<Vec<ProjectInfo>, String> {
    let storage = ProjectStorage::new(PathBuf::from(storage_dir))
        .map_err(|e| e.to_string())?;

    storage.list_projects()
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn delete_project(
    project_id: String,
    storage_dir: String,
) -> Result<(), String> {
    let storage = ProjectStorage::new(PathBuf::from(storage_dir))
        .map_err(|e| e.to_string())?;

    storage.delete_project(&project_id)
        .map_err(|e| e.to_string())
}

#[tauri::command]
pub async fn export_project(
    project_id: String,
    export_path: String,
    storage_dir: String,
) -> Result<String, String> {
    let storage = ProjectStorage::new(PathBuf::from(storage_dir))
        .map_err(|e| e.to_string())?;

    let output_path = storage.export_project(&project_id, Path::new(&export_path))
        .map_err(|e| e.to_string())?;

    Ok(output_path.to_string_lossy().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_create_project() {
        let project = Project::new("Test Project".to_string());
        assert_eq!(project.name, "Test Project");
        assert_eq!(project.tracks.len(), 0);
    }

    #[test]
    fn test_save_load_project() {
        let temp_dir = TempDir::new().unwrap();
        let storage = ProjectStorage::new(temp_dir.path().to_path_buf()).unwrap();

        let mut project = Project::new("Test".to_string());
        project.add_track(AudioTrack {
            id: "track1".to_string(),
            name: "Track 1".to_string(),
            audio_path: String::new(),
            volume: 1.0,
            pan: 0.0,
            muted: false,
            solo: false,
            color: "#ff0000".to_string(),
            effects: Vec::new(),
            start_time: 0.0,
            duration: 10.0,
        });

        storage.save_project(&project).unwrap();

        let loaded = storage.load_project(&project.id).unwrap();
        assert_eq!(loaded.name, project.name);
        assert_eq!(loaded.tracks.len(), 1);
    }

    #[test]
    fn test_list_projects() {
        let temp_dir = TempDir::new().unwrap();
        let storage = ProjectStorage::new(temp_dir.path().to_path_buf()).unwrap();

        let project1 = Project::new("Project 1".to_string());
        let project2 = Project::new("Project 2".to_string());

        storage.save_project(&project1).unwrap();
        storage.save_project(&project2).unwrap();

        let projects = storage.list_projects().unwrap();
        assert_eq!(projects.len(), 2);
    }

    #[test]
    fn test_delete_project() {
        let temp_dir = TempDir::new().unwrap();
        let storage = ProjectStorage::new(temp_dir.path().to_path_buf()).unwrap();

        let project = Project::new("Test".to_string());
        storage.save_project(&project).unwrap();

        storage.delete_project(&project.id).unwrap();

        let projects = storage.list_projects().unwrap();
        assert_eq!(projects.len(), 0);
    }
}

