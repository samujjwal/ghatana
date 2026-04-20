package trading.compliance

# Default deny for trading operations
default allow = false

# Allow trade if all compliance checks pass
allow {
    trade_amount_valid
    instrument_eligible
    counterparty_valid
    trading_hours_valid
}

# Validate trade amount is positive and within limits
trade_amount_valid {
    input.trade.amount > 0
    input.trade.amount <= data.limits.max_trade_amount
}

# Validate instrument is in the approved list
instrument_eligible {
    input.trade.instrument
    data.instruments[input.trade.instrument]
}

# Validate counterparty is not restricted
counterparty_valid {
    not data.restricted_parties[input.trade.counterparty]
}

# Validate trade is within allowed trading hours
trading_hours_valid {
    current_hour := time.hour(input.trade.timestamp)
    current_hour >= data.trading_hours.start
    current_hour < data.trading_hours.end
}

# Risk assessment based on trade parameters
risk_score[risk_score] {
    amount_risk := calculate_amount_risk(input.trade.amount, data.limits.max_trade_amount)
    counterparty_risk := get_counterparty_risk(input.trade.counterparty, data.counterparty_risks)
    market_risk := get_market_risk(input.trade.instrument, data.market_risks)
    
    risk_score := (amount_risk * 0.3) + (counterparty_risk * 0.25) + (market_risk * 0.25) + (data.risk.volatility_factor * 0.2)
}

risk_level[level] {
    risk_score := calculate_risk_score(input.trade)
    risk_score >= data.risk.thresholds.high
    level := "HIGH"
}

risk_level[level] {
    risk_score := calculate_risk_score(input.trade)
    risk_score >= data.risk.thresholds.medium
    risk_score < data.risk.thresholds.high
    level := "MEDIUM"
}

risk_level[level] {
    risk_score := calculate_risk_score(input.trade)
    risk_score < data.risk.thresholds.medium
    level := "LOW"
}

# Helper functions
calculate_amount_risk(amount, max_amount) = risk {
    risk := amount / max_amount
}

get_counterparty_risk(counterparty, risks) = risk {
    risks[counterparty]
}

get_counterparty_risk(counterparty, risks) = 0.5 {
    not risks[counterparty]
}

get_market_risk(instrument, risks) = risk {
    risks[instrument]
}

get_market_risk(instrument, risks) = 0.3 {
    not risks[instrument]
}

calculate_risk_score(trade) = score {
    score := risk_score[trade]
}
