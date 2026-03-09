#!/bin/bash
set -e

# Script to generate basic build.gradle.kts for migrated modules

MODULES=(
    "platform/java/runtime"
    "platform/java/plugin"
    "platform/java/config"
    "products/aep/platform/java/agents"
    "products/aep/platform/java/operators"
    "products/aep/platform/java/events"
    "products/aep/platform/java/workflow"
    "products/data-cloud/platform/java/governance"
    "products/data-cloud/platform/java/ingestion"
    "products/data-cloud/platform/java/storage"
    "products/shared-services/platform/java/ai"
    "products/shared-services/platform/java/connectors"
    "products/security-gateway/platform/java/auth"
    "products/flashit/platform/java/context"
)

TEMPLATE=$(cat <<EOF
plugins {
    id("java-library")
}

group = "com.ghatana.products"

dependencies {
    // Platform Dependencies
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    
    // Todo: Add specific dependencies
    // implementation("io.activej:activej-promise:6.0-beta2")
}
EOF
)

PLATFORM_TEMPLATE=$(cat <<EOF
plugins {
    id("java-library")
}

group = "com.ghatana.platform"

dependencies {
    implementation(project(":platform:java:core"))
    
    // Todo: Add specific dependencies
}
EOF
)

for mod in "${MODULES[@]}"; do
    FILE="$mod/build.gradle.kts"
    
    if [ ! -f "$FILE" ]; then
        echo "Creating $FILE"
        
        # Check if it's a platform module to use correct group/template
        if [[ "$mod" == platform* ]]; then
             echo "$PLATFORM_TEMPLATE" > "$FILE"
        else
             echo "$TEMPLATE" > "$FILE"
        fi
        
    else
        echo "Skipping $FILE (already exists)"
    fi
done

echo "Build files generation complete."
