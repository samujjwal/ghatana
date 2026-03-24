# Example Agent Definitions
# This directory contains schema-driven agent configurations
# that replace the previous Java class-based approach.

# To migrate an existing agent:
# 1. Run: ./gradlew :core:agents:runMigration --args="--agent=YourAgentName"
# 2. Review and customize the generated YAML
# 3. Test with: ./gradlew :core:agents:runValidation --args="--agent=your.agent.id"

# See DEVELOPER_MIGRATION_GUIDE.md for detailed instructions.
