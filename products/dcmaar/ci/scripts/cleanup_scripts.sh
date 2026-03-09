#!/bin/bash
set -e

echo "Creating archive directories..."
mkdir -p scripts/archive/test-data

# Move one-time fix scripts to archive
echo "Archiving one-time fix scripts..."
mv -v scripts/fix-proto-gomod.sh scripts/archive/ 2>/dev/null || echo "fix-proto-gomod.sh not found or already moved"
mv -v scripts/fix_proto_structure.sh scripts/archive/ 2>/dev/null || echo "fix_proto_structure.sh not found or already moved"
mv -v scripts/reorganize_proto.sh scripts/archive/ 2>/dev/null || echo "reorganize_proto.sh not found or already moved"

# Move evaluation script
echo "Archiving evaluation script..."
mv -v scripts/evaluate_capabilities.py scripts/archive/ 2>/dev/null || echo "evaluate_capabilities.py not found or already moved"

# Move test data generation scripts
echo "Archiving test data scripts..."
mv -v scripts/generate-test-data.sh scripts/archive/test-data/ 2>/dev/null || echo "generate-test-data.sh not found or already moved"
mv -v scripts/validate-test-data.sh scripts/archive/test-data/ 2>/dev/null || echo "validate-test-data.sh not found or already moved"

# Move test data directories if they exist
if [ -d "scripts/generate-test-data" ]; then
    mv -v scripts/generate-test-data scripts/archive/test-data/
fi

if [ -d "scripts/validate-test-data" ]; then
    mv -v scripts/validate-test-data scripts/archive/test-data/
fi

echo "Cleanup complete. Temporary scripts have been moved to scripts/archive/"
echo "You can review them there before deciding to delete them permanently."

# Create a README in the archive directory
cat > scripts/archive/README.md << 'EOL'
# Archived Scripts

This directory contains scripts that were used for one-time tasks or have been replaced by newer versions.

## Contents

- **Root directory**: One-time fix and migration scripts
- **test-data/**: Test data generation and validation scripts

## Last Updated
$(date)

## Notes
- These scripts are kept for reference but are not part of the active codebase.
- Before deleting any scripts, ensure they are not referenced elsewhere in the project.
- Consider documenting the purpose of each script in this README before archiving.
EOL

echo "Created scripts/archive/README.md with details about the archived scripts."
