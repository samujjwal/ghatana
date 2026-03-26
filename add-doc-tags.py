#!/usr/bin/env python3
"""
Add @doc tags to Java files that are missing them.
"""

import os
import re
from pathlib import Path

# List of files that need @doc tags (from build failure)
FILES_TO_UPDATE = [
    "products/finance/src/main/java/com/ghatana/finance/contracts/FinanceContracts.java",
    "products/finance/src/main/java/com/ghatana/finance/contracts/ContractValidationRunner.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelApprovalRecord.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelNotApprovedException.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceModelGovernanceImpl.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelRepository.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceModelMetadata.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelApprovalRepository.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/AlertService.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceAIEvaluationImpl.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelRecord.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/agents/FraudDetectionAgent.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/agents/FraudDetectionResult.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelPerformanceRecord.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceAutonomyManagerImpl.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ModelPerformanceRepository.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceAgentOrchestratorImpl.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/ApprovalWorkflowService.java",
    "products/finance/src/main/java/com/ghatana/finance/ai/FinanceAIModule.java",
    "products/finance/src/main/java/com/ghatana/finance/service/TransactionService.java",
    "products/finance/src/main/java/com/ghatana/finance/service/TransactionResult.java",
    "products/finance/src/main/java/com/ghatana/finance/service/Transaction.java",
    "products/phr/src/main/java/com/ghatana/phr/repository/PatientRecordRepository.java",
    "products/phr/src/main/java/com/ghatana/phr/repository/ConsentRepository.java",
    "products/phr/src/main/java/com/ghatana/phr/repository/UserRepository.java",
    "products/phr/src/main/java/com/ghatana/phr/repository/TenantConfigRepository.java",
    "products/phr/src/main/java/com/ghatana/phr/security/PHRPrivacyManagerImpl.java",
    "products/phr/src/main/java/com/ghatana/phr/security/SecurityContextHolder.java",
    "products/phr/src/main/java/com/ghatana/phr/security/PHRSecurityConfig.java",
    "products/phr/src/main/java/com/ghatana/phr/security/HIPAAPrivacyPolicy.java",
    "products/phr/src/main/java/com/ghatana/phr/security/PHRSecurityManagerImpl.java",
    "products/phr/src/main/java/com/ghatana/phr/observability/PHRAuditTrailServiceImpl.java",
    "products/phr/src/main/java/com/ghatana/phr/observability/PHRExplainabilityFrameworkImpl.java",
    "products/phr/src/main/java/com/ghatana/phr/observability/PHRTelemetryManagerImpl.java",
    "products/phr/src/main/java/com/ghatana/phr/observability/PHRTelemetryConfig.java",
    "products/phr/src/main/java/com/ghatana/phr/observability/PHRExplainabilityContext.java",
    "products/phr/src/main/java/com/ghatana/phr/model/ProviderInfo.java",
    "products/phr/src/main/java/com/ghatana/phr/model/TenantConfig.java",
    "products/phr/src/main/java/com/ghatana/phr/model/PatientRecords.java",
    "products/phr/src/main/java/com/ghatana/phr/model/PHRUser.java",
    "products/phr/src/main/java/com/ghatana/phr/model/PatientConsent.java",
    "products/phr/src/main/java/com/ghatana/phr/model/PatientRecord.java",
    "products/phr/src/main/java/com/ghatana/phr/api/PatientController.java",
    "products/phr/src/main/java/com/ghatana/phr/service/PatientService.java",
    "products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/QueryType.java",
    "products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/AnalyticsQuery.java",
    "products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/QueryResult.java",
    "products/data-cloud/platform-analytics/src/main/java/com/ghatana/datacloud/analytics/QueryPlan.java",
    "products/data-cloud/platform-config/src/main/java/com/ghatana/datacloud/pattern/DefaultPatternMatcher.java",
    "products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/schema/SchemaFormat.java",
    "products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/schema/EventSchema.java",
    "products/data-cloud/platform-entity/src/main/java/com/ghatana/datacloud/schema/CompatibilityMode.java",
    "products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/SecretResolver.java",
    "products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/SecretValue.java",
    "products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/EnvSecretProvider.java",
    "products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/SecretReference.java",
    "products/data-cloud/platform-event/src/main/java/com/ghatana/datacloud/event/spi/secrets/FileSecretProvider.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/application/nlq/ValidationResult.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/application/nlq/QueryResult.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/application/nlq/QueryPlan.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/application/nlq/NLQService.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/quality/MLQualityScorer.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/backpressure/BackpressureConfig.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/ExcelExporter.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/CsvImporter.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/infrastructure/importexport/ImportExportService.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/client/feedback/DefaultFeedbackCollector.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/client/autonomy/AutonomyLog.java",
    "products/data-cloud/platform-launcher/src/main/java/com/ghatana/datacloud/embedded/RecordCodec.java",
]

