/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.contracts;

import com.ghatana.kernel.contracts.validation.ContractValidationGate;
import com.ghatana.kernel.contracts.KernelContract;
import java.io.PrintStream;
import java.util.List;

/**
 * Component for ContractValidationRunner
 *
 * @doc.type class
 * @doc.purpose Component for ContractValidationRunner
 * @doc.layer product
 * @doc.pattern Service
 */
public class ContractValidationRunner {

    static ContractValidationGate.GateResult validateAllContracts() {
        ContractValidationGate gate = new ContractValidationGate();
        return gate.validateContractsForDeployment(FinanceContracts.getAllContracts());
    }

    static int run(PrintStream out, PrintStream err) {
        List<KernelContract> contracts = FinanceContracts.getAllContracts();
        out.println("Validating " + contracts.size() + " Finance contracts...");

        ContractValidationGate.GateResult result = validateAllContracts();

        if (!result.isValid()) {
            err.println("\nContract validation FAILED!");
            err.println("Found " + result.getViolations().size() + " violations:\n");

            for (ContractValidationGate.ComplianceViolation violation : result.getViolations()) {
                err.println("  * " + violation);
            }
            return 1;
        }

        out.println("\nAll contracts validated successfully!");
        out.println("Finance contracts are ready for deployment.");
        return 0;
    }

    public static void main(String[] args) {
        System.exit(run(System.out, System.err));
    }
}
