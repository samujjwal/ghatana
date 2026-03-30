/**
 * YAPPC Config Validation Tasks
 *
 * Extracted from the root build.gradle.kts to keep it concise.
 * Applied via: apply(from = "gradle/yappc-validations.gradle.kts")
 *
 * Tasks registered here:
 *   - validateAgentCatalog:   Agent YAML definitions, registry, capabilities, mappings, event-routing
 *   - validateEventSchemas:   Event JSON schemas and pipeline schema references
 *   - validatePipelines:      Pipeline YAML structural correctness
 *   - validateLifecycleConfig: stages.yaml / transitions.yaml cross-reference integrity
 *   - validateWorkflowConfig:  canonical-workflows.yaml stage references
 *   - validatePolicyConfig:    Policy YAML files (id uniqueness, rules presence)
 *
 * All tasks are added to the "verification" group and wired into the "check" lifecycle.
 */

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("org.yaml:snakeyaml:2.0")
    }
}

// ============================================================================
// Agent Catalog Validation Task
// ============================================================================
tasks.register("validateAgentCatalog") {
    description = "Validates all agent YAML definitions against catalog schema, checks for dangling capability references and broken delegation chains"
    group = "verification"

    val projectDir = layout.projectDirectory.asFile
    val agentDefsDir = layout.projectDirectory.dir("config/agents/definitions").asFile
    val registryFile = layout.projectDirectory.file("config/agents/registry.yaml").asFile
    val capabilitiesFile = layout.projectDirectory.file("config/agents/capabilities.yaml").asFile
    val mappingsFile = layout.projectDirectory.file("config/agents/mappings.yaml").asFile
    val eventRoutingFile = layout.projectDirectory.file("config/agents/event-routing.yaml").asFile
    val failOnUnregisteredDefs = System.getenv("YAPPC_FAIL_ON_UNREGISTERED_AGENT_DEFS")
        ?.trim()
        ?.equals("true", ignoreCase = true)
        ?: false

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()

        val agentYamlFiles = agentDefsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }
            .toList()

        println("Validating ${agentYamlFiles.size} agent YAML definitions...")

        var errors = 0
        var warnings = 0
        val validLevels = setOf(1, 2, 3)

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

                listOf("id", "name", "version", "metadata").forEach { field ->
                    if (!doc.containsKey(field)) {
                        println("ERROR: ${file.relativeTo(projectDir)} missing required field: $field")
                        errors++
                    }
                }

                val id = doc["id"]?.toString()?.trim() ?: return@forEach

                if (agentIds.containsKey(id)) {
                    println("ERROR: Duplicate agent ID '$id' in ${file.name} (first seen in ${agentIds[id]})")
                    errors++
                } else {
                    agentIds[id] = file.name
                }

                @Suppress("UNCHECKED_CAST")
                val metadata = doc["metadata"] as? Map<String, Any>
                val level = (metadata?.get("level") as? Number)?.toInt()
                if (level != null && level !in validLevels) {
                    println("ERROR: ${file.name} has invalid metadata.level=$level (must be 1, 2, or 3)")
                    errors++
                }

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

        val allKnownIds = agentIds.keys
        agentDefs.forEach { def ->
            (def.delegatesTo + def.escalatesTo).forEach { targetId ->
                if (targetId !in allKnownIds) {
                    println("ERROR: ${def.file.name} (${def.id}) references unknown agent: $targetId")
                    errors++
                }
            }
        }

        val explicitDefinitionPaths = mutableSetOf<String>()
        val coveredDefinitionDirectories = mutableSetOf<String>()

        if (registryFile.exists()) {
            val registryText = registryFile.readText()
            Regex("definition:\\s+(.+\\.yaml)").findAll(registryText).forEach { match ->
                val definitionPath = match.groupValues[1].trim()
                explicitDefinitionPaths.add(definitionPath)

                val defPath = File(projectDir, "config/agents/$definitionPath")
                if (!defPath.exists()) {
                    println("ERROR: registry.yaml references non-existent definition: $definitionPath")
                    errors++
                }
            }

            try {
                @Suppress("UNCHECKED_CAST")
                val registryDoc = yaml.load<Map<String, Any>>(registryText)

                @Suppress("UNCHECKED_CAST")
                val phases = registryDoc["phases"] as? Map<String, Any> ?: emptyMap()
                phases.values
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.endsWith("/") }
                    .forEach { coveredDefinitionDirectories.add(it) }

                @Suppress("UNCHECKED_CAST")
                val domains = registryDoc["domains"] as? Map<String, Any> ?: emptyMap()
                domains.values
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.endsWith("/") }
                    .forEach { coveredDefinitionDirectories.add(it) }
            } catch (e: Exception) {
                println("ERROR: Failed to parse registry.yaml for directory coverage: ${e.message}")
                errors++
            }

            val agentConfigRoot = File(projectDir, "config/agents")
            val unregisteredDefinitions = agentYamlFiles
                .map { it.relativeTo(agentConfigRoot).invariantSeparatorsPath }
                .filter { relativePath ->
                    val coveredByExplicitEntry = relativePath in explicitDefinitionPaths
                    val coveredByDirectory = coveredDefinitionDirectories.any { directory ->
                        relativePath.startsWith(directory)
                    }
                    !coveredByExplicitEntry && !coveredByDirectory
                }

            if (unregisteredDefinitions.isNotEmpty()) {
                val sample = unregisteredDefinitions.take(20)
                val message = buildString {
                    append("Unregistered agent definitions detected (")
                    append(unregisteredDefinitions.size)
                    append("). Sample: ")
                    append(sample.joinToString(", "))
                    append(". Add them to registry.yaml or phase/domain directories.")
                }

                if (failOnUnregisteredDefs) {
                    println("ERROR: $message")
                    errors++
                } else {
                    println("WARNING: $message")
                    warnings++
                }
            }
        } else {
            println("ERROR: registry.yaml not found"); errors++
        }

        val knownCapabilities = mutableSetOf<String>()
        if (capabilitiesFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val caps = (yaml.load<Map<String, Any>>(capabilitiesFile.readText())
                    ?.get("capabilities") as? List<Map<String, Any>>) ?: emptyList()
                caps.forEach { cap -> cap["id"]?.toString()?.let { knownCapabilities.add(it) } }
            } catch (e: Exception) { println("ERROR: Failed to parse capabilities.yaml: ${e.message}"); errors++ }
        } else { println("ERROR: capabilities.yaml not found"); errors++ }

        if (mappingsFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val agents = (yaml.load<Map<String, Any>>(mappingsFile.readText())
                    ?.get("agents") as? List<Map<String, Any>>) ?: emptyList()
                agents.forEach { agent ->
                    val agentId = agent["id"]?.toString() ?: "unknown"
                    @Suppress("UNCHECKED_CAST")
                    (agent["capabilities"] as? List<*> ?: emptyList<Any>()).forEach { cap ->
                        val capId = cap?.toString()
                        if (capId != null && capId !in knownCapabilities) {
                            println("ERROR: mappings.yaml agent '$agentId' references unknown capability: $capId")
                            errors++
                        }
                    }
                }
            } catch (e: Exception) { println("ERROR: Failed to parse mappings.yaml: ${e.message}"); errors++ }
        } else { println("ERROR: mappings.yaml not found"); errors++ }

        if (eventRoutingFile.exists()) {
            try {
                @Suppress("UNCHECKED_CAST")
                val routes = (yaml.load<Map<String, Any>>(eventRoutingFile.readText())
                    ?.get("event_routing") as? List<Map<String, Any>>) ?: emptyList()
                routes.forEach { route ->
                    val routeAgentId = route["agent_id"]?.toString()
                    if (routeAgentId != null && routeAgentId !in allKnownIds) {
                        println("WARNING: event-routing.yaml topic '${route["topic"] ?: "unknown"}' routes to unknown agent: $routeAgentId")
                        warnings++
                    }
                }
            } catch (e: Exception) { println("ERROR: Failed to parse event-routing.yaml: ${e.message}"); errors++ }
        } else { println("ERROR: event-routing.yaml not found"); errors++ }

        if (errors > 0) throw GradleException("Agent catalog validation failed with $errors error(s) and $warnings warning(s)")
        println("Agent catalog validation PASSED: ${agentYamlFiles.size} definitions, ${allKnownIds.size} unique IDs, $warnings warning(s), 0 errors")
    }
}

