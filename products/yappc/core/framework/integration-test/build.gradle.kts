/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("java")
    id("application")
}

dependencies {
    // Framework dependencies
    implementation(project(":products:yappc:core:framework"))

    // Logging
    implementation(libs.logback.classic)

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation(libs.jackson.databind)
}

application {
    mainClass.set("com.ghatana.yappc.test.FrameworkIntegrationTest")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}
