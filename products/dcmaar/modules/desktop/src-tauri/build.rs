use std::fs;
use std::path::PathBuf;

fn main() {
    // Trigger recompilation when a new migration is added
    println!("cargo:rerun-if-changed=migrations");
    // Build Tauri application
    tauri_build::build();

    // Generate gRPC code from proto files
    let manifest_dir = PathBuf::from(std::env::var("CARGO_MANIFEST_DIR").unwrap());
    let out_dir = PathBuf::from(std::env::var("OUT_DIR").unwrap());
    let workspace_root = manifest_dir.ancestors().nth(3).unwrap().to_path_buf();
    let proto_root = workspace_root.join("contracts/proto-core");
    let third_party_proto = workspace_root.join("core/third_party/protobuf");

    // Create the output directory if it doesn't exist
    fs::create_dir_all(&out_dir).expect("failed to create proto output directory");
    
    // List of proto files to compile
    let proto_files = vec![
        "dcmaar/v1/common.proto",
        "dcmaar/v1/metrics.proto",
        "dcmaar/v1/events.proto",
        "dcmaar/v1/actions.proto",
        "dcmaar/v1/desktop.proto",
    ];

    // Convert to full paths
    let proto_files: Vec<PathBuf> = proto_files
        .iter()
        .map(|p| proto_root.join(p))
        .collect();

    // Include paths for protoc
    // Include: dcmaar proto files, third-party proto files, validate proto files, and system protobuf well-known types
    let proto_include = vec![
        proto_root.clone(),
        third_party_proto.clone(),
        third_party_proto.join("validate"),
        PathBuf::from("/usr/include"),  // System protobuf well-known types
    ];

    // Print debug information
    println!("Proto files to compile:");
    for file in &proto_files {
        println!("  {}", file.display());
    }

    println!("\nProto include paths:");
    for path in &proto_include {
        println!("  {}", path.display());
    }

    println!("\nOutput directory: {}", out_dir.display());

    // Configure tonic-build
    // We disable compile_well_known_types and rely on extern_path mappings instead
    // This avoids prost trying to generate code for well-known types with nested module paths
    let config = tonic_build::configure()
        .build_server(false)  // Desktop is client-only
        .out_dir(&out_dir)
        .extern_path(".google.protobuf.Timestamp", "::prost_types::Timestamp")
        .extern_path(".google.protobuf.Duration", "::prost_types::Duration")
        // For types not in prost_types, we need to generate them but with correct paths
        // Since Struct, Any, Empty are used, we'll generate them inline
        .protoc_arg(format!("-I{}", proto_root.display()))
        .protoc_arg(format!("-I{}", third_party_proto.display()))
        .protoc_arg("--experimental_allow_proto3_optional");  // Enable proto3 optional fields

    // Compile the proto files
    match config.compile(&proto_files, &proto_include) {
        Ok(_) => {
            println!("Successfully compiled protobuf files to {}", out_dir.display());

            // Optionally sync the generated file into the source tree so IDEs can index it
            let generated_file = out_dir.join("dcmaar.v1.rs");
            let dest = manifest_dir.join("src/proto/dcmaar.v1.rs");

            if let Ok(new_content) = fs::read(&generated_file) {
                let needs_copy = match fs::read(&dest) {
                    Ok(existing) => existing != new_content,
                    Err(_) => true,
                };

                if needs_copy {
                    if let Some(parent) = dest.parent() {
                        fs::create_dir_all(parent).expect("failed to create proto sync directory");
                    }
                    fs::write(&dest, new_content).expect("failed to sync generated proto file");
                    println!("Synced generated proto to {}", dest.display());
                } else {
                    println!("Checked-in proto copy is up to date");
                }
            } else {
                println!("warning: generated file {} not found", generated_file.display());
            }
        }
        Err(e) => {
            eprintln!("Failed to compile protos: {}", e);
            std::process::exit(1);
        }
    }
    
    // Rerun if proto files change
    for proto_file in &proto_files {
        println!("cargo:rerun-if-changed={}", proto_file.display());
    }
    
    // Also watch the third_party proto files
    println!("cargo:rerun-if-changed={}", third_party_proto.display());
}
