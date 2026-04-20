package aml.rules

# Default deny for AML checks
default allow = false

# Allow transaction if AML checks pass
allow {
    kyc_verified
    aml_screened
    not_sanctioned
    not_high_risk_jurisdiction
    below_reporting_threshold_or_reported
}

# KYC verification check
kyc_verified {
    input.compliance.kyc_verified == true
}

# AML screening check
aml_screened {
    input.compliance.aml_screened == true
}

# Sanctioned entity check
not_sanctioned {
    not data.sanctions[input.compliance.counterparty]
}

not_sanctioned {
    not data.sanctions[input.compliance.jurisdiction]
}

# High-risk jurisdiction check
not_high_risk_jurisdiction {
    not data.high_risk_jurisdictions[input.compliance.jurisdiction]
}

# Reporting threshold check
below_reporting_threshold_or_reported {
    input.compliance.amount < data.thresholds.reporting
}

below_reporting_threshold_or_reported {
    input.compliance.amount >= data.thresholds.reporting
    input.compliance.regulatory_reported == true
}

# Transaction monitoring for suspicious patterns
suspicious_pattern[pattern] {
    is_structuring_pattern
    pattern := "structuring"
}

suspicious_pattern[pattern] {
    is_round_amount_pattern
    pattern := "round_amount"
}

suspicious_pattern[pattern] {
    is_high_frequency_pattern
    pattern := "high_frequency"
}

# Detect structuring (multiple small transactions to avoid reporting)
is_structuring_pattern {
    count(input.compliance.related_transactions) >= data.structuring.min_transactions
    sum_amount := sum([tx.amount | tx := input.compliance.related_transactions[_]])
    sum_amount >= data.thresholds.reporting
}

# Detect suspicious round amounts (common in money laundering)
is_round_amount_pattern {
    input.compliance.amount % 10000 == 0
    input.compliance.amount >= data.thresholds.round_amount
}

# Detect high-frequency transactions
is_high_frequency_pattern {
    count(input.compliance.recent_transactions) >= data.frequency.max_transactions
}

# Enhanced due diligence required
enhanced_due_diligence_required {
    high_risk_factors_count >= 2
}

high_risk_factors_count := count([
    data.high_risk_jurisdictions[input.compliance.jurisdiction],
    data.sanctions[input.compliance.counterparty],
    input.compliance.amount >= data.thresholds.enhanced_dd,
    input.compliance.cash_transaction == true
])