def infer_class_type(content: str) -> str:
    """Infer class type from content."""
    if "enum " in content:
        return "enum"
    elif "interface " in content:
        return "interface"
    elif "record " in content:
        return "record"
    else:
        return "class"

def infer_pattern(filename: str, classname: str) -> str:
    """Infer pattern from filename and class name."""
    if "Repository" in classname:
        return "Repository"
    elif "Service" in classname and any(x in classname for x in ["Impl", "Manager"]):
        return "Service"
    elif "Config" in classname:
        return "Configuration"
    elif "Manager" in classname:
        return "Manager"
    elif "Record" in classname or ("Result" in classname and classname.endswith("Record")):
        return "ValueObject"
    elif classname.endswith("Result") or classname.endswith("Exception"):
        return "ValueObject"
    elif "Agent" in classname:
        return "Agent"
    elif "Contracts" in classname:
        return "Registry"
    else:
        return "Service"

def get_class_name(content: str) -> str:
    """Extract class name from Java source."""
    match = re.search(r'(?:public\s+)?(?:final\s+)?(?:class|interface|record|enum)\s+(\w+)', content)
    return match.group(1) if match else "Unknown"

def generate_doc_block(filename: str, content: str, classtype: str) -> str:
    """Generate appropriate @doc block."""
    classname = get_class_name(content)
    pattern = infer_pattern(filename, classname)
    layer = "product"  # Most of these are product layer
    
    # Build purpose from class name
    if "Repository" in classname:
        purpose = f"Data access layer for {classname.replace('Repository', '')}"
    elif "Service" in classname:
        purpose = f"Business logic service for {classname.replace('Service', 'Service', 1).replace('Impl', '')}"
    elif "Config" in classname:
        purpose = f"Configuration and setup for {classname.replace('Config', '')}"
    elif classname.endswith("Exception"):
        purpose = "Exception type for error handling"
    elif classname.endswith("Result"):
        purpose = "Result object for asynchronous operations"
    elif "Record" in classname:
        purpose = "Data record entity"
    else:
        purpose = f"Component for {classname}"
    
    doc = f"""/**
 * {purpose}
 *
 * @doc.type {classtype}
 * @doc.purpose {purpose}
 * @doc.layer {layer}
 * @doc.pattern {pattern}
 */"""
    
    return doc

def add_doc_tags(filepath: str):
    """Add @doc tags to a Java file."""
    path = Path(filepath)
    if not path.exists():
        print(f"⚠️  Not found: {filepath}")
        return False
    
    with open(path, 'r') as f:
        content = f.read()
    
    # Check if already has @doc tags
    if "@doc.type" in content or "@doc.purpose" in content:
        print(f"✅ Already has @doc tags: {filepath}")
        return True
    
    # Find the class/interface/record declaration
    class_match = re.search(r'((?:public\s+)?(?:final\s+)?(?:class|interface|record|enum)\s+\w+[^{]*)\{', content)
    if not class_match:
        print(f"❌ Could not find class declaration in {filepath}")
        return False
    
    classtype = infer_class_type(content)
    doc_block = generate_doc_block(filepath, content, classtype)
    
    # Insert doc block before class declaration
    # Look for existing javadoc or insert before class
    existing_javadoc = re.search(r'/\*\*.*?\*/', content, re.DOTALL)
    if existing_javadoc:
        # Replace existing javadoc
        new_content = content[:existing_javadoc.start()] + doc_block + content[existing_javadoc.end():]
    else:
        # Insert before class declaration - find position
        class_start = content.find("public class") if "public class" in content else content.find("class ")
        if "public " not in content[:class_start + 10]:
            class_start = content.find("public", max(0, class_start - 100))
        
        # Find start of line
        line_start = content.rfind('\n', 0, class_start) + 1
        
        new_content = content[:line_start] + doc_block + "\n" + content[line_start:]
    
    with open(path, 'w') as f:
        f.write(new_content)
    
    print(f"✅ Updated: {filepath}")
    return True

if __name__ == "__main__":
    os.chdir("/Users/samujjwal/Development/ghatana")
    
    success_count = 0
    for file in FILES_TO_UPDATE:
        if add_doc_tags(file):
            success_count += 1
    
    print(f"\n✅ Successfully updated {success_count}/{len(FILES_TO_UPDATE)} files")
