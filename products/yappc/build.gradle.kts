plugins {
    id("java")
}

group = "com.ghatana.products.yappc"
version = "2.0.0"

description = "YAPPC — AI-Native Product Development Platform"

// Add dependencies for JSON and YAML processing
buildscript {
    dependencies {
        classpath("org.json:json:20231013")
        classpath("org.yaml:snakeyaml:2.0")
    }
}

// ============================================================================
// Shared Configuration for All Subprojects
// ============================================================================
subprojects {
    if (!file("$projectDir/src/main/java").exists() &&
        !file("$projectDir/src/main/kotlin").exists() &&
        !file("$projectDir/src/test/java").exists()) {
        return@subprojects
    }

    apply(plugin = "java-library")

    group = "com.ghatana.products.yappc"
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    // ========================================================================
    // Dependency Governance Constraints
    // ========================================================================
    configurations.all {
        // Ban direct LangChain4J — use platform:java:ai-integration instead
        exclude(group = "dev.langchain4j", module = "langchain4j")
        exclude(group = "dev.langchain4j", module = "langchain4j-open-ai")
        exclude(group = "dev.langchain4j", module = "langchain4j-anthropic")

        // Ban Spring Reactor — use ActiveJ Promise
        exclude(group = "io.projectreactor", module = "reactor-core")

        // Ban RxJava — use ActiveJ Promise
        exclude(group = "io.reactivex.rxjava3", module = "rxjava")
    }
}

