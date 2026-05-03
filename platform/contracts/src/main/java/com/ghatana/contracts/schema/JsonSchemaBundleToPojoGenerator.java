/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 *
 * PROPRIETARY/CONFIDENTIAL. Use is subject to the terms of a separate
 * license agreement between you and Ghatana Platforms, Inc. You may not
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of this software, in whole or in part, except as expressly
 * permitted under the applicable written license agreement.
 *
 * Unauthorized use, reproduction, or distribution of this software, or any
 * portion of it, may result in severe civil and criminal penalties, and
 * will be prosecuted to the maximum extent possible under the law.
 */
package com.ghatana.contracts.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.*;
import com.sun.codemodel.JDefinedClass;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Minimal JSON-Schema bundle ➜ POJO generator using only JCodeModel (no jsonschema2pojo).
 *
 * <p>Input bundle has all real schemas under $defs where each key is a dotted FQ name, e.g.
 * ghatana.contracts.agent.v1.Metadata
 *
 * <p>We generate 1 Java type per $defs entry and wire cross-references by $ref.
 *
 * <p>Usage (via Gradle JavaExec): args: <bundlePath> <outputDir> [--root=com]
 * [--override=fromFqn=toFqn[,..]] [--include-prefix=prefix1,prefix2]
  * @doc.type enum
 * @doc.purpose Provides json schema bundle to pojo generator functionality.
 * @doc.layer platform
 * @doc.pattern Component
*/
public class JsonSchemaBundleToPojoGenerator {

    private static final ObjectMapper M = new ObjectMapper();

    // Constants for duplicate literals
    private static final String ROOT_PREFIX = "--root=";
    private static final String OVERRIDE_PREFIX = "--override=";
    private static final String INCLUDE_PREFIX_PREFIX = "--include-prefix=";
    private static final String CLASS_SUFFIX_PREFIX = "--class-suffix=";
    private static final String DRY_RUN_FLAG = "--dry-run";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String TYPE = "type";
    private static final String DESCRIPTION = "description";
    private static final String POJO_SUFFIX = "Pojo";
    private static final String DOT = ".";
    private static final String EMPTY_STRING = "";
    private static final String COMMA = ",";
    private static final String EQUALS = "=";
    private static final String ARRAY = "array";
    private static final String OBJECT = "object";
    private static final String STRING = "string";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";
    private static final String BOOLEAN = "boolean";
    private static final String NULL = "null";
    private static final String INT64 = "int64";
    private static final String LONG = "long";
    private static final String INT32 = "int32";
    private static final String INT = "int";
    private static final String DATE_TIME = "date-time";
    private static final String PROTO = "Proto";
    private static final int MIN_ARGS = 2;
    private static final int TWO = 2;
    private static final int TEN = 10;
    private static final int TWO_SIZE = 2;

