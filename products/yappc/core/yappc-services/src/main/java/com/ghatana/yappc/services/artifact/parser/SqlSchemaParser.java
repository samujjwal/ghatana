package com.ghatana.yappc.services.artifact.parser;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @doc.type class
 * @doc.purpose Parses SQL DDL (CREATE TABLE, ALTER TABLE, INDEX, CONSTRAINT) using JOOQ SQL parser for schema extraction and diff.
 * @doc.layer service
 * @doc.pattern Extractor
 */
public final class SqlSchemaParser {

    private static final Logger log = LoggerFactory.getLogger(SqlSchemaParser.class);
    private final DSLContext dsl;

    public SqlSchemaParser() {
        this.dsl = DSL.using(new DefaultConfiguration().set(SQLDialect.POSTGRES));
    }

    /**
     * Parse a SQL DDL script and extract table definitions, columns, and constraints.
     *
     * @param sqlScript raw SQL DDL string
     * @return map with tables list and any parse errors
     */
    public Map<String, Object> parseSchema(String sqlScript) {
        List<Map<String, Object>> tables = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Split on semicolons to get individual statements
        String[] statements = sqlScript.split("(?<=;)")
                ;

        for (String rawStmt : statements) {
            String stmt = rawStmt.trim();
            if (stmt.isBlank()) continue;

            try {
                var query = dsl.parser().parseQuery(stmt);
                if (query != null) {
                    tables.add(Map.of(
                            "statement", stmt.substring(0, Math.min(stmt.length(), 200)),
                            "parsed", true,
                            "type", query.getClass().getSimpleName()
                    ));
                } else {
                    tables.addAll(fallbackParseStatement(stmt));
                }
            } catch (Exception e) {
                log.debug("JOOQ parse failed for statement: {}", stmt.substring(0, Math.min(stmt.length(), 100)), e);
                // Fallback heuristic
                if (stmt.toUpperCase().startsWith("CREATE TABLE")) {
                    tables.add(extractCreateTable(stmt));
                } else {
                    errors.add("Parse error: " + e.getMessage() + " | Statement: " + stmt.substring(0, Math.min(stmt.length(), 100)));
                }
            }
        }

        return Map.of(
                "tables", tables,
                "statementCount", statements.length,
                "errorCount", errors.size(),
                "errors", errors
        );
    }

    private List<Map<String, Object>> fallbackParseStatement(String stmt) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (stmt.toUpperCase().startsWith("CREATE TABLE")) {
            results.add(extractCreateTable(stmt));
        } else if (stmt.toUpperCase().startsWith("ALTER TABLE")) {
            results.add(Map.of(
                    "statement", stmt.substring(0, Math.min(stmt.length(), 200)),
                    "type", "ALTER_TABLE",
                    "parsed", true
            ));
        } else if (stmt.toUpperCase().startsWith("CREATE INDEX")) {
            results.add(Map.of(
                    "statement", stmt.substring(0, Math.min(stmt.length(), 200)),
                    "type", "CREATE_INDEX",
                    "parsed", true
            ));
        } else {
            results.add(Map.of(
                    "statement", stmt.substring(0, Math.min(stmt.length(), 200)),
                    "type", "UNKNOWN",
                    "parsed", false
            ));
        }
        return results;
    }

    private Map<String, Object> extractCreateTable(String stmt) {
        Map<String, Object> table = new HashMap<>();
        table.put("type", "CREATE_TABLE");
        table.put("parsed", true);

        // Extract table name using regex
        String upper = stmt.toUpperCase();
        int tableIdx = upper.indexOf("TABLE");
        if (tableIdx >= 0) {
            String afterTable = stmt.substring(tableIdx + 5).trim();
            if (afterTable.startsWith("IF NOT EXISTS")) {
                afterTable = afterTable.substring(13).trim();
            }
            // Read until first whitespace or parenthesis
            StringBuilder nameBuilder = new StringBuilder();
            for (char c : afterTable.toCharArray()) {
                if (c == ' ' || c == '(' || c == ';') {
                    break;
                }
                nameBuilder.append(c);
            }
            table.put("tableName", nameBuilder.toString().trim().replace("\"", "").replace("`", ""));
        }

        // Extract columns from between parentheses
        int openParen = stmt.indexOf('(');
        int closeParen = stmt.lastIndexOf(')');
        if (openParen >= 0 && closeParen > openParen) {
            String colsBlock = stmt.substring(openParen + 1, closeParen);
            List<Map<String, Object>> columns = new ArrayList<>();
            // Simple split on commas (not robust for nested types, but catches most cases)
            String[] rawCols = colsBlock.split(",");
            for (String rawCol : rawCols) {
                String col = rawCol.trim();
                if (col.isEmpty()) continue;
                if (col.toUpperCase().startsWith("CONSTRAINT") || col.toUpperCase().startsWith("PRIMARY KEY")
                        || col.toUpperCase().startsWith("FOREIGN KEY") || col.toUpperCase().startsWith("UNIQUE")) {
                    continue; // Skip constraints for column list
                }
                String[] parts = col.split("\\s+", 3);
                if (parts.length >= 2) {
                    Map<String, Object> column = new HashMap<>();
                    column.put("name", parts[0].replace("\"", "").replace("`", ""));
                    column.put("type", parts[1]);
                    column.put("constraints", parts.length > 2 ? parts[2] : "");
                    columns.add(column);
                }
            }
            table.put("columns", columns);
        }

        return table;
    }
}
