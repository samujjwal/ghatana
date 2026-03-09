#!/usr/bin/env python3
"""
Tests for Documentation Copilot
"""

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import mock_open, patch

from copilot import (
    DocumentationCopilot,
    ProtoAnalyzer,
    APIAnalyzer,
    EventSequenceExtractor,
    MarkdownGenerator,
    JupyterGenerator,
    ProtoMessage,
    APIEndpoint,
    EventSequence,
)


class TestProtoAnalyzer(unittest.TestCase):
    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.analyzer = ProtoAnalyzer(self.temp_dir)

    def test_parse_proto_file(self):
        proto_content = '''
        syntax = "proto3";
        package dcmaar.v1;

        // Agent status message
        message AgentStatus {
            string agent_id = 1;
            repeated string tags = 2;
            int64 timestamp = 3;
        }

        // Health check response
        message HealthResponse {
            bool healthy = 1;
            string message = 2;
        }
        '''
        
        proto_file = self.temp_dir / "test.proto"
        proto_file.write_text(proto_content)
        
        messages = self.analyzer._parse_proto_file(proto_file)
        
        self.assertEqual(len(messages), 2)
        
        # Check AgentStatus message
        agent_status = next(msg for msg in messages if msg.name == "AgentStatus")
        self.assertEqual(agent_status.package, "dcmaar.v1")
        self.assertEqual(len(agent_status.fields), 3)
        self.assertEqual(agent_status.fields[0]["name"], "agent_id")
        self.assertEqual(agent_status.fields[0]["type"], "string")
        self.assertEqual(agent_status.fields[1]["repeated"], True)
        
        # Check HealthResponse message
        health_response = next(msg for msg in messages if msg.name == "HealthResponse")
        self.assertEqual(len(health_response.fields), 2)

    def test_extract_message_description(self):
        content = '''
        // Agent status message
        // Contains current status of the agent
        message AgentStatus {
            string agent_id = 1;
        }
        '''
        
        description = self.analyzer._extract_message_description(content, "AgentStatus")
        self.assertIn("Agent status message", description)
        self.assertIn("Contains current status", description)


class TestAPIAnalyzer(unittest.TestCase):
    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.analyzer = APIAnalyzer([self.temp_dir])

    def test_analyze_go_endpoints(self):
        go_content = '''
        package main

        import "net/http"

        func HandleHealth(w http.ResponseWriter, r *http.Request) {
            // Health check handler
        }

        func HandleStatus(w http.ResponseWriter, r *http.Request) {
            // Status handler
        }

        func main() {
            http.HandleFunc("/health", HandleHealth)
            http.Handle("/api/status", HandleStatus)
        }
        '''
        
        go_file = self.temp_dir / "server.go"
        go_file.write_text(go_content)
        
        endpoints = self.analyzer._analyze_go_endpoints(self.temp_dir)
        
        self.assertEqual(len(endpoints), 2)
        
        health_endpoint = next(ep for ep in endpoints if ep.path == "/health")
        self.assertEqual(health_endpoint.method, "GET")
        self.assertIn("HandleHealth", health_endpoint.description)

    def test_analyze_typescript_endpoints(self):
        ts_content = '''
        export async function fetchStatus(): Promise<StatusResponse> {
            const response = await fetch('/api/status');
            return response.json();
        }

        export async function updateConfig(config: Config): Promise<void> {
            await fetch('/api/config', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });
        }
        '''
        
        ts_file = self.temp_dir / "api.ts"
        ts_file.write_text(ts_content)
        
        endpoints = self.analyzer._analyze_typescript_endpoints(self.temp_dir)
        
        self.assertEqual(len(endpoints), 2)
        
        status_endpoint = next(ep for ep in endpoints if ep.path == "/api/status")
        self.assertEqual(status_endpoint.method, "GET")
        
        config_endpoint = next(ep for ep in endpoints if ep.path == "/api/config")
        self.assertEqual(config_endpoint.method, "POST")

    def test_infer_http_method(self):
        test_cases = [
            ("GetUser", "GET"),
            ("CreateUser", "POST"),
            ("UpdateUser", "PUT"),
            ("DeleteUser", "DELETE"),
            ("HandleUser", "GET"),  # Default
        ]
        
        for handler_name, expected_method in test_cases:
            method = self.analyzer._infer_http_method(handler_name, "")
            self.assertEqual(method, expected_method)


