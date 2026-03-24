package com.ghatana.yappc.api;

import io.activej.http.HttpResponse;

/**
 * Utility class for creating HTTP responses with common patterns.
 
 * @doc.type class
 * @doc.purpose Handles http responses operations
 * @doc.layer core
 * @doc.pattern ValueObject
* @doc.gaa.lifecycle perceive
*/
public final class HttpResponses {
    
    private HttpResponses() {}
    
    /**
     * Creates a 200 OK response with JSON content from String.
     * 
     * @param jsonString JSON string
     * @return HTTP response
     */
    public static HttpResponse ok200Json(String jsonString) {
        return HttpResponse.ok200().withJson(jsonString).build();
    }
    
    /**
     * Creates a 200 OK response with JSON content from bytes.
     * 
     * @param jsonBytes JSON bytes
     * @return HTTP response
     */
    public static HttpResponse ok200Json(byte[] jsonBytes) {
        return HttpResponse.ok200().withJson(new String(jsonBytes)).build();
    }
    
    /**
     * Creates a 500 error response with plain text.
     * 
     * @param message Error message
     * @return HTTP response  
     */
    public static HttpResponse error500(String message) {
        return HttpResponse.ofCode(500).withPlainText(message).build();
    }
    
    /**
     * Creates a 400 bad request response.
     * 
     * @param message Error message
     * @return HTTP response
     */
    public static HttpResponse badRequest400(String message) {
        return HttpResponse.ofCode(400).withPlainText(message).build();
    }
}
