#!/bin/bash
# Create unique stub files for each disabled validator

files=(
  "APIContractValidator"
  "AnalyticsContractValidator"
  "AutonomousContractValidator"
  "ContractValidator"
  "EventContractValidator"
  "ExperienceContractValidator"
  "SchemaContractValidator"
)

for file in "${files[@]}"; do
  filepath="platform/java/kernel/src/main/java/com/ghatana/kernel/contracts/validator.disabled/${file}.java"
  cat > "$filepath" << EOF
package com.ghatana.kernel.contracts.validator;

/**
 * Disabled - Incomplete validator implementation
 * This validator has unresolved symbol references and is disabled pending reimplementation.
 */
@Deprecated(forRemoval = true)
class ${file}Disabled {
}
EOF
done

echo "All validator files replaced with stubs"
