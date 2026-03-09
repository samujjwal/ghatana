package com.ghatana.pipeline.registry.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility for detecting and auditing statefulness in application components.
 *
 * <p>Purpose: Analyzes classes to identify potential stateful components that
 * should be externalized for horizontal scaling. Detects mutable collections,
 * thread-local storage, and other state-holding patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Detects stateful components that need externalization
 * @doc.layer product
 * @doc.pattern Utility
 * @since 2.0.0
 */
public class StatefulnessAuditor {
    
    private static final Logger LOG = LoggerFactory.getLogger(StatefulnessAuditor.class);
    
    // Classes that are known to be stateful and should be externalized
    private static final Set<String> STATEFUL_CLASSES = Set.of(
        "java.util.HashMap",
        "java.util.ArrayList",
        "java.util.HashSet",
        "java.util.LinkedList",
        "java.util.TreeMap",
        "java.util.TreeSet",
        "java.util.concurrent.ConcurrentHashMap",
        "java.util.concurrent.CopyOnWriteArrayList",
        "java.util.concurrent.CopyOnWriteArraySet",
        "java.util.concurrent.ConcurrentSkipListMap",
        "java.util.concurrent.ConcurrentSkipListSet"
    );
    
    // Classes that are known to be thread-local and should be externalized
    private static final Set<String> THREAD_LOCAL_CLASSES = Set.of(
        "java.lang.ThreadLocal",
        "java.lang.InheritableThreadLocal"
    );
    
    // Packages to scan
    private final List<String> packagesToScan;
    
    // Results
    private final List<StatefulComponent> statefulComponents = new ArrayList<>();
    
    /**
     * Create a new statefulness auditor.
     */
    public StatefulnessAuditor(List<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }
    
    /**
     * Audit the application for statefulness.
     */
    public List<StatefulComponent> audit() {
        statefulComponents.clear();
        
        // Find all classes in the packages to scan
        List<Class<?>> classes = findClasses();
        
        // Check each class for statefulness
        for (Class<?> clazz : classes) {
            checkClass(clazz);
        }
        
        return statefulComponents;
    }
    
