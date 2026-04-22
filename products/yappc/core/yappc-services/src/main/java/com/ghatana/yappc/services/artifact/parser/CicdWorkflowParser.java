package com.ghatana.yappc.services.artifact.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Parses CI/CD workflow files (GitHub Actions YAML) to extract job dependencies, triggers, secrets usage, and environment variables.
 * @doc.layer service
 * @doc.pattern Extractor
 */
public final class CicdWorkflowParser {

    private static final Logger log = LoggerFactory.getLogger(CicdWorkflowParser.class);
    private final ObjectMapper yamlMapper;

    public CicdWorkflowParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * Parse a GitHub Actions workflow YAML string.
     *
     * @param yamlContent raw YAML content
     * @return parsed model map with name, triggers, jobs, dependencies, secrets, envVars
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseGitHubActionsWorkflow(String yamlContent) {
        try {
            Map<String, Object> workflow = yamlMapper.readValue(yamlContent, Map.class);

            String name = (String) workflow.getOrDefault("name", "Unnamed Workflow");
            Map<String, Object> onTriggers = workflow.containsKey("on")
                    ? (Map<String, Object>) workflow.get("on")
                    : Map.of();

            List<String> triggers = new ArrayList<>();
            if (onTriggers != null) {
                for (String key : onTriggers.keySet()) {
                    triggers.add(key);
                }
            }

            Map<String, Object> rawJobs = (Map<String, Object>) workflow.getOrDefault("jobs", Map.of());
            List<Map<String, Object>> jobs = new ArrayList<>();
            List<Map<String, Object>> dependencies = new ArrayList<>();
            List<String> secrets = new ArrayList<>();
            List<String> envVars = new ArrayList<>();

            for (Map.Entry<String, Object> entry : rawJobs.entrySet()) {
                String jobId = entry.getKey();
                Map<String, Object> jobDef = (Map<String, Object>) entry.getValue();
                Map<String, Object> job = new HashMap<>();
                job.put("id", jobId);
                job.put("name", jobDef.getOrDefault("name", jobId));
                job.put("runsOn", jobDef.getOrDefault("runs-on", "ubuntu-latest"));

                // Extract needs (job dependencies)
                Object needs = jobDef.get("needs");
                List<String> needsList = new ArrayList<>();
                if (needs instanceof String) {
                    needsList.add((String) needs);
                    dependencies.add(Map.of(
                            "from", jobId,
                            "to", needs,
                            "type", "needs"
                    ));
                } else if (needs instanceof List) {
                    for (Object n : (List<?>) needs) {
                        if (n instanceof String) {
                            needsList.add((String) n);
                            dependencies.add(Map.of(
                                    "from", jobId,
                                    "to", n,
                                    "type", "needs"
                            ));
                        }
                    }
                }
                job.put("needs", needsList);

                // Extract steps for secrets and env
                List<Map<String, Object>> rawSteps = (List<Map<String, Object>>) jobDef.getOrDefault("steps", List.of());
                List<Map<String, Object>> steps = new ArrayList<>();
                for (Map<String, Object> step : rawSteps) {
                    Map<String, Object> stepModel = new HashMap<>();
                    stepModel.put("name", step.getOrDefault("name", "Unnamed Step"));
                    stepModel.put("uses", step.get("uses"));
                    stepModel.put("run", step.get("run"));

                    // Extract secrets from with/env blocks
                    Map<String, Object> with = (Map<String, Object>) step.get("with");
                    if (with != null) {
                        for (String key : with.keySet()) {
                            String val = String.valueOf(with.get(key));
                            if (val.contains("secrets.")) {
                                secrets.add(val.replace("${{ secrets.", "").replace("}}", "").trim());
                            }
                            if (val.contains("env.")) {
                                envVars.add(val.replace("${{ env.", "").replace("}}", "").trim());
                            }
                        }
                    }
                    steps.add(stepModel);
                }
                job.put("steps", steps);
                jobs.add(job);
            }

            // Extract top-level env vars
            Map<String, Object> env = (Map<String, Object>) workflow.get("env");
            if (env != null) {
                envVars.addAll(env.keySet());
            }

            return Map.of(
                    "workflowName", name,
                    "platform", "github-actions",
                    "triggers", triggers,
                    "jobs", jobs,
                    "jobDependencies", dependencies,
                    "secretsUsed", secrets.stream().distinct().toList(),
                    "environmentVariables", envVars.stream().distinct().toList(),
                    "parsed", true
            );

        } catch (IOException e) {
            log.error("Failed to parse GitHub Actions workflow YAML", e);
            return Map.of(
                    "error", "YAML parse error: " + e.getMessage(),
                    "parsed", false
            );
        }
    }

