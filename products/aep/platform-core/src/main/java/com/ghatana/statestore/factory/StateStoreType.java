package com.ghatana.statestore.factory;

/**
 * Types of state stores supported by the factory.
 */
public enum StateStoreType {
    /**
     * In-memory state store (testing only - data lost on restart).
     */
    MEMORY,
    
    /**
     * File-based state store (simple persistence, local development).
     */
    FILE,
    
    /**
     * RocksDB embedded KV store (high performance, production local).
     */
    ROCKSDB,
    
    /**
     * Redis distributed state store (cross-instance sharing).
     */
    REDIS,
    
    /**
     * Hybrid local + centralized state store (best of both worlds).
     */
    HYBRID
}
