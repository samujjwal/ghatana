package reporting.rules

# Reporting requirement rules
reporting_required[report_type] {
    trade_requires_reporting
    report_type := "trade"
}

reporting_required[report_type] {
    transaction_requires_sar
    report_type := "sar"
}

reporting_required[report_type] {
    large_cash_transaction
    report_type := "ctr"
}

reporting_required[report_type] {
    cross_border_transaction
    report_type := "cross_border"
}

# Trade reporting requirements
trade_requires_reporting {
    input.reporting.amount >= data.thresholds.trade_reporting
}

trade_requires_reporting {
    data.reportable_instruments[input.reporting.instrument]
}

# Suspicious Activity Report (SAR) requirements
transaction_requires_sar {
    input.reporting.suspicious_indicators
    count(input.reporting.suspicious_indicators) >= data.sar.min_indicators
}

transaction_requires_sar {
    input.reporting.structured_transaction
}

transaction_requires_sar {
    input.reporting.round_amount_pattern
}

transaction_requires_sar {
    data.high_risk_jurisdictions[input.reporting.jurisdiction]
}

# Currency Transaction Report (CTR) requirements
large_cash_transaction {
    input.reporting.cash_transaction == true
    input.reporting.amount >= data.thresholds.ctr
}

large_cash_transaction {
    input.reporting.currency == "USD"
    input.reporting.amount >= data.thresholds.ctr_usd
}

# Cross-border reporting requirements
cross_border_transaction {
    input.reporting.origin_country != input.reporting.destination_country
    input.reporting.amount >= data.thresholds.cross_border
}

cross_border_transaction {
    data.reporting_countries[input.reporting.destination_country]
    input.reporting.amount >= data.thresholds.country_specific[input.reporting.destination_country]
}

# Reporting deadline calculation
reporting_deadline[deadline] {
    report_type := determine_report_type(input.reporting)
    deadline := data.deadlines[report_type]
}

determine_report_type(reporting_data) = "trade" {
    trade_requires_reporting with input as reporting_data
}

determine_report_type(reporting_data) = "sar" {
    transaction_requires_sar with input as reporting_data
}

determine_report_type(reporting_data) = "ctr" {
    large_cash_transaction with input as reporting_data
}

determine_report_type(reporting_data) = "cross_border" {
    cross_border_transaction with input as reporting_data
}

# Regulatory jurisdiction mapping
regulatory_authority[authority] {
    jurisdiction := input.reporting.jurisdiction
    authority := data.regulatory_authorities[jurisdiction]
}

regulatory_authority[authority] {
    not input.reporting.jurisdiction
    authority := data.regulatory_authorities["default"]
}

# Report format requirements
report_format[format] {
    jurisdiction := input.reporting.jurisdiction
    format := data.report_formats[jurisdiction]
}

report_format[format] {
    not input.reporting.jurisdiction
    format := data.report_formats["default"]
}

# Required fields for reporting
required_fields[field] {
    report_type := determine_report_type(input.reporting)
    field := data.required_fields[report_type][_]
}

# Validation that all required fields are present
report_valid {
    required_fields[field]
    input.reporting[field]
}