    public static void main(String[] args) throws Exception {
        if (args.length < MIN_ARGS) {
            System.err.println(
                    "Usage: JsonSchemaBundleToPojoGenerator <bundlePath> <outputDir> [--root=com]"
                            + " [--override=a=b,c=d] [--include-prefix=p1,p2] [--dry-run]");
            System.exit(1);
        }

        final Path bundlePath = Paths.get(args[0]);
        final File outDir = Paths.get(args[1]).toFile();

        String rootPrefix = EMPTY_STRING;
        Map<String, String> overrides = new HashMap<>();
        Set<String> includePrefixes = new LinkedHashSet<>();
        String classSuffix = POJO_SUFFIX;
        boolean dryRun = false;

        for (int i = 2; i < args.length; i++) {
            String a = args[i];
            if (a == null) continue;
            if (a.startsWith(ROOT_PREFIX)) {
                rootPrefix = a.substring(ROOT_PREFIX.length());
                if (rootPrefix.endsWith(DOT))
                    rootPrefix = rootPrefix.substring(0, rootPrefix.length() - 1);
            } else if (a.startsWith(OVERRIDE_PREFIX)) {
                String v = a.substring(OVERRIDE_PREFIX.length());
                for (String pair : v.split(COMMA)) {
                    if (pair.isEmpty()) continue;
                    String[] kv = pair.split(EQUALS);
                    if (kv.length == TWO) overrides.put(kv[0].trim(), kv[1].trim());
                }
            } else if (a.startsWith(INCLUDE_PREFIX_PREFIX)) {
                String v = a.substring(INCLUDE_PREFIX_PREFIX.length());
                for (String p : v.split(COMMA)) if (!p.isBlank()) includePrefixes.add(p.trim());
            } else if (a.startsWith(CLASS_SUFFIX_PREFIX)) {
                classSuffix = a.substring(CLASS_SUFFIX_PREFIX.length());
                if (classSuffix == null) classSuffix = EMPTY_STRING;
            } else if (DRY_RUN_FLAG.equals(a)) {
                dryRun = true;
            } else {
                System.out.println("Ignoring unknown arg: " + a);
            }
        }

        System.out.println("Output directory: " + outDir.getAbsolutePath());
        if (!includePrefixes.isEmpty()) {
            System.out.println("Include prefixes: " + includePrefixes);
        }
        if (!overrides.isEmpty()) {
            System.out.println("Overrides: " + overrides);
        }
        System.out.println("Class suffix: " + (classSuffix.isEmpty() ? "<none>" : classSuffix));

        Bundle bundle = Bundle.load(bundlePath);
        if (dryRun) {
            System.out.println("--dry-run specified; exiting before code generation.");
            return;
        }

        JCodeModel model = new JCodeModel();

        // Registry of schema key -> generated type
        Map<String, JType> typeRegistry = new LinkedHashMap<>();

        // 1) Declare all types first so refs can resolve in a second pass
        for (String key : bundle.keysInOrder()) {
            if (!includePrefixes.isEmpty() && includePrefixes.stream().noneMatch(key::startsWith)) {
                continue;
            }
            String pkg = fqPackage(rootPrefix, key);
            String simple = applySuffix(simpleName(key), classSuffix);
            JsonNode schema = bundle.defs.get(key);

            if (isEnum(schema)) {
                // Reuse existing enum from proto-generated classes instead of generating a new one.
                // Priority: overrides mapping > derived FQN from key.
                String targetFqn = overrides.getOrDefault(key, null);
                if (targetFqn == null) {
                    // Determine if this enum is nested by checking if its parent key exists in defs
                    String enumPkg = fqPackage(rootPrefix, key);
                    int lastDot = key.lastIndexOf('.');
                    String enumSimple = lastDot >= 0 ? key.substring(lastDot + 1) : key;
                    String parentKey = lastDot > 0 ? key.substring(0, lastDot) : "";
                    boolean isNested = parentKey.length() > 0 && bundle.defs.has(parentKey);
                    if (isNested) {
                        String outerSimple = simpleName(parentKey);
                        String basePkg = fqPackage(rootPrefix, parentKey);
                        targetFqn =
                                basePkg.isEmpty()
                                        ? outerSimple + "." + enumSimple
                                        : basePkg + "." + outerSimple + "." + enumSimple;
                    } else {
                        targetFqn = enumPkg.isEmpty() ? enumSimple : enumPkg + "." + enumSimple;
                    }
                }
                JClass ref = model.ref(targetFqn);
                typeRegistry.put(key, ref);
            } else {
                try {
                    JDefinedClass c = model._package(pkg)._class(simple);
                    addJavadoc(c, schema);
                    typeRegistry.put(key, c);
                } catch (JClassAlreadyExistsException ex) {
                    JDefinedClass existing = ex.getExistingClass();
                    typeRegistry.put(key, existing);
                }
            }
        }

        // 2) Populate fields / enum constants. Some schema keys may map to the same
        // JDefinedClass (e.g., aliases or logically merged types). To keep generation
        // idempotent, only populate each JDefinedClass once.
        Set<JDefinedClass> populated = new HashSet<>();
        for (Map.Entry<String, JType> e : typeRegistry.entrySet()) {
            String key = e.getKey();
            JsonNode schema = bundle.defs.get(key);
            JType jt = e.getValue();

            if (jt instanceof JDefinedClass
                    && ((JDefinedClass) jt).getClassType() == ClassType.ENUM) {
                populateEnum((JDefinedClass) jt, schema);
            } else if (jt instanceof JDefinedClass) {
                JDefinedClass dc = (JDefinedClass) jt;
                if (!populated.add(dc)) {
                    // Already populated this class from a previous key; skip to
                    // avoid duplicate fields such as "event".
                    continue;
                }
                populateObject(
                        model,
                        dc,
                        schema,
                        bundle,
                        typeRegistry,
                        overrides,
                        rootPrefix,
                        classSuffix);
            }
        }

        model.build(outDir);
        // Verify and summarize generated files
        int[] count = {0};
        List<String> samples = new ArrayList<>();
        if (outDir.exists()) {
            final Path root = outDir.toPath();
            Files.walk(root)
                    .filter(p -> p.toString().endsWith(".java"))
                    .forEach(
                            p -> {
                                count[0]++;
                                if (samples.size() < TEN) {
                                    samples.add(root.relativize(p).toString());
                                }
                            });
        }
        System.out.println(count[0] + " POJO generation complete");
    }

