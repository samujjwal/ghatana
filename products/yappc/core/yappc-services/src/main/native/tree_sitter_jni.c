/*
 * JNI bridge for Tree-sitter incremental parsing library.
 *
 * Dynamically loads tree-sitter language grammar libraries and exposes
 * parse, walk, and destroy operations to Java.
 *
 * Prerequisites:
 *   - libtree-sitter.so / dylib / dll (core tree-sitter library)
 *   - libtree-sitter-{lang}.so / dylib / dll for each target language
 *
 * Build:
 *   cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
 *   cmake --build build --parallel
 */

#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <dirent.h>
#include <sys/stat.h>
#include "tree_sitter/api.h"

/* ------------------------------------------------------------------ */
/*  Platform macros                                                     */
/* ------------------------------------------------------------------ */

#ifdef __APPLE__
    #define LIB_PREFIX "libtree-sitter-"
    #define LIB_SUFFIX ".dylib"
    #define ALT_SUFFIX ".0.dylib"
#elif defined(_WIN32)
    #define LIB_PREFIX "tree-sitter-"
    #define LIB_SUFFIX ".dll"
    #define ALT_SUFFIX ""
#else
    #define LIB_PREFIX "libtree-sitter-"
    #define LIB_SUFFIX ".so"
    #define ALT_SUFFIX ".so.0"
#endif

/* ------------------------------------------------------------------ */
/*  Data structures                                                     */
/* ------------------------------------------------------------------ */

typedef const TSLanguage *(*ts_language_func_t)(void);

typedef struct {
    TSParser *parser;
    TSLanguage *language;
    void *library_handle;
} parser_context_t;

typedef struct {
    char *buffer;
    size_t length;
    size_t capacity;
} json_builder_t;

/* ------------------------------------------------------------------ */
/*  JSON builder helpers                                                */
/* ------------------------------------------------------------------ */

static void jb_init(json_builder_t *jb) {
    jb->capacity = 4096;
    jb->buffer = (char *)malloc(jb->capacity);
    jb->buffer[0] = '\0';
    jb->length = 0;
}

static void jb_free(json_builder_t *jb) {
    free(jb->buffer);
    jb->buffer = NULL;
    jb->length = jb->capacity = 0;
}

static void jb_ensure(json_builder_t *jb, size_t extra) {
    if (jb->length + extra >= jb->capacity) {
        size_t new_cap = (jb->length + extra) * 2;
        if (new_cap < 4096) new_cap = 4096;
        jb->buffer = (char *)realloc(jb->buffer, new_cap);
        jb->capacity = new_cap;
    }
}

static void jb_append_raw(json_builder_t *jb, const char *str, size_t len) {
    jb_ensure(jb, len + 1);
    memcpy(jb->buffer + jb->length, str, len);
    jb->length += len;
    jb->buffer[jb->length] = '\0';
}

static void jb_append(json_builder_t *jb, const char *str) {
    jb_append_raw(jb, str, strlen(str));
}

static void jb_append_int(json_builder_t *jb, long val) {
    char buf[32];
    int n = snprintf(buf, sizeof(buf), "%ld", val);
    jb_append_raw(jb, buf, (size_t)n);
}

static void jb_append_bool(json_builder_t *jb, bool val) {
    jb_append(jb, val ? "true" : "false");
}