// ============================================================================
// Event Schema Validation Task
// ============================================================================
tasks.register("validateEventSchemas") {
    description = "Validates all event JSON schemas and ensures they are properly referenced by pipelines"
    group = "verification"

    val eventSchemaDir = layout.projectDirectory.dir("config/agents/event-schemas").asFile
    val pipelineDir = layout.projectDirectory.dir("config/pipelines").asFile
    val pipelineSchemaFile = layout.projectDirectory.file("config/validation/pipeline-schema-v1.json").asFile

    doLast {
        val eventSchemaFiles = eventSchemaDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json") }.toList()
        val pipelineFiles = pipelineDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }.toList()

        println("Validating ${eventSchemaFiles.size} event schemas and ${pipelineFiles.size} pipelines...")

        var errors = 0
        val schemaNames = mutableSetOf<String>()

        eventSchemaFiles.forEach { file ->
            try {
                val content = file.readText()
                if (!content.contains("\$schema") || !content.contains("\$id") ||
                    !content.contains("\"title\"") || !content.contains("\"version\"") || !content.contains("\"type\"")) {
                    println("ERROR: ${file.name} missing required JSON Schema fields"); errors++
                }
                val schemaName = file.name.removeSuffix(".json")
                if (!schemaNames.add(schemaName)) { println("ERROR: Duplicate schema name: $schemaName"); errors++ }
            } catch (e: Exception) { println("ERROR: Invalid JSON in ${file.name}: ${e.message}"); errors++ }
        }

        pipelineFiles.forEach { file ->
            Regex("schema:\\s*([a-z0-9-]+-v\\d+)").findAll(file.readText()).forEach { match ->
                val schemaName = match.groupValues[1]
                if (schemaName !in schemaNames) {
                    println("ERROR: ${file.name} references non-existent schema: $schemaName"); errors++
                }
            }
        }

        if (!pipelineSchemaFile.exists()) {
            println("ERROR: pipeline-schema-v1.json not found"); errors++
        }

        if (errors > 0) throw GradleException("Event schema validation failed with $errors error(s)")
        println("Event schema validation PASSED: ${eventSchemaFiles.size} schemas, ${pipelineFiles.size} pipelines, 0 errors")
    }
}