// ============================================================================
// Agent Catalog Validation Task
// ============================================================================
tasks.register("validateAgentCatalog") {
    description = "Validates all agent YAML definitions against catalog schema, checks for dangling capability references and broken delegation chains"
    group = "verification"

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        val agentDefsDir = file("config/agents/definitions")
        val registryFile = file("config/agents/registry.yaml")
        val capabilitiesFile = file("config/agents/capabilities.yaml")
        val mappingsFile = file("config/agents/mappings.yaml")
        val eventRoutingFile = file("config/agents/event-routing.yaml")

        // Collect all agent YAML definition files
        val agentYamlFiles = agentDefsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }
            .toList()

        println("Validating ${agentYamlFiles.size} agent YAML definitions...")

        var errors = 0
        var warnings = 0
        val validLevels = setOf(1, 2, 3)

        // ---- Pass 1: Parse all definitions, collect IDs and validate structure ----
        data class AgentDef(
            val id: String,
            val file: java.io.File,
            val delegatesTo: List<String>,
            val escalatesTo: List<String>,
            val level: Int?
        )

        val agentDefs = mutableListOf<AgentDef>()
        val agentIds = mutableMapOf<String, String>()

        agentYamlFiles.forEach { file ->
            try {
                @Suppress("UNCHECKED_CAST")
                val doc = yaml.load<Map<String, Any>>(file.readText())
                    ?: throw IllegalArgumentException("Empty YAML")

                // Required top-level fields
                val requiredFields = listOf("id", "name", "version", "metadata")
                requiredFields.forEach { field ->
                    if (!doc.containsKey(field)) {
                        println("ERROR: ${file.relativeTo(projectDir)} missing required field: $field")
                        errors++
                    }
                }

                val id = doc["id"]?.toString()?.trim() ?: return@forEach

                // Duplicate ID check
                if (agentIds.containsKey(id)) {
                    println("ERROR: Duplicate agent ID '$id' in ${file.name} (first seen in ${agentIds[id]})")
                    errors++
                } else {
                    agentIds[id] = file.name
                }

                // Metadata.level validation
                @Suppress("UNCHECKED_CAST")
                val metadata = doc["metadata"] as? Map<String, Any>
                val level = (metadata?.get("level") as? Number)?.toInt()
                if (level != null && level !in validLevels) {
                    println("ERROR: ${file.name} has invalid metadata.level=$level (must be 1, 2, or 3)")
                    errors++
                }

                // Extract delegation references
                @Suppress("UNCHECKED_CAST")
                val delegation = doc["delegation"] as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val delegatesTo = (delegation?.get("can_delegate_to") as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim() } ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val escalatesTo = (delegation?.get("escalates_to") as? List<*>)
                    ?.mapNotNull { it?.toString()?.trim() } ?: emptyList()

                agentDefs.add(AgentDef(id, file, delegatesTo, escalatesTo, level))

            } catch (e: Exception) {
                println("ERROR: Failed to parse ${file.relativeTo(projectDir)}: ${e.message}")
                errors++
            }
        }

        // ---- Pass 2: Validate delegation chains resolve to known agent IDs ----
        val allKnownIds = agentIds.keys
        agentDefs.forEach { def ->
            def.delegatesTo.forEach { targetId ->
                if (targetId !in allKnownIds) {
                    println("ERROR: ${def.file.name} (${def.id}) delegates to unknown agent: $targetId")
                    errors++
                }
            }
            def.escalatesTo.forEach { targetId ->
                if (targetId !in allKnownIds) {
                    println("ERROR: ${def.file.name} (${def.id}) escalates to unknown agent: $targetId")
                    errors++
                }
            }
        }

        // ---- Pass 3: Validate registry references resolve to definition files ----
        if (registryFile.exists()) {
            val registryContent = registryFile.readText()
            val definitionRefs = Regex("definition:\\s+(.+\\.yaml)").findAll(registryContent)
            definitionRefs.forEach { match ->
                val defPath = file("config/agents/${match.groupValues[1]}")
                if (!defPath.exists()) {
                    println("ERROR: registry.yaml references non-existent definition: ${match.groupValues[1]}")
                    errors++
                }
            }
        } else {
            println("ERROR: registry.yaml not found")
            errors++
        }

        // ---- Pass 4: Validate capabilities.yaml and cross-reference mappings ----
        val knownCapabilities = mutableSetOf<String>()
        if (capabilitiesFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val capDoc = yaml.load<Map<String, Any>>(capabilitiesFile.readText())
                @Suppress("UNCHECKED_CAST")
                val caps = capDoc?.get("capabilities") as? List<Map<String, Any>> ?: emptyList()
                caps.forEach { cap ->
                    cap["id"]?.toString()?.let { knownCapabilities.add(it) }
                }
            } catch (e: Exception) {
                println("ERROR: Failed to parse capabilities.yaml: ${e.message}")
                errors++
            }
        } else {
            println("ERROR: capabilities.yaml not found")
            errors++
        }

        if (mappingsFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val mapDoc = yaml.load<Map<String, Any>>(mappingsFile.readText())
                @Suppress("UNCHECKED_CAST")
                val agents = mapDoc?.get("agents") as? List<Map<String, Any>> ?: emptyList()
                agents.forEach { agent ->
                    val agentId = agent["id"]?.toString() ?: "unknown"
                    @Suppress("UNCHECKED_CAST")
                    val caps = agent["capabilities"] as? List<*> ?: emptyList<Any>()
                    caps.forEach { cap ->
                        val capId = cap?.toString()
                        if (capId != null && capId !in knownCapabilities) {
                            println("ERROR: mappings.yaml agent '$agentId' references unknown capability: $capId")
                            errors++
                        }
                    }
                }
            } catch (e: Exception) {
                println("ERROR: Failed to parse mappings.yaml: ${e.message}")
                errors++
            }
        } else {
            println("ERROR: mappings.yaml not found")
            errors++
        }

        // ---- Pass 5: Validate event-routing agent IDs reference existing agents ----
        if (eventRoutingFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val routingDoc = yaml.load<Map<String, Any>>(eventRoutingFile.readText())
                @Suppress("UNCHECKED_CAST")
                val routes = routingDoc?.get("event_routing") as? List<Map<String, Any>> ?: emptyList()
                routes.forEach { route ->
                    val routeAgentId = route["agent_id"]?.toString()
                    if (routeAgentId != null && routeAgentId !in allKnownIds) {
                        val topic = route["topic"]?.toString() ?: "unknown"
                        println("WARNING: event-routing.yaml topic '$topic' routes to unknown agent: $routeAgentId")
                        warnings++
                    }
                }
            } catch (e: Exception) {
                println("ERROR: Failed to parse event-routing.yaml: ${e.message}")
                errors++
            }
        } else {
            println("ERROR: event-routing.yaml not found")
            errors++
        }

        // ---- Summary ----
        if (errors > 0) {
            throw GradleException("Agent catalog validation failed with $errors error(s) and $warnings warning(s)")
        }

        println("Agent catalog validation PASSED: ${agentYamlFiles.size} definitions, ${allKnownIds.size} unique IDs, $warnings warning(s), 0 errors")
    }
}

