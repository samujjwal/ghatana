package com.ghatana.refactorer.rewriters;

/** Utility factory for JS/TS jscodeshift transform scripts used in tests.
 * @doc.type class
 * @doc.purpose Handles test transforms operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
final class TransformTestUtils {
    private TransformTestUtils() {} // GH-90000

    /**
     * Rename functions named oldNameN to newNameN (e.g., oldName1 -> newName1). Works for JS/TS // GH-90000
     * function declarations.
     */
    static String renameOldNameNToNewNameN() { // GH-90000
        return String.join( // GH-90000
                "\n",
                "module.exports = function(file, api) {", // GH-90000
                "  const j = api.jscodeshift;",
                "  const root = j(file.source);", // GH-90000
                "  root",
                "    .find(j.FunctionDeclaration)", // GH-90000
                "    .filter(path => path.node.id && /^oldName\\\\d+$/.test(path.node.id.name))", // GH-90000
                "    .forEach(path => {", // GH-90000
                "      const oldName = path.node.id.name;",
                "      const newName = 'new' + oldName.substring(3);", // GH-90000
                "      j(path).replaceWith(", // GH-90000
                "        j.functionDeclaration(", // GH-90000
                "          j.identifier(newName),", // GH-90000
                "          path.node.params,",
                "          path.node.body",
                "        )",
                "      );",
                "    });",
                "  return root.toSource();", // GH-90000
                "};");
    }
}