static void jb_append_escaped(json_builder_t *jb, const char *str, size_t len) {
    for (size_t i = 0; i < len; i++) {
        unsigned char c = (unsigned char)str[i];
        switch (c) {
            case '"': jb_append(jb, "\\\""); break;
            case '\\': jb_append(jb, "\\\\"); break;
            case '\b': jb_append(jb, "\\b"); break;
            case '\f': jb_append(jb, "\\f"); break;
            case '\n': jb_append(jb, "\\n"); break;
            case '\r': jb_append(jb, "\\r"); break;
            case '\t': jb_append(jb, "\\t"); break;
            default:
                if (c < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u00%02x", c);
                    jb_append(jb, buf);
                } else {
                    char buf[2] = {(char)c, '\0'};
                    jb_append(jb, buf);
                }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Tree serialization                                                  */
/* ------------------------------------------------------------------ */

static void serialize_node(json_builder_t *jb, TSNode node, const char *source) {
    jb_append(jb, "{");

    jb_append(jb, "\"type\":\"");
    jb_append_escaped(jb, ts_node_type(node), strlen(ts_node_type(node)));
    jb_append(jb, "\",");

    jb_append(jb, "\"startByte\":");
    jb_append_int(jb, (long)ts_node_start_byte(node));
    jb_append(jb, ",");

    jb_append(jb, "\"endByte\":");
    jb_append_int(jb, (long)ts_node_end_byte(node));
    jb_append(jb, ",");

    TSPoint start = ts_node_start_point(node);
    TSPoint end = ts_node_end_point(node);

    jb_append(jb, "\"startRow\":");
    jb_append_int(jb, (long)start.row);
    jb_append(jb, ",");

    jb_append(jb, "\"startColumn\":");
    jb_append_int(jb, (long)start.column);
    jb_append(jb, ",");

    jb_append(jb, "\"endRow\":");
    jb_append_int(jb, (long)end.row);
    jb_append(jb, ",");

    jb_append(jb, "\"endColumn\":");
    jb_append_int(jb, (long)end.column);
    jb_append(jb, ",");

    jb_append(jb, "\"isNamed\":");
    jb_append_bool(jb, ts_node_is_named(node));
    jb_append(jb, ",");

    jb_append(jb, "\"isMissing\":");
    jb_append_bool(jb, ts_node_is_missing(node));
    jb_append(jb, ",");

    /* Include text for leaf / short nodes to aid downstream extraction */
    uint32_t child_count = ts_node_child_count(node);
    uint32_t start_b = ts_node_start_byte(node);
    uint32_t end_b = ts_node_end_byte(node);
    uint32_t text_len = end_b - start_b;
    if (text_len > 0 && text_len <= 200) {
        jb_append(jb, "\"text\":\"");
        jb_append_escaped(jb, source + start_b, text_len);
        jb_append(jb, "\",");
    }

    jb_append(jb, "\"children\":[");
    for (uint32_t i = 0; i < child_count; i++) {
        if (i > 0) jb_append(jb, ",");
        TSNode child = ts_node_child(node, i);
        serialize_node(jb, child, source);
    }
    jb_append(jb, "]}");
}

/* ------------------------------------------------------------------ */
/*  Language library loading                                            */
/* ------------------------------------------------------------------ */

static void build_library_name(char *buf, size_t buf_size, const char *language) {
    snprintf(buf, buf_size, "%s%s%s", LIB_PREFIX, language, LIB_SUFFIX);
}

static void build_symbol_name(char *buf, size_t buf_size, const char *language) {
    /* Symbol names use underscores, e.g. tree_sitter_java */
    snprintf(buf, buf_size, "tree_sitter_");
    size_t prefix_len = strlen("tree_sitter_");
    size_t lang_len = strlen(language);
    for (size_t i = 0; i < lang_len && (prefix_len + i) < buf_size - 1; i++) {
        char c = language[i];
        buf[prefix_len + i] = (c == '-') ? '_' : c;
    }
    buf[prefix_len + lang_len] = '\0';
}

static void throw_runtime_exception(JNIEnv *env, const char *message) {
    jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (ex != NULL) {
        (*env)->ThrowNew(env, ex, message);
    }
}

static void throw_illegal_state_exception(JNIEnv *env, const char *message) {
    jclass ex = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (ex != NULL) {
        (*env)->ThrowNew(env, ex, message);
    }
}

/* ------------------------------------------------------------------ */
/*  JNI Methods                                                         */
/* ------------------------------------------------------------------ */

JNIEXPORT jlong JNICALL
Java_com_ghatana_yappc_services_artifact_parser_TreeSitterParser_nativeCreateParser(
    JNIEnv *env, jobject obj, jstring language_name) {

    const char *lang = (*env)->GetStringUTFChars(env, language_name, NULL);
    if (lang == NULL) return 0;

    char lib_name[256];
    build_library_name(lib_name, sizeof(lib_name), lang);

    /* Try loading the language grammar library */
    void *handle = dlopen(lib_name, RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        /* Try alternative suffix */
        char alt_name[256];
        snprintf(alt_name, sizeof(alt_name), "%s%s%s", LIB_PREFIX, lang, ALT_SUFFIX);
        handle = dlopen(alt_name, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!handle) {
        /* Try full system path */
        char sys_path[512];
        snprintf(sys_path, sizeof(sys_path), "/usr/local/lib/%s", lib_name);
        handle = dlopen(sys_path, RTLD_NOW | RTLD_GLOBAL);
    }
    if (!handle) {
        char sys_path2[512];
        snprintf(sys_path2, sizeof(sys_path2), "/usr/lib/%s", lib_name);
        handle = dlopen(sys_path2, RTLD_NOW | RTLD_GLOBAL);
    }

    if (!handle) {
        char msg[512];
        snprintf(msg, sizeof(msg), "Failed to load tree-sitter language library '%s' for language '%s': %s",
                 lib_name, lang, dlerror());
        throw_runtime_exception(env, msg);
        (*env)->ReleaseStringUTFChars(env, language_name, lang);
        return 0;
    }

    /* Resolve the language factory symbol */
    char sym_name[256];
    build_symbol_name(sym_name, sizeof(sym_name), lang);

    ts_language_func_t lang_func = (ts_language_func_t)dlsym(handle, sym_name);
    if (!lang_func) {
        /* Some distributions omit the 'tree_sitter_' prefix */
        lang_func = (ts_language_func_t)dlsym(handle, sym_name + strlen("tree_sitter_"));
    }

    if (!lang_func) {
        char msg[512];
        snprintf(msg, sizeof(msg), "Symbol '%s' not found in language library for '%s': %s",
                 sym_name, lang, dlerror());
        throw_runtime_exception(env, msg);
        dlclose(handle);
        (*env)->ReleaseStringUTFChars(env, language_name, lang);
        return 0;
    }

    /* Create parser and bind language */
    TSLanguage *language = (TSLanguage *)lang_func();
    TSParser *parser = ts_parser_new();
    if (!parser) {
        throw_runtime_exception(env, "ts_parser_new() returned NULL");
        dlclose(handle);
        (*env)->ReleaseStringUTFChars(env, language_name, lang);
        return 0;
    }

    if (!ts_parser_set_language(parser, language)) {
        throw_runtime_exception(env, "ts_parser_set_language() failed");
        ts_parser_delete(parser);
        dlclose(handle);
        (*env)->ReleaseStringUTFChars(env, language_name, lang);
        return 0;
    }

    parser_context_t *ctx = (parser_context_t *)malloc(sizeof(parser_context_t));
    ctx->parser = parser;
    ctx->language = language;
    ctx->library_handle = handle;

    (*env)->ReleaseStringUTFChars(env, language_name, lang);
    return (jlong)ctx;
}

JNIEXPORT jstring JNICALL
Java_com_ghatana_yappc_services_artifact_parser_TreeSitterParser_nativeParseString(
    JNIEnv *env, jobject obj, jlong handle, jstring source) {

    if (handle == 0) {
        throw_illegal_state_exception(env, "Parser handle is null");
        return NULL;
    }

    parser_context_t *ctx = (parser_context_t *)handle;
    const char *src = (*env)->GetStringUTFChars(env, source, NULL);
    if (src == NULL) return NULL;

    jsize len = (*env)->GetStringUTFLength(env, source);

    TSTree *tree = ts_parser_parse_string(ctx->parser, NULL, src, (uint32_t)len);
    if (!tree) {
        throw_runtime_exception(env, "ts_parser_parse_string() returned NULL");
        (*env)->ReleaseStringUTFChars(env, source, src);
        return NULL;
    }

    TSNode root = ts_tree_root_node(tree);

    json_builder_t jb;
    jb_init(&jb);
    jb_append(&jb, "{\"root\":");
    serialize_node(&jb, root, src);
    jb_append(&jb, "}");

    jstring result = (*env)->NewStringUTF(env, jb.buffer);

    jb_free(&jb);
    ts_tree_delete(tree);
    (*env)->ReleaseStringUTFChars(env, source, src);

    return result;
}

JNIEXPORT void JNICALL
Java_com_ghatana_yappc_services_artifact_parser_TreeSitterParser_nativeDestroyParser(
    JNIEnv *env, jobject obj, jlong handle) {

    if (handle == 0) return;

    parser_context_t *ctx = (parser_context_t *)handle;
    if (ctx->parser) {
        ts_parser_delete(ctx->parser);
    }
    if (ctx->library_handle) {
        dlclose(ctx->library_handle);
    }
    free(ctx);
}

/* ------------------------------------------------------------------ */
/*  Supported language discovery                                        */
/* ------------------------------------------------------------------ */

static int scan_directory_for_languages(JNIEnv *env, jobjectArray *out, int *count, int max, const char *dir_path) {
    DIR *dir = opendir(dir_path);
    if (!dir) return 0;

    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL && *count < max) {
        const char *name = entry->d_name;
        size_t len = strlen(name);
        size_t prefix_len = strlen(LIB_PREFIX);
        size_t suffix_len = strlen(LIB_SUFFIX);

        if (len > prefix_len + suffix_len &&
            strncmp(name, LIB_PREFIX, prefix_len) == 0 &&
            strcmp(name + len - suffix_len, LIB_SUFFIX) == 0) {

            /* Extract language name */
            size_t lang_len = len - prefix_len - suffix_len;
            if (lang_len > 0 && lang_len < 64) {
                char lang[64];
                strncpy(lang, name + prefix_len, lang_len);
                lang[lang_len] = '\0';

                jstring jlang = (*env)->NewStringUTF(env, lang);
                if (jlang) {
                    (*env)->SetObjectArrayElement(env, *out, *count, jlang);
                    (*env)->DeleteLocalRef(env, jlang);
                    (*count)++;
                }
            }
        }
    }
    closedir(dir);
    return 1;
}

JNIEXPORT jobjectArray JNICALL
Java_com_ghatana_yappc_services_artifact_parser_TreeSitterParser_nativeGetSupportedLanguages(
    JNIEnv *env, jclass cls) {

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    if (!stringClass) return NULL;

    int max_langs = 256;
    jobjectArray result = (*env)->NewObjectArray(env, max_langs, stringClass, NULL);
    if (!result) return NULL;

    int count = 0;

    /* Scan common library paths */
    scan_directory_for_languages(env, &result, &count, max_langs, "/usr/local/lib");
    scan_directory_for_languages(env, &result, &count, max_langs, "/usr/lib");
    scan_directory_for_languages(env, &result, &count, max_langs, "/opt/homebrew/lib");
    scan_directory_for_languages(env, &result, &count, max_langs, ".");

    /* If nothing found, return an empty array of the correct size */
    jobjectArray trimmed = (*env)->NewObjectArray(env, count, stringClass, NULL);
    for (int i = 0; i < count; i++) {
        jobject elem = (*env)->GetObjectArrayElement(env, result, i);
        (*env)->SetObjectArrayElement(env, trimmed, i, elem);
        (*env)->DeleteLocalRef(env, elem);
    }

    return trimmed;
}