    /**
     * Find all classes in the packages to scan.
     */
    private List<Class<?>> findClasses() {
        List<Class<?>> classes = new ArrayList<>();
        
        for (String packageName : packagesToScan) {
            try {
                String packagePath = packageName.replace('.', '/');
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                
                // Get the package directory
                Enumeration<java.net.URL> resources = classLoader.getResources(packagePath);
                while (resources.hasMoreElements()) {
                    java.net.URL resource = resources.nextElement();
                    File directory = new File(resource.getFile());
                    
                    // Find all class files in the directory
                    try (Stream<Path> paths = Files.walk(Paths.get(directory.toURI()))) {
                        List<Class<?>> packageClasses = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".class"))
                            .map(path -> {
                                String className = path.toString()
                                    .replace(directory.toString(), "")
                                    .replace("/", ".")
                                    .replace("\\", ".")
                                    .replace(".class", "");
                                
                                if (className.startsWith(".")) {
                                    className = className.substring(1);
                                }
                                
                                className = packageName + "." + className;
                                
                                try {
                                    return Class.forName(className);
                                } catch (ClassNotFoundException e) {
                                    LOG.warn("Failed to load class: {}", className, e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                        
                        classes.addAll(packageClasses);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to scan package: {}", packageName, e);
            }
        }
        
        return classes;
    }
    
    /**
     * Check a class for statefulness.
     */
    private void checkClass(Class<?> clazz) {
        // Skip interfaces, enums, and abstract classes
        if (clazz.isInterface() || clazz.isEnum() || Modifier.isAbstract(clazz.getModifiers())) {
            return;
        }
        
        // Check fields for statefulness
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            // Skip static and final fields
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            
            // Check if field type is stateful
            Class<?> fieldType = field.getType();
            if (isStatefulType(fieldType)) {
                statefulComponents.add(new StatefulComponent(
                    clazz.getName(),
                    field.getName(),
                    fieldType.getName(),
                    StatefulnessType.MUTABLE_STATE
                ));
            }
            
            // Check if field is thread-local
            if (isThreadLocalType(fieldType)) {
                statefulComponents.add(new StatefulComponent(
                    clazz.getName(),
                    field.getName(),
                    fieldType.getName(),
                    StatefulnessType.THREAD_LOCAL
                ));
            }
        }
        
        // Check singleton pattern
        checkSingleton(clazz);
    }
    
    /**
     * Check if a class implements the singleton pattern.
     */
    private void checkSingleton(Class<?> clazz) {
        try {
            // Check for INSTANCE field
            try {
                Field instanceField = clazz.getDeclaredField("INSTANCE");
                if (Modifier.isStatic(instanceField.getModifiers()) && 
                    instanceField.getType().equals(clazz)) {
                    statefulComponents.add(new StatefulComponent(
                        clazz.getName(),
                        "INSTANCE",
                        clazz.getName(),
                        StatefulnessType.SINGLETON
                    ));
                    return;
                }
            } catch (NoSuchFieldException e) {
                // Ignore
            }
            
            // Check for instance field
            try {
                Field instanceField = clazz.getDeclaredField("instance");
                if (Modifier.isStatic(instanceField.getModifiers()) && 
                    instanceField.getType().equals(clazz)) {
                    statefulComponents.add(new StatefulComponent(
                        clazz.getName(),
                        "instance",
                        clazz.getName(),
                        StatefulnessType.SINGLETON
                    ));
                    return;
                }
            } catch (NoSuchFieldException e) {
                // Ignore
            }
            
            // Check for getInstance method
            try {
                clazz.getDeclaredMethod("getInstance");
                statefulComponents.add(new StatefulComponent(
                    clazz.getName(),
                    "getInstance()",
                    clazz.getName(),
                    StatefulnessType.SINGLETON
                ));
            } catch (NoSuchMethodException e) {
                // Ignore
            }
        } catch (Exception e) {
            LOG.warn("Failed to check singleton pattern for class: {}", clazz.getName(), e);
        }
    }
    
    /**
     * Check if a type is stateful.
     */
    private boolean isStatefulType(Class<?> type) {
        return STATEFUL_CLASSES.contains(type.getName()) ||
               Map.class.isAssignableFrom(type) ||
               Collection.class.isAssignableFrom(type) ||
               ConcurrentHashMap.class.isAssignableFrom(type);
    }
    
    /**
     * Check if a type is thread-local.
     */
    private boolean isThreadLocalType(Class<?> type) {
        return THREAD_LOCAL_CLASSES.contains(type.getName()) ||
               ThreadLocal.class.isAssignableFrom(type);
    }
    
    /**
     * Generate a report of stateful components.
     */
    public String generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("# Statefulness Audit Report\n\n");
        report.append("## Summary\n\n");
        report.append("- Total stateful components: ").append(statefulComponents.size()).append("\n");
        report.append("- Mutable state: ").append(countByType(StatefulnessType.MUTABLE_STATE)).append("\n");
        report.append("- Thread-local: ").append(countByType(StatefulnessType.THREAD_LOCAL)).append("\n");
        report.append("- Singletons: ").append(countByType(StatefulnessType.SINGLETON)).append("\n\n");
        
        report.append("## Stateful Components\n\n");
        report.append("| Class | Field | Type | Statefulness |\n");
        report.append("|-------|-------|------|-------------|\n");
        
        for (StatefulComponent component : statefulComponents) {
            report.append("| ")
                .append(component.getClassName()).append(" | ")
                .append(component.getFieldName()).append(" | ")
                .append(component.getFieldType()).append(" | ")
                .append(component.getType()).append(" |\n");
        }
        
        report.append("\n## Recommendations\n\n");
        report.append("1. Externalize mutable state to Redis or a database.\n");
        report.append("2. Replace thread-local variables with request-scoped context.\n");
        report.append("3. Convert singletons to dependency-injected components.\n");
        
        return report.toString();
    }
    
    /**
     * Count stateful components by type.
     */
    private long countByType(StatefulnessType type) {
        return statefulComponents.stream()
            .filter(component -> component.getType() == type)
            .count();
    }
    
    /**
     * Stateful component information.
     */
    public static class StatefulComponent {
        private final String className;
        private final String fieldName;
        private final String fieldType;
        private final StatefulnessType type;
        
        public StatefulComponent(String className, String fieldName, String fieldType, StatefulnessType type) {
            this.className = className;
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.type = type;
        }
        
        public String getClassName() {
            return className;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public String getFieldType() {
            return fieldType;
        }
        
        public StatefulnessType getType() {
            return type;
        }
    }
    
    /**
     * Types of statefulness.
     */
    public enum StatefulnessType {
        MUTABLE_STATE,
        THREAD_LOCAL,
        SINGLETON
    }
}