class TestEventSequenceExtractor(unittest.TestCase):
    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.extractor = EventSequenceExtractor([self.temp_dir])

    def test_parse_go_test_file(self):
        test_content = '''
        package main

        import "testing"

        func TestAgentStartup(t *testing.T) {
            // Step 1: Initialize agent configuration
            // Step 2: Start agent services
            // Step 3: Verify agent is healthy
            // Then: Agent should be ready to receive requests
        }

        func TestFailure(t *testing.T) {
            // This test has no clear steps
        }
        '''
        
        test_file = self.temp_dir / "agent_test.go"
        test_file.write_text(test_content)
        
        sequences = self.extractor._parse_test_file(test_file)
        
        self.assertEqual(len(sequences), 1)  # Only TestAgentStartup should have steps
        
        sequence = sequences[0]
        self.assertEqual(sequence.name, "AgentStartup")
        self.assertEqual(len(sequence.steps), 4)
        self.assertIn("Initialize agent configuration", sequence.steps[0]["description"])

    def test_parse_python_test_file(self):
        test_content = '''
        def test_api_integration(self):
            """Test API integration flow"""
            # Given: API server is running
            # When: Client sends request
            # Then: Server responds correctly
        '''
        
        test_file = self.temp_dir / "test_api.py"
        test_file.write_text(test_content)
        
        sequences = self.extractor._parse_test_file(test_file)
        
        self.assertEqual(len(sequences), 1)
        sequence = sequences[0]
        self.assertEqual(sequence.name, "api_integration")
        self.assertEqual(len(sequence.steps), 3)


class TestMarkdownGenerator(unittest.TestCase):
    def setUp(self):
        self.generator = MarkdownGenerator()

    def test_generate_api_docs(self):
        endpoints = [
            APIEndpoint(
                method="GET",
                path="/api/health",
                description="Health check endpoint",
                request_type=None,
                response_type=None,
                parameters=[],
                examples=[]
            ),
            APIEndpoint(
                method="POST",
                path="/api/agents",
                description="Create new agent",
                request_type="AgentRequest",
                response_type="AgentResponse",
                parameters=[
                    {"name": "name", "type": "string", "description": "Agent name"}
                ],
                examples=[]
            )
        ]
        
        docs = self.generator.generate_api_docs(endpoints)
        
        self.assertIn("# API Documentation", docs)
        self.assertIn("## Api API", docs)
        self.assertIn("### GET /api/health", docs)
        self.assertIn("### POST /api/agents", docs)
        self.assertIn("Health check endpoint", docs)
        self.assertIn("Create new agent", docs)

    def test_generate_proto_docs(self):
        messages = [
            ProtoMessage(
                name="AgentStatus",
                fields=[
                    {"name": "agent_id", "type": "string", "number": 1, "repeated": False},
                    {"name": "tags", "type": "string", "number": 2, "repeated": True}
                ],
                description="Agent status information",
                package="dcmaar.v1",
                file_path="/path/to/agent.proto"
            )
        ]
        
        docs = self.generator.generate_proto_docs(messages)
        
        self.assertIn("# Protocol Buffer Messages", docs)
        self.assertIn("## Package: dcmaar.v1", docs)
        self.assertIn("### AgentStatus", docs)
        self.assertIn("Agent status information", docs)
        self.assertIn("| agent_id | string |", docs)
        self.assertIn("| tags | repeated string |", docs)

    def test_generate_sequence_docs(self):
        sequences = [
            EventSequence(
                name="AgentStartup",
                description="Agent startup sequence",
                steps=[
                    {"number": 1, "action": "execute", "description": "Initialize configuration"},
                    {"number": 2, "action": "execute", "description": "Start services"}
                ],
                prerequisites=["Docker running"],
                expected_outcomes=["Agent healthy"]
            )
        ]
        
        docs = self.generator.generate_sequence_docs(sequences)
        
        self.assertIn("# Event Sequences", docs)
        self.assertIn("## AgentStartup", docs)
        self.assertIn("Agent startup sequence", docs)
        self.assertIn("**Prerequisites:**", docs)
        self.assertIn("- Docker running", docs)
        self.assertIn("**Steps:**", docs)
        self.assertIn("1. Initialize configuration", docs)
        self.assertIn("2. Start services", docs)
        self.assertIn("**Expected Outcomes:**", docs)
        self.assertIn("- Agent healthy", docs)


class TestJupyterGenerator(unittest.TestCase):
    def setUp(self):
        self.generator = JupyterGenerator()

    def test_generate_api_notebook(self):
        endpoints = [
            APIEndpoint(
                method="GET",
                path="/api/status",
                description="Get system status",
                request_type=None,
                response_type=None,
                parameters=[],
                examples=[]
            ),
            APIEndpoint(
                method="POST",
                path="/api/data",
                description="Submit data",
                request_type=None,
                response_type=None,
                parameters=[],
                examples=[]
            )
        ]
        
        notebook = self.generator.generate_api_notebook(endpoints)
        
        self.assertEqual(notebook["nbformat"], 4)
        self.assertIn("cells", notebook)
        self.assertIn("metadata", notebook)
        
        cells = notebook["cells"]
        self.assertTrue(len(cells) > 0)
        
        # Check title cell
        title_cell = cells[0]
        self.assertEqual(title_cell["cell_type"], "markdown")
        self.assertIn("DCMAAR API Explorer", title_cell["source"][0])
        
        # Check setup cell
        setup_cell = cells[1]
        self.assertEqual(setup_cell["cell_type"], "code")
        self.assertIn("import requests", "".join(setup_cell["source"]))
        
        # Check endpoint cells
        get_cell_found = False
        post_cell_found = False
        
        for cell in cells:
            if cell["cell_type"] == "code":
                source = "".join(cell["source"])
                if "requests.get" in source and "/api/status" in source:
                    get_cell_found = True
                elif "requests.post" in source and "/api/data" in source:
                    post_cell_found = True
        
        self.assertTrue(get_cell_found, "GET endpoint cell not found")
        self.assertTrue(post_cell_found, "POST endpoint cell not found")


