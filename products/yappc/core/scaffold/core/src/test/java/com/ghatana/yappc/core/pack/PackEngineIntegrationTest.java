package com.ghatana.yappc.core.pack;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.template.SimpleTemplateEngine;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles pack engine integration test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PackEngineIntegrationTest {

    @Test
    void testLoadBasePack(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());

        // Test loading base pack
        Path basePackPath = Path.of("packs/base");
        if (basePackPath.toFile().exists()) {
            Pack basePack = engine.loadPack(basePackPath);
            assertNotNull(basePack);
            assertEquals("base", basePack.getMetadata().name());
            assertEquals("BASE", basePack.getMetadata().type().toString());
            assertTrue(basePack.getMetadata().templates().containsKey("gitignore"));
        }
    }

    @Test
    void testLoadActiveJServicePack(@TempDir Path tempDir) throws Exception {
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine());

        // Test loading ActiveJ service pack
        Path servicePackPath = Path.of("packs/java-service-activej-gradle");
        if (servicePackPath.toFile().exists()) {
            Pack servicePack = engine.loadPack(servicePackPath);
            assertNotNull(servicePack);
            assertEquals("java-service-activej-gradle", servicePack.getMetadata().name());
            assertEquals("SERVICE", servicePack.getMetadata().type().toString());
            assertTrue(servicePack.getMetadata().templates().containsKey("main-class"));
            assertTrue(servicePack.getMetadata().templates().containsKey("dockerfile"));
        }
    }
}
