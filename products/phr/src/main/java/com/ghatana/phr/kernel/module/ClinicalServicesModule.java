package com.ghatana.phr.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.phr.kernel.service.ClinicalNoteService;
import com.ghatana.phr.kernel.service.DocumentService;
import com.ghatana.phr.kernel.service.ImagingService;
import com.ghatana.phr.kernel.service.LabResultService;
import io.activej.promise.Promise;
import java.util.Set;
import com.ghatana.phr.kernel.service.PatientRecordService;

/**
 * Clinical Services Module for PHR.
 *
 * <p>Groups clinical and medical record PHR services:
 * <ul>
 *   <li>Patient record management</li>
 *   <li>Clinical notes and documentation</li>
 *   <li>Medical document management</li>
 *   <li>Lab result tracking</li>
 *   <li>Medical imaging management</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR clinical services domain module
 * @doc.layer product
 * @doc.pattern Domain Module
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class ClinicalServicesModule implements KernelModule {

    private final PatientRecordService patientRecordService;
    private final ClinicalNoteService clinicalNoteService;
    private final DocumentService documentService;
    private final LabResultService labResultService;
    private final ImagingService imagingService;

    public ClinicalServicesModule(
            PatientRecordService patientRecordService,
            ClinicalNoteService clinicalNoteService,
            DocumentService documentService,
            LabResultService labResultService,
            ImagingService imagingService) {
        this.patientRecordService = patientRecordService;
        this.clinicalNoteService = clinicalNoteService;
        this.documentService = documentService;
        this.labResultService = labResultService;
        this.imagingService = imagingService;
    }

    @Override
    public String getModuleId() {
        return "phr-clinical-services";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of();
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of();
    }

    @Override
    public void initialize(KernelContext context) {}

    @Override
    public Promise<Void> start() {
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return HealthStatus.healthy();
    }

    public String getName() {
        return "phr-clinical-services";
    }

    public PatientRecordService getPatientRecordService() {
        return patientRecordService;
    }

    public ClinicalNoteService getClinicalNoteService() {
        return clinicalNoteService;
    }

    public DocumentService getDocumentService() {
        return documentService;
    }

    public LabResultService getLabResultService() {
        return labResultService;
    }

    public ImagingService getImagingService() {
        return imagingService;
    }
}
