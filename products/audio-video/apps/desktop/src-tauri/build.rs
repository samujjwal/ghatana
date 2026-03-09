fn main() {
    // Compile proto files
    tonic_build::configure()
        .build_server(false)
        .compile(
            &["proto/stt.proto", "proto/tts.proto", "proto/vision.proto", "proto/multimodal.proto"],
            &["proto"],
        )
        .expect("Failed to compile protos");
    
    tauri_build::build()
}
