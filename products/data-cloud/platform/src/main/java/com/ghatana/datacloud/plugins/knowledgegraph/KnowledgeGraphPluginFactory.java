package com.ghatana.datacloud.plugins.knowledgegraph;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.plugins.knowledgegraph.analytics.CentralityAnalyticsEngine;
import com.ghatana.datacloud.plugins.knowledgegraph.analytics.GraphAnalyticsEngine;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.DataCloudGraphStorageAdapter;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.GraphStorageAdapter;
import com.ghatana.datacloud.plugins.knowledgegraph.traversal.BfsTraversalEngine;
import com.ghatana.datacloud.plugins.knowledgegraph.traversal.GraphTraversalEngine;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating Knowledge Graph plugin instances.
 * 
 * <p>Handles dependency injection and wiring of plugin components.
 * 
 * @doc.type class
 * @doc.purpose Plugin factory
 * @doc.layer factory
 * @doc.pattern Factory
 */
@Slf4j
public class KnowledgeGraphPluginFactory {
    
    /**
     * Creates a fully configured Knowledge Graph plugin instance.
     */
    public static KnowledgeGraphPlugin create(EntityRepository entityRepository) {
        log.info("Creating Knowledge Graph plugin");
        
        // Create storage adapter
        GraphStorageAdapter storageAdapter = new DataCloudGraphStorageAdapter(entityRepository);
        
        // Create traversal engine
        GraphTraversalEngine traversalEngine = new BfsTraversalEngine(storageAdapter);
        
        // Create analytics engine
        GraphAnalyticsEngine analyticsEngine = new CentralityAnalyticsEngine(storageAdapter);
        
        // Create plugin
        KnowledgeGraphPlugin plugin = new KnowledgeGraphPluginImpl(storageAdapter, traversalEngine);
        
        log.info("Knowledge Graph plugin created successfully");
        return plugin;
    }
    
    /**
     * Creates a plugin with custom components for testing.
     */
    public static KnowledgeGraphPlugin createWithComponents(
            GraphStorageAdapter storageAdapter,
            GraphTraversalEngine traversalEngine) {
        
        log.info("Creating Knowledge Graph plugin with custom components");
        return new KnowledgeGraphPluginImpl(storageAdapter, traversalEngine);
    }
}
