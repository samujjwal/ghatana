/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.tools;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Represents a tool backed by a Java method that an agent can invoke.
 *
 * <p>FunctionTool binds a specific method on a class to a callable tool definition,
 * including parameter schema introspection, input validation, and execution binding.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Parameter Schema</b>: JSON Schema generation from method parameters</li>
 *   <li><b>Input Validation</b>: Type checking and required field enforcement</li>
 *   <li><b>Execution Binding</b>: Direct invocation of the bound method on a target instance</li>
 *   <li><b>LLM Integration</b>: Conversion to JSON Schema map for LLM function calling</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Bind to a method
 * FunctionTool tool = FunctionTool.create(MyService.class, "processTransaction")
 *     .withDescription("Process a financial transaction");
 *
 * // Get JSON Schema for LLM tool calling
 * Map<String, Object> schema = tool.toJsonSchema();
 *
 * // Validate input before execution
 * tool.validateInput(Map.of("amount", 100, "currency", "USD"));
 *
 * // Execute with a target instance
 * Object result = tool.invoke(myServiceInstance, Map.of("amount", 100));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Agent tool binding with schema, validation, and execution
 * @doc.layer framework
 * @doc.pattern Adapter
 */
public final class FunctionTool {

    private final Class<?> targetClass;
    private final String methodName;
    private final String description;
    private final Method resolvedMethod;
    private final List<ParameterInfo> parameterSchema;

    private FunctionTool(Class<?> targetClass, String methodName, @Nullable String description) {
        this.targetClass = Objects.requireNonNull(targetClass, "targetClass must not be null");
        this.methodName = Objects.requireNonNull(methodName, "methodName must not be null");
        this.resolvedMethod = resolveMethod(targetClass, methodName);
        this.parameterSchema = introspectParameters(resolvedMethod);
        this.description = description != null ? description
                : targetClass.getSimpleName() + "." + methodName;
    }

    /**
     * Create a FunctionTool from a class and method name.
     *
     * @param clazz      the class containing the method
     * @param methodName the method to bind
     * @return a new FunctionTool instance
     */
    public static FunctionTool create(Class<?> clazz, String methodName) {
        return new FunctionTool(clazz, methodName, null);
    }

