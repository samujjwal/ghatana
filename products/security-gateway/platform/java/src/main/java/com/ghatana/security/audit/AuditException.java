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
package com.ghatana.security.audit;

/**
 * Exception thrown when audit operations fail.
 
 *
 * @doc.type class
 * @doc.purpose Audit exception
 * @doc.layer core
 * @doc.pattern Exception
*/
public class AuditException extends Exception {
    
    public AuditException(String message) {
        super(message);
    }
    
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}