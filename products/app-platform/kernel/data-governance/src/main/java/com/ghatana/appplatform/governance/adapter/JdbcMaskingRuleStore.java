/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.governance.adapter;

import com.ghatana.appplatform.governance.DynamicDataMaskingService.MaskingRule;
import com.ghatana.appplatform.governance.DynamicDataMaskingService.MaskingType;
import com.ghatana.appplatform.governance.port.MaskingRuleStore;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

/**
 * JDBC implementation of {@link MaskingRuleStore}.
 *
 * @doc.type class
 * @doc.purpose JDBC adapter for masking rule persistence (PostgreSQL)
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JdbcMaskingRuleStore implements MaskingRuleStore {

    private final DataSource dataSource;

    public JdbcMaskingRuleStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public MaskingRule fetchRule(String fieldPattern, String classificationLevel) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT rule_id, field_pattern, classification_level, masking_type, exempt_roles " +
                 "FROM masking_rule_configs " +
                 "WHERE field_pattern = ? AND classification_level = ?")) {
            ps.setString(1, fieldPattern);
            ps.setString(2, classificationLevel);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Array arr = rs.getArray("exempt_roles");
                List<String> exemptRoles = arr != null
                    ? Arrays.asList((String[]) arr.getArray())
                    : List.of();
                return new MaskingRule(
                    rs.getString("rule_id"),
                    rs.getString("field_pattern"),
                    rs.getString("classification_level"),
                    MaskingType.valueOf(rs.getString("masking_type")),
                    exemptRoles
                );
            }
        }
    }

    @Override
    public void upsertRule(MaskingRule rule) throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO masking_rule_configs " +
                 "(rule_id, field_pattern, classification_level, masking_type, exempt_roles) " +
                 "VALUES (?, ?, ?, ?, ?) " +
                 "ON CONFLICT (field_pattern, classification_level) DO UPDATE SET " +
                 "masking_type = EXCLUDED.masking_type, exempt_roles = EXCLUDED.exempt_roles")) {
            ps.setString(1, rule.ruleId());
            ps.setString(2, rule.fieldPattern());
            ps.setString(3, rule.classificationLevel());
            ps.setString(4, rule.maskingType().name());
            ps.setArray(5, c.createArrayOf("text", rule.exemptRoles().toArray()));
            ps.executeUpdate();
        }
    }
}
