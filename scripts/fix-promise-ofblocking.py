#!/usr/bin/env python3
"""
Script to fix Promise.ofBlocking() anti-patterns in Data-Cloud codebase.

This script replaces Promise.ofBlocking(executor, () -> {...}) patterns with
the correct Promise.of() pattern following established codebase conventions.

Pattern:
  BEFORE: return Promise.ofBlocking(executor, () -> { ... return result; });
  AFTER:  try { ... return Promise.of(result); } catch (Exception e) { return Promise.ofException(e); }
"""

import re
import sys
from pathlib import Path
from typing import List, Tuple

def fix_promise_ofblocking_in_file(file_path: Path) -> Tuple[int, bool]:
    """
    Fix Promise.ofBlocking() patterns in a single file.
    
    Returns:
        Tuple of (number_of_fixes, file_was_modified)
    """
    try:
        content = file_path.read_text(encoding='utf-8')
        original_content = content
        fixes_count = 0
        
        # Pattern 1: Simple Promise.ofBlocking with executor parameter
        # Matches: Promise.ofBlocking(executor, () -> { ... })
        pattern1 = r'return\s+Promise\.ofBlocking\(executor,\s*\(\)\s*->\s*\{([^}]*(?:\{[^}]*\}[^}]*)*)\}\);'
        
        def replace_pattern1(match):
            nonlocal fixes_count
            body = match.group(1).strip()
            fixes_count += 1
            
            # Check if body ends with return statement
            if 'return' in body:
                # Extract the return value
                return_match = re.search(r'return\s+([^;]+);', body)
                if return_match:
                    return_value = return_match.group(1).strip()
                    # Remove the return statement from body
                    body_without_return = re.sub(r'return\s+[^;]+;', '', body).strip()
                    
                    return f'''try {{
            {body_without_return}
            return Promise.of({return_value});
        }} catch (Exception e) {{
            return Promise.ofException(e);
        }}'''
            
            # No explicit return, assume void
            return f'''try {{
            {body}
            return Promise.of(null);
        }} catch (Exception e) {{
            return Promise.ofException(e);
        }}'''
        
        # Apply pattern 1
        content = re.sub(pattern1, replace_pattern1, content, flags=re.DOTALL)
        
        # Write back if modified
        if content != original_content:
            file_path.write_text(content, encoding='utf-8')
            return fixes_count, True
        
        return 0, False
        
    except Exception as e:
        print(f"Error processing {file_path}: {e}", file=sys.stderr)
        return 0, False

def main():
    """Main entry point."""
    # List of files to fix (from grep results)
    files_to_fix = [
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/BlobStorageConnector.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/ClickHouseTimeSeriesConnector.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/persistence/JpaEntityRepositoryImpl.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/iceberg/CoolTierStoragePlugin.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/streaming/KafkaStreamingPlugin.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/vector/vector/VectorMemoryPlugin.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/CephObjectStorageConnector.java",
        "products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServer.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/config/ConfigLoader.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/persistence/JpaCollectionRepositoryImpl.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/storage/OpenSearchConnector.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/recovery/DisasterRecoveryManager.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/storage/WarmTierEventLogStore.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/compliance/ComplianceReporter.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/kafka/KafkaEventLogStore.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/ColdTierArchivePlugin.java",
        "products/data-cloud/platform/src/test/java/com/ghatana/datacloud/infrastructure/storage/PostgresJsonbConnectorIntegrationTest.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/storage/AdminStorageManagementService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/lineage/LineageTracker.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/iceberg/IcebergTableManager.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/client/DistributedHttpDataCloudClient.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/cache/CacheService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/enterprise/documentation/AutoDocumentationGenerator.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/ai/AIModelManager.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/edge/LightweightEdgeDeployment.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/cache/EmbeddingCacheAdapter.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/cache/QueryPlanCacheAdapter.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/cache/RedisCollectionCacheAdapter.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/cache/VectorSearchCacheAdapter.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/GlacierRestoreManager.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/scaling/PluginAutoScaler.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/analytics/anomaly/StatisticalAnomalyDetector.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/analytics/export/EntityExportService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/observability/StorageService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/workflow/AgentRecommendationService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/config/health/PluginHealthCheckFactory.java",
        "products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/learning/DataCloudLearningBridge.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/analytics/report/ReportService.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/ai/QueryRecommender.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/query/AdvancedQueryBuilder.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/application/realtime/RealtimeExecutionMonitor.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/brain/DefaultDataCloudBrain.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/infrastructure/backpressure/BackpressureManager.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/iceberg/TierMigrationScheduler.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/plugins/storage/ArchiveMigrationScheduler.java",
        "products/data-cloud/platform/src/main/java/com/ghatana/datacloud/workflow/WorkflowRunRepository.java",
    ]
    
    # Get workspace root
    workspace_root = Path(__file__).parent.parent
    
    total_fixes = 0
    files_modified = 0
    files_failed = []
    
    print("Starting Promise.ofBlocking() fix automation...")
    print(f"Processing {len(files_to_fix)} files...\n")
    
    for file_rel_path in files_to_fix:
        file_path = workspace_root / file_rel_path
        
        if not file_path.exists():
            print(f"⚠️  File not found: {file_rel_path}")
            files_failed.append(file_rel_path)
            continue
        
        fixes, modified = fix_promise_ofblocking_in_file(file_path)
        
        if modified:
            files_modified += 1
            total_fixes += fixes
            print(f"✅ Fixed {fixes} instances in {file_rel_path}")
        else:
            print(f"⏭️  No changes needed in {file_rel_path}")
    
    print(f"\n{'='*80}")
    print(f"Summary:")
    print(f"  Total files processed: {len(files_to_fix)}")
    print(f"  Files modified: {files_modified}")
    print(f"  Total fixes applied: {total_fixes}")
    print(f"  Files failed: {len(files_failed)}")
    
    if files_failed:
        print(f"\nFailed files:")
        for f in files_failed:
            print(f"  - {f}")
    
    print(f"{'='*80}\n")
    
    return 0 if not files_failed else 1

if __name__ == "__main__":
    sys.exit(main())
