package com.ghatana.phr.model;

/**
 * Component for ProviderInfo
 *
 * @doc.type class
 * @doc.purpose Component for ProviderInfo
 * @doc.layer product
 * @doc.pattern Service
 */
public class ProviderInfo {
    private String providerId;
    private String providerName;
    private String specialty;
    private String licenseNumber;
    private String organization;

    public ProviderInfo() {
    }

    public ProviderInfo(String providerId, String providerName) {
        this.providerId = providerId;
        this.providerName = providerName;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }
}