// ============================================================================
// Event Schema Validation Task
// ============================================================================
tasks.register("validateEventSchemas") {
    description = "Validates all event JSON schemas and ensures they are properly referenced"
    group = "verification"

    doLast {
        val eventSchemasDir = file("config/agents/event-schemas")
        val pipelineDir = file("config/pipelines")
        val validationDir = file("config/validation")

        // Collect all event schema files
        val eventSchemaFiles = eventSchemasDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json") }
            .toList()

        // Collect all pipeline files
        val pipelineFiles = pipelineDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }
            .toList()

        println("Validating ${eventSchemaFiles.size} event schemas and ${pipelineFiles.size} pipelines...")

        var errors = 0
        val schemaNames = mutableSetOf<String>()

        // Validate event schemas (simplified validation without external dependencies)
        eventSchemaFiles.forEach { file ->
            try {
                val content = file.readText()
                
                // Basic JSON structure validation - escape $ properly
                if (!content.contains("\$schema") || !content.contains("\$id") || 
                    !content.contains("\"title\"") || !content.contains("\"version\"") || !content.contains("\"type\"")) {
                    println("ERROR: ${file.name} missing required JSON Schema fields")
                    errors++
                }

                // Extract schema name from filename
                val schemaName = file.name.replace(".json", "")
                if (schemaNames.contains(schemaName)) {
                    println("ERROR: Duplicate schema name: $schemaName")
                    errors++
                } else {
                    schemaNames.add(schemaName)
                }

            } catch (e: Exception) {
                println("ERROR: Invalid JSON in ${file.name}: ${e.message}")
                errors++
            }
        }

        // Validate pipeline references
        pipelineFiles.forEach { file ->
            val content = file.readText()
            
            // Check if pipeline references valid schemas
            val schemaRefs = Regex("schema:\\s*([a-z0-9-]+-v\\d+)").findAll(content)
            schemaRefs.forEach { match ->
                val schemaName = match.groupValues[1]
                if (!schemaNames.contains(schemaName)) {
                    println("ERROR: ${file.name} references non-existent schema: $schemaName")
                    errors++
                }
            }
        }

        // Validate pipeline schema exists
        val pipelineSchemaFile = file("config/validation/pipeline-schema-v1.json")
        if (!pipelineSchemaFile.exists()) {
            println("ERROR: pipeline-schema-v1.json not found")
            errors++
        }

        if (errors > 0) {
            throw GradleException("Event schema validation failed with $errors error(s)")
        }

        println("Event schema validation PASSED: ${eventSchemaFiles.size} schemas, ${pipelineFiles.size} pipelines, 0 errors")
    }
}

