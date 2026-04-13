#!/usr/bin/env bash
# Setup script to add ttr to PATH

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "Add this to your ~/.bashrc or ~/.zshrc:"
echo ""
echo "export PATH=\"\$PATH:${PROJECT_ROOT}/bin\""
echo ""
echo "Or run this command now:"
echo "  export PATH=\"\$PATH:${PROJECT_ROOT}/bin\""
echo ""
echo "Then you can use 'ttr' from anywhere:"
echo "  ttr dev"
echo "  ttr test"
echo "  ttr doctor"