    // ---------------------------- Schema ➜ Java helpers ----------------------------

    private static void populateObject(
            JCodeModel model,
            JDefinedClass clazz,
            JsonNode schema,
            Bundle bundle,
            Map<String, JType> types,
            Map<String, String> overrides,
            String rootPrefix,
            String classSuffix)
            throws JClassAlreadyExistsException {
        // properties
        JsonNode props = schema.path("properties");
        if (props.isObject()) {
            Iterator<String> it = props.fieldNames();
            while (it.hasNext()) {
                String jsonName = it.next();
                JsonNode prop = props.get(jsonName);
                JType fieldType =
                        toType(
                                model,
                                prop,
                                bundle,
                                types,
                                overrides,
                                rootPrefix,
                                clazz,
                                jsonName,
                                classSuffix);
                String fieldName = safeFieldName(jsonName);
                addFieldWithAccessors(model, clazz, fieldName, jsonName, fieldType, prop);
            }
        }

        // additionalProperties -> Map<String, T>
        JsonNode addl = schema.get(ADDITIONAL_PROPERTIES);
        if (addl != null && !addl.isBoolean()) {
            JType valType =
                    toType(
                            model,
                            addl,
                            bundle,
                            types,
                            overrides,
                            rootPrefix,
                            clazz,
                            "value",
                            classSuffix);
            JClass mapType = model.ref(Map.class).narrow(model.ref(String.class), valType.boxify());
            addFieldWithAccessors(
                    model, clazz, ADDITIONAL_PROPERTIES, ADDITIONAL_PROPERTIES, mapType, addl);
        }
    }

    private static void populateEnum(JDefinedClass enumClass, JsonNode schema) {
        JsonNode en = schema.get("enum");
        if (en != null && en.isArray()) {
            for (JsonNode v : en) {
                String n = v.asText();
                if (n.isBlank()) continue;
                // Ensure valid enum constant name
                String constName = n.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
                if (Character.isDigit(constName.charAt(0))) constName = "_" + constName;
                enumClass.enumConstant(constName);
            }
        }
    }

    private static JType toType(
            JCodeModel model,
            JsonNode typeNode,
            Bundle bundle,
            Map<String, JType> types,
            Map<String, String> overrides,
            String rootPrefix,
            JDefinedClass owner,
            String suggestedName,
            String classSuffix) {
        JsonNode node = typeNode;
        // $ref only
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            String key = refKey(ref);
            String override = overrides.getOrDefault(key, null);
            if (override != null) {
                return model.ref(override);
            }
            JType t = types.get(key);
            if (t != null) return t;
            // If reference does not exist (shouldn't happen), fall back to Object
            return model.ref(Object.class);
        }

        if (node.has("anyOf")) {
            // we only support null or a type
            JsonNode anyOf = node.get("anyOf");
            if (!NULL.equalsIgnoreCase(anyOf.get(0).get(TYPE).asText(EMPTY_STRING))) {
                node = anyOf.get(0);
            } else if (anyOf.size() == TWO_SIZE) {
                node = anyOf.get(1);
            } else {
                return model.ref(Object.class);
            }
        }

        // Arrays
        if (ARRAY.equals(node.path(TYPE).asText(null))) {
            JsonNode items = node.get("items");
            // If missing items, default to Object
            JType itemType =
                    (items == null)
                            ? model.ref(Object.class)
                            : toType(
                                    model,
                                    items,
                                    bundle,
                                    types,
                                    overrides,
                                    rootPrefix,
                                    owner,
                                    singular(suggestedName),
                                    classSuffix);
            return model.ref(List.class).narrow(itemType.boxify());
        }

        // Objects
        if (OBJECT.equals(node.path(TYPE).asText(null))) {
            // Map-like object: only additionalProperties present -> Map<String, V>
            if (!node.has("properties") && node.has(ADDITIONAL_PROPERTIES)) {
                JsonNode addl = node.get(ADDITIONAL_PROPERTIES);
                JType valType =
                        toType(
                                model,
                                addl,
                                bundle,
                                types,
                                overrides,
                                rootPrefix,
                                owner,
                                suggestedName + "Value",
                                classSuffix);
                return model.ref(Map.class).narrow(model.ref(String.class), valType.boxify());
            }
            // Bare object with neither properties nor additionalProperties -> Map<String,Object>
            if (!node.has("properties") && !node.has(ADDITIONAL_PROPERTIES)) {
                return model.ref(Map.class)
                        .narrow(model.ref(String.class), model.ref(Object.class));
            }
            String nestedName = owner.name() + "_" + toJavaIdentifier(suggestedName, true);
            nestedName = applySuffix(nestedName, classSuffix);
            try {
                JDefinedClass nested =
                        owner._class(JMod.PUBLIC | JMod.STATIC, nestedName, ClassType.CLASS);
                addJavadoc(nested, node);
                populateObject(
                        model, nested, node, bundle, types, overrides, rootPrefix, classSuffix);
                return nested;
            } catch (JClassAlreadyExistsException ex) {
                return ex.getExistingClass();
            }
        }

