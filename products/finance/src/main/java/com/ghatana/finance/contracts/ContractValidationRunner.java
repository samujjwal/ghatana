/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.contracts;

import com.ghatana.kernel.contracts.validation.ContractValidationGate;
import com.ghatana.kernel.contracts.KernelContract;
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

    public static void main(String[] args) {
        ContractValidationGate gate = new ContractValidationGate();
        
        List<KernelContract> contracts = FinanceContracts.getAllContracts();
        
        System.out.println("Validating " + contracts.size() + " Finance contracts...");
        
        ContractValidationGate.GateResult result = 
            gate.validateContractsForDeployment(contracts);
        
        if (!result.isValid()) {
            System.err.println("\n❌ Contract validation FAILED!");
            System.err.println("Found " + result.getViolations().size() + " violations:\n");
            
            for (ContractValidationGate.ComplianceViolation violation : result.getViolations()) {
                System.err.println("  • " + violation);
            }
            
            System.exit(1);
        }
        
        System.out.println("\n✅ All contracts validated successfully!");
        System.out.println("Finance contracts are ready for deployment.");
        System.exit(0);
    }
}
