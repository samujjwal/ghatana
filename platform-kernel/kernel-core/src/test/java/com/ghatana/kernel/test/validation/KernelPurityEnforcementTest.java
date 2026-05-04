package com.ghatana.kernel.test.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
@DisplayName("Kernel Purity Enforcement Tests")
@Tag("purity-validation")
class KernelPurityEnforcementTest {

    private static final String KERNEL_SOURCE_PATH = "platform/java/kernel/src/main/java";

    // Product-specific packages that should NEVER appear in kernel code
    private static final String[] FORBIDDEN_IMPORTS = {
        "com.ghatana.products",
        "com.ghatana.phr",
        "com.ghatana.finance",
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
        "DomainAlphaCapabilities",
        "DomainBetaCapabilities"
    };

    @Test
    @DisplayName("Kernel code contains no product-specific imports")
    void testNoProductImports() throws IOException { 
        List<String> violations = new ArrayList<>(); 
        Path kernelPath = Paths.get(KERNEL_SOURCE_PATH); 

        if (!Files.exists(kernelPath)) { 
            // Skip test if kernel path doesn't exist (e.g., in CI without full checkout) 
            return;
        }

        try (Stream<Path> paths = Files.walk(kernelPath)) { 
            paths.filter(Files::isRegularFile) 
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> { 
                     try {
                         String content = Files.readString(file); 
                         for (String forbidden : FORBIDDEN_IMPORTS) { 
                             if (content.contains("import " + forbidden)) { 
                                 violations.add(file + " contains forbidden import: " + forbidden); 
                             }
                         }
                     } catch (IOException e) { 
                         fail("Failed to read file: " + file); 
                     }
                 });
        }

