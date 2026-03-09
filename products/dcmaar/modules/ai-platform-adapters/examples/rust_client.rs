//! Example Rust client for DCMaar Agent
//!
//! This demonstrates how to:
//! - Connect to the agent via IPC
//! - Make RPC calls
//! - Handle responses and errors
//!
//! Run with: cargo run --example rust_client

use agent_ipc::{IpcClient, IpcResult, TcpTransport, Transport};
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
struct MetricsRequest {
    metrics: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
struct MetricsResponse {
    data: serde_json::Value,
}

#[derive(Debug, Serialize, Deserialize)]
struct CommandRequest {
    command: String,
    args: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
struct CommandResponse {
    exit_code: i32,
    stdout: String,
    stderr: String,
}

#[tokio::main]
async fn main() -> IpcResult<()> {
    println!("🚀 DCMaar Agent - Rust Client Example\n");

    // Connect to the agent
    println!("📡 Connecting to agent at localhost:9090...");
    let transport = Transport::Tcp(TcpTransport::new("127.0.0.1:9090")?);
    let mut client = IpcClient::new(transport);

    // Example 1: Get system metrics
    println!("\n📊 Example 1: Get System Metrics");
    println!("================================");

    let metrics_request = MetricsRequest {
        metrics: vec!["cpu".to_string(), "memory".to_string(), "disk".to_string()],
    };

    match client.call::<_, MetricsResponse>("get_metrics", metrics_request).await {
        Ok(response) => {
            println!("✅ Received metrics:");
            println!("{}", serde_json::to_string_pretty(&response.data).unwrap());
        }
        Err(e) => {
            println!("❌ Error getting metrics: {}", e);
        }
    }

    // Example 2: Execute a command
    println!("\n⚙️  Example 2: Execute Command");
    println!("==============================");

    let cmd_request = CommandRequest {
        command: "echo".to_string(),
        args: vec!["Hello from DCMaar!".to_string()],
    };

    match client.call::<_, CommandResponse>("execute_command", cmd_request).await {
        Ok(response) => {
            println!("✅ Command executed:");
            println!("   Exit code: {}", response.exit_code);
            println!("   Output: {}", response.stdout.trim());
            if !response.stderr.is_empty() {
                println!("   Errors: {}", response.stderr.trim());
            }
        }
        Err(e) => {
            println!("❌ Error executing command: {}", e);
        }
    }

    // Example 3: Error handling
    println!("\n🔍 Example 3: Error Handling");
    println!("============================");

    let invalid_request = MetricsRequest {
        metrics: vec!["invalid_metric".to_string()],
    };

    match client.call::<_, MetricsResponse>("get_metrics", invalid_request).await {
        Ok(response) => {
            println!("✅ Received response: {:?}", response);
        }
        Err(e) => {
            println!("❌ Expected error occurred: {}", e);
        }
    }

    // Example 4: Multiple sequential calls
    println!("\n🔄 Example 4: Multiple Sequential Calls");
    println!("=======================================");

    for i in 1..=3 {
        println!("  Call #{}", i);

        let request = MetricsRequest {
            metrics: vec!["cpu".to_string()],
        };

        match client.call::<_, MetricsResponse>("get_metrics", request).await {
            Ok(_) => println!("    ✅ Success"),
            Err(e) => println!("    ❌ Error: {}", e),
        }
    }

    println!("\n✨ All examples completed!");

    Ok(())
}
