#!/bin/bash
set -e

# Create .git/hooks directory if it doesn't exist
mkdir -p .git/hooks

# Create pre-commit hook
cat > .git/hooks/pre-commit << 'EOL'
#!/bin/bash

# Run proto validation
./scripts/pre-commit-proto || exit 1

# Continue with other pre-commit hooks if they exist
if [ -f .git/hooks/pre-commit.local ]; then
  ./.git/hooks/pre-commit.local "$@" || exit 1
fi

exit 0
EOL

# Make the hook executable
chmod +x .git/hooks/pre-commit

echo "✅ Git hooks set up successfully"
echo "   Proto validation will run before each commit"
