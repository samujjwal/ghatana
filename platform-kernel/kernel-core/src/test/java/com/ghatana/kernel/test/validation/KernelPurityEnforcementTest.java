package com.ghatana.kernel.test.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kernel Purity Enforcement Test.
 *
 * <p>Validates that kernel code contains zero product-specific references.
 * This test ensures architectural boundaries are maintained and prevents
 * product contamination of the kernel platform.</p>
 *
 * @doc.type class
 * @doc.purpose Kernel purity validation test
 * @doc.layer kernel
 * @doc.pattern Test
 */
@DisplayName("Kernel Purity Enforcement Tests [GH-90000]")
class KernelPurityEnforcementTest {

    private static final String KERNEL_SOURCE_PATH = "platform/java/kernel/src/main/java";

    // Product-specific packages that should NEVER appear in kernel code
    private static final String[] FORBIDDEN_IMPORTS = {
        "com.ghatana.phr",
        "com.ghatana.finance",
        "com.ghatana.products",
        "com.ghatana.aep",
        "com.ghatana.data-cloud",
        "com.ghatana.audio-video",
        "com.ghatana.aura",
        "com.ghatana.tutorputor",
        "com.ghatana.yappc"
    };

    // Product-specific terms that should not appear in kernel code
    private static final String[] FORBIDDEN_TERMS = {
        "PatientRecord",
        "Appointment",
        "Medication",
        "Portfolio",
        "Trade",
        "Order",
        "Ledger",
        "MarketData",
        "PhrCapabilities",
        "FinanceCapabilities"
    };

    @Test
    @DisplayName("Kernel code contains no product-specific imports [GH-90000]")
    void testNoProductImports() throws IOException { // GH-90000
        List<String> violations = new ArrayList<>(); // GH-90000
        Path kernelPath = Paths.get(KERNEL_SOURCE_PATH); // GH-90000

        if (!Files.exists(kernelPath)) { // GH-90000
            // Skip test if kernel path doesn't exist (e.g., in CI without full checkout) // GH-90000
            return;
        }

        try (Stream<Path> paths = Files.walk(kernelPath)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                 .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                 .forEach(file -> { // GH-90000
                     try {
                         String content = Files.readString(file); // GH-90000
                         for (String forbidden : FORBIDDEN_IMPORTS) { // GH-90000
                             if (content.contains("import " + forbidden)) { // GH-90000
                                 violations.add(file + " contains forbidden import: " + forbidden); // GH-90000
                             }
                         }
                     } catch (IOException e) { // GH-90000
                         fail("Failed to read file: " + file); // GH-90000
                     }
                 });
        }

        assertTrue(violations.isEmpty(), // GH-90000
            "Kernel purity violation - product-specific imports found:\n" +
            String.join("\n", violations)); // GH-90000
    }

    @Test
    @DisplayName("Kernel code contains no product-specific class references [GH-90000]")
    void testNoProductClassReferences() throws IOException { // GH-90000
        List<String> violations = new ArrayList<>(); // GH-90000
        Path kernelPath = Paths.get(KERNEL_SOURCE_PATH); // GH-90000

        if (!Files.exists(kernelPath)) { // GH-90000
            return;
        }

        try (Stream<Path> paths = Files.walk(kernelPath)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                 .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                 .forEach(file -> { // GH-90000
                     try {
                         String content = Files.readString(file); // GH-90000
                         for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
                             // Check for class references (not in comments) // GH-90000
                             String[] lines = content.split("\n [GH-90000]");
                             for (int i = 0; i < lines.length; i++) { // GH-90000
                                 String line = lines[i].trim(); // GH-90000
                                 // Skip comments and JavaDoc
                                 if (line.startsWith("// [GH-90000]") || line.startsWith("* [GH-90000]") || line.startsWith("/* [GH-90000]")) {
                                     continue;
                                 }
                                 if (line.contains(forbidden)) { // GH-90000
                                     violations.add(file + ":" + (i + 1) + " contains forbidden term: " + forbidden); // GH-90000
                                 }
                             }
                         }
                     } catch (IOException e) { // GH-90000
                         fail("Failed to read file: " + file); // GH-90000
                     }
                 });
        }

