package com.ghatana.platform.testing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiDependencyLeakTest {

    private static final List<String> FORBIDDEN_PREFIXES = List.of( // GH-90000
            "org.testcontainers.",
            "io.grpc.",
            "net.datafaker.");

    @Test
    void publicApiShouldNotExposeInternalThirdPartyDependencies() throws IOException, URISyntaxException, ClassNotFoundException { // GH-90000
        List<String> violations = new ArrayList<>(); // GH-90000
        for (Class<?> candidate : loadPlatformTestingClasses()) { // GH-90000
            if (candidate.getName().contains(".internal. [GH-90000]")) {
                continue;
            }
            if (candidate.getName().startsWith("com.ghatana.platform.testing.fixtures. [GH-90000]")) {
                continue;
            }
            collectViolations(candidate, violations); // GH-90000
        }

        assertThat(violations) // GH-90000
                .withFailMessage("Public/protected testing API leaks internal third-party dependencies:%n%s", // GH-90000
                        String.join(System.lineSeparator(), violations)) // GH-90000
                .isEmpty(); // GH-90000
    }

    private static void collectViolations(Class<?> candidate, List<String> violations) { // GH-90000
        if (Modifier.isPublic(candidate.getModifiers()) || Modifier.isProtected(candidate.getModifiers())) { // GH-90000
            recordForbiddenType(violations, candidate.getName() + " superclass", candidate.getSuperclass()); // GH-90000
            for (Class<?> iface : candidate.getInterfaces()) { // GH-90000
                recordForbiddenType(violations, candidate.getName() + " interface", iface); // GH-90000
            }
        }

        for (Field field : candidate.getDeclaredFields()) { // GH-90000
            if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) { // GH-90000
                recordForbiddenType(violations, // GH-90000
                        candidate.getName() + "#" + field.getName(), // GH-90000
                        field.getType()); // GH-90000
            }
        }

        for (Constructor<?> constructor : candidate.getDeclaredConstructors()) { // GH-90000
            if (Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())) { // GH-90000
                for (Class<?> parameterType : constructor.getParameterTypes()) { // GH-90000
                    recordForbiddenType(violations, // GH-90000
                            candidate.getName() + " constructor parameter", // GH-90000
                            parameterType);
                }
                for (Class<?> exceptionType : constructor.getExceptionTypes()) { // GH-90000
                    recordForbiddenType(violations, // GH-90000
                            candidate.getName() + " constructor throws", // GH-90000
                            exceptionType);
                }
            }
        }

        for (Method method : candidate.getDeclaredMethods()) { // GH-90000
            if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) { // GH-90000
                recordForbiddenType(violations, // GH-90000
                        candidate.getName() + "#" + method.getName() + " return type", // GH-90000
                        method.getReturnType()); // GH-90000
                for (Class<?> parameterType : method.getParameterTypes()) { // GH-90000
                    recordForbiddenType(violations, // GH-90000
                            candidate.getName() + "#" + method.getName() + " parameter", // GH-90000
                            parameterType);
                }
                for (Class<?> exceptionType : method.getExceptionTypes()) { // GH-90000
                    recordForbiddenType(violations, // GH-90000
                            candidate.getName() + "#" + method.getName() + " throws", // GH-90000
                            exceptionType);
                }
            }
        }
    }

    private static void recordForbiddenType(List<String> violations, String location, Class<?> type) { // GH-90000
        if (type == null) { // GH-90000
            return;
        }

        Class<?> normalized = type.isArray() ? type.getComponentType() : type; // GH-90000
        String typeName = normalized.getName(); // GH-90000
        for (String forbiddenPrefix : FORBIDDEN_PREFIXES) { // GH-90000
            if (typeName.startsWith(forbiddenPrefix)) { // GH-90000
                violations.add(location + " -> " + typeName); // GH-90000
            }
        }
    }

    private static List<Class<?>> loadPlatformTestingClasses() throws IOException, URISyntaxException, ClassNotFoundException { // GH-90000
        CodeSource codeSource = BaseTest.class.getProtectionDomain().getCodeSource(); // GH-90000
        Path classesRoot = Path.of(codeSource.getLocation().toURI()); // GH-90000
        Path packageRoot = classesRoot.resolve("com/ghatana/platform/testing [GH-90000]");

        try (Stream<Path> paths = Files.walk(packageRoot)) { // GH-90000
            List<Class<?>> classes = new ArrayList<>(); // GH-90000
            for (Path path : paths.filter(Files::isRegularFile) // GH-90000
                    .filter(file -> file.getFileName().toString().endsWith(".class [GH-90000]"))
                    .filter(file -> !file.getFileName().toString().contains("$ [GH-90000]"))
                    .sorted(Comparator.naturalOrder()) // GH-90000
                    .toList()) { // GH-90000
                String className = classesRoot.relativize(path) // GH-90000
                        .toString() // GH-90000
                        .replace('/', '.') // GH-90000
                        .replace('\\', '.') // GH-90000
                        .replaceAll("\\.class$", ""); // GH-90000
                classes.add(Class.forName(className)); // GH-90000
            }
            return classes;
        }
    }
}
