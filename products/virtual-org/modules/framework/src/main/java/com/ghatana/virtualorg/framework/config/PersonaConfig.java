package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persona configuration POJO.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a persona configuration loaded from YAML files. Personas define
 * the behavioral characteristics, communication style, expertise, and
 * personality traits that shape how an agent interacts and operates.
 *
 * <p>
 * <b>Persona vs Role</b><br>
 * - <b>Role</b>: What an agent CAN do (permissions, capabilities) -
 * <b>Persona</b>: HOW an agent does it (style, tone, behavior)
 *
 * <p>
 * <b>Persona Types</b><br>
 * - <b>base</b>: Foundational personas (Developer, Manager, Analyst) -
 * <b>specialized</b>: Domain-specific (SeniorEngineer, TechLead, ProductOwner)
 * - <b>composite</b>: Combination of multiple personas - <b>custom</b>:
 * Organization-defined personas
 *
 * <p>
 * <b>Usage Example (YAML)</b>
 * <pre>{@code
 * apiVersion: virtualorg.ghatana.com/v1
 * kind: Persona
 * metadata:
 *   name: senior-engineer
 *   namespace: engineering
 * spec:
 *   displayName: "Senior Software Engineer"
 *   type: specialized
 *   extends: developer
 *   traits:
 *     communication:
 *       style: technical
 *       tone: professional
 *       verbosity: moderate
 *     decision_making:
 *       approach: analytical
 *       risk_tolerance: moderate
 *   expertise:
 *     domains: [backend, distributed-systems]
 *     level: senior
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Persona configuration data model
 * @doc.layer core
 * @doc.pattern Value Object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PersonaConfig(
        @JsonProperty("apiVersion")
        String apiVersion,
        @JsonProperty("kind")
        String kind,
        @JsonProperty("metadata")
        ConfigMetadata metadata,
        @JsonProperty("spec")
        PersonaSpec spec
        ) {

    public boolean isValid() {
        return "virtualorg.ghatana.com/v1".equals(apiVersion)
                && "Persona".equals(kind)
                && metadata != null
                && spec != null
                && spec.type() != null;
    }

    public String getName() {
        return metadata != null ? metadata.name() : null;
    }

    public PersonaType getPersonaType() {
        return spec != null && spec.type() != null
                ? PersonaType.valueOf(spec.type().toUpperCase())
                : PersonaType.CUSTOM;
    }

    /**
     * Gets the display name from the spec.
     *
     * @return Display name or null
     */
    public String getDisplayName() {
        return spec != null ? spec.displayName() : null;
    }

    /**
     * Gets the communication style from traits.
     *
     * @return Communication style or null
     */
    public String getCommunicationStyle() {
        if (spec != null && spec.traits() != null && spec.traits().communication() != null) {
            return spec.traits().communication().style();
        }
        return null;
    }

    /**
     * Gets the expertise domains.
     *
     * @return List of domains or empty list
     */
    public List<String> getExpertiseDomains() {
        if (spec != null && spec.expertise() != null && spec.expertise().domains() != null) {
            return spec.expertise().domains();
        }
        return List.of();
    }

    /**
     * Gets the specializations.
     *
     * @return List of specializations or empty list
     */
    public List<String> getSpecializations() {
        if (spec != null && spec.expertise() != null && spec.expertise().specializations() != null) {
            return spec.expertise().specializations();
        }
        return List.of();
    }
}

/**
 * Persona type classification.
 */
enum PersonaType {
    /**
     * Base personas: Developer, Manager, Analyst, Designer
     */
    BASE,
    /**
     * Specialized technical personas: SeniorEngineer, TechLead
     */
    SPECIALIZED,
    /**
     * Composite personas combining multiple base personas
     */
    COMPOSITE,
    /**
     * Custom organization-defined personas
     */
    CUSTOM
}

/**
 * Persona specification containing all persona details.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaSpec(
        @JsonProperty("displayName")
        String displayName,
        @JsonProperty("type")
        String type,
        @JsonProperty("description")
        String description,
        @JsonProperty("extends")
        String extendsPersona,
        @JsonProperty("composedOf")
        List<String> composedOf,
        @JsonProperty("traits")
        PersonaTraitsConfig traits,
        @JsonProperty("expertise")
        ExpertiseConfig expertise,
        @JsonProperty("role")
        PersonaRoleConfig role,
        @JsonProperty("behavior")
        PersonaBehaviorConfig behavior,
        @JsonProperty("communication")
        CommunicationConfig communication,
        @JsonProperty("prompt")
        PersonaPromptConfig prompt,
        @JsonProperty("constraints")
        PersonaConstraintsConfig constraints,
        @JsonProperty("examples")
        PersonaExamplesConfig examples
        ) {

    public String type() {
        return type != null ? type : "custom";
    }
}

/**
 * Persona traits configuration - defines personality characteristics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaTraitsConfig(
        @JsonProperty("communication")
        TraitCommunicationConfig communication,
        @JsonProperty("decision_making")
        TraitDecisionMakingConfig decisionMaking,
        @JsonProperty("work_style")
        TraitWorkStyleConfig workStyle,
        @JsonProperty("collaboration")
        TraitCollaborationConfig collaboration,
        @JsonProperty("custom")
        Map<String, Object> custom
        ) {

}

/**
 * Communication traits.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TraitCommunicationConfig(
        @JsonProperty("style")
        String style,
        @JsonProperty("tone")
        String tone,
        @JsonProperty("verbosity")
        String verbosity,
        @JsonProperty("formality")
        String formality,
        @JsonProperty("usesTechnicalJargon")
        Boolean usesTechnicalJargon,
        @JsonProperty("usesEmojis")
        Boolean usesEmojis,
        @JsonProperty("preferredFormat")
        String preferredFormat
        ) {

    public String style() {
        return style != null ? style : "balanced";
    }

    public String tone() {
        return tone != null ? tone : "professional";
    }

    public String verbosity() {
        return verbosity != null ? verbosity : "moderate";
    }

    public Boolean usesTechnicalJargon() {
        return usesTechnicalJargon != null ? usesTechnicalJargon : true;
    }
}

/**
 * Decision-making traits.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TraitDecisionMakingConfig(
        @JsonProperty("approach")
        String approach,
        @JsonProperty("riskTolerance")
        String riskTolerance,
        @JsonProperty("speed")
        String speed,
        @JsonProperty("consensus")
        String consensus,
        @JsonProperty("dataOriented")
        Boolean dataOriented
        ) {

    public String approach() {
        return approach != null ? approach : "analytical";
    }

    public String riskTolerance() {
        return riskTolerance != null ? riskTolerance : "moderate";
    }

    public Boolean dataOriented() {
        return dataOriented != null ? dataOriented : true;
    }
}

/**
 * Work style traits.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TraitWorkStyleConfig(
        @JsonProperty("pace")
        String pace,
        @JsonProperty("focus")
        String focus,
        @JsonProperty("planning")
        String planning,
        @JsonProperty("adaptability")
        String adaptability,
        @JsonProperty("attention_to_detail")
        String attentionToDetail
        ) {

    public String pace() {
        return pace != null ? pace : "steady";
    }

    public String focus() {
        return focus != null ? focus : "balanced";
    }
}

/**
 * Collaboration traits.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TraitCollaborationConfig(
        @JsonProperty("teamOrientation")
        String teamOrientation,
        @JsonProperty("leadershipStyle")
        String leadershipStyle,
        @JsonProperty("feedbackStyle")
        String feedbackStyle,
        @JsonProperty("conflictResolution")
        String conflictResolution,
        @JsonProperty("mentoring")
        Boolean mentoring
        ) {

    public String teamOrientation() {
        return teamOrientation != null ? teamOrientation : "collaborative";
    }

    public Boolean mentoring() {
        return mentoring != null ? mentoring : false;
    }
}

/**
 * Expertise configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ExpertiseConfig(
        @JsonProperty("domains")
        List<String> domains,
        @JsonProperty("level")
        String level,
        @JsonProperty("yearsExperience")
        Integer yearsExperience,
        @JsonProperty("skills")
        List<SkillConfig> skills,
        @JsonProperty("certifications")
        List<String> certifications,
        @JsonProperty("specializations")
        List<String> specializations
        ) {

    public String level() {
        return level != null ? level : "mid";
    }
}

/**
 * Individual skill configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record SkillConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("level")
        String level,
        @JsonProperty("yearsExperience")
        Integer yearsExperience,
        @JsonProperty("isPrimary")
        Boolean isPrimary
        ) {

    public String level() {
        return level != null ? level : "proficient";
    }

    public Boolean isPrimary() {
        return isPrimary != null ? isPrimary : false;
    }
}

/**
 * Persona role configuration - defines what the persona CAN do.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaRoleConfig(
        @JsonProperty("roleId")
        String roleId,
        @JsonProperty("title")
        String title,
        @JsonProperty("level")
        String level,
        @JsonProperty("permissions")
        List<String> permissions,
        @JsonProperty("capabilities")
        List<String> capabilities,
        @JsonProperty("restrictions")
        List<String> restrictions,
        @JsonProperty("inheritsFrom")
        List<String> inheritsFrom
        ) {

    public String level() {
        return level != null ? level : "individual";
    }
}

/**
 * Persona behavior configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaBehaviorConfig(
        @JsonProperty("proactivity")
        ProactivityConfig proactivity,
        @JsonProperty("escalation")
        BehaviorEscalationConfig escalation,
        @JsonProperty("learning")
        LearningConfig learning,
        @JsonProperty("boundaries")
        List<String> boundaries,
        @JsonProperty("preferences")
        Map<String, Object> preferences
        ) {

}

/**
 * Proactivity behavior configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ProactivityConfig(
        @JsonProperty("level")
        String level,
        @JsonProperty("initiatesTasks")
        Boolean initiatesTasks,
        @JsonProperty("suggestsImprovements")
        Boolean suggestsImprovements,
        @JsonProperty("asksQuestions")
        Boolean asksQuestions,
        @JsonProperty("alertsOnIssues")
        Boolean alertsOnIssues
        ) {

    public String level() {
        return level != null ? level : "moderate";
    }

    public Boolean asksQuestions() {
        return asksQuestions != null ? asksQuestions : true;
    }
}

/**
 * Behavior escalation configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record BehaviorEscalationConfig(
        @JsonProperty("threshold")
        String threshold,
        @JsonProperty("preferredPath")
        List<String> preferredPath,
        @JsonProperty("escalatesOn")
        List<String> escalatesOn,
        @JsonProperty("neverEscalates")
        List<String> neverEscalates
        ) {

    public String threshold() {
        return threshold != null ? threshold : "moderate";
    }
}

/**
 * Learning behavior configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LearningConfig(
        @JsonProperty("enabled")
        Boolean enabled,
        @JsonProperty("adaptationRate")
        String adaptationRate,
        @JsonProperty("learnFrom")
        List<String> learnFrom,
        @JsonProperty("retentionPeriod")
        String retentionPeriod
        ) {

    public Boolean enabled() {
        return enabled != null ? enabled : true;
    }

    public String adaptationRate() {
        return adaptationRate != null ? adaptationRate : "moderate";
    }
}

/**
 * Communication configuration for the persona.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CommunicationConfig(
        @JsonProperty("responseFormat")
        ResponseFormatConfig responseFormat,
        @JsonProperty("language")
        LanguageConfig language,
        @JsonProperty("templates")
        List<CommunicationTemplateConfig> templates
        ) {

}

/**
 * Response format configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ResponseFormatConfig(
        @JsonProperty("default")
        String defaultFormat,
        @JsonProperty("codeBlocks")
        Boolean codeBlocks,
        @JsonProperty("markdown")
        Boolean markdown,
        @JsonProperty("maxLength")
        Integer maxLength,
        @JsonProperty("structured")
        Boolean structured
        ) {

    public String defaultFormat() {
        return defaultFormat != null ? defaultFormat : "markdown";
    }

    public Boolean codeBlocks() {
        return codeBlocks != null ? codeBlocks : true;
    }

    public Boolean markdown() {
        return markdown != null ? markdown : true;
    }
}

/**
 * Language preferences.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record LanguageConfig(
        @JsonProperty("primary")
        String primary,
        @JsonProperty("supported")
        List<String> supported,
        @JsonProperty("detectAndRespond")
        Boolean detectAndRespond
        ) {

    public String primary() {
        return primary != null ? primary : "en";
    }

    public Boolean detectAndRespond() {
        return detectAndRespond != null ? detectAndRespond : false;
    }
}

/**
 * Communication template for specific scenarios.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record CommunicationTemplateConfig(
        @JsonProperty("name")
        String name,
        @JsonProperty("scenario")
        String scenario,
        @JsonProperty("template")
        String template,
        @JsonProperty("variables")
        List<String> variables
        ) {

}

/**
 * Prompt engineering configuration for the persona.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaPromptConfig(
        @JsonProperty("systemPrompt")
        String systemPrompt,
        @JsonProperty("systemPromptTemplate")
        String systemPromptTemplate,
        @JsonProperty("contextInstructions")
        List<String> contextInstructions,
        @JsonProperty("outputInstructions")
        List<String> outputInstructions,
        @JsonProperty("examples")
        List<PromptExampleConfig> examples,
        @JsonProperty("negativeExamples")
        List<PromptExampleConfig> negativeExamples,
        @JsonProperty("variables")
        Map<String, String> variables
        ) {

}

/**
 * Prompt example for few-shot learning.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PromptExampleConfig(
        @JsonProperty("input")
        String input,
        @JsonProperty("output")
        String output,
        @JsonProperty("context")
        String context,
        @JsonProperty("explanation")
        String explanation
        ) {

}

/**
 * Persona constraints configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaConstraintsConfig(
        @JsonProperty("mustNot")
        List<String> mustNot,
        @JsonProperty("must")
        List<String> must,
        @JsonProperty("should")
        List<String> should,
        @JsonProperty("shouldNot")
        List<String> shouldNot,
        @JsonProperty("topics")
        TopicConstraintsConfig topics,
        @JsonProperty("actions")
        ActionConstraintsConfig actions
        ) {

}

/**
 * Topic-level constraints.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TopicConstraintsConfig(
        @JsonProperty("allowed")
        List<String> allowed,
        @JsonProperty("forbidden")
        List<String> forbidden,
        @JsonProperty("requiresEscalation")
        List<String> requiresEscalation
        ) {

}

/**
 * Action-level constraints for the persona.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ActionConstraintsConfig(
        @JsonProperty("allowed")
        List<String> allowed,
        @JsonProperty("forbidden")
        List<String> forbidden,
        @JsonProperty("requiresApproval")
        List<String> requiresApproval,
        @JsonProperty("maxDailyActions")
        Map<String, Integer> maxDailyActions
        ) {

}

/**
 * Persona examples configuration for demonstration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record PersonaExamplesConfig(
        @JsonProperty("conversations")
        List<ConversationExampleConfig> conversations,
        @JsonProperty("taskResponses")
        List<TaskResponseExampleConfig> taskResponses
        ) {

}

/**
 * Conversation example demonstrating persona behavior.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ConversationExampleConfig(
        @JsonProperty("scenario")
        String scenario,
        @JsonProperty("messages")
        List<MessageExampleConfig> messages,
        @JsonProperty("notes")
        String notes
        ) {

}

/**
 * Single message in a conversation example.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record MessageExampleConfig(
        @JsonProperty("role")
        String role,
        @JsonProperty("content")
        String content
        ) {

}

/**
 * Task response example demonstrating persona work output.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TaskResponseExampleConfig(
        @JsonProperty("taskType")
        String taskType,
        @JsonProperty("input")
        String input,
        @JsonProperty("response")
        String response,
        @JsonProperty("reasoning")
        String reasoning
        ) {

}
