package com.ghatana.refactorer.indexer;

import com.ghatana.refactorer.shared.PolyfixProjectContext;
import java.util.*;
import org.apache.logging.log4j.Logger;

/**
 * In-memory store for indexed TypeScript/JavaScript symbols and their exports. 
 * @doc.type class
 * @doc.purpose Handles symbol store operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class SymbolStore {
    private final PolyfixProjectContext context;
    private final Logger logger;
    private final Map<String, Set<ExportInfo>> exportsBySymbol;

    public SymbolStore(PolyfixProjectContext context) {
        this.context = context;
        this.logger = context.log();
        this.exportsBySymbol = new HashMap<>();
    }

    /**
 * Records an exported symbol from a TypeScript/JavaScript file. */
    public void putTsExport(String symbol, String filePath, String exportName, boolean isDefault) {
        exportsBySymbol
                .computeIfAbsent(symbol, k -> new HashSet<>())
                .add(new ExportInfo(filePath, exportName, isDefault));
    }

    /**
 * Finds all exports matching the given symbol name. */
    public Set<ExportInfo> findTsExports(String symbol) {
        return exportsBySymbol.getOrDefault(symbol, Collections.emptySet());
    }

    /**
     * Gets the total number of unique exported symbols in the store.
     *
     * @return The number of unique exported symbols
     */
    public int getExportCount() {
        return exportsBySymbol.size();
    }

    /**
 * Prints all exports to the log for debugging purposes. */
    public void debugPrintExports() {
        if (exportsBySymbol.isEmpty()) {
            logger.debug("No exports found in symbol store");
            return;
        }

        logger.debug("=== Exports in SymbolStore ({} unique symbols) ===", exportsBySymbol.size());
        for (Map.Entry<String, Set<ExportInfo>> entry : exportsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            Set<ExportInfo> exports = entry.getValue();

            logger.debug("Symbol: {}", symbol);
            for (ExportInfo exportInfo : exports) {
                logger.debug(
                        "  - {} (file: {}, isDefault: {})",
                        exportInfo.getExportName(),
                        exportInfo.getFilePath(),
                        exportInfo.isDefault());
            }
        }
        logger.debug("==============================================");
    }

    /**
 * Clears all stored symbol information. */
    public void clear() {
        exportsBySymbol.clear();
    }

    /**
 * Information about an exported symbol. */
    public static class ExportInfo {
        private final String filePath;
        private final String exportName;
        private final boolean isDefault;

        public ExportInfo(String filePath, String exportName, boolean isDefault) {
            this.filePath = filePath;
            this.exportName = exportName;
            this.isDefault = isDefault;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getExportName() {
            return exportName;
        }

        public boolean isDefault() {
            return isDefault;
        }
    }
}
