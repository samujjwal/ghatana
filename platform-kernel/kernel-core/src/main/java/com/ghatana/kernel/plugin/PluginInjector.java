package com.ghatana.kernel.plugin;

import com.ghatana.kernel.annotation.PluginInject;
import com.ghatana.kernel.context.KernelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Injects {@link KernelContext} dependencies into
 * {@link com.ghatana.kernel.plugin.KernelPlugin} fields annotated with
 * {@link PluginInject}.
 *
 * <p>Call {@link #inject(Object, KernelContext)} once per plugin during the
 * plugin's {@code initialize(KernelContext)} phase. The injector traverses the
 * full class hierarchy (including superclasses) to find annotated fields.</p>
 *
 * <p>Injection rules:</p>
 * <ul>
 *   <li>Required fields ({@code @PluginInject}) – resolved via
 *       {@link KernelContext#getDependency(Class)}. Throws if absent.</li>
 *   <li>Optional fields ({@code @PluginInject(optional = true)}) – resolved via
 *       {@link KernelContext#getOptionalDependency(Class)}. Left {@code null} if
 *       absent; no exception is thrown.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Reflective field-level dependency injection for kernel plugins
 * @doc.layer core
 * @doc.pattern Dependency Injection
 */
public final class PluginInjector {

    private static final Logger LOG = LoggerFactory.getLogger(PluginInjector.class);

    /**
     * Injects all {@link PluginInject}-annotated fields of {@code target} with
     * dependencies resolved from {@code context}.
     *
     * @param target  the plugin instance to inject into; must not be {@code null}
     * @param context the kernel context providing dependencies; must not be {@code null}
     * @throws NullPointerException     if either argument is {@code null}
     * @throws PluginInjectionException if a required dependency cannot be resolved
     */
    public void inject(Object target, KernelContext context) {
        if (target == null) {
            throw new NullPointerException("target cannot be null");
        }
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }

        List<Field> injectableFields = collectInjectableFields(target.getClass());
        List<String> failures = new ArrayList<>();

        for (Field field : injectableFields) {
            PluginInject annotation = field.getAnnotation(PluginInject.class);
            boolean optional = annotation.optional();

            try {
                injectField(target, field, context, optional);
            } catch (Exception e) {
                String message = buildFailureMessage(target, field, e);
                if (optional) {
                    LOG.warn(message);
                } else {
                    failures.add(message);
                    LOG.error(message, e);
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new PluginInjectionException(
                "Dependency injection failed for " + target.getClass().getName() + ": " + failures
            );
        }

        LOG.debug("Injected {} field(s) into {}", injectableFields.size(), target.getClass().getSimpleName());
    }

    // ==================== Helpers ====================

    private List<Field> collectInjectableFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.isAnnotationPresent(PluginInject.class)) {
                    fields.add(field);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private void injectField(Object target, Field field, KernelContext context, boolean optional) {
        @SuppressWarnings("unchecked")
        Class<Object> fieldType = (Class<Object>) field.getType();

        Object value;
        if (optional) {
            Optional<Object> resolved = context.getOptionalDependency(fieldType);
            if (resolved.isEmpty()) {
                LOG.debug("Optional dependency [{}] not found for field '{}' in {}; leaving null",
                    fieldType.getSimpleName(), field.getName(), target.getClass().getSimpleName());
                return;
            }
            value = resolved.get();
        } else {
            value = context.getDependency(fieldType);
        }

        field.setAccessible(true);
        try {
            field.set(target, value);
            LOG.debug("Injected [{}] into field '{}' of {}",
                fieldType.getSimpleName(), field.getName(), target.getClass().getSimpleName());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set field " + field.getName(), e);
        }
    }

    private String buildFailureMessage(Object target, Field field, Exception cause) {
        return String.format(
            "Failed to inject field '%s' (%s) in %s: %s",
            field.getName(), field.getType().getSimpleName(),
            target.getClass().getSimpleName(), cause.getMessage()
        );
    }

    /**
     * Thrown when one or more required {@link PluginInject} fields cannot be
     * resolved from the {@link KernelContext}.
     */
    public static final class PluginInjectionException extends RuntimeException {

        public PluginInjectionException(String message) {
            super(message);
        }
    }
}