// ============================================================================
// Pipeline Validation Task
// ============================================================================
tasks.register("validatePipelines") {
    description = "Validates all pipeline YAML definitions against pipeline schema"
    group = "verification"

    val pipelineSchemaFile = layout.projectDirectory.file("config/validation/pipeline-schema-v1.json").asFile
    val pipelineDir = layout.projectDirectory.dir("config/pipelines").asFile

    doLast {
        if (!pipelineSchemaFile.exists())
            throw GradleException("Pipeline schema not found: config/validation/pipeline-schema-v1.json")

        val pipelineFiles = pipelineDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }.toList()

        println("Validating ${pipelineFiles.size} pipeline definitions...")

        var errors = 0
        val pipelineNames = mutableSetOf<String>()
        val yaml = org.yaml.snakeyaml.Yaml()

        pipelineFiles.forEach { file ->
            try {
                @Suppress("UNCHECKED_CAST")
                val pipeline = yaml.load<Map<String, Any>>(file.readText())

                listOf("apiVersion", "kind", "metadata", "spec").forEach { field ->
                    if (!pipeline.containsKey(field)) { println("ERROR: ${file.name} missing required field: $field"); errors++ }
                }

                @Suppress("UNCHECKED_CAST")
                val name = (pipeline["metadata"] as? Map<String, Any>)?.get("name") as? String
                if (name == null) { println("ERROR: ${file.name} missing metadata.name"); errors++ }
                else if (!pipelineNames.add(name)) { println("ERROR: Duplicate pipeline name: $name"); errors++ }

                @Suppress("UNCHECKED_CAST")
                val spec = pipeline["spec"] as? Map<String, Any>
                if (spec != null && (spec["operators"] as? List<*>)?.isEmpty() != false) {
                    println("ERROR: ${file.name} must have at least one operator"); errors++
                }
            } catch (e: Exception) { println("ERROR: Invalid YAML in ${file.name}: ${e.message}"); errors++ }
        }

        if (errors > 0) throw GradleException("Pipeline validation failed with $errors error(s)")
        println("Pipeline validation PASSED: ${pipelineFiles.size} pipelines, ${pipelineNames.size} unique names, 0 errors")
    }
}

