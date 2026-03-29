package com.ghatana.kernel.adapter.datacloud;

/**
 * Handle for a DataCloud transaction.
 *
 * @doc.type interface
 * @doc.purpose DataCloud transaction handle
 * @doc.layer kernel
 * @doc.pattern ValueObject
 */
public interface TransactionHandle {
    String getId();
    boolean isActive();
}
