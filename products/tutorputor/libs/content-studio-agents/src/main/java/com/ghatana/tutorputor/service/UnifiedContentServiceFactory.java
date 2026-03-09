package com.ghatana.tutorputor.service;

import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.tutorputor.agent.*;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import com.ghatana.tutorputor.experiment.ContentStrategySelector;
import com.ghatana.tutorputor.experiment.ExperimentManager;
import com.ghatana.tutorputor.experiment.ExperimentMetricsCollector;
import com.ghatana.tutorputor.worker.ContentJobQueue;
import com.ghatana.tutorputor.worker.JobProgressTracker;
import io.activej.dns.DnsClient;
import io.activej.dns.IDnsClient;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.reactor.nio.NioReactor;
import io.micrometer.core.instrument.MeterRegistry;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Factory for creating and wiring the unified content service.
 * 
 * <p>Handles dependency injection and initialization of all components
 * required by the UnifiedContentService.
 *
 * @doc.type class
 * @doc.purpose Service factory and DI container
 * @doc.layer product
 * @doc.pattern Factory
 */
public class UnifiedContentServiceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(UnifiedContentServiceFactory.class);

    /**
     * Creates a fully configured UnifiedContentService.
     *
     * @param config the factory configuration
     * @return the configured service
     */
    public static UnifiedContentService create(@NotNull FactoryConfig config) {
        LOG.info("Creating UnifiedContentService...");
        
        // Create executor for async operations
        Executor asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Create HTTP client for external APIs (ActiveJ)
        Eventloop eventloop = Eventloop.builder()
            .withThreadName("tutorputor-knowledge")
            .build();
        Thread eventloopThread = new Thread(eventloop::run, "tutorputor-knowledge-eventloop");
        eventloopThread.setDaemon(true);
        eventloopThread.start();

        NioReactor reactor = (NioReactor) eventloop;
        IDnsClient dnsClient = DnsClient.create(reactor, InetAddress.getLoopbackAddress());
        HttpClient httpClient = HttpClient.create(reactor, dnsClient);

        // Create knowledge base service
        KnowledgeBaseService knowledgeService = new KnowledgeBaseService(httpClient, config.meterRegistry());
        
        // Create content generation output generator
        ContentGenerationOutputGenerator contentOutputGenerator = new ContentGenerationOutputGenerator(
            config.llmGateway(),
            knowledgeService,
            asyncExecutor,
            config.meterRegistry()
        );
        
        // Create content quality validator
        ContentQualityValidator contentValidator = new ContentQualityValidator(config.meterRegistry());
        
        // Create content generation agent
        ContentGenerationAgent contentAgent = new ContentGenerationAgent(
            contentOutputGenerator,
            knowledgeService,
            contentValidator
        );
        
        // Create tutoring response generator
        TutoringResponseGenerator tutoringOutputGenerator = new TutoringResponseGenerator(
            config.llmGateway(),
            asyncExecutor,
            config.meterRegistry()
        );
        
        // Create learner interaction agent
        LearnerInteractionAgent tutoringAgent = new LearnerInteractionAgent(tutoringOutputGenerator);
        
        // Create experiment manager
        ExperimentManager experimentManager = new ExperimentManager(config.meterRegistry());
        
        // Create strategy selector
        ContentStrategySelector strategySelector = new ContentStrategySelector(
            experimentManager,
            "llm-standard"  // default strategy
        );
        
        // Create metrics collector
        ExperimentMetricsCollector metricsCollector = 
            new ExperimentMetricsCollector(experimentManager);
        
        // Create job queue
        ContentJobQueue.QueueConfig queueConfig = new ContentJobQueue.QueueConfig(
            config.maxConcurrentJobs(),
            config.maxRetries(),
            config.retryDelay()
        );
        ContentJobQueue jobQueue = new ContentJobQueue(queueConfig, config.meterRegistry());
        
        // Create progress tracker
        JobProgressTracker progressTracker = new JobProgressTracker(config.meterRegistry());
        
        // Assemble service config
        UnifiedContentService.ServiceConfig serviceConfig = new UnifiedContentService.ServiceConfig(
            config.tenantId(),
            config.memoryStore(),
            contentAgent,
            tutoringAgent,
            knowledgeService,
            strategySelector,
            experimentManager,
            metricsCollector,
            jobQueue,
            progressTracker,
            config.meterRegistry()
        );
        
        LOG.info("UnifiedContentService created successfully");
        
        return new UnifiedContentService(serviceConfig);
    }

    /**
     * Creates a minimal service for testing.
     *
     * @param llmGateway the LLM gateway
     * @param memoryStore the memory store
     * @param meterRegistry the meter registry
     * @return the configured service
     */
    public static UnifiedContentService createForTesting(
            @NotNull LLMGateway llmGateway,
            @NotNull MemoryStore memoryStore,
            @NotNull MeterRegistry meterRegistry) {
        
        return create(FactoryConfig.builder()
            .tenantId("test-tenant")
            .llmGateway(llmGateway)
            .memoryStore(memoryStore)
            .meterRegistry(meterRegistry)
            .build());
    }

    /**
     * Factory configuration.
     */
    public record FactoryConfig(
        String tenantId,
        LLMGateway llmGateway,
        MemoryStore memoryStore,
        MeterRegistry meterRegistry,
        String wikipediaApiUrl,
        String openStaxApiUrl,
        String openStaxApiKey,
        String khanAcademyApiUrl,
        String khanAcademyApiKey,
        int maxConcurrentJobs,
        int maxRetries,
        Duration retryDelay
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String tenantId;
            private LLMGateway llmGateway;
            private MemoryStore memoryStore;
            private MeterRegistry meterRegistry;
            private String wikipediaApiUrl = "https://en.wikipedia.org/w/api.php";
            private String openStaxApiUrl = "https://openstax.org/api/v2";
            private String openStaxApiKey = "";
            private String khanAcademyApiUrl = "https://www.khanacademy.org/api/v1";
            private String khanAcademyApiKey = "";
            private int maxConcurrentJobs = 10;
            private int maxRetries = 3;
            private Duration retryDelay = Duration.ofSeconds(5);

            public Builder tenantId(String tenantId) { 
                this.tenantId = tenantId; 
                return this; 
            }
            
            public Builder llmGateway(LLMGateway llmGateway) { 
                this.llmGateway = llmGateway; 
                return this; 
            }
            
            public Builder memoryStore(MemoryStore memoryStore) { 
                this.memoryStore = memoryStore; 
                return this; 
            }
            
            public Builder meterRegistry(MeterRegistry meterRegistry) { 
                this.meterRegistry = meterRegistry; 
                return this; 
            }
            
            public Builder wikipediaApiUrl(String url) { 
                this.wikipediaApiUrl = url; 
                return this; 
            }
            
            public Builder openStaxApiUrl(String url) { 
                this.openStaxApiUrl = url; 
                return this; 
            }
            
            public Builder openStaxApiKey(String key) { 
                this.openStaxApiKey = key; 
                return this; 
            }
            
            public Builder khanAcademyApiUrl(String url) { 
                this.khanAcademyApiUrl = url; 
                return this; 
            }
            
            public Builder khanAcademyApiKey(String key) { 
                this.khanAcademyApiKey = key; 
                return this; 
            }
            
            public Builder maxConcurrentJobs(int max) { 
                this.maxConcurrentJobs = max; 
                return this; 
            }
            
            public Builder maxRetries(int retries) { 
                this.maxRetries = retries; 
                return this; 
            }
            
            public Builder retryDelay(Duration delay) { 
                this.retryDelay = delay; 
                return this; 
            }

            public FactoryConfig build() {
                if (tenantId == null) throw new IllegalStateException("tenantId required");
                if (llmGateway == null) throw new IllegalStateException("llmGateway required");
                if (memoryStore == null) throw new IllegalStateException("memoryStore required");
                if (meterRegistry == null) throw new IllegalStateException("meterRegistry required");
                
                return new FactoryConfig(
                    tenantId,
                    llmGateway,
                    memoryStore,
                    meterRegistry,
                    wikipediaApiUrl,
                    openStaxApiUrl,
                    openStaxApiKey,
                    khanAcademyApiUrl,
                    khanAcademyApiKey,
                    maxConcurrentJobs,
                    maxRetries,
                    retryDelay
                );
            }
        }
    }
}
