package com.ghatana.refactorer.rewriters;

/** Utility factory for JS/TS jscodeshift transform scripts used in tests. 
 * @doc.type class
 * @doc.purpose Handles test transforms operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
final class TestTransforms {
    private TestTransforms() {}

    /**
     * Rename functions named oldNameN to newNameN (e.g., oldName1 -> newName1). Works for JS/TS
     * function declarations.
     */
    static String renameOldNameNToNewNameN() {
        return String.join(
                "\n",
                "module.exports = function(file, api) {",
                "  const j = api.jscodeshift;",
                "  const root = j(file.source);",
                "  root",
                "    .find(j.FunctionDeclaration)",
                "    .filter(path => path.node.id && /^oldName\\\\d+$/.test(path.node.id.name))",
                "    .forEach(path => {",
                "      const oldName = path.node.id.name;",
                "      const newName = 'new' + oldName.substring(3);",
                "      j(path).replaceWith(",
                "        j.functionDeclaration(",
                "          j.identifier(newName),",
                "          path.node.params,",
                "          path.node.body",
                "        )",
                "      );",
                "    });",
                "  return root.toSource();",
                "};");
    }
}