    /**
     * Returns a copy with a custom description.
     *
     * @param description human-readable tool description
     * @return new FunctionTool with the given description
     */
    public FunctionTool withDescription(String description) {
        return new FunctionTool(targetClass, methodName, description);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    public Class<?> getTargetClass() { return targetClass; }
    public String getMethodName() { return methodName; }
    public String getDescription() { return description; }

    /** Returns the resolved {@link Method}, or null if not resolvable. */
    @Nullable
    public Method getResolvedMethod() { return resolvedMethod; }

    /** Returns parameter schema information. */
    @NotNull
    public List<ParameterInfo> getParameterSchema() {
        return Collections.unmodifiableList(parameterSchema);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // JSON Schema Generation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Generates a JSON Schema representation of this tool's parameters.
     *
     * <p>Output format is compatible with OpenAI / Anthropic function calling:
     * <pre>{@code
     * {
     *   "name": "processTransaction",
     *   "description": "Process a financial transaction",
     *   "parameters": {
     *     "type": "object",
     *     "properties": {
     *       "amount": { "type": "number", "description": "Transaction amount" },
     *       "currency": { "type": "string", "description": "ISO currency code" }
     *     },
     *     "required": ["amount", "currency"]
     *   }
     * }
     * }</pre>
     *
     * @return JSON Schema-compatible map
     */
    @NotNull
    public Map<String, Object> toJsonSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ParameterInfo param : parameterSchema) {
            Map<String, Object> propSchema = new LinkedHashMap<>();
            propSchema.put("type", param.jsonType());
            if (param.description() != null) {
                propSchema.put("description", param.description());
            }
            properties.put(param.name(), propSchema);
            if (param.required()) {
                required.add(param.name());
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", methodName);
        schema.put("description", description);
        schema.put("parameters", Map.of(
                "type", "object",
                "properties", properties,
                "required", required
        ));
        return Collections.unmodifiableMap(schema);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Input Validation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validates input arguments against the parameter schema.
     *
     * @param input argument map (parameter name → value)
     * @return list of validation error messages (empty = valid)
     */
    @NotNull
    public List<String> validateInput(@NotNull Map<String, Object> input) {
        List<String> errors = new ArrayList<>();

        for (ParameterInfo param : parameterSchema) {
            Object value = input.get(param.name());
            if (value == null) {
                if (param.required()) {
                    errors.add("Required parameter '" + param.name() + "' is missing");
                }
                continue;
            }

            // Type coercion check
            if (!isTypeCompatible(value, param.javaType())) {
                errors.add("Parameter '" + param.name() + "' expected type "
                        + param.javaType().getSimpleName() + " but got "
                        + value.getClass().getSimpleName());
            }
        }

        // Check for unknown parameters
        for (String key : input.keySet()) {
            if (parameterSchema.stream().noneMatch(p -> p.name().equals(key))) {
                errors.add("Unknown parameter '" + key + "'");
            }
        }

        return errors;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Execution Binding
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Invokes the bound method on a target instance with the given arguments.
     *
     * @param target the object instance to invoke the method on
     * @param args   argument map (parameter name → value)
     * @return the method return value
     * @throws ToolInvocationException if invocation fails
     */
    @Nullable
    public Object invoke(@NotNull Object target, @NotNull Map<String, Object> args) {
        if (resolvedMethod == null) {
            throw new ToolInvocationException("Method '" + methodName + "' could not be resolved on "
                    + targetClass.getName());
        }

        // Validate input first
        List<String> validationErrors = validateInput(args);
        if (!validationErrors.isEmpty()) {
            throw new ToolInvocationException("Input validation failed: " + validationErrors);
        }

        // Build positional args from the named map
        Object[] positionalArgs = new Object[parameterSchema.size()];
        for (int i = 0; i < parameterSchema.size(); i++) {
            ParameterInfo param = parameterSchema.get(i);
            Object value = args.get(param.name());
            positionalArgs[i] = coerce(value, param.javaType());
        }

        try {
            resolvedMethod.setAccessible(true);
            return resolvedMethod.invoke(target, positionalArgs);
        } catch (IllegalAccessException e) {
            throw new ToolInvocationException("Cannot access method " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new ToolInvocationException("Method " + methodName + " threw exception: "
                    + e.getCause().getMessage(), e.getCause());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Describes a single parameter of the bound method.
     *
     * @param name        parameter name (from bytecode or annotation)
     * @param javaType    the Java class of the parameter
     * @param jsonType    JSON Schema type string
     * @param description optional description
     * @param required    whether the parameter is required (primitive → always required)
     */
    public record ParameterInfo(
            @NotNull String name,
            @NotNull Class<?> javaType,
            @NotNull String jsonType,
            @Nullable String description,
            boolean required
    ) {}

    /**
     * Exception thrown when tool invocation fails.
     */
    public static class ToolInvocationException extends RuntimeException {
        public ToolInvocationException(String message) {
            super(message);
        }

        public ToolInvocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Private Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    @Nullable
    private static Method resolveMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        // Search superclass and interfaces
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }

    private static List<ParameterInfo> introspectParameters(@Nullable Method method) {
        if (method == null) return List.of();

        Parameter[] params = method.getParameters();
        List<ParameterInfo> result = new ArrayList<>(params.length);

        for (Parameter p : params) {
            String name = p.isNamePresent() ? p.getName() : "arg" + result.size();
            Class<?> type = p.getType();
            String jsonType = javaToJsonType(type);
            boolean required = type.isPrimitive(); // primitives can't be null

            result.add(new ParameterInfo(name, type, jsonType, null, required));
        }

        return result;
    }

    private static String javaToJsonType(Class<?> type) {
        if (type == String.class || type == CharSequence.class) return "string";
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class) return "integer";
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) return "number";
        if (type == boolean.class || type == Boolean.class) return "boolean";
        if (type.isArray() || List.class.isAssignableFrom(type)
                || Set.class.isAssignableFrom(type)) return "array";
        return "object";
    }

    private static boolean isTypeCompatible(Object value, Class<?> expectedType) {
        if (expectedType.isPrimitive()) {
            expectedType = boxedType(expectedType);
        }
        return expectedType.isInstance(value)
                || (Number.class.isAssignableFrom(expectedType) && value instanceof Number);
    }

    @Nullable
    private static Object coerce(@Nullable Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;

        // Number coercion
        if (value instanceof Number num) {
            if (targetType == int.class || targetType == Integer.class) return num.intValue();
            if (targetType == long.class || targetType == Long.class) return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class || targetType == Float.class) return num.floatValue();
            if (targetType == short.class || targetType == Short.class) return num.shortValue();
        }

        // String coercion for primitive wrappers
        if (value instanceof String str) {
            if (targetType == int.class || targetType == Integer.class) return Integer.parseInt(str);
            if (targetType == long.class || targetType == Long.class) return Long.parseLong(str);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(str);
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(str);
        }

        return value;
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == short.class) return Short.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        return primitive;
    }

    @Override
    public String toString() {
        return "FunctionTool{" + description + ", params=" + parameterSchema.size() + "}";
    }
}
