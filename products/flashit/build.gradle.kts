import com.ghatana.buildlogic.ProductPackValidationExtension

plugins {
    id("java-module")
    id("product-pack-validation")
}

group = "com.ghatana.flashit"
version = rootProject.version

dependencies {
    // Platform core dependencies
    api(project(":platform:java:core"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform-plugins:plugin-compliance"))
    implementation(project(":platform-plugins:plugin-audit-trail"))
    implementation(project(":platform-plugins:plugin-human-approval"))
    
    // Libraries
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)

    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    format("misc") {
        target(
            "build.gradle.kts",
            "settings.gradle.kts",
            ".gitignore",
            "README.md",
            "OWNER.md"
        )
        targetExclude("backend/**", "client/**", "build/**", ".gradle/**")
    }
}

tasks.matching { task ->
    task.name == "spotlessMisc" ||
        task.name == "spotlessMiscCheck" ||
        task.name == "spotlessXml" ||
        task.name == "spotlessXmlCheck"
}.configureEach {
    enabled = false
}

// Aggregate build task for all flashit components
tasks.register("buildAll") {
    group = "build"
    description = "Builds all flashit components (Java modules, TypeScript packages)"
    dependsOn(
        ":products:flashit:backend:agent:build",
        ":products:flashit:backend:gateway:build",
        ":products:flashit:client/web:build",
        ":products:flashit:libs/ts/shared:build",
        "build"
    )
}

configure<ProductPackValidationExtension> {
    productName.set("FlashIt")
    manifestFile.set(layout.projectDirectory.file("domain-pack-manifest.yaml"))
    policyPackTestPatterns.set(
        listOf(
            "com.ghatana.flashit.kernel.FlashItPackContractTest",
            "com.ghatana.flashit.kernel.FlashItKernelBoundaryContractTest"
        )
    )
    complianceSourceFile.set(layout.projectDirectory.file(
        "src/main/java/com/ghatana/flashit/kernel/policy/FlashItComplianceRulePack.java"
    ))
    complianceRulePrefix.set("FLASHIT-")
}
