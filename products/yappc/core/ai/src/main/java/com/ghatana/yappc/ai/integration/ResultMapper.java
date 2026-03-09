package com.ghatana.yappc.ai.integration;

import com.ghatana.yappc.ai.router.AIResponse;

/**
 * Interface for mapping AI responses to agent results.
 * 
 * @param <T> the result type
 * 
 * @doc.type interface
 * @doc.purpose AI response mapping
 
 * @doc.layer core
 * @doc.pattern Mapper
*/
public interface ResultMapper<T> {
    
    /**
     * Maps an AI response to an agent result.
     * 
     * @param <Req> the request type
     * @param response the AI response
     * @param request the original request
     * @return the mapped result
     */
    <Req> T mapResponse(AIResponse response, Req request);
}
