#!/bin/bash
set -e

# Script to generate build.gradle.kts for Consolidated Product Platform Modules

MODULES=(
    "products/aep/platform/java"
    "products/data-cloud/platform/java"
    "products/shared-services/platform/java"
    "products/security-gateway/platform/java"
    "products/flashit/platform/java"
)

TEMPLATE=$(cat <<EOF
plugins {
    id("java-library")
}

group = "com.ghatana.products"

dependencies {
    // Platform Dependencies - All products get the full platform capability
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:auth"))
    implementation(project(":platform:java:config"))
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
}
EOF
)

for mod in "${MODULES[@]}"; do
    FILE="$mod/build.gradle.kts"
    
    echo "Creating/Overwriting $FILE"
    echo "$TEMPLATE" > "$FILE"
done

echo "Build files generation complete."
