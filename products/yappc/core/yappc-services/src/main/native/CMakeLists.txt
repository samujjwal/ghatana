cmake_minimum_required(VERSION 3.16)
project(tree_sitter_jni C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# --- JNI -------------------------------------------------------------------
find_package(JNI REQUIRED)
if(NOT JNI_FOUND)
    message(FATAL_ERROR "JNI not found. Set JAVA_HOME or CMAKE_PREFIX_PATH.")
endif()

# --- tree-sitter core ------------------------------------------------------
find_library(TREE_SITTER_LIB tree-sitter
    PATHS /usr/local/lib /usr/lib /opt/homebrew/lib
    DOC "Core tree-sitter shared library"
)
if(NOT TREE_SITTER_LIB)
    message(FATAL_ERROR "libtree-sitter not found. Install tree-sitter (e.g. 'brew install tree-sitter').")
endif()

find_path(TREE_SITTER_INCLUDE tree_sitter/api.h
    PATHS /usr/local/include /usr/include /opt/homebrew/include
    DOC "tree-sitter C API headers"
)
if(NOT TREE_SITTER_INCLUDE)
    message(FATAL_ERROR "tree_sitter/api.h not found. Install tree-sitter headers.")
endif()

# --- Build shared JNI bridge -----------------------------------------------
add_library(tree_sitter_jni SHARED tree_sitter_jni.c)

target_include_directories(tree_sitter_jni PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${TREE_SITTER_INCLUDE}
)

target_link_libraries(tree_sitter_jni
    ${TREE_SITTER_LIB}
    ${CMAKE_DL_LIBS}
)

set_target_properties(tree_sitter_jni PROPERTIES
    C_VISIBILITY_PRESET hidden
)

# macOS: set install_name so the loader resolves @loader_path
if(APPLE)
    set_target_properties(tree_sitter_jni PROPERTIES
        MACOSX_RPATH ON
        INSTALL_RPATH "@loader_path;/usr/local/lib;/opt/homebrew/lib"
    )
endif()

# Install to build directory
install(TARGETS tree_sitter_jni
    LIBRARY DESTINATION ${CMAKE_CURRENT_BINARY_DIR}
    RUNTIME DESTINATION ${CMAKE_CURRENT_BINARY_DIR}
)