        assertTrue(violations.isEmpty(), // GH-90000
            "Kernel purity violation - product-specific terms found:\n" +
            String.join("\n", violations)); // GH-90000
    }

    @Test
    @DisplayName("Kernel capabilities contain no product-specific constants [GH-90000]")
    void testKernelCapabilitiesPurity() throws IOException { // GH-90000
        Path capabilitiesFile = Paths.get(KERNEL_SOURCE_PATH, // GH-90000
            "com/ghatana/kernel/descriptor/KernelCapability.java");

        if (!Files.exists(capabilitiesFile)) { // GH-90000
            return;
        }

        String content = Files.readString(capabilitiesFile); // GH-90000

        // Verify no product-specific capability constants
        for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
            assertFalse(content.contains("public static final KernelCapability " + forbidden.toUpperCase()), // GH-90000
                "KernelCapability contains product-specific constant: " + forbidden);
        }

        // Verify no nested Products class
        assertFalse(content.contains("class Products [GH-90000]"),
            "KernelCapability contains deprecated Products inner class");

        // Verify no product-specific comments
        assertFalse(content.contains("Product-specific capability constants [GH-90000]"),
            "KernelCapability contains product-specific comments");
    }

    @Test
    @DisplayName("Kernel adapter interfaces contain no product-specific methods [GH-90000]")
    void testAdapterInterfacesPurity() throws IOException { // GH-90000
        Path adapterPath = Paths.get(KERNEL_SOURCE_PATH, "com/ghatana/kernel/adapter"); // GH-90000

        if (!Files.exists(adapterPath)) { // GH-90000
            return;
        }

        List<String> violations = new ArrayList<>(); // GH-90000

        try (Stream<Path> paths = Files.walk(adapterPath)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                 .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                 .forEach(file -> { // GH-90000
                     try {
                         String content = Files.readString(file); // GH-90000
                         for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
                             if (content.contains(forbidden)) { // GH-90000
                                 violations.add(file + " contains product-specific term: " + forbidden); // GH-90000
                             }
                         }
                     } catch (IOException e) { // GH-90000
                         fail("Failed to read file: " + file); // GH-90000
                     }
                 });
        }

        assertTrue(violations.isEmpty(), // GH-90000
            "Kernel adapter purity violation:\n" + String.join("\n", violations)); // GH-90000
    }

    @Test
    @DisplayName("Kernel service base classes contain no product-specific logic [GH-90000]")
    void testServiceBaseClassesPurity() throws IOException { // GH-90000
        Path servicePath = Paths.get(KERNEL_SOURCE_PATH, "com/ghatana/kernel/service"); // GH-90000

        if (!Files.exists(servicePath)) { // GH-90000
            return;
        }

        List<String> violations = new ArrayList<>(); // GH-90000

        try (Stream<Path> paths = Files.walk(servicePath)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                 .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                 .forEach(file -> { // GH-90000
                     try {
                         String content = Files.readString(file); // GH-90000
                         for (String forbidden : FORBIDDEN_IMPORTS) { // GH-90000
                             if (content.contains("import " + forbidden)) { // GH-90000
                                 violations.add(file + " imports product package: " + forbidden); // GH-90000
                             }
                         }
                         for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
                             if (content.contains(forbidden)) { // GH-90000
                                 violations.add(file + " contains product-specific term: " + forbidden); // GH-90000
                             }
                         }
                     } catch (IOException e) { // GH-90000
                         fail("Failed to read file: " + file); // GH-90000
                     }
                 });
        }

        assertTrue(violations.isEmpty(), // GH-90000
            "Kernel service base class purity violation:\n" + String.join("\n", violations)); // GH-90000
    }

    @Test
    @DisplayName("AbstractDataService contains no product-specific code [GH-90000]")
    void testAbstractDataServicePurity() throws IOException { // GH-90000
        Path abstractDataService = Paths.get(KERNEL_SOURCE_PATH, // GH-90000
            "com/ghatana/kernel/service/AbstractDataService.java");

        if (!Files.exists(abstractDataService)) { // GH-90000
            return;
        }

        String content = Files.readString(abstractDataService); // GH-90000

        // Verify no product imports
        for (String forbidden : FORBIDDEN_IMPORTS) { // GH-90000
            assertFalse(content.contains("import " + forbidden), // GH-90000
                "AbstractDataService imports product package: " + forbidden);
        }

        // Verify no product-specific terms
        for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
            assertFalse(content.contains(forbidden), // GH-90000
                "AbstractDataService contains product-specific term: " + forbidden);
        }

        // Verify it's in kernel package
        assertTrue(content.contains("package com.ghatana.kernel.service [GH-90000]"),
            "AbstractDataService not in kernel package");
    }

    @Test
    @DisplayName("DataCloud adapter classes contain no product-specific code [GH-90000]")
    void testDataCloudAdapterPurity() throws IOException { // GH-90000
        Path dataCloudPath = Paths.get(KERNEL_SOURCE_PATH, // GH-90000
            "com/ghatana/kernel/adapter/datacloud");

        if (!Files.exists(dataCloudPath)) { // GH-90000
            return;
        }

        List<String> violations = new ArrayList<>(); // GH-90000

        try (Stream<Path> paths = Files.walk(dataCloudPath)) { // GH-90000
            paths.filter(Files::isRegularFile) // GH-90000
                 .filter(p -> p.toString().endsWith(".java [GH-90000]"))
                 .forEach(file -> { // GH-90000
                     try {
                         String content = Files.readString(file); // GH-90000

                         // Check for product imports
                         for (String forbidden : FORBIDDEN_IMPORTS) { // GH-90000
                             if (content.contains("import " + forbidden)) { // GH-90000
                                 violations.add(file + " imports: " + forbidden); // GH-90000
                             }
                         }

                         // Check for product-specific terms in class/method names
                         for (String forbidden : FORBIDDEN_TERMS) { // GH-90000
                             if (content.contains("class " + forbidden) || // GH-90000
                                 content.contains("interface " + forbidden)) { // GH-90000
                                 violations.add(file + " defines product-specific type: " + forbidden); // GH-90000
                             }
                         }
                     } catch (IOException e) { // GH-90000
                         fail("Failed to read file: " + file); // GH-90000
                     }
                 });
        }

        assertTrue(violations.isEmpty(), // GH-90000
            "DataCloud adapter purity violation:\n" + String.join("\n", violations)); // GH-90000
    }
}
