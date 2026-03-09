// Capability 1: Crash Causal Hints (Agent)
// Correlates crash events with preceding signals (library loads, kernel events)
// to provide likely culprit hints for faster debugging.

use std::collections::{HashMap, VecDeque};
use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use serde::{Deserialize, Serialize};

/// Buffer duration for crash correlation analysis
const CRASH_BUFFER_DURATION: Duration = Duration::from_secs(30);

/// Maximum number of events to buffer per event type
const MAX_BUFFER_SIZE: usize = 1000;

/// Minimum confidence threshold for emitting crash hints
const MIN_CONFIDENCE_THRESHOLD: f64 = 0.6;

/// Signal event that might be correlated with crashes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SignalEvent {
    /// Event timestamp in milliseconds since epoch
    pub timestamp: u64,
    /// Type of the signal event
    pub event_type: SignalType,
    /// Component associated with the signal
    pub component: String,
    /// Additional event-specific details
    pub details: HashMap<String, String>,
}

/// Types of signals that can correlate with crashes
#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum SignalType {
    /// A dynamic library was loaded (path)
    LibraryLoad(String),    // Library path
    /// A kernel-level event occurred (named)
    KernelEvent(String),    // Kernel event type
    /// Memory pressure reported (bytes)
    MemoryPressure(u64),    // Memory usage in bytes
    /// A system call was observed (name)
    SystemCall(String),     // Syscall name
    /// A child process was spawned (process name)
    ProcessSpawn(String),   // Process name
}

/// Crash hint with confidence and supporting evidence
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CrashHint {
    /// Suggested culprit component or resource
    pub culprit: String,
    /// Confidence score (0.0..1.0)
    pub confidence: f64,
    /// Supporting evidence lines used to compute the hint
    pub evidence: Vec<String>,
    /// Human-readable signal type that produced the hint
    pub signal_type: String,
    /// Time difference between signal and crash (ms)
    pub time_delta_ms: u64,
}

/// Event emitted when a crash is detected with correlation hints
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CrashHintEvent {
    /// Crash time in ms since epoch
    pub crash_timestamp: u64,
    /// Crash signal name (e.g., SIGSEGV)
    pub crash_signal: String,      // SIGSEGV, SIGBUS, etc.
    /// Process id that crashed
    pub process_id: u32,
    /// Process executable/name
    pub process_name: String,
    /// Sorted hints produced by correlation analysis
    pub hints: Vec<CrashHint>,
    /// Window size used for correlation (ms)
    pub buffer_window_ms: u64,
}

/// Circular buffer for storing signal events
#[derive(Debug)]
struct SignalBuffer {
    events: VecDeque<SignalEvent>,
    max_size: usize,
}

impl SignalBuffer {
    fn new(max_size: usize) -> Self {
        Self {
            events: VecDeque::with_capacity(max_size),
            max_size,
        }
    }

    fn push(&mut self, event: SignalEvent) {
        if self.events.len() >= self.max_size {
            self.events.pop_front();
        }
        self.events.push_back(event);
    }

    fn get_recent_events(&self, since: u64) -> Vec<&SignalEvent> {
        self.events
            .iter()
            .filter(|event| event.timestamp >= since)
            .collect()
    }
}

/// Crash analyzer that correlates crashes with preceding signals
#[derive(Debug)]
pub struct CrashAnalyzer {
    signal_buffers: Arc<Mutex<HashMap<String, SignalBuffer>>>,
}

