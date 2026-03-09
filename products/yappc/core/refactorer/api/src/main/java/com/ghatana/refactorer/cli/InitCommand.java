/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.cli;

import com.ghatana.refactorer.shared.FS;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandLine.Command(name = "init", description = "Write starter config files")
/**
 * @doc.type class
 * @doc.purpose Handles init command operations
 * @doc.layer core
 * @doc.pattern Command
 */
public final class InitCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(InitCommand.class);
    @CommandLine.ParentCommand PolyfixCommand parent;

    public void run() {
        Path root = parent.root;
        try {
            FS.atomicWrite(
                    root.resolve("polyfix.json"),
                    "{\n"
                            + "  \"languages\": [\"java\",\"ts\",\"json\",\"yaml\"],\n"
                            + "  \"budgets\": { \"maxPasses\": 3, \"maxEditsPerFile\": 20 }\n"
                            + "}\n");
            log.info("Wrote polyfix.json");

            // Auto-detect TS/JS project and generate default configs
            boolean hasTs = Files.exists(root.resolve("tsconfig.json"));
            boolean hasPackage = Files.exists(root.resolve("package.json"));
            if (hasTs || hasPackage) {
                Path tsConfigDir = root.resolve("config/ts");
                Files.createDirectories(tsConfigDir);

                Path eslintBase = tsConfigDir.resolve("eslint.base.json");
                String eslintContent =
                        """
                {
                  "env": { "es2021": true, "browser": true, "node": true },
                  "parserOptions": { "ecmaVersion": 2021, "sourceType": "module" },
                  "extends": ["eslint:recommended"],
                  "rules": {}
                }
                """;
                FS.atomicWrite(eslintBase, eslintContent);

                Path prettierCfg = tsConfigDir.resolve("prettier.config.json");
                String prettierContent =
                        """
                {
                  "printWidth": 100,
                  "singleQuote": true,
                  "trailingComma": "es5"
                }
                """;
                FS.atomicWrite(prettierCfg, prettierContent);

                log.info("Initialized TS/JS config: {}", tsConfigDir);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