    /**
     * Parse a GitLab CI YAML string (basic heuristic support).
     *
     * @param yamlContent raw YAML content
     * @return parsed model map with stages, jobs, and dependencies
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseGitLabCiWorkflow(String yamlContent) {
        try {
            Map<String, Object> workflow = yamlMapper.readValue(yamlContent, Map.class);
            List<String> stages = (List<String>) workflow.getOrDefault("stages", List.of("build", "test", "deploy"));

            List<Map<String, Object>> jobs = new ArrayList<>();
            List<Map<String, Object>> dependencies = new ArrayList<>();

            for (Map.Entry<String, Object> entry : workflow.entrySet()) {
                String key = entry.getKey();
                if (key.equals("stages") || key.equals("variables") || key.equals("workflow") || key.startsWith(".")) {
                    continue; // Skip meta keys and hidden jobs
                }
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> jobDef = (Map<String, Object>) entry.getValue();
                    Map<String, Object> job = new HashMap<>();
                    job.put("id", key);
                    job.put("stage", jobDef.getOrDefault("stage", "build"));
                    job.put("script", jobDef.getOrDefault("script", List.of()));

                    Object needs = jobDef.get("needs");
                    List<String> needsList = new ArrayList<>();
                    if (needs instanceof String) {
                        needsList.add((String) needs);
                        dependencies.add(Map.of("from", key, "to", needs, "type", "needs"));
                    } else if (needs instanceof List) {
                        for (Object n : (List<?>) needs) {
                            if (n instanceof String) {
                                needsList.add((String) n);
                                dependencies.add(Map.of("from", key, "to", n, "type", "needs"));
                            } else if (n instanceof Map) {
                                String jobName = (String) ((Map<String, Object>) n).get("job");
                                if (jobName != null) {
                                    needsList.add(jobName);
                                    dependencies.add(Map.of("from", key, "to", jobName, "type", "needs"));
                                }
                            }
                        }
                    }
                    job.put("needs", needsList);
                    jobs.add(job);
                }
            }

            return Map.of(
                    "platform", "gitlab-ci",
                    "stages", stages,
                    "jobs", jobs,
                    "jobDependencies", dependencies,
                    "parsed", true
            );

        } catch (IOException e) {
            log.error("Failed to parse GitLab CI YAML", e);
            return Map.of(
                    "error", "YAML parse error: " + e.getMessage(),
                    "parsed", false
            );
        }
    }

    /**
     * Auto-detect and parse CI/CD workflow content.
     */
    public Map<String, Object> parseWorkflow(String yamlContent, String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".github/workflows/") || lowerName.contains("github")) {
            return parseGitHubActionsWorkflow(yamlContent);
        }
        if (lowerName.contains("gitlab") || lowerName.contains(".gitlab-ci")) {
            return parseGitLabCiWorkflow(yamlContent);
        }
        // Default to GitHub Actions parsing attempt
        Map<String, Object> result = parseGitHubActionsWorkflow(yamlContent);
        if (Boolean.TRUE.equals(result.get("parsed"))) {
            return result;
        }
        return parseGitLabCiWorkflow(yamlContent);
    }
}