        assertTrue(violations.isEmpty(), 
            "Kernel purity violation - product-specific imports found:\n" +
            String.join("\n", violations)); 
    }

    @Test
    @DisplayName("Kernel code contains no product-specific class references")
    void testNoProductClassReferences() throws IOException { 
        List<String> violations = new ArrayList<>(); 
        Path kernelPath = Paths.get(KERNEL_SOURCE_PATH); 

        if (!Files.exists(kernelPath)) { 
            return;
        }

        try (Stream<Path> paths = Files.walk(kernelPath)) { 
            paths.filter(Files::isRegularFile) 
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> { 
                     try {
                         String content = Files.readString(file); 
                         for (String forbidden : FORBIDDEN_TERMS) { 
                             // Check for class references (not in comments) 
                             String[] lines = content.split("\n");
                             for (int i = 0; i < lines.length; i++) { 
                                 String line = lines[i].trim(); 
                                 // Skip comments and JavaDoc
                                 if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                                     continue;
                                 }
                                 if (line.contains(forbidden)) { 
                                     violations.add(file + ":" + (i + 1) + " contains forbidden term: " + forbidden); 
                                 }
                             }
                         }
                     } catch (IOException e) { 
                         fail("Failed to read file: " + file); 
                     }
                 });
        }

        assertTrue(violations.isEmpty(), 
            "Kernel purity violation - product-specific terms found:\n" +
            String.join("\n", violations)); 
    }

    @Test
    @DisplayName("Kernel capabilities contain no product-specific constants")
    void testKernelCapabilitiesPurity() throws IOException { 
        Path capabilitiesFile = Paths.get(KERNEL_SOURCE_PATH, 
            "com/ghatana/kernel/descriptor/KernelCapability.java");

        if (!Files.exists(capabilitiesFile)) { 
            return;
        }

        String content = Files.readString(capabilitiesFile); 

        // Verify no product-specific capability constants
        for (String forbidden : FORBIDDEN_TERMS) { 
            assertFalse(content.contains("public static final KernelCapability " + forbidden.toUpperCase()), 
                "KernelCapability contains product-specific constant: " + forbidden);
        }

        // Verify no nested Products class
        assertFalse(content.contains("class Products"),
            "KernelCapability contains deprecated Products inner class");

        // Verify no product-specific comments
        assertFalse(content.contains("Product-specific capability constants"),
            "KernelCapability contains product-specific comments");
    }

    @Test
    @DisplayName("Kernel adapter interfaces contain no product-specific methods")
    void testAdapterInterfacesPurity() throws IOException { 
        Path adapterPath = Paths.get(KERNEL_SOURCE_PATH, "com/ghatana/kernel/adapter"); 

        if (!Files.exists(adapterPath)) { 
            return;
        }

        List<String> violations = new ArrayList<>(); 

        try (Stream<Path> paths = Files.walk(adapterPath)) { 
            paths.filter(Files::isRegularFile) 
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> { 
                     try {
                         String content = Files.readString(file); 
                         for (String forbidden : FORBIDDEN_TERMS) { 
                             if (content.contains(forbidden)) { 
                                 violations.add(file + " contains product-specific term: " + forbidden); 
                             }
                         }
                     } catch (IOException e) { 
                         fail("Failed to read file: " + file); 
                     }
                 });
        }

        assertTrue(violations.isEmpty(), 
            "Kernel adapter purity violation:\n" + String.join("\n", violations)); 
    }

    @Test
    @DisplayName("Kernel service base classes contain no product-specific logic")
    void testServiceBaseClassesPurity() throws IOException { 
        Path servicePath = Paths.get(KERNEL_SOURCE_PATH, "com/ghatana/kernel/service"); 

        if (!Files.exists(servicePath)) { 
            return;
        }

        List<String> violations = new ArrayList<>(); 

        try (Stream<Path> paths = Files.walk(servicePath)) { 
            paths.filter(Files::isRegularFile) 
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> { 
                     try {
                         String content = Files.readString(file); 
                         for (String forbidden : FORBIDDEN_IMPORTS) { 
                             if (content.contains("import " + forbidden)) { 
                                 violations.add(file + " imports product package: " + forbidden); 
                             }
                         }
                         for (String forbidden : FORBIDDEN_TERMS) { 
                             if (content.contains(forbidden)) { 
                                 violations.add(file + " contains product-specific term: " + forbidden); 
                             }
                         }
                     } catch (IOException e) { 
                         fail("Failed to read file: " + file); 
                     }
                 });
        }

        assertTrue(violations.isEmpty(), 
            "Kernel service base class purity violation:\n" + String.join("\n", violations)); 
    }

    @Test
    @DisplayName("AbstractDataService contains no product-specific code")
    void testAbstractDataServicePurity() throws IOException { 
        Path abstractDataService = Paths.get(KERNEL_SOURCE_PATH, 
            "com/ghatana/kernel/service/AbstractDataService.java");

        if (!Files.exists(abstractDataService)) { 
            return;
        }

        String content = Files.readString(abstractDataService); 

        // Verify no product imports
        for (String forbidden : FORBIDDEN_IMPORTS) { 
            assertFalse(content.contains("import " + forbidden), 
                "AbstractDataService imports product package: " + forbidden);
        }

        // Verify no product-specific terms
        for (String forbidden : FORBIDDEN_TERMS) { 
            assertFalse(content.contains(forbidden), 
                "AbstractDataService contains product-specific term: " + forbidden);
        }

        // Verify it's in kernel package
        assertTrue(content.contains("package com.ghatana.kernel.service"),
            "AbstractDataService not in kernel package");
    }

    @Test
    @DisplayName("DataCloud adapter classes contain no product-specific code")
    void testDataCloudAdapterPurity() throws IOException { 
        Path dataCloudPath = Paths.get(KERNEL_SOURCE_PATH, 
            "com/ghatana/kernel/adapter/datacloud");

        if (!Files.exists(dataCloudPath)) { 
            return;
        }

        List<String> violations = new ArrayList<>(); 

        try (Stream<Path> paths = Files.walk(dataCloudPath)) { 
            paths.filter(Files::isRegularFile) 
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(file -> { 
                     try {
                         String content = Files.readString(file); 

                         // Check for product imports
                         for (String forbidden : FORBIDDEN_IMPORTS) { 
                             if (content.contains("import " + forbidden)) { 
                                 violations.add(file + " imports: " + forbidden); 
                             }
                         }

                         // Check for product-specific terms in class/method names
                         for (String forbidden : FORBIDDEN_TERMS) { 
                             if (content.contains("class " + forbidden) || 
                                 content.contains("interface " + forbidden)) { 
                                 violations.add(file + " defines product-specific type: " + forbidden); 
                             }
                         }
                     } catch (IOException e) { 
                         fail("Failed to read file: " + file); 
                     }
                 });
        }

        assertTrue(violations.isEmpty(), 
            "DataCloud adapter purity violation:\n" + String.join("\n", violations)); 
    }
}
