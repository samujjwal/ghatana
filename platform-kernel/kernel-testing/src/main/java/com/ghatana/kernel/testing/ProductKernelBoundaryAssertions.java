package com.ghatana.kernel.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose Shared static assertions that keep product code on stable kernel APIs only.
 * @doc.layer platform
 * @doc.pattern TestUtility
 */
public final class ProductKernelBoundaryAssertions {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+([^;]+);", Pattern.MULTILINE);
    private static final Pattern FORBIDDEN_KERNEL_IMPLEMENTATION =
            Pattern.compile("^com\\.ghatana\\.kernel\\..*(Abstract|Default|InMemory).+|^com\\.ghatana\\.kernel\\..*Impl$");
    private static final Set<String> EXPLICITLY_FORBIDDEN_IMPORTS = Set.of(
            "com.ghatana.kernel.bridge.AbstractKernelBridge",
            "com.ghatana.kernel.context.DefaultKernelContext",
            "com.ghatana.kernel.extension.AbstractKernelExtension",
            "com.ghatana.kernel.module.AbstractKernelModule",
            "com.ghatana.kernel.policy.DefaultBoundaryPolicyResolver",
            "com.ghatana.kernel.policy.InMemoryBoundaryPolicyStore",
            "com.ghatana.kernel.service.AbstractDataService",
            "com.ghatana.kernel.service.AbstractKernelService",
            "com.ghatana.kernel.adapter.aep.AepKernelAdapterImpl",
            "com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapterImpl");
    private static final Set<String> SCANNED_EXTENSIONS = Set.of(
            ".java", ".kt", ".kts", ".ts", ".tsx", ".js", ".jsx");

    private ProductKernelBoundaryAssertions() {
    }

    public static void assertProductUsesOnlyStableKernelApis(String productName, Path... roots) throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path root : roots) {
            if (root == null || !Files.exists(root)) {
                continue;
            }

            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(ProductKernelBoundaryAssertions::isScannableSourceFile)
                    .forEach(path -> inspectFile(root, path, violations));
        }

        assertThat(violations)
                .withFailMessage(
                        "%s imports kernel implementation classes instead of stable public APIs:%n%s",
                        productName,
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static boolean isScannableSourceFile(Path path) {
        String name = path.getFileName().toString();
        return SCANNED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static void inspectFile(Path root, Path file, List<String> violations) {
        try {
            String content = Files.readString(file);
            Matcher matcher = IMPORT_PATTERN.matcher(content);
            while (matcher.find()) {
                String importedType = matcher.group(1).trim();
                if (importedType.contains(".internal.")
                        || EXPLICITLY_FORBIDDEN_IMPORTS.contains(importedType)
                        || FORBIDDEN_KERNEL_IMPLEMENTATION.matcher(importedType).matches()) {
                    violations.add(root.relativize(file) + " -> forbidden kernel import " + importedType);
                }
            }
        } catch (IOException exception) {
            violations.add(root.relativize(file) + " -> unable to inspect source: " + exception.getMessage());
        }
    }
}
