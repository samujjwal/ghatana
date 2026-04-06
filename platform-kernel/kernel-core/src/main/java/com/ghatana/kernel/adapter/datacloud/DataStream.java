package com.ghatana.kernel.adapter.datacloud;

import io.activej.promise.Promise;

/**
 * Stream for reading/writing data chunks.
 *
 * @doc.type interface
 * @doc.purpose DataCloud data stream
 * @doc.layer kernel
 * @doc.pattern Stream
 */
public interface DataStream {
    Promise<byte[]> readChunk();
    Promise<Void> writeChunk(byte[] data);
    Promise<Void> close();
    boolean isOpen();
}