// ============================================================================
// Lifecycle Config Validation Task
// Validates cross-references between stages.yaml and transitions.yaml
// ============================================================================
tasks.register("validateLifecycleConfig") {
    description = "Validates lifecycle stages.yaml and transitions.yaml for structural integrity and cross-references"
    group = "verification"

    val stagesFile = layout.projectDirectory.file("config/lifecycle/stages.yaml").asFile
    val transitionsFile = layout.projectDirectory.file("config/lifecycle/transitions.yaml").asFile

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        var errors = 0

        if (!stagesFile.exists()) throw GradleException("config/lifecycle/stages.yaml not found")

        @Suppress("UNCHECKED_CAST")
        val stagesList = (yaml.load<Map<String, Any>>(stagesFile.readText())
            ?.get("stages") as? List<Map<String, Any>>) ?: emptyList()
        val knownStageIds = mutableSetOf<String>()
        stagesList.forEach { stage ->
            val id = stage["id"]?.toString()?.trim()
            if (id.isNullOrBlank()) { println("ERROR: stages.yaml entry missing 'id' field"); errors++ }
            else if (!knownStageIds.add(id)) { println("ERROR: Duplicate stage id '$id' in stages.yaml"); errors++ }
        }
        println("Loaded ${knownStageIds.size} stage IDs: $knownStageIds")

        if (!transitionsFile.exists()) throw GradleException("config/lifecycle/transitions.yaml not found")

        @Suppress("UNCHECKED_CAST")
        val transitionsList = (yaml.load<Map<String, Any>>(transitionsFile.readText())
            ?.get("transitions") as? List<Map<String, Any>>) ?: emptyList()

        transitionsList.forEachIndexed { idx, t ->
            val from = t["from"]?.toString()?.trim()
            val to   = t["to"]?.toString()?.trim()
            if (from.isNullOrBlank()) { println("ERROR: transitions.yaml entry #$idx missing 'from' field"); errors++ }
            else if (from !in knownStageIds) { println("ERROR: transitions.yaml entry #$idx 'from: $from' is not a known stage ID"); errors++ }
            if (to.isNullOrBlank()) { println("ERROR: transitions.yaml entry #$idx missing 'to' field"); errors++ }
            else if (to !in knownStageIds) { println("ERROR: transitions.yaml entry #$idx 'to: $to' is not a known stage ID"); errors++ }

            @Suppress("UNCHECKED_CAST")
            (t["required_artifacts"] as? List<*> ?: emptyList<Any>()).forEachIndexed { ai, artifact ->
                if (artifact?.toString().isNullOrBlank()) {
                    println("ERROR: transitions.yaml entry #$idx required_artifacts[$ai] is blank"); errors++
                }
            }
        }

        if (errors > 0) throw GradleException("Lifecycle config validation failed with $errors error(s)")
        println("Lifecycle config validation PASSED: ${knownStageIds.size} stages, ${transitionsList.size} transitions, 0 errors")
    }
}