// ============================================================================
// Pipeline Validation Task
// ============================================================================
tasks.register("validatePipelines") {
    description = "Validates all pipeline YAML definitions against pipeline schema"
    group = "verification"

    doLast {
        val pipelineDir = file("config/pipelines")
        val pipelineSchemaFile = file("config/validation/pipeline-schema-v1.json")

        if (!pipelineSchemaFile.exists()) {
            throw GradleException("Pipeline schema not found: config/validation/pipeline-schema-v1.json")
        }

        // Collect all pipeline files
        val pipelineFiles = pipelineDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }
            .toList()

        println("Validating ${pipelineFiles.size} pipeline definitions...")

        var errors = 0
        val pipelineNames = mutableSetOf<String>()

        pipelineFiles.forEach { file ->
            try {
                val content = file.readText()
                val yaml = org.yaml.snakeyaml.Yaml()
                val pipeline = yaml.load<Map<String, Any>>(content)

                // Check required top-level fields
                val requiredFields = listOf("apiVersion", "kind", "metadata", "spec")
                requiredFields.forEach { field ->
                    if (!pipeline.containsKey(field)) {
                        println("ERROR: ${file.name} missing required field: $field")
                        errors++
                    }
                }

                // Extract pipeline name
                @Suppress("UNCHECKED_CAST")
                val metadata = pipeline["metadata"] as? Map<String, Any>
                val name = metadata?.get("name") as? String
                if (name == null) {
                    println("ERROR: ${file.name} missing metadata.name")
                    errors++
                } else {
                    if (pipelineNames.contains(name)) {
                        println("ERROR: Duplicate pipeline name: $name")
                        errors++
                    } else {
                        pipelineNames.add(name)
                    }
                }

                // Validate spec structure
                @Suppress("UNCHECKED_CAST")
                val spec = pipeline["spec"] as? Map<String, Any>
                if (spec != null) {
                    if (!spec.containsKey("operators") || (spec["operators"] as? List<*>)?.isEmpty() == true) {
                        println("ERROR: ${file.name} must have at least one operator")
                        errors++
                    }
                }

            } catch (e: Exception) {
                println("ERROR: Invalid YAML in ${file.name}: ${e.message}")
                errors++
            }
        }

        if (errors > 0) {
            throw GradleException("Pipeline validation failed with $errors error(s)")
        }

        println("Pipeline validation PASSED: ${pipelineFiles.size} pipelines, ${pipelineNames.size} unique names, 0 errors")
    }
}

// ============================================================================
// Lifecycle Config Validation Task (Dimension 8.1.2)
// Validates cross-references between stages.yaml and transitions.yaml:
//   - Every stage ID in transitions.yaml must exist in stages.yaml
//   - Every artifact referenced in transitions.yaml is non-empty
// ============================================================================
tasks.register("validateLifecycleConfig") {
    description = "Validates lifecycle stages.yaml and transitions.yaml for structural integrity and cross-references"
    group = "verification"

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        val stagesFile     = file("config/lifecycle/stages.yaml")
        val transitionsFile = file("config/lifecycle/transitions.yaml")

        var errors = 0

        // ---- Load stages ----
        if (!stagesFile.exists()) {
            throw GradleException("config/lifecycle/stages.yaml not found")
        }
        @Suppress("UNCHECKED_CAST")
        val stagesDoc = yaml.load<Map<String, Any>>(stagesFile.readText())
        @Suppress("UNCHECKED_CAST")
        val stagesList = stagesDoc?.get("stages") as? List<Map<String, Any>> ?: emptyList()
        val knownStageIds = mutableSetOf<String>()
        stagesList.forEach { stage ->
            val id = stage["id"]?.toString()?.trim()
            if (id.isNullOrBlank()) {
                println("ERROR: stages.yaml entry missing 'id' field")
                errors++
            } else {
                if (!knownStageIds.add(id)) {
                    println("ERROR: Duplicate stage id '$id' in stages.yaml")
                    errors++
                }
            }
        }
        println("Loaded ${knownStageIds.size} stage IDs: $knownStageIds")

        // ---- Load and validate transitions ----
        if (!transitionsFile.exists()) {
            throw GradleException("config/lifecycle/transitions.yaml not found")
        }
        @Suppress("UNCHECKED_CAST")
        val transitionsDoc = yaml.load<Map<String, Any>>(transitionsFile.readText())
        @Suppress("UNCHECKED_CAST")
        val transitionsList = transitionsDoc?.get("transitions") as? List<Map<String, Any>> ?: emptyList()

        transitionsList.forEachIndexed { idx, t ->
            val from = t["from"]?.toString()?.trim()
            val to   = t["to"]?.toString()?.trim()

            if (from.isNullOrBlank()) {
                println("ERROR: transitions.yaml entry #$idx missing 'from' field")
                errors++
            } else if (from !in knownStageIds) {
                println("ERROR: transitions.yaml entry #$idx 'from: $from' is not a known stage ID")
                errors++
            }

            if (to.isNullOrBlank()) {
                println("ERROR: transitions.yaml entry #$idx missing 'to' field")
                errors++
            } else if (to !in knownStageIds) {
                println("ERROR: transitions.yaml entry #$idx 'to: $to' is not a known stage ID")
                errors++
            }

            // Validate required_artifacts entries are non-empty strings
            @Suppress("UNCHECKED_CAST")
            val artifacts = t["required_artifacts"] as? List<*> ?: emptyList<Any>()
            artifacts.forEachIndexed { ai, artifact ->
                if (artifact?.toString().isNullOrBlank()) {
                    println("ERROR: transitions.yaml entry #$idx required_artifacts[$ai] is blank")
                    errors++
                }
            }
        }

        if (errors > 0) {
            throw GradleException("Lifecycle config validation failed with $errors error(s)")
        }

        println("Lifecycle config validation PASSED: ${knownStageIds.size} stages, ${transitionsList.size} transitions, 0 errors")
    }
}

