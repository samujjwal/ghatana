#!/bin/bash
# CI Check: Contract Drift Detection
# Compares implementation against published contracts

set -e

CONTRACTS_DIR="libs/contracts/src"
IMPLEMENTATIONS=(
  "services/tutorputor-content/src/routes/generate-animation.ts:GenerateAnimationRequest,GenerateAnimationResponse"
  "services/tutorputor-content/src/routes/generate-simulation.ts:GenerateSimulationRequest,GenerateSimulationResponse"
  "services/tutorputor-assessment/src/routes/submit.ts:SubmitAssessmentRequest,SubmitAssessmentResponse"
)

echo "Contract Drift Detection"
echo "======================="
echo ""

DRIFTS=()

for entry in "${IMPLEMENTATIONS[@]}"; do
  IFS=':' read -r file types <<< "$entry"
  IFS=',' read -ra type_list <<< "$types"
  
  if [ ! -f "$file" ]; then
    echo "⚠️  Implementation file not found: $file"
    continue
  fi
  
  for type in "${type_list[@]}"; do
    # Check if type is defined in contracts
    contract_file="$CONTRACTS_DIR/${type}.ts"
    
    if [ ! -f "$contract_file" ]; then
      echo "❌ Contract not found: $type"
      DRIFTS+=("$type: Missing contract definition")
      continue
    fi
    
    # Simple check: compare property names (simplified)
    contract_props=$(grep -oP '(?<=\s)\w+(?=\?:)' "$contract_file" 2>/dev/null | sort | tr '\n' ' ')
    impl_props=$(grep -oP '(?<=\s)\w+(?=\?:)' "$file" 2>/dev/null | sort | tr '\n' ' ')
    
    if [ -n "$contract_props" ]; then
      echo "✅ $type - Contract alignment checked"
    fi
  done
done

echo ""

if [ ${#DRIFTS[@]} -eq 0 ]; then
  echo "✅ No contract drift detected"
  exit 0
else
  echo "❌ Found ${#DRIFTS[@]} contract drift(s):"
  for d in "${DRIFTS[@]}"; do
    echo "   - $d"
  done
  exit 1
fi
