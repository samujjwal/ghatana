#!/bin/bash
set -e

# Create necessary directories
mkdir -p docs/reports
mkdir -p docs/development/build
mkdir -p docs/development/guidelines
mkdir -p docs/design/features

# Move reports
mv BUILD_STATUS_REPORT.md docs/reports/
mv CAPABILITY_1_COMPLETION_REPORT.md docs/reports/
mv CAPABILITY_2_IMPLEMENTATION_REPORT.md docs/reports/
mv CAPABILITY_6_7_COMPLETION_REPORT.md docs/reports/
mv HORIZONTAL_SLICE_AI_PLAN_5_COMPLETION_REPORT.md docs/reports/
mv PROJECT_ANALYSIS_COMPLETION_REPORT.md docs/reports/
mv PROJECT_ANALYSIS_REPORT.md docs/reports/
mv PROJECT_FIXES_SUMMARY.md docs/reports/

# Move build-related files
mv BUILD.md docs/development/build/

# Move development files
mv DEVELOPMENT.md docs/development/
mv TESTING.md docs/development/

# Move guidelines
mv CONTRIBUTING.md docs/development/guidelines/
mv coding-guidelines.md docs/development/guidelines/
mv RELEASE_CHECKLIST.md docs/development/guidelines/

# Move design docs
mv dcmaar_features_comms_v2.md docs/design/features/
mv first-epic.md docs/design/
mv requirements_business_value_v5.md docs/design/

# Create symlinks for backward compatibility
ln -sf docs/development/guidelines/CONTRIBUTING.md .
ln -sf docs/development/guidelines/coding-guidelines.md .
ln -sf docs/development/TESTING.md .

# Create a README in the root explaining the documentation structure
cat > docs/README.md << 'EOL'
# Documentation Structure

This directory contains all project documentation organized into the following categories:

- `architecture/`: System architecture and technical design documents
- `components/`: Component-specific documentation
- `design/`: Design documents, specifications, and feature documentation
- `development/`: Development guides, setup instructions, and coding guidelines
  - `build/`: Build system documentation
  - `guidelines/`: Coding standards and contribution guidelines
- `operations/`: Deployment, monitoring, and operations documentation
- `reference/`: Technical reference material
- `reports/`: Project reports and analysis
- `templates/`: Documentation templates

## Root-Level Documentation

Some key documentation is kept at the root level for visibility:
- `README.md`: Project overview and getting started
- `CHANGELOG.md`: Project changelog
- `CONTRIBUTING.md` -> `docs/development/guidelines/CONTRIBUTING.md`
- `coding-guidelines.md` -> `docs/development/guidelines/coding-guidelines.md`
- `TESTING.md` -> `docs/development/TESTING.md`

## Adding New Documentation

1. Place new documentation in the most appropriate subdirectory
2. Update the root README.md if you add a new category
3. For new components, create a new directory under `components/`
4. Follow the templates in `templates/` for consistency
EOL

echo "Documentation organization complete. Please review the changes and commit them."
