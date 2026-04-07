/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;

/**
 * Component for Transaction
 *
 * @doc.type class
 * @doc.purpose Component for Transaction
 * @doc.layer product
 * @doc.pattern Service
 */
public class Transaction {
    
    private String id;
    private String tenantId;
    private double amount;
    private String currency;
    private String location;
    private String merchantCategory;
    private String counterpartyCountry;
    private String paymentMethod;
    private double velocity;
    private Instant timestamp;
    private String status;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    public double getAmount() {
        return amount;
    }
    
    public void setAmount(double amount) {
        this.amount = amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public void setMerchantCategory(String merchantCategory) {
        this.merchantCategory = merchantCategory;
    }

    public String getCounterpartyCountry() {
        return counterpartyCountry;
    }

    public void setCounterpartyCountry(String counterpartyCountry) {
        this.counterpartyCountry = counterpartyCountry;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public double getVelocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = velocity;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("tenant_id", tenantId);
        map.put("amount", amount);
        map.put("currency", currency);
        map.put("location", location);
        map.put("merchant_category", merchantCategory);
        map.put("counterparty_country", counterpartyCountry);
        map.put("payment_method", paymentMethod);
        map.put("velocity", velocity);
        map.put("timestamp", timestamp);
        map.put("status", status);
        return map;
    }
}