impl CrashAnalyzer {
    /// Create a new crash analyzer
    pub fn new() -> Self {
        Self {
            signal_buffers: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Record a signal event for future correlation
    pub fn record_signal(&self, event: SignalEvent) {
        let mut buffers = self.signal_buffers.lock().unwrap();
        let buffer_key = format!("{:?}", event.event_type);
        
        let buffer = buffers
            .entry(buffer_key)
            .or_insert_with(|| SignalBuffer::new(MAX_BUFFER_SIZE));
        
        buffer.push(event);
    }

    /// Analyze a crash and generate hints based on recent signals
    pub fn analyze_crash(
        &self,
        crash_timestamp: u64,
        crash_signal: String,
        process_id: u32,
        process_name: String,
    ) -> CrashHintEvent {
        let window_start = crash_timestamp.saturating_sub(CRASH_BUFFER_DURATION.as_millis() as u64);
        let buffers = self.signal_buffers.lock().unwrap();
        
        let mut hints = Vec::new();
        
        // Analyze each signal type for correlation
    for (_signal_type, buffer) in buffers.iter() {
            let recent_events = buffer.get_recent_events(window_start);
            
            for event in recent_events {
                let hint = self.analyze_signal_correlation(
                    event,
                    crash_timestamp,
                    &crash_signal,
                    process_id,
                    &process_name,
                );
                
                if hint.confidence >= MIN_CONFIDENCE_THRESHOLD {
                    hints.push(hint);
                }
            }
        }
        
        // Sort hints by confidence (highest first)
        hints.sort_by(|a, b| b.confidence.partial_cmp(&a.confidence).unwrap());
        
        CrashHintEvent {
            crash_timestamp,
            crash_signal,
            process_id,
            process_name,
            hints,
            buffer_window_ms: CRASH_BUFFER_DURATION.as_millis() as u64,
        }
    }

    /// Analyze correlation between a signal event and a crash
    fn analyze_signal_correlation(
        &self,
        signal: &SignalEvent,
        crash_timestamp: u64,
        crash_signal: &str,
        _process_id: u32,
        _process_name: &str,
    ) -> CrashHint {
        let time_delta = crash_timestamp - signal.timestamp;
    let mut confidence: f64;
        let mut evidence = Vec::new();
        
        match &signal.event_type {
            SignalType::LibraryLoad(lib_path) => {
                confidence = self.score_library_correlation(lib_path, crash_signal, time_delta);
                evidence.push(format!("Library '{}' loaded {}ms before crash", lib_path, time_delta));
                
                // Higher confidence for known problematic libraries
                if lib_path.contains("msvcr") || lib_path.contains("kernel32") {
                    confidence += 0.2;
                    evidence.push("Known problematic library".to_string());
                }
            }
            
            SignalType::KernelEvent(event_type) => {
                confidence = self.score_kernel_correlation(event_type, crash_signal, time_delta);
                evidence.push(format!("Kernel event '{}' occurred {}ms before crash", event_type, time_delta));
            }
            
            SignalType::MemoryPressure(usage) => {
                confidence = self.score_memory_correlation(*usage, crash_signal, time_delta);
                evidence.push(format!("Memory pressure: {} bytes {}ms before crash", usage, time_delta));
            }
            
            SignalType::SystemCall(syscall) => {
                confidence = self.score_syscall_correlation(syscall, crash_signal, time_delta);
                evidence.push(format!("System call '{}' executed {}ms before crash", syscall, time_delta));
            }
            
            SignalType::ProcessSpawn(proc_name) => {
                confidence = self.score_process_correlation(proc_name, crash_signal, time_delta);
                evidence.push(format!("Process '{}' spawned {}ms before crash", proc_name, time_delta));
            }
        }
        
        // Apply time decay - more recent events are more likely to be causal
        let time_decay = 1.0 - (time_delta as f64 / CRASH_BUFFER_DURATION.as_millis() as f64);
        confidence *= time_decay.max(0.1); // Minimum 10% of original confidence
        
        CrashHint {
            culprit: signal.component.clone(),
            confidence: confidence.min(1.0),
            evidence,
            signal_type: format!("{:?}", signal.event_type),
            time_delta_ms: time_delta,
        }
    }

    /// Score correlation between library load and crash
    fn score_library_correlation(&self, lib_path: &str, crash_signal: &str, time_delta: u64) -> f64 {
        let mut score = 0.4; // Base score for library correlation
        
        // Higher score for recent library loads
        if time_delta < 5000 { // Within 5 seconds
            score += 0.3;
        }
        
        // Higher score for specific crash signals
        match crash_signal {
            "SIGSEGV" => score += 0.2, // Segmentation fault often from bad libraries
            "SIGBUS" => score += 0.15,  // Bus error can be library-related
            _ => {}
        }
        
        // Library-specific scoring
        if lib_path.ends_with(".dll") || lib_path.ends_with(".so") {
            score += 0.1;
        }
        
        score
    }

    /// Score correlation between kernel event and crash
    fn score_kernel_correlation(&self, event_type: &str, crash_signal: &str, time_delta: u64) -> f64 {
        let mut score = 0.3; // Base score for kernel correlation
        
        // Specific kernel event patterns
        match event_type {
            "page_fault" if crash_signal == "SIGSEGV" => score += 0.4,
            "memory_protection" => score += 0.3,
            "io_error" => score += 0.2,
            _ => {}
        }
        
        // Recent events are more significant
        if time_delta < 1000 { // Within 1 second
            score += 0.2;
        }
        
        score
    }

    /// Score correlation between memory pressure and crash
    fn score_memory_correlation(&self, usage: u64, crash_signal: &str, time_delta: u64) -> f64 {
        let mut score = 0.2; // Base score for memory correlation
        
        // High memory usage increases correlation
        let gb_usage = usage as f64 / (1024.0 * 1024.0 * 1024.0);
        if gb_usage > 8.0 {
            score += 0.3;
        } else if gb_usage > 4.0 {
            score += 0.2;
        }
        
        // Memory-related crashes
        match crash_signal {
            "SIGSEGV" => score += 0.2,
            "SIGBUS" => score += 0.15,
            _ => {}
        }
        
        // Recent pressure more significant
        if time_delta < 2000 { // Within 2 seconds
            score += 0.1;
        }
        
        score
    }

    /// Score correlation between system call and crash
    fn score_syscall_correlation(&self, syscall: &str, crash_signal: &str, time_delta: u64) -> f64 {
        let mut score = 0.3; // Base score for syscall correlation
        
        // High-risk system calls
        match syscall {
            "mmap" | "munmap" => score += 0.3,
            "malloc" | "free" => score += 0.25,
            "read" | "write" => score += 0.2,
            _ => {}
        }
        
        // Signal-specific correlation
        match crash_signal {
            "SIGSEGV" if syscall.contains("mem") => score += 0.2,
            "SIGBUS" if syscall.contains("io") => score += 0.15,
            _ => {}
        }
        
        // Very recent syscalls are highly significant
        if time_delta < 500 { // Within 500ms
            score += 0.2;
        }
        
        score
    }

    /// Score correlation between process spawn and crash
    fn score_process_correlation(&self, proc_name: &str, _crash_signal: &str, time_delta: u64) -> f64 {
        let mut score = 0.25; // Base score for process correlation
        
        // Process types that commonly cause issues
        if proc_name.contains("driver") || proc_name.contains("service") {
            score += 0.2;
        }
        
        // Recent spawns more likely to be causal
        if time_delta < 10000 { // Within 10 seconds
            score += 0.15;
        }
        
        score
    }
}

impl Default for CrashAnalyzer {
    fn default() -> Self {
        Self::new()
    }
}

/// Helper function to get current timestamp in milliseconds
pub fn current_timestamp() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_crash_analyzer_basic() {
        let analyzer = CrashAnalyzer::new();
        let timestamp = current_timestamp();
        
        // Record a library load event
        let signal = SignalEvent {
            timestamp: timestamp - 1000, // 1 second before crash
            event_type: SignalType::LibraryLoad("msvcr120.dll".to_string()),
            component: "kernel32".to_string(),
            details: HashMap::new(),
        };
        
        analyzer.record_signal(signal);
        
        // Analyze crash
        let crash_event = analyzer.analyze_crash(
            timestamp,
            "SIGSEGV".to_string(),
            1234,
            "test.exe".to_string(),
        );
        
        assert!(!crash_event.hints.is_empty());
        assert!(crash_event.hints[0].confidence > MIN_CONFIDENCE_THRESHOLD);
    }

    #[test]
    fn test_signal_buffer_capacity() {
        let mut buffer = SignalBuffer::new(2);
        
        let signal1 = SignalEvent {
            timestamp: 1000,
            event_type: SignalType::LibraryLoad("lib1.dll".to_string()),
            component: "test".to_string(),
            details: HashMap::new(),
        };
        
        let signal2 = SignalEvent {
            timestamp: 2000,
            event_type: SignalType::LibraryLoad("lib2.dll".to_string()),
            component: "test".to_string(),
            details: HashMap::new(),
        };
        
        let signal3 = SignalEvent {
            timestamp: 3000,
            event_type: SignalType::LibraryLoad("lib3.dll".to_string()),
            component: "test".to_string(),
            details: HashMap::new(),
        };
        
        buffer.push(signal1);
        buffer.push(signal2);
        buffer.push(signal3); // Should evict signal1
        
        assert_eq!(buffer.events.len(), 2);
        assert_eq!(buffer.events.front().unwrap().timestamp, 2000);
    }

    #[test]
    fn test_time_decay_scoring() {
        let analyzer = CrashAnalyzer::new();
        
        // Recent library load should score higher
        let recent_hint = analyzer.analyze_signal_correlation(
            &SignalEvent {
                timestamp: 10000,
                event_type: SignalType::LibraryLoad("test.dll".to_string()),
                component: "test".to_string(),
                details: HashMap::new(),
            },
            10500, // 500ms later
            "SIGSEGV",
            1234,
            "test.exe",
        );
        
        // Old library load should score lower
        let old_hint = analyzer.analyze_signal_correlation(
            &SignalEvent {
                timestamp: 10000,
                event_type: SignalType::LibraryLoad("test.dll".to_string()),
                component: "test".to_string(),
                details: HashMap::new(),
            },
            40000, // 30 seconds later
            "SIGSEGV",
            1234,
            "test.exe",
        );
        
        assert!(recent_hint.confidence > old_hint.confidence);
    }
}