class TestDocumentationCopilot(unittest.TestCase):
    def setUp(self):
        self.temp_dir = Path(tempfile.mkdtemp())
        self.output_dir = self.temp_dir / "docs"
        self.copilot = DocumentationCopilot(self.temp_dir)

    def test_generate_documentation(self):
        # Create mock proto file
        proto_dir = self.temp_dir / "contracts" / "proto"
        proto_dir.mkdir(parents=True, exist_ok=True)
        
        proto_file = proto_dir / "test.proto"
        proto_file.write_text('''
        syntax = "proto3";
        package test;
        
        message TestMessage {
            string id = 1;
        }
        ''')
        
        # Create mock Go file
        server_dir = self.temp_dir / "services" / "server"
        server_dir.mkdir(parents=True, exist_ok=True)
        
        go_file = server_dir / "main.go"
        go_file.write_text('''
        package main
        
        import "net/http"
        
        func HandleTest(w http.ResponseWriter, r *http.Request) {}
        
        func main() {
            http.HandleFunc("/test", HandleTest)
        }
        ''')
        
        # Create mock test file
        test_dir = self.temp_dir / "tests"
        test_dir.mkdir(parents=True, exist_ok=True)
        
        test_file = test_dir / "integration_test.go"
        test_file.write_text('''
        func TestIntegration(t *testing.T) {
            // Step 1: Setup test environment
            // Step 2: Execute test scenario
        }
        ''')
        
        # Generate documentation
        self.copilot.generate_documentation(self.output_dir)
        
        # Verify output files exist
        self.assertTrue((self.output_dir / "api.md").exists())
        self.assertTrue((self.output_dir / "protocols.md").exists())
        self.assertTrue((self.output_dir / "sequences.md").exists())
        self.assertTrue((self.output_dir / "api_explorer.ipynb").exists())
        self.assertTrue((self.output_dir / "summary.json").exists())
        
        # Verify summary content
        with open(self.output_dir / "summary.json") as f:
            summary = json.load(f)
        
        self.assertIn("proto_messages", summary)
        self.assertIn("api_endpoints", summary)
        self.assertIn("event_sequences", summary)
        self.assertIn("files_generated", summary)
        self.assertEqual(len(summary["files_generated"]), 4)

    def test_integration_with_mock_data(self):
        """Test with more comprehensive mock data"""
        # This would be a more comprehensive integration test
        # with realistic project structure and data
        pass


class TestEdgeCases(unittest.TestCase):
    def test_empty_proto_file(self):
        analyzer = ProtoAnalyzer(Path("/nonexistent"))
        messages = analyzer.analyze_protos()
        self.assertEqual(len(messages), 0)

    def test_malformed_proto_file(self):
        temp_dir = Path(tempfile.mkdtemp())
        analyzer = ProtoAnalyzer(temp_dir)
        
        proto_file = temp_dir / "bad.proto"
        proto_file.write_text("This is not a valid proto file")
        
        # Should handle gracefully without crashing
        messages = analyzer._parse_proto_file(proto_file)
        self.assertEqual(len(messages), 0)

    def test_empty_go_file(self):
        temp_dir = Path(tempfile.mkdtemp())
        analyzer = APIAnalyzer([temp_dir])
        
        go_file = temp_dir / "empty.go"
        go_file.write_text("package main")
        
        endpoints = analyzer._analyze_go_endpoints(temp_dir)
        self.assertEqual(len(endpoints), 0)

    def test_no_test_steps(self):
        temp_dir = Path(tempfile.mkdtemp())
        extractor = EventSequenceExtractor([temp_dir])
        
        test_file = temp_dir / "minimal_test.go"
        test_file.write_text('''
        func TestMinimal(t *testing.T) {
            assert.True(t, true)
        }
        ''')
        
        sequences = extractor._parse_test_file(test_file)
        # Should have sequence but no steps
        self.assertEqual(len(sequences), 1)
        self.assertEqual(len(sequences[0].steps), 0)


if __name__ == "__main__":
    unittest.main()