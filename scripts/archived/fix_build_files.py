import re, os

files = [
    "/Users/samujjwal/Development/ghatana/products/finance/data-governance/build.gradle.kts",
    "/Users/samujjwal/Development/ghatana/products/finance/incident-management/build.gradle.kts",
    "/Users/samujjwal/Development/ghatana/products/finance/ledger-framework/build.gradle.kts",
    "/Users/samujjwal/Development/ghatana/products/finance/rules-engine/build.gradle.kts",
    "/Users/samujjwal/Development/ghatana/products/finance/operator-workflows/build.gradle.kts",
    "/Users/samujjwal/Development/ghatana/products/finance/regulator-portal/build.gradle.kts",
]

fixed_deps_block = """dependencies {
    // Platform core dependencies
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:audit"))

    // Kernel modules
    implementation(project(":platform:java:kernel:modules:authentication"))
    implementation(project(":platform:java:kernel:modules:config"))
    implementation(project(":platform:java:kernel:modules:event-store"))
    implementation(project(":platform:java:kernel:modules:audit"))

    // ActiveJ Promise
    implementation(libs.activej.promise)

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Micrometer for metrics
    implementation(libs.micrometer.core)

    // PostgreSQL
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}"""

boilerplate_deps_pattern = re.compile(
    r'dependencies \{.*?testImplementation\("io\.activej:activej-test"\)\n\}',
    re.DOTALL
)

for fpath in files:
    with open(fpath) as f:
        content = f.read()
    if boilerplate_deps_pattern.search(content):
        new_content = boilerplate_deps_pattern.sub(fixed_deps_block, content)
        with open(fpath, 'w') as f:
            f.write(new_content)
        print("FIXED: " + fpath)
    else:
        print("SKIPPED (pattern not found): " + fpath)
