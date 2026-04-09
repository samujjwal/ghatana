package com.ghatana.datacloud.launcher;

/**
 * @doc.type class
 * @doc.purpose Represents standalone transport startup failures for Data-Cloud launcher flows
 * @doc.layer product
 * @doc.pattern Exception
 */
public final class DataCloudTransportStartupException extends RuntimeException {

    public DataCloudTransportStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
