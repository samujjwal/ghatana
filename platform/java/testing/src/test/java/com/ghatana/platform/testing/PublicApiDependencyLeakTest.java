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

    private static final List<String> FORBIDDEN_PREFIXES = List.of(
            "org.testcontainers.",
            "io.grpc.",
            "net.datafaker.");

    @Test
    void publicApiShouldNotExposeInternalThirdPartyDependencies() throws IOException, URISyntaxException, ClassNotFoundException {
        List<String> violations = new ArrayList<>();
        for (Class<?> candidate : loadPlatformTestingClasses()) {
            if (candidate.getName().contains(".internal.")) {
                continue;
            }
            collectViolations(candidate, violations);
        }

        assertThat(violations)
                .withFailMessage("Public/protected testing API leaks internal third-party dependencies:%n%s",
                        String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static void collectViolations(Class<?> candidate, List<String> violations) {
        if (Modifier.isPublic(candidate.getModifiers()) || Modifier.isProtected(candidate.getModifiers())) {
            recordForbiddenType(violations, candidate.getName() + " superclass", candidate.getSuperclass());
            for (Class<?> iface : candidate.getInterfaces()) {
                recordForbiddenType(violations, candidate.getName() + " interface", iface);
            }
        }

        for (Field field : candidate.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) {
                recordForbiddenType(violations,
                        candidate.getName() + "#" + field.getName(),
                        field.getType());
            }
        }

        for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    recordForbiddenType(violations,
                            candidate.getName() + " constructor parameter",
                            parameterType);
                }
                for (Class<?> exceptionType : constructor.getExceptionTypes()) {
                    recordForbiddenType(violations,
                            candidate.getName() + " constructor throws",
                            exceptionType);
                }
            }
        }

        for (Method method : candidate.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
                recordForbiddenType(violations,
                        candidate.getName() + "#" + method.getName() + " return type",
                        method.getReturnType());
                for (Class<?> parameterType : method.getParameterTypes()) {
                    recordForbiddenType(violations,
                            candidate.getName() + "#" + method.getName() + " parameter",
                            parameterType);
                }
                for (Class<?> exceptionType : method.getExceptionTypes()) {
                    recordForbiddenType(violations,
                            candidate.getName() + "#" + method.getName() + " throws",
                            exceptionType);
                }
            }
        }
    }

    private static void recordForbiddenType(List<String> violations, String location, Class<?> type) {
        if (type == null) {
            return;
        }

        Class<?> normalized = type.isArray() ? type.getComponentType() : type;
        String typeName = normalized.getName();
        for (String forbiddenPrefix : FORBIDDEN_PREFIXES) {
            if (typeName.startsWith(forbiddenPrefix)) {
                violations.add(location + " -> " + typeName);
            }
        }
    }

    private static List<Class<?>> loadPlatformTestingClasses() throws IOException, URISyntaxException, ClassNotFoundException {
        CodeSource codeSource = BaseTest.class.getProtectionDomain().getCodeSource();
        Path classesRoot = Path.of(codeSource.getLocation().toURI());
        Path packageRoot = classesRoot.resolve("com/ghatana/platform/testing");

        try (Stream<Path> paths = Files.walk(packageRoot)) {
            List<Class<?>> classes = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".class"))
                    .filter(file -> !file.getFileName().toString().contains("$"))
                    .sorted(Comparator.naturalOrder())
                    .toList()) {
                String className = classesRoot.relativize(path)
                        .toString()
                        .replace('/', '.')
                        .replace('\\', '.')
                        .replaceAll("\\.class$", "");
                classes.add(Class.forName(className));
            }
            return classes;
        }
    }
}
