package com.ghatana.phr.kernel.module;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.phr.kernel.service.AppointmentService;
import com.ghatana.phr.kernel.service.BillingService;
import com.ghatana.phr.kernel.service.ReferralService;
import com.ghatana.phr.kernel.service.TelemedicineService;
import io.activej.promise.Promise;
import java.util.Set;

/**
 * Administrative Services Module for PHR.
 *
 * <p>Groups administrative and operational PHR services:
 * <ul>
 *   <li>Appointment scheduling and management</li>
 *   <li>Billing and claims processing</li>
 *   <li>Patient referrals</li>
 *   <li>Telemedicine sessions</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose PHR administrative services domain module
 * @doc.layer product
 * @doc.pattern Domain Module
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class AdministrativeServicesModule implements KernelModule {

    private final AppointmentService appointmentService;
    private final BillingService billingService;
    private final ReferralService referralService;
    private final TelemedicineService telemedicineService;

    public AdministrativeServicesModule(
            AppointmentService appointmentService,
            BillingService billingService,
            ReferralService referralService,
            TelemedicineService telemedicineService) {
        this.appointmentService = appointmentService;
        this.billingService = billingService;
        this.referralService = referralService;
        this.telemedicineService = telemedicineService;
    }

    @Override
    public String getModuleId() {
        return "phr-administrative-services";
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
        return "phr-administrative-services";
    }

    public AppointmentService getAppointmentService() {
        return appointmentService;
    }

    public BillingService getBillingService() {
        return billingService;
    }

    public ReferralService getReferralService() {
        return referralService;
    }

    public TelemedicineService getTelemedicineService() {
        return telemedicineService;
    }
}
