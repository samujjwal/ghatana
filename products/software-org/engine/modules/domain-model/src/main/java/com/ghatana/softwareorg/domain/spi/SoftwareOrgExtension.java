package com.ghatana.softwareorg.domain.spi;

import com.ghatana.softwareorg.domain.agent.SoftwareAgentFactory;
import com.ghatana.virtualorg.framework.VirtualOrgContext;
import com.ghatana.virtualorg.framework.norm.Norm;
import com.ghatana.virtualorg.framework.ontology.Concept;
import com.ghatana.virtualorg.framework.spi.VirtualOrgExtension;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Software-Org extension for the Virtual-Org framework.
 *
 * <p><b>Purpose</b><br>
 * Extends the generic Virtual-Org framework with software engineering
 * specific concepts, agents, and norms.
 *
 * <p><b>Capabilities Added</b><br>
 * - Software engineering agent templates (CodeReviewer, TechLead, etc.)
 * - Software domain ontology (CodeReview, PullRequest, Deployment, etc.)
 * - Software engineering norms (respond to PRs, no Friday deploys, etc.)
 *
 * <p><b>SPI Registration</b><br>
 * Register in:
 * {@code META-INF/services/com.ghatana.virtualorg.framework.spi.VirtualOrgExtension}
 *
 * @doc.type class
 * @doc.purpose Software-Org framework extension
 * @doc.layer product
 * @doc.pattern Plugin, SPI
 */
public class SoftwareOrgExtension implements VirtualOrgExtension {

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareOrgExtension.class);

    @Override
    public String getName() {
        return "software-org";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public List<String> getDependencies() {
        return List.of(); // No dependencies on other extensions
    }

    @Override
    public Promise<Void> initialize(VirtualOrgContext context) {
        LOG.info("Initializing Software-Org extension...");

        // Register agent factory
        context.getAgentRegistry().register(new SoftwareAgentFactory());
        LOG.debug("Registered SoftwareAgentFactory");

        // Define domain ontology
        defineSoftwareOntology(context);

        // Register domain norms
        registerSoftwareNorms(context);

        LOG.info("Software-Org extension initialized");
        return Promise.complete();
    }

    private void defineSoftwareOntology(VirtualOrgContext context) {
        var ontology = context.getOntology();

        // Software task types
        ontology.defineSync(Concept.builder("code-review", "CodeReview")
                .description("Review of code changes")
                .parent("review")
                .synonyms("PR Review", "Pull Request Review", "Code Assessment")
                .build());

        ontology.defineSync(Concept.builder("pull-request", "PullRequest")
                .description("A request to merge code changes")
                .parent("artifact")
                .synonyms("PR", "Merge Request", "MR")
                .build());

        ontology.defineSync(Concept.builder("deployment", "Deployment")
                .description("Releasing code to an environment")
                .parent("activity")
                .synonyms("Release", "Deploy", "Ship")
                .build());

        ontology.defineSync(Concept.builder("sprint", "Sprint")
                .description("A time-boxed development iteration")
                .parent("activity")
                .synonyms("Iteration", "Cycle")
                .build());

        ontology.defineSync(Concept.builder("incident", "Incident")
                .description("An unplanned service disruption")
                .parent("activity")
                .synonyms("Outage", "Issue", "Problem")
                .build());

        ontology.defineSync(Concept.builder("bug", "Bug")
                .description("A software defect")
                .parent("artifact")
                .synonyms("Defect", "Issue", "Error")
                .build());

        ontology.defineSync(Concept.builder("feature", "Feature")
                .description("A new capability to implement")
                .parent("artifact")
                .synonyms("User Story", "Enhancement", "Capability")
                .build());

        LOG.debug("Defined {} software domain concepts", 7);
    }

    private void registerSoftwareNorms(VirtualOrgContext context) {
        var normRegistry = context.getNormRegistry();

        // Obligation: Respond to P1 incidents quickly
        normRegistry.register(Norm.obligation("respond-p1-incident")
                .description("Acknowledge P1 incidents within 15 minutes")
                .condition("incident.priority == 'P1'")
                .action("acknowledge-incident")
                .deadline(Duration.ofMinutes(15))
                .targetRole("on-call-engineer")
                .penalty(0.8)
                .build());

        // Obligation: Review PRs within 24 hours
        normRegistry.register(Norm.obligation("review-pr-timely")
                .description("Review pull requests within 24 hours")
                .condition("pull-request.status == 'OPEN'")
                .action("review-pull-request")
                .deadline(Duration.ofHours(24))
                .targetRole("code-reviewer")
                .penalty(0.3)
                .build());

        // Prohibition: No Friday deployments
        normRegistry.register(Norm.prohibition("no-friday-deploy")
                .description("Do not deploy to production on Fridays")
                .condition("day_of_week == 'FRIDAY' && environment == 'production'")
                .action("deploy-production")
                .penalty(0.7)
                .build());

        // Prohibition: No unreviewed code merges
        normRegistry.register(Norm.prohibition("no-unreviewed-merge")
                .description("Do not merge code without at least one approval")
                .condition("pull-request.approvals < 1")
                .action("merge-pull-request")
                .penalty(0.9)
                .build());

        // Permission: Tech leads can approve architecture changes
        normRegistry.register(Norm.permission("approve-architecture")
                .description("Tech leads can approve architecture changes")
                .action("approve-architecture-change")
                .targetRole("tech-lead")
                .build());

        // Permission: Release managers can deploy to production
        normRegistry.register(Norm.permission("deploy-production")
                .description("Release managers can deploy to production")
                .action("deploy-production")
                .targetRole("release-manager")
                .build());

        LOG.debug("Registered {} software domain norms", 6);
    }

    @Override
    public Promise<Void> shutdown() {
        LOG.info("Shutting down Software-Org extension");
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> healthCheck() {
        return Promise.of(true);
    }
}
