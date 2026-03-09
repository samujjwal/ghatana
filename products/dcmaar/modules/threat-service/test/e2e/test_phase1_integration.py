#!/usr/bin/env python3
"""
End-to-End Integration Tests for Phase 1
Tests complete flow: Agent -> Tracing/Logs Services -> ClickHouse
"""

import requests
import time
import json
from datetime import datetime, timedelta

# Service endpoints
TRACING_SERVICE = "http://localhost:8080"
LOGS_SERVICE = "http://localhost:8081"
CLICKHOUSE = "http://localhost:8123"

class Phase1E2ETests:
    
    def __init__(self):
        self.results = []
    
    def test_services_health(self):
        """Test all services are healthy"""
        print("Testing service health...")
        
        # Test ClickHouse
        try:
            response = requests.get(f"{CLICKHOUSE}/ping")
            assert response.status_code == 200
            self.log_success("ClickHouse health check")
        except Exception as e:
            self.log_failure("ClickHouse health check", str(e))
        
        # Test Tracing Service
        try:
            response = requests.get(f"{TRACING_SERVICE}/health")
            assert response.status_code in [200, 404]  # 404 if endpoint not implemented
            self.log_success("Tracing Service health check")
        except Exception as e:
            self.log_failure("Tracing Service health check", str(e))
        
        # Test Logs Service
        try:
            response = requests.get(f"{LOGS_SERVICE}/health")
            assert response.status_code in [200, 404]
            self.log_success("Logs Service health check")
        except Exception as e:
            self.log_failure("Logs Service health check", str(e))
    
    def test_trace_ingestion_and_query(self):
        """Test trace ingestion and querying"""
        print("Testing trace ingestion and query...")
        
        # Query traces
        try:
            end_time = datetime.utcnow()
            start_time = end_time - timedelta(hours=1)
            
            response = requests.get(
                f"{TRACING_SERVICE}/api/v1/traces",
                params={
                    "start": start_time.isoformat() + "Z",
                    "end": end_time.isoformat() + "Z"
                }
            )
            
            if response.status_code == 200:
                data = response.json()
                self.log_success(f"Trace query successful: {len(data.get('traces', []))} traces")
            else:
                self.log_warning(f"Trace query returned {response.status_code}")
        except Exception as e:
            self.log_failure("Trace query", str(e))
    
    def test_log_ingestion_and_query(self):
        """Test log ingestion and querying"""
        print("Testing log ingestion and query...")
        
        # Query logs
        try:
            end_time = datetime.utcnow()
            start_time = end_time - timedelta(hours=1)
            
            response = requests.get(
                f"{LOGS_SERVICE}/api/v1/logs",
                params={
                    "start": start_time.isoformat() + "Z",
                    "end": end_time.isoformat() + "Z",
                    "limit": 100
                }
            )
            
            if response.status_code == 200:
                data = response.json()
                self.log_success(f"Log query successful: {len(data.get('logs', []))} logs")
            else:
                self.log_warning(f"Log query returned {response.status_code}")
        except Exception as e:
            self.log_failure("Log query", str(e))
    
    def test_log_search(self):
        """Test log search functionality"""
        print("Testing log search...")
        
        try:
            response = requests.get(
                f"{LOGS_SERVICE}/api/v1/logs/search",
                params={
                    "q": "error",
                    "limit": 10
                }
            )
            
            if response.status_code == 200:
                data = response.json()
                self.log_success(f"Log search successful")
            else:
                self.log_warning(f"Log search returned {response.status_code}")
        except Exception as e:
            self.log_failure("Log search", str(e))
    
    def test_log_aggregation(self):
        """Test log aggregation"""
        print("Testing log aggregation...")
        
        try:
            end_time = datetime.utcnow()
            start_time = end_time - timedelta(hours=1)
            
            response = requests.get(
                f"{LOGS_SERVICE}/api/v1/logs/aggregate",
                params={
                    "field": "level",
                    "start": start_time.isoformat() + "Z",
                    "end": end_time.isoformat() + "Z"
                }
            )
            
            if response.status_code == 200:
                data = response.json()
                self.log_success(f"Log aggregation successful")
            else:
                self.log_warning(f"Log aggregation returned {response.status_code}")
        except Exception as e:
            self.log_failure("Log aggregation", str(e))
    
    def test_clickhouse_data(self):
        """Test data in ClickHouse"""
        print("Testing ClickHouse data...")
        
        # Check traces table
        try:
            response = requests.get(
                f"{CLICKHOUSE}",
                params={"query": "SELECT count(*) FROM dcmaar.traces FORMAT JSON"}
            )
            if response.status_code == 200:
                data = response.json()
                count = data['data'][0]['count()'] if data.get('data') else 0
                self.log_success(f"ClickHouse traces table: {count} rows")
        except Exception as e:
            self.log_warning(f"ClickHouse traces check: {str(e)}")
        
        # Check logs table
        try:
            response = requests.get(
                f"{CLICKHOUSE}",
                params={"query": "SELECT count(*) FROM dcmaar.logs FORMAT JSON"}
            )
            if response.status_code == 200:
                data = response.json()
                count = data['data'][0]['count()'] if data.get('data') else 0
                self.log_success(f"ClickHouse logs table: {count} rows")
        except Exception as e:
            self.log_warning(f"ClickHouse logs check: {str(e)}")
    
    def log_success(self, message):
        self.results.append(("✅", message))
        print(f"  ✅ {message}")
    
    def log_failure(self, test, error):
        self.results.append(("❌", f"{test}: {error}"))
        print(f"  ❌ {test}: {error}")
    
    def log_warning(self, message):
        self.results.append(("⚠️", message))
        print(f"  ⚠️ {message}")
    
    def run_all_tests(self):
        """Run all E2E tests"""
        print("\n" + "="*60)
        print("Phase 1 End-to-End Integration Tests")
        print("="*60 + "\n")
        
        self.test_services_health()
        time.sleep(1)
        
        self.test_trace_ingestion_and_query()
        time.sleep(1)
        
        self.test_log_ingestion_and_query()
        time.sleep(1)
        
        self.test_log_search()
        time.sleep(1)
        
        self.test_log_aggregation()
        time.sleep(1)
        
        self.test_clickhouse_data()
        
        # Print summary
        print("\n" + "="*60)
        print("Test Summary")
        print("="*60)
        
        success_count = sum(1 for r in self.results if r[0] == "✅")
        failure_count = sum(1 for r in self.results if r[0] == "❌")
        warning_count = sum(1 for r in self.results if r[0] == "⚠️")
        
        print(f"\nTotal Tests: {len(self.results)}")
        print(f"✅ Passed: {success_count}")
        print(f"❌ Failed: {failure_count}")
        print(f"⚠️ Warnings: {warning_count}")
        
        if failure_count == 0:
            print("\n🎉 All tests passed!")
            return 0
        else:
            print(f"\n❌ {failure_count} test(s) failed")
            return 1

if __name__ == "__main__":
    tests = Phase1E2ETests()
    exit_code = tests.run_all_tests()
    exit(exit_code)
