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
    void testLoadBasePack(@TempDir Path tempDir) throws Exception { // GH-90000
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine()); // GH-90000

        // Test loading base pack
        Path basePackPath = Path.of("packs/base");
        if (basePackPath.toFile().exists()) { // GH-90000
            Pack basePack = engine.loadPack(basePackPath); // GH-90000
            assertNotNull(basePack); // GH-90000
            assertEquals("base", basePack.getMetadata().name()); // GH-90000
            assertEquals("BASE", basePack.getMetadata().type().toString()); // GH-90000
            assertTrue(basePack.getMetadata().templates().containsKey("gitignore"));
        }
    }

    @Test
    void testLoadActiveJServicePack(@TempDir Path tempDir) throws Exception { // GH-90000
        DefaultPackEngine engine = new DefaultPackEngine(new SimpleTemplateEngine()); // GH-90000

        // Test loading ActiveJ service pack
        Path servicePackPath = Path.of("packs/java-service-activej-gradle");
        if (servicePackPath.toFile().exists()) { // GH-90000
            Pack servicePack = engine.loadPack(servicePackPath); // GH-90000
            assertNotNull(servicePack); // GH-90000
            assertEquals("java-service-activej-gradle", servicePack.getMetadata().name()); // GH-90000
            assertEquals("SERVICE", servicePack.getMetadata().type().toString()); // GH-90000
            assertTrue(servicePack.getMetadata().templates().containsKey("main-class"));
            assertTrue(servicePack.getMetadata().templates().containsKey("dockerfile"));
        }
    }
}
