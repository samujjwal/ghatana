//! Integration tests for the commands API endpoints

#![cfg(feature = "legacy-agent-tests")]

use agent_rs::commands::{Command, CommandStatus};
use reqwest::StatusCode;
use serde_json::json;
use test_log::test;
use uuid::Uuid;

use crate::common::TestContext;

#[tokio::test]
async fn test_create_and_list_commands() {
    let ctx = TestContext::new().await;

    // Test creating a command
    let create_response = ctx
        .config
        .client
        .post(&format!("http://{}/commands", ctx.config.addr))
        .json(&json!({
            "command": "echo",
            "args": ["test"],
            "timeout_secs": 10,
        }))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(create_response.status(), StatusCode::CREATED);

    let command: Command = create_response
        .json()
        .await
        .expect("Failed to parse response");
    assert_eq!(command.command, "echo");
    assert_eq!(command.args, vec!["test"]);
    assert_eq!(command.status, CommandStatus::Running);

    // Test listing commands
    let list_response = ctx
        .config
        .client
        .get(&format!("http://{}/commands", ctx.config.addr))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(list_response.status(), StatusCode::OK);

    let commands: Vec<Command> = list_response
        .json()
        .await
        .expect("Failed to parse response");
    assert!(!commands.is_empty(), "Expected at least one command");
    assert!(
        commands.iter().any(|c| c.id == command.id),
        "Created command not found in list"
    );
}

#[tokio::test]
async fn test_get_command() {
    let ctx = TestContext::new().await;

    // First, create a command
    let create_response = ctx
        .config
        .client
        .post(&format!("http://{}/commands", ctx.config.addr))
        .json(&json!({
            "command": "echo",
            "args": ["test"],
        }))
        .send()
        .await
        .expect("Failed to send request");

    let command: Command = create_response
        .json()
        .await
        .expect("Failed to parse response");

    // Test getting the command by ID
    let get_response = ctx
        .config
        .client
        .get(&format!(
            "http://{}/commands/{}",
            ctx.config.addr, command.id
        ))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(get_response.status(), StatusCode::OK);

    let fetched_command: Command = get_response.json().await.expect("Failed to parse response");
    assert_eq!(fetched_command.id, command.id);
    assert_eq!(fetched_command.command, "echo");
}

#[tokio::test]
async fn test_cancel_command() {
    let ctx = TestContext::new().await;

    // First, create a long-running command
    let create_response = ctx
        .config
        .client
        .post(&format!("http://{}/commands", ctx.config.addr))
        .json(&json!({
            "command": "sleep",
            "args": ["30"],
        }))
        .send()
        .await
        .expect("Failed to send request");

    let command: Command = create_response
        .json()
        .await
        .expect("Failed to parse response");

    // Test canceling the command
    let cancel_response = ctx
        .config
        .client
        .post(&format!(
            "http://{}/commands/{}/cancel",
            ctx.config.addr, command.id
        ))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(cancel_response.status(), StatusCode::OK);

    // Verify the command was canceled
    let get_response = ctx
        .config
        .client
        .get(&format!(
            "http://{}/commands/{}",
            ctx.config.addr, command.id
        ))
        .send()
        .await
        .expect("Failed to send request");

    let updated_command: Command = get_response.json().await.expect("Failed to parse response");
    assert_eq!(updated_command.status, CommandStatus::Canceled);
}

#[tokio::test]
async fn test_command_result() {
    let ctx = TestContext::new().await;

    // First, create a quick command
    let create_response = ctx
        .config
        .client
        .post(&format!("http://{}/commands", ctx.config.addr))
        .json(&json!({
            "command": "echo",
            "args": ["test"],
        }))
        .send()
        .await
        .expect("Failed to send request");

    let command: Command = create_response
        .json()
        .await
        .expect("Failed to parse response");

    // Wait a moment for the command to complete
    tokio::time::sleep(std::time::Duration::from_millis(100)).await;

    // Test getting the command result
    let result_response = ctx
        .config
        .client
        .get(&format!(
            "http://{}/commands/{}/result",
            ctx.config.addr, command.id
        ))
        .send()
        .await
        .expect("Failed to send request");

    assert_eq!(result_response.status(), StatusCode::OK);

    let result: serde_json::Value = result_response
        .json()
        .await
        .expect("Failed to parse response");
    assert_eq!(result["exit_code"], 0);
    assert!(result["stdout"].as_str().unwrap().contains("test"));
}
