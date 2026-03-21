package com.ghatana.phr.plugin;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.phr.kernel.PhrCapabilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PhrKernelPlugin Tests")
class PhrKernelPluginTest {

    @Test
    @DisplayName("Should use product-owned PHR capabilities with canonical ids")
    void shouldUseProductOwnedPhrCapabilitiesWithCanonicalIds() {
        PhrKernelPlugin plugin = new PhrKernelPlugin();

        Set<KernelCapability> capabilities = plugin.getManifest().getCapabilities();

        assertEquals(6, capabilities.size());
        assertTrue(capabilities.contains(PhrCapabilities.PATIENT_RECORDS));
        assertTrue(capabilities.contains(PhrCapabilities.CONSENT_MANAGEMENT));
        assertTrue(capabilities.contains(PhrCapabilities.FHIR_INTEROP));
        assertTrue(capabilities.contains(PhrCapabilities.CLINICAL_DOCUMENTS));
        assertTrue(capabilities.contains(PhrCapabilities.APPOINTMENT_SCHEDULING));
        assertTrue(capabilities.contains(PhrCapabilities.MEDICATION_MANAGEMENT));
        assertTrue(capabilities.stream().allMatch(cap -> cap.getCapabilityId().startsWith("phr.")));
    }

    @Test
    @DisplayName("Should export PHR service contracts")
    void shouldExportPhrServiceContracts() {
        PhrKernelPlugin plugin = new PhrKernelPlugin();

        Set<String> exportedContracts = plugin.getExportedContracts();

        assertTrue(exportedContracts.contains("com.ghatana.phr.PatientRecordService"));
        assertTrue(exportedContracts.contains("com.ghatana.phr.ConsentService"));
        assertTrue(exportedContracts.contains("com.ghatana.phr.FHIRService"));
    }

    @Test
    @DisplayName("Should require kernel service contracts")
    void shouldRequireKernelServiceContracts() {
        PhrKernelPlugin plugin = new PhrKernelPlugin();

        Set<String> requiredContracts = plugin.getRequiredContracts();

        assertTrue(requiredContracts.contains("com.ghatana.kernel.modules.authentication.AuthenticationService"));
        assertTrue(requiredContracts.contains("com.ghatana.kernel.modules.audit.AuditService"));
    }
}
