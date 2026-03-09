use std::env;
use std::fs;
use std::path::{Path, PathBuf};

fn collect_proto_files(root: &Path, files: &mut Vec<PathBuf>) -> anyhow::Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let path = entry.path();
        if path.is_dir() {
            collect_proto_files(&path, files)?;
        } else if path.extension().is_some_and(|ext| ext == "proto") {
            files.push(path);
        }
    }
    Ok(())
}

fn main() -> anyhow::Result<()> {
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR")?);
    let out_dir = PathBuf::from(env::var("OUT_DIR")?);
    let proto_dir = manifest_dir.join("dcmaar/v1");
    let third_party = manifest_dir.join("../../core/third_party/protobuf");

    fs::create_dir_all(&out_dir)?;

    let mut protos = Vec::new();
    collect_proto_files(&proto_dir, &mut protos)?;

    tonic_build::configure()
        .out_dir(out_dir)
        .type_attribute(".", "#[derive(serde::Serialize, serde::Deserialize)]")
        .extern_path(".google.protobuf.Timestamp", "::prost_types::Timestamp")
        .extern_path(".google.protobuf.Duration", "::prost_types::Duration")
        .protoc_arg("--experimental_allow_proto3_optional")
        .compile(
            &protos,
            &[
                manifest_dir.as_path(),
                proto_dir.as_path(),
                third_party.as_path(),
                Path::new("/usr/include"),  // System protobuf well-known types
            ],
        )?;

    println!("cargo:rerun-if-changed={}", proto_dir.display());
    println!("cargo:rerun-if-changed={}", third_party.display());
    println!("cargo:rerun-if-changed={}", manifest_dir.display());

    Ok(())
}