// ============================================================================
// Workflow Config Validation Task (Dimension 8.1.3)
// Validates canonical-workflows.yaml:
//   - All stage references resolve to known stage IDs from stages.yaml
//   - All required top-level fields present (id, name, stages/steps)
// ============================================================================
tasks.register("validateWorkflowConfig") {
    description = "Validates canonical-workflows.yaml stage references resolve to known lifecycle stages"
    group = "verification"

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        val stagesFile   = file("config/lifecycle/stages.yaml")
        val workflowFile = file("config/workflows/canonical-workflows.yaml")

        var errors = 0

        // ---- Load known stage IDs ----
        if (!stagesFile.exists()) {
            throw GradleException("config/lifecycle/stages.yaml not found — run validateLifecycleConfig first")
        }
        @Suppress("UNCHECKED_CAST")
        val stagesDoc = yaml.load<Map<String, Any>>(stagesFile.readText())
        @Suppress("UNCHECKED_CAST")
        val stagesList = stagesDoc?.get("stages") as? List<Map<String, Any>> ?: emptyList()
        val knownStageIds = stagesList.mapNotNull { it["id"]?.toString()?.trim() }.toSet()

        // ---- Load and validate workflows ----
        if (!workflowFile.exists()) {
            throw GradleException("config/workflows/canonical-workflows.yaml not found")
        }
        @Suppress("UNCHECKED_CAST")
        val workflowDoc = yaml.load<Map<String, Any>>(workflowFile.readText())

        // Workflows may be structured as top-level map entries or a list
        @Suppress("UNCHECKED_CAST")
        val workflowsList = when (val wfl = workflowDoc?.get("workflows")) {
            is List<*> -> wfl.filterIsInstance<Map<String, Any>>()
            is Map<*, *> -> (wfl as Map<String, Map<String, Any>>).values.toList()
            else -> {
                // Try treating root keys as workflow definitions
                workflowDoc?.entries
                    ?.filter { it.value is Map<*, *> }
                    ?.map { @Suppress("UNCHECKED_CAST") it.value as Map<String, Any> }
                    ?: emptyList()
            }
        }

        println("Validating ${workflowsList.size} canonical workflows...")

        workflowsList.forEachIndexed { idx, wf ->
            val wfId = wf["id"]?.toString() ?: wf["name"]?.toString() ?: "#$idx"

            // Required fields
            listOf("id", "name").forEach { field ->
                if (!wf.containsKey(field)) {
                    println("WARNING: workflow '$wfId' missing recommended field: $field")
                }
            }

            // Validate stage/step references
            @Suppress("UNCHECKED_CAST")
            val steps = (wf["stages"] ?: wf["steps"] ?: wf["phases"]) as? List<*> ?: emptyList<Any>()
            steps.forEachIndexed { si, step ->
                val stageRef = when (step) {
                    is Map<*, *> -> step["stage"]?.toString() ?: step["id"]?.toString()
                    is String    -> step
                    else         -> null
                }
                if (stageRef != null && stageRef !in knownStageIds) {
                    println("ERROR: workflow '$wfId' step[$si] references unknown stage: $stageRef")
                    errors++
                }
            }
        }

        if (errors > 0) {
            throw GradleException("Workflow config validation failed with $errors error(s)")
        }

        println("Workflow config validation PASSED: ${workflowsList.size} workflows, 0 errors")
    }
}

