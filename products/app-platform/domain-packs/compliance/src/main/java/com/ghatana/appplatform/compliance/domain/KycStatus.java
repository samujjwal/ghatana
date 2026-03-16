package com.ghatana.appplatform.compliance.domain;

/**
 * @doc.type    Enum
 * @doc.purpose KYC verification status from K-01 client profile (D07-006).
 * @doc.layer   Domain
 * @doc.pattern Value Object
 */
public enum KycStatus {
    VERIFIED,
    PENDING,
    EXPIRED,
    REJECTED,
    SUSPENDED
}
