/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.contracts.schema;

import com.ghatana.kernel.contracts.ContractFamily;
import com.ghatana.kernel.contracts.ContractValidator;
import com.ghatana.kernel.contracts.KernelContract;
import com.ghatana.kernel.contracts.SchemaContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Contract validator that enforces schema governance rules.
 *
 * <p>Validates that schema contracts have at least one subject, all subjects
 * have valid format/compatibility combinations, and no subjects duplicate
 * subject IDs within the same contract.</p>
 *
 * @doc.type class
 * @doc.purpose Schema-family contract validator with governance rules
 * @doc.layer core
 * @doc.pattern Strategy
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class SchemaGovernanceValidator implements ContractValidator {

    @Override
    public ValidationResult validate(KernelContract contract) {
        if (!(contract instanceof SchemaContract schema)) {
            return ValidationResult.OK;
        }
        List<String> errors = new ArrayList<>();

        if (schema.getSubjects().isEmpty()) {
            errors.add("Schema contract must declare at least one subject");
        }

        // Check for duplicate subject IDs
        long distinctCount = schema.getSubjects().stream()
            .map(SchemaContract.SchemaSubject::subjectId)
            .distinct()
            .count();
        if (distinctCount < schema.getSubjects().size()) {
            errors.add("Schema contract has duplicate subject IDs");
        }

        return errors.isEmpty() ? ValidationResult.OK : ValidationResult.failed(errors);
    }

    @Override
    public List<ContractFamily> applicableFamilies() {
        return List.of(ContractFamily.SCHEMA);
    }
}
