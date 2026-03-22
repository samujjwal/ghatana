package com.ghatana.tutorputor.contentgeneration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main launcher for the tutorputor content generation service.
 *
 * @doc.type class
 * @doc.purpose Minimal application launcher for content generation service
 * @doc.layer application
 * @doc.pattern Bootstrap
 */
public final class ContentGenerationLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationLauncher.class);

    private ContentGenerationLauncher() {
    }

    public static void main(String[] args) {
        LOG.info("Starting tutorputor content generation service");
    }
}
