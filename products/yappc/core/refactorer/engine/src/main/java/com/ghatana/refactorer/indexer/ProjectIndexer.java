package com.ghatana.refactorer.indexer;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.nio.file.Path;
import org.apache.logging.log4j.Logger;

/**
 * Coordinates indexing of TypeScript/JavaScript projects and their dependencies. 
 * @doc.type class
 * @doc.purpose Handles project indexer operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class ProjectIndexer {
    private final PolyfixProjectContext context;
    private final Logger logger;
    private final SymbolStore symbolStore;
    private final NodeIndexer nodeIndexer;

    /**
     * Creates a new ProjectIndexer for the given project context.
     *
     * @param context The project context
     */
    public ProjectIndexer(PolyfixProjectContext context) {
        this.context = context;
        this.logger = context.log();
        this.symbolStore = new SymbolStore(context);
        this.nodeIndexer = new NodeIndexer(context, symbolStore);
    }

    /**
     * Indexes the project at the given root directory.
     *
     * @param projectRoot The root directory of the project to index
     */
    public void indexProject(Path projectRoot) {
        logger.info("Starting project indexing in: {}", projectRoot);

        try {
            // Index the project using the NodeIndexer
            nodeIndexer.index(projectRoot, symbolStore);

            logger.info("Completed project indexing in: {}", projectRoot);
            logger.info("Found {} unique exports", symbolStore.getExportCount());
        } catch (Exception e) {
            logger.error("Error indexing project: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to index project", e);
        }
    }

    /**
     * Gets the symbol store containing all indexed exports.
     *
     * @return The symbol store
     */
    public SymbolStore getSymbolStore() {
        return symbolStore;
    }
}