        // string with enum handled in populateEnum when declared as top-level; here for inline ->
        // simple String
        if (STRING.equals(node.path(TYPE).asText(null))) {
            String format = node.path("format").asText(EMPTY_STRING);
            if (INT64.equals(format) || LONG.equalsIgnoreCase(format)) {
                return model.ref(Long.class);
            }
            if (INT32.equals(format) || INT.equalsIgnoreCase(format)) {
                return model.ref(Integer.class);
            }
            if (DATE_TIME.equalsIgnoreCase(format)) return model.ref(Long.class);

            return model.ref(String.class);
        }
        if (INTEGER.equals(node.path(TYPE).asText(null))) {
            String format = node.path("format").asText(EMPTY_STRING);
            return (INT64.equals(format) || LONG.equalsIgnoreCase(format))
                    ? model.ref(Long.class)
                    : model.ref(Integer.class);
        }
        if (NUMBER.equals(node.path(TYPE).asText(null))) {
            // Ensure we use java.lang.Number for all number types
            return model.ref(Number.class);
        }
        if (BOOLEAN.equals(node.path(TYPE).asText(null))) return model.ref(Boolean.class);

        // Fallback
        return model.ref(Object.class);
    }

    private static void addFieldWithAccessors(
            JCodeModel model,
            JDefinedClass clazz,
            String fieldName,
            String jsonName,
            JType type,
            JsonNode schema) {
        // private T field;
        JFieldVar f = clazz.field(JMod.PRIVATE, type, fieldName);

        // @com.fasterxml.jackson.annotation.JsonProperty("jsonName")
        JClass jsonProp = model.ref("com.fasterxml.jackson.annotation.JsonProperty");
        f.annotate(jsonProp).param("value", jsonName);

        // JavaDoc from description if present
        if (schema != null && schema.has(DESCRIPTION)) {
            f.javadoc().add(schema.get(DESCRIPTION).asText());
        }

        // getter
        String getter =
                (type.unboxify().equals(model.BOOLEAN))
                        ? (fieldName.startsWith("is")
                                ? fieldName
                                : "is" + toJavaIdentifier(fieldName, true))
                        : ("get" + toJavaIdentifier(fieldName, true));
        JMethod getM = clazz.method(JMod.PUBLIC, type, getter);
        getM.body()._return(JExpr._this().ref(f));

        // setter
        String setter = "set" + toJavaIdentifier(fieldName, true);
        JMethod setM = clazz.method(JMod.PUBLIC, model.VOID, setter);
        JVar p = setM.param(type, fieldName);
        setM.body().assign(JExpr._this().ref(f), p);
    }

    private static boolean isEnum(JsonNode schema) {
        return STRING.equals(schema.path(TYPE).asText(null)) && schema.has("enum");
    }

    private static String refKey(String ref) {
        if (ref.startsWith("#/$defs/")) return ref.substring("#/$defs/".length());
        // Already a key
        return ref.replaceFirst("^#/?", "");
    }

    private static String fqPackage(String rootPrefix, String key) {
        int last = key.lastIndexOf('.');
        String base = (last > 0) ? key.substring(0, last) : "";
        String pkg = base;
        if (!rootPrefix.isBlank()) pkg = rootPrefix + "." + pkg;
        return sanitizePackage(pkg);
    }

    private static String simpleName(String key) {
        int last = key.lastIndexOf('.');
        String raw = (last >= 0) ? key.substring(last + 1) : key;
        return toJavaIdentifier(raw, true);
    }

    private static String sanitizePackage(String p) {
        String[] parts = p.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            String s = part.replaceAll("[^A-Za-z0-9_]", "");
            if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "_" + s;
            if (sb.length() > 0) sb.append('.');
            sb.append(s.toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static String safeFieldName(String json) {
        String id = toJavaIdentifier(json, false);
        if (id.equals("class")) return "clazz"; // avoid keyword collision
        return id;
    }

    private static String singular(String s) { // naive best-effort
        if (s.endsWith("ies")) return s.substring(0, s.length() - 3) + "y";
        if (s.endsWith("s") && !s.endsWith("ss")) return s.substring(0, s.length() - 1);
        return s;
    }

    private static void addJavadoc(JDefinedClass c, JsonNode schema) {
        if (schema != null && schema.has(DESCRIPTION)) {
            c.javadoc().add(schema.get(DESCRIPTION).asText());
        }
    }

    // ---------------------------- Suffix helper ----------------------------

    private static String applySuffix(String name, String suffix) {
        if (suffix == null || suffix.isEmpty()) return name;
        // Avoid double suffix if already present (case-sensitive check)
        // By convention, Proto suffix is added to proto-generated classes; replace with suffix
        if (name.endsWith(PROTO)) name = name.substring(0, name.length() - PROTO.length());
        return name.endsWith(suffix) ? name : name + suffix;
    }

    // ---------------------------- Bundle loader ----------------------------

    private static final class Bundle {
        final ObjectNode root;
        final ObjectNode defs;

        private Bundle(ObjectNode root, ObjectNode defs) {
            this.root = root;
            this.defs = defs;
        }

        static Bundle load(Path path) throws IOException {
            JsonNode n = M.readTree(Files.newBufferedReader(path));
            if (!(n instanceof ObjectNode))
                throw new IllegalArgumentException("Bundle root must be an object");
            ObjectNode root = (ObjectNode) n;
            JsonNode d = root.has("$defs") ? root.get("$defs") : root.get("definitions");
            if (!(d instanceof ObjectNode))
                throw new IllegalArgumentException("No $defs or definitions found in the bundle");
            return new Bundle(root, (ObjectNode) d);
        }

        List<String> keysInOrder() {
            List<String> keys = new ArrayList<>();
            defs.fieldNames().forEachRemaining(keys::add);
            return keys;
        }
    }

    // ---------------------------- Naming helpers ----------------------------

    private static final Set<String> JAVA_KEYWORDS =
            new HashSet<>(
                    Arrays.asList(
                            "abstract",
                            "assert",
                            "boolean",
                            "break",
                            "byte",
                            "case",
                            "catch",
                            "char",
                            "class",
                            "const",
                            "continue",
                            "default",
                            "do",
                            "double",
                            "else",
                            "enum",
                            "extends",
                            "final",
                            "finally",
                            "float",
                            "for",
                            "goto",
                            "if",
                            "implements",
                            "import",
                            "instanceof",
                            "int",
                            "interface",
                            "long",
                            "native",
                            "new",
                            "package",
                            "private",
                            "protected",
                            "public",
                            "return",
                            "short",
                            "static",
                            "strictfp",
                            "super",
                            "switch",
                            "synchronized",
                            "this",
                            "throw",
                            "throws",
                            "transient",
                            "try",
                            "void",
                            "volatile",
                            "while",
                            "record",
                            "var",
                            "yield"));

    private static final Set<String> ACRONYMS =
            new HashSet<>(
                    Arrays.asList(
                            "id", "uuid", "api", "http", "https", "url", "uri", "tcp", "udp", "ip",
                            "json", "xml", "rpc", "io", "cpu", "gpu"));

    private static String toJavaIdentifier(String s, boolean capitalize) {
        List<String> toks = splitTokens(s);
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (String raw : toks) {
            String lower = raw.toLowerCase(Locale.ROOT);
            String piece =
                    Character.toUpperCase(raw.charAt(0))
                            + (raw.length() > 1 ? raw.substring(1) : "");
            if (first && !capitalize)
                piece = Character.toLowerCase(piece.charAt(0)) + piece.substring(1);
            out.append(piece);
            first = false;
        }
        String id = out.length() == 0 ? (capitalize ? "Generated" : "generated") : out.toString();
        if (Character.isDigit(id.charAt(0))) id = "_" + id;
        if (JAVA_KEYWORDS.contains(id)) id = id + "_";
        return id;
    }

    private static List<String> splitTokens(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split("[^A-Za-z0-9]+")) {
            if (part.isEmpty()) continue;
            int start = 0;
            for (int i = 1; i < part.length(); i++) {
                char p = part.charAt(i - 1), c = part.charAt(i);
                boolean boundary =
                        (Character.isLowerCase(p) && Character.isUpperCase(c))
                                || (Character.isLetter(p) && Character.isDigit(c))
                                || (Character.isDigit(p) && Character.isLetter(c));
                if (boundary) {
                    out.add(part.substring(start, i));
                    start = i;
                }
            }
            out.add(part.substring(start));
        }
        return out;
    }
}
