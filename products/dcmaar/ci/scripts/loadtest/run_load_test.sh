#!/bin/bash

set -e

# Default values
EVENTS=1000
CONCURRENCY=10
SERVER="localhost:50051"
TLS=false
CA_FILE=""
OUTPUT_DIR="./loadtest_results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="${OUTPUT_DIR}/loadtest_${TIMESTAMP}.md"

# Parse command line arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -e|--events)
      EVENTS="$2"
      shift
      shift
      ;;
    -c|--concurrency)
      CONCURRENCY="$2"
      shift
      shift
      ;;
    -s|--server)
      SERVER="$2"
      shift
      shift
      ;;
    --tls)
      TLS=true
      shift
      ;;
    --ca)
      CA_FILE="$2"
      shift
      shift
      ;;
    -o|--output-dir)
      OUTPUT_DIR="$2"
      shift
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [options]"
      echo "Options:"
      echo "  -e, --events NUM       Number of events to send (default: 1000)"
      echo "  -c, --concurrency NUM  Number of concurrent workers (default: 10)"
      echo "  -s, --server ADDR      Server address (default: localhost:50051)"
      echo "  --tls                  Enable TLS (default: false)"
      echo "  --ca FILE              CA certificate file (required if --tls is used)"
      echo "  -o, --output-dir DIR   Directory to store results (default: ./loadtest_results)"
      echo "  -h, --help             Show this help message"
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Validate TLS configuration
if [ "$TLS" = true ] && [ -z "$CA_FILE" ]; then
  echo "Error: --ca option is required when --tls is used"
  exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Build the load test binary if it doesn't exist
if [ ! -f "./event-loadtest" ]; then
  echo "Building load test binary..."
  go build -o event-loadtest .
  if [ $? -ne 0 ]; then
    echo "Failed to build load test binary"
    exit 1
  fi
fi

# Prepare command line arguments
ARGS=(
  "--events" "$EVENTS"
  "--concurrency" "$CONCURRENCY"
  "--server" "$SERVER"
)

if [ "$TLS" = true ]; then
  ARGS+=("--tls")
  if [ -n "$CA_FILE" ]; then
    ARGS+=("--ca" "$CA_FILE")
  fi
fi

# Run the load test
echo "Starting load test with ${EVENTS} events and ${CONCURRENCY} concurrent workers..."
echo "Server: ${SERVER}"
echo "TLS: ${TLS}"
if [ -n "$CA_FILE" ]; then
  echo "CA Certificate: ${CA_FILE}"
fi
echo "Output Directory: ${OUTPUT_DIR}"

echo ""
./event-loadtest "${ARGS[@]}" 2>&1 | tee "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log"

# Generate report
{
  echo "# Load Test Report"
  echo "**Date:** $(date)"
  echo "**Duration:** $(grep 'Duration:' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1)"
  echo "**Total Events:** $(grep 'Total Events:' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1 | awk '{print $3}')"
  echo "**Success Rate:** $(grep 'Success:' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1 | awk '{print $2, $3}' | tr -d '()%')"
  echo "**Failure Rate:** $(grep 'Failed:' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1 | awk '{print $2, $3}' | tr -d '()%')"
  echo "**Events Per Second:** $(grep 'Total Events:' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1 | awk -F'[()]' '{print $2}' | awk '{print $1}')"
  echo "\n## Test Configuration"
  echo "- Events: ${EVENTS}"
  echo "- Concurrency: ${CONCURRENCY}"
  echo "- Server: ${SERVER}"
  echo "- TLS: ${TLS}"
  if [ -n "${CA_FILE}" ]; then
    echo "- CA File: ${CA_FILE}"
  fi
  echo -e "\n## Results Summary"
  echo '```'
  grep -A 5 'Load Test Results' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -n 6
  echo '```'
  echo -e "\n## Recommendations"
  echo "1. Review the detailed log for any errors or warnings"
  echo "2. Check system metrics during the test period"
  echo "3. Consider adjusting rate limits if needed"
} > "${REPORT_FILE}"

# Generate a summary
SUCCESS_COUNT=$(grep -oP 'Success: \K\d+' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1)
FAILED_COUNT=$(grep -oP 'Failed: \K\d+' "${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log" | tail -1)
TOTAL_EVENTS=$((SUCCESS_COUNT + FAILED_COUNT))

if [ -n "$SUCCESS_COUNT" ] && [ -n "$FAILED_COUNT" ]; then
  echo ""
  echo "=== Test Summary ==="
  echo "Total Events: $TOTAL_EVENTS"
  echo "Success: $SUCCESS_COUNT"
  echo "Failed: $FAILED_COUNT"
  if [ $TOTAL_EVENTS -gt 0 ]; then
    SUCCESS_RATE=$(echo "scale=2; $SUCCESS_COUNT * 100 / $TOTAL_EVENTS" | bc)
    echo "Success Rate: ${SUCCESS_RATE}%"
  fi
fi

echo "Load test completed. Report generated: ${REPORT_FILE}"
echo "Test completed. Results saved to ${OUTPUT_DIR}/loadtest_${TIMESTAMP}.log"