// ============================================================================
// Workflow Config Validation Task
// Validates canonical-workflows.yaml stage references resolve to known stages
// ============================================================================
tasks.register("validateWorkflowConfig") {
    description = "Validates canonical-workflows.yaml stage references resolve to known lifecycle stages"
    group = "verification"

    val stagesFile = layout.projectDirectory.file("config/lifecycle/stages.yaml").asFile
    val workflowFile = layout.projectDirectory.file("config/workflows/canonical-workflows.yaml").asFile

    doLast {
        val yaml = org.yaml.snakeyaml.Yaml()
        var errors = 0

        if (!stagesFile.exists())
            throw GradleException("config/lifecycle/stages.yaml not found — run validateLifecycleConfig first")

        @Suppress("UNCHECKED_CAST")
        val knownStageIds = ((yaml.load<Map<String, Any>>(stagesFile.readText())
            ?.get("stages") as? List<Map<String, Any>>) ?: emptyList())
            .mapNotNull { it["id"]?.toString()?.trim() }.toSet()

        if (!workflowFile.exists())
            throw GradleException("config/workflows/canonical-workflows.yaml not found")

        @Suppress("UNCHECKED_CAST")
        val workflowDoc = yaml.load<Map<String, Any>>(workflowFile.readText())
        @Suppress("UNCHECKED_CAST")
        val workflowsList = when (val wfl = workflowDoc?.get("workflows")) {
            is List<*> -> wfl.filterIsInstance<Map<String, Any>>()
            is Map<*, *> -> (wfl as Map<String, Map<String, Any>>).values.toList()
            else -> {
                val entries = workflowDoc?.entries ?: emptySet()
                entries.mapNotNull { entry ->
                    @Suppress("UNCHECKED_CAST")
                    (entry.value as? Map<String, Any>)
                }
            }
        }

        println("Validating ${workflowsList.size} canonical workflows...")

        workflowsList.forEachIndexed { idx, wf ->
            val wfId = wf["id"]?.toString() ?: wf["name"]?.toString() ?: "#$idx"
            listOf("id", "name").forEach { field ->
                if (!wf.containsKey(field)) println("WARNING: workflow '$wfId' missing recommended field: $field")
            }

            @Suppress("UNCHECKED_CAST")
            val steps = (wf["stages"] ?: wf["steps"] ?: wf["phases"]) as? List<*> ?: emptyList<Any>()
            steps.forEachIndexed { si, step ->
                val stageRef = when (step) {
                    is Map<*, *> -> step["stage"]?.toString() ?: step["id"]?.toString()
                    is String    -> step
                    else         -> null
                }
                if (stageRef != null && stageRef !in knownStageIds) {
                    println("ERROR: workflow '$wfId' step[$si] references unknown stage: $stageRef"); errors++
                }
            }
        }

        if (errors > 0) throw GradleException("Workflow config validation failed with $errors error(s)")
        println("Workflow config validation PASSED: ${workflowsList.size} workflows, 0 errors")
    }
}

// ============================================================================
// Policy Config Validation Task
// Validates all YAML files in config/policies/ for ID uniqueness and rules presence
// ============================================================================
tasks.register("validatePolicyConfig") {
    description = "Validates all policy YAML files in config/policies/ for structural correctness and ID uniqueness"
    group = "verification"

    val policyDir = layout.projectDirectory.dir("config/policies").asFile

    doLast {
        val yaml      = org.yaml.snakeyaml.Yaml()
        var errors    = 0

        if (!policyDir.exists()) throw GradleException("config/policies/ directory not found")

        val policyFiles = policyDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".yaml") }.toList()

        if (policyFiles.isEmpty()) throw GradleException("No policy YAML files found in config/policies/")

        println("Validating ${policyFiles.size} policy YAML file(s)...")

        val seenPolicyIds = mutableSetOf<String>()
        policyFiles.forEach { file ->
            try {
                @Suppress("UNCHECKED_CAST")
                val policies = (yaml.load<Map<String, Any>>(file.readText())
                    ?.get("policies") as? List<Map<String, Any>>) ?: emptyList()

                policies.forEachIndexed { idx, policy ->
                    val id = policy["id"]?.toString()?.trim()
                    if (id.isNullOrBlank()) { println("ERROR: ${file.name} policy[$idx] missing 'id' field"); errors++ }
                    else if (!seenPolicyIds.add(id)) { println("ERROR: ${file.name} duplicate policy ID: $id"); errors++ }
                    if (!policy.containsKey("version")) println("WARNING: ${file.name} policy '${id ?: idx}' missing 'version' field")
                    @Suppress("UNCHECKED_CAST")
                    if ((policy["rules"] as? List<*> ?: emptyList<Any>()).isEmpty()) {
                        println("ERROR: ${file.name} policy '${id ?: idx}' has no rules"); errors++
                    }
                }
            } catch (e: Exception) { println("ERROR: Failed to parse ${file.name}: ${e.message}"); errors++ }
        }

        if (errors > 0) throw GradleException("Policy config validation failed with $errors error(s)")
        println("Policy config validation PASSED: ${policyFiles.size} file(s), ${seenPolicyIds.size} policies, 0 errors")
    }
}

// Wire all validation tasks into the standard check lifecycle
tasks.findByName("check")?.dependsOn(
    "validateAgentCatalog",
    "validateEventSchemas",
    "validatePipelines",
    "validateLifecycleConfig",
    "validateWorkflowConfig",
    "validatePolicyConfig"
)