// ============================================================================
// Policy Config Validation Task (Dimension 8.1.4 / 4.5)
// Validates all YAML files in config/policies/:
//   - Required fields present (id, version, rules)
//   - Duplicate policy IDs detected
// ============================================================================
tasks.register("validatePolicyConfig") {
    description = "Validates all policy YAML files in config/policies/ for structural correctness and ID uniqueness"
    group = "verification"

    doLast {
        val yaml      = org.yaml.snakeyaml.Yaml()
        val policyDir = file("config/policies")
        var errors    = 0

        if (!policyDir.exists()) {
            throw GradleException("config/policies/ directory not found")
        }

        val policyFiles = policyDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }
            .toList()

        if (policyFiles.isEmpty()) {
            throw GradleException("No policy YAML files found in config/policies/")
        }

        println("Validating ${policyFiles.size} policy YAML file(s)...")

        val seenPolicyIds = mutableSetOf<String>()

        policyFiles.forEach { file ->
            try {
                @Suppress("UNCHECKED_CAST")
                val doc = yaml.load<Map<String, Any>>(file.readText())
                    ?: throw IllegalArgumentException("Empty YAML")

                @Suppress("UNCHECKED_CAST")
                val policies = doc["policies"] as? List<Map<String, Any>> ?: emptyList()

                policies.forEachIndexed { idx, policy ->
                    val id = policy["id"]?.toString()?.trim()
                    if (id.isNullOrBlank()) {
                        println("ERROR: ${file.name} policy[$idx] missing 'id' field")
                        errors++
                    } else if (!seenPolicyIds.add(id)) {
                        println("ERROR: ${file.name} duplicate policy ID: $id")
                        errors++
                    }

                    if (!policy.containsKey("version")) {
                        println("WARNING: ${file.name} policy '${id ?: idx}' missing 'version' field")
                    }

                    @Suppress("UNCHECKED_CAST")
                    val rules = policy["rules"] as? List<*> ?: emptyList<Any>()
                    if (rules.isEmpty()) {
                        println("ERROR: ${file.name} policy '${id ?: idx}' has no rules")
                        errors++
                    }
                }

            } catch (e: Exception) {
                println("ERROR: Failed to parse ${file.name}: ${e.message}")
                errors++
            }
        }

        if (errors > 0) {
            throw GradleException("Policy config validation failed with $errors error(s)")
        }

        println("Policy config validation PASSED: ${policyFiles.size} file(s), ${seenPolicyIds.size} policies, 0 errors")
    }
}

tasks.findByName("check")?.dependsOn("validateAgentCatalog")
tasks.findByName("check")?.dependsOn("validateEventSchemas")
tasks.findByName("check")?.dependsOn("validatePipelines")
tasks.findByName("check")?.dependsOn("validateLifecycleConfig")
tasks.findByName("check")?.dependsOn("validateWorkflowConfig")
tasks.findByName("check")?.dependsOn("validatePolicyConfig")
