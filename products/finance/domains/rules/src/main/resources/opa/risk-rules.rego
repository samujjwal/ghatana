package risk.rules

# Risk assessment rules
risk_level[level] {
    risk_score := calculate_total_risk(input.risk)
    risk_score >= data.risk.thresholds.high
    level := "HIGH"
}

risk_level[level] {
    risk_score := calculate_total_risk(input.risk)
    risk_score >= data.risk.thresholds.medium
    risk_score < data.risk.thresholds.high
    level := "MEDIUM"
}

risk_level[level] {
    risk_score := calculate_total_risk(input.risk)
    risk_score < data.risk.thresholds.medium
    level := "LOW"
}

risk_level[level] {
    not input.risk
    level := "UNKNOWN"
}

# Calculate total risk score from multiple factors
calculate_total_risk(risk_data) = total_risk {
    amount_risk := calculate_amount_risk(risk_data.amount, data.limits.max_trade_amount)
    counterparty_risk := get_counterparty_risk(risk_data.counterparty, data.counterparty_risks)
    market_risk := get_market_risk(risk_data.instrument, data.market_risks)
    volatility_risk := calculate_volatility_risk(risk_data.volatility)
    
    total_risk := (amount_risk * data.risk.weights.amount) +
                  (counterparty_risk * data.risk.weights.counterparty) +
                  (market_risk * data.risk.weights.market) +
                  (volatility_risk * data.risk.weights.volatility)
}

# Component risk calculations
calculate_amount_risk(amount, max_amount) = risk {
    risk := amount / max_amount
}

calculate_amount_risk(_, _) = 0.5 {
    not input.risk.amount
}

get_counterparty_risk(counterparty, risks) = risk {
    risks[counterparty]
}

get_counterparty_risk(counterparty, risks) = 0.5 {
    not risks[counterparty]
    not counterparty
}

get_counterparty_risk(_, _) = 0.5 {
    not input.risk.counterparty
}

get_market_risk(instrument, risks) = risk {
    risks[instrument]
}

get_market_risk(instrument, risks) = 0.3 {
    not risks[instrument]
    not instrument
}

get_market_risk(_, _) = 0.3 {
    not input.risk.instrument
}

calculate_volatility_risk(volatility) = risk {
    risk := volatility / 100
}

calculate_volatility_risk(_) = 0.2 {
    not input.risk.volatility
}

# Risk mitigation factors
mitigation_factor[factor] {
    input.risk.hedged == true
    factor := "hedging"
}

mitigation_factor[factor] {
    input.risk.collateralized == true
    factor := "collateral"
}

mitigation_factor[factor] {
    input.risk.insured == true
    factor := "insurance"
}

# Adjusted risk score considering mitigations
adjusted_risk_score[adjusted_score] {
    base_risk := calculate_total_risk(input.risk)
    mitigation_count := count(mitigation_factor)
    mitigation_discount := mitigation_count * data.risk.mitigation_discount_per_factor
    adjusted_score := base_risk - mitigation_discount
}

adjusted_risk_score[adjusted_score] {
    base_risk := calculate_total_risk(input.risk)
    adjusted_score := base_risk
    not mitigation_factor[_]
}

# Concentration risk check
concentration_risk[status] {
    exposure := calculate_exposure(input.risk.counterparty, input.risk.amount)
    exposure >= data.concentration.threshold
    status := "HIGH"
}

concentration_risk[status] {
    exposure := calculate_exposure(input.risk.counterparty, input.risk.amount)
    exposure < data.concentration.threshold
    status := "ACCEPTABLE"
}

calculate_exposure(counterparty, amount) = total_exposure {
    existing_exposure := data.exposures[counterparty]
    total_exposure := existing_exposure + amount
}

calculate_exposure(counterparty, amount) = amount {
    not data.exposures[counterparty]
}
