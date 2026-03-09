#!/bin/sh
# Performance measurement utilities for Git hooks

# Start a performance timer
PERF_START_TIME=0
PERF_START_MEM=0
PERF_START_CPU=0

# Get current time in milliseconds
current_time_ms() {
  date +%s%3N
}

# Get current memory usage in KB
current_memory_kb() {
  if [ -f /proc/self/status ]; then
    grep VmRSS /proc/self/status | awk '{print $2}'
  else
    # Fallback for non-Linux
    ps -o rss= -p $$ | awk '{print $1}'
  fi
}

# Get current CPU time in milliseconds
current_cpu_ms() {
  if [ -f /proc/self/stat ]; then
    # Sum user and system time from /proc/self/stat
    read -r user sys < <(awk '{print $14+$15+$16+$17}' /proc/self/stat 2>/dev/null)
    echo $((user + sys * 10))  # Convert jiffies to ms (assuming 100Hz clock)
  else
    # Fallback for non-Linux
    ps -o time= -p $$ | awk -F: '{ print ($1 * 60000) + ($2 * 1000) + int($3 * 1000) }'
  fi
}

# Start performance measurement
start_perf_measurement() {
  PERF_START_TIME=$(current_time_ms)
  PERF_START_MEM=$(current_memory_kb)
  PERF_START_CPU=$(current_cpu_ms)
  export PERF_START_TIME PERF_START_MEM PERF_START_CPU
}

# End performance measurement and log results
end_perf_measurement() {
  local hook_name=$1
  local end_time
  local end_mem
  local end_cpu
  local duration_ms
  local mem_used_kb
  local cpu_used_ms
  
  end_time=$(current_time_ms)
  end_mem=$(current_memory_kb)
  end_cpu=$(current_cpu_ms)
  
  duration_ms=$((end_time - PERF_START_TIME))
  mem_used_kb=$((end_mem - PERF_START_MEM))
  cpu_used_ms=$((end_cpu - PERF_START_CPU))
  
  # Log to file
  mkdir -p ".husky/perf-logs"
  local log_file=".husky/perf-logs/$(date +%Y%m%d).log"
  
  echo "[$(date -u +"%Y-%m-%dT%H:%M:%SZ")] hook=$hook_name duration_ms=$duration_ms memory_kb=$mem_used_kb cpu_ms=$cpu_used_ms" >> "$log_file"
  
  # Output to console in CI or if debug is enabled
  if [ -n "$CI" ] || [ "$HUSKY_DEBUG" = "1" ]; then
    echo "[perf] $hook_name completed in ${duration_ms}ms (CPU: ${cpu_used_ms}ms, Memory: ${mem_used_kb}KB)"
  fi
}

# Check if command exists
command_exists() {
  command -v "$1" >/dev/null 2>&1
}

# Check dependencies
check_dependencies() {
  local missing=0
  
  for cmd in $@; do
    if ! command_exists "$cmd"; then
      echo "Error: Required command '$cmd' is not installed"
      missing=$((missing + 1))
    fi
  done
  
  if [ $missing -gt 0 ]; then
    echo "Error: $missing required dependencies are missing"
    return 1
  fi
  
  return 0
}

# Source this file to make functions available
# Example usage in hooks:
# . "$(dirname -- "$0")/_/performance.sh"
# start_perf_measurement
# # Your hook commands here
# end_perf_measurement "hook-name"
