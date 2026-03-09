/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 *
 * This source code and the accompanying materials are the confidential
 * and proprietary information of Ghatana Inc. ("Confidential Information").
 * You shall not disclose such Confidential Information and shall use it
 * only in accordance with the terms of the license agreement you entered
 * into with Ghatana Inc.
 *
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package com.ghatana.security.grpc;

/**
 * Exception thrown when user authentication fails.
 
 *
 * @doc.type class
 * @doc.purpose Authentication exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class AuthenticationException extends Exception {
    
    public AuthenticationException(String message) {
        super(message);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}