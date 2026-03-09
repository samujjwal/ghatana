/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-02-04
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.types.time;

/**
 * Time value with unit.
 *
 * @doc.type record
 * @doc.purpose Duration value with associated time unit
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record GTimeValue(long amount, GTimeUnit unit) {
    
    public GTimeValue {
        if (amount < 0) {
            throw new IllegalArgumentException("Time value amount cannot be negative");
        }
        if (unit == null) {
            throw new IllegalArgumentException("Time unit cannot be null");
        }
    }
    
    public static GTimeValue of(long amount, GTimeUnit unit) {
        return new GTimeValue(amount, unit);
    }
    
    public static GTimeValue millis(long amount) {
        return new GTimeValue(amount, GTimeUnit.MILLISECONDS);
    }
    
    public static GTimeValue seconds(long amount) {
        return new GTimeValue(amount, GTimeUnit.SECONDS);
    }
    
    public static GTimeValue minutes(long amount) {
        return new GTimeValue(amount, GTimeUnit.MINUTES);
    }
    
    public static GTimeValue hours(long amount) {
        return new GTimeValue(amount, GTimeUnit.HOURS);
    }
    
    public static GTimeValue days(long amount) {
        return new GTimeValue(amount, GTimeUnit.DAYS);
    }
}
