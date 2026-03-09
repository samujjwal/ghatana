#!/usr/bin/env python3
"""
Sequential Test Runner for Guardian Backend

Runs all test files sequentially to avoid concurrent transaction deadlocks.

Individual tests pass 100%, but concurrent execution causes PostgreSQL deadlocks
in authService.register() and related transactional operations.

Usage:
    python run_tests_sequential.py                 # Run all tests
    python run_tests_sequential.py --show-failures  # Show failures
    python run_tests_sequential.py --verbose       # Verbose output
    python run_tests_sequential.py --coverage      # Run with coverage

See: CONCURRENT_TEST_FIX_STRATEGY.md for details
"""

import os
import subprocess
import sys
import json
from pathlib import Path
from typing import List, Tuple, Dict
import time
from datetime import datetime, timedelta

class TestRunner:
    def __init__(self, verbose=False, show_failures=False, coverage=False):
        self.verbose = verbose
        self.show_failures = show_failures
        self.coverage = coverage
        self.backend_dir = Path(__file__).parent
        self.test_dir = self.backend_dir / "src" / "__tests__"
        self.results: Dict[str, bool] = {}
        self.errors: Dict[str, str] = {}
        self.start_time = None
        self.end_time = None

    def find_test_files(self) -> List[str]:
        """Find all test files"""
        test_files = sorted([f for f in self.test_dir.rglob("*.test.ts")])
        return [str(f.relative_to(self.backend_dir)) for f in test_files]

    def run_test_file(self, test_file: str) -> Tuple[bool, str]:
        """Run a single test file sequentially"""
        cmd = ["pnpm", "test", test_file, "--run", "--no-coverage"]
        
        if self.verbose:
            print(f"\n  Running: {test_file}")
        
        try:
            result = subprocess.run(
                cmd,
                cwd=str(self.backend_dir),
                capture_output=True,
                text=True,
                timeout=60
            )
            
            passed = result.returncode == 0
            error_msg = result.stderr if result.returncode != 0 else ""
            
            return passed, error_msg
        except subprocess.TimeoutExpired:
            return False, "Test timeout (60s exceeded)"
        except Exception as e:
            return False, str(e)

    def run_all_tests(self) -> Tuple[int, int]:
        """Run all test files sequentially"""
        test_files = self.find_test_files()
        total = len(test_files)
        passed = 0
        failed = 0
        
        print(f"\n{'='*80}")
        print(f"🧪 Guardian Backend - Sequential Test Execution")
        print(f"{'='*80}")
        print(f"\nFound {total} test files\n")
        
        self.start_time = time.time()
        
        for i, test_file in enumerate(test_files, 1):
            # Run test
            test_passed, error = self.run_test_file(test_file)
            
            # Update results
            self.results[test_file] = test_passed
            if error:
                self.errors[test_file] = error
            
            if test_passed:
                passed += 1
                status = "✅ PASS"
            else:
                failed += 1
                status = "❌ FAIL"
            
            # Print progress
            pct = (i / total) * 100
            print(f"[{i:2d}/{total}] {pct:5.1f}% {status} {test_file}")
        
        self.end_time = time.time()
        
        # Print summary
        duration = self.end_time - self.start_time
        pass_rate = (passed / total * 100) if total > 0 else 0
        
        print(f"\n{'='*80}")
        print(f"📊 Test Summary")
        print(f"{'='*80}")
        print(f"  Total:   {total} files")
        print(f"  Passed:  {passed} ({pass_rate:.1f}%)")
        print(f"  Failed:  {failed} ({100-pass_rate:.1f}%)")
        print(f"  Duration: {int(duration)}s")
        print(f"{'='*80}\n")
        
        if self.show_failures and failed > 0:
            print(f"\n❌ Failed Tests:\n")
            for test_file, error in self.errors.items():
                if not self.results.get(test_file, True):
                    print(f"  • {test_file}")
                    if error and self.verbose:
                        print(f"    Error: {error[:200]}")
            print()
        
        return passed, failed

def main():
    verbose = "--verbose" in sys.argv
    show_failures = "--show-failures" in sys.argv
    coverage = "--coverage" in sys.argv
    
    runner = TestRunner(verbose=verbose, show_failures=show_failures, coverage=coverage)
    passed, failed = runner.run_all_tests()
    
    if failed > 0:
        print(f"❌ {failed} test files failed")
        sys.exit(1)
    else:
        print(f"✅ All test files passed!")
        sys.exit(0)

if __name__ == "__main__":
    main()
