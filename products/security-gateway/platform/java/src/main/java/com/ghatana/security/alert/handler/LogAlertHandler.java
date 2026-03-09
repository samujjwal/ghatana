package com.ghatana.security.alert.handler;

import com.ghatana.security.alert.SecurityAlert;
import com.ghatana.security.alert.SecurityAlertManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs security alerts to the application logs.
 
 *
 * @doc.type class
 * @doc.purpose Log alert handler
 * @doc.layer core
 * @doc.pattern Handler
*/
public class LogAlertHandler implements SecurityAlertManager.SecurityAlertHandler {
    private static final Logger logger = LoggerFactory.getLogger(LogAlertHandler.class);
    
    @Override
    public Promise<Void> handle(SecurityAlert alert) {
        String logMessage = String.format(
            "SECURITY_ALERT: type=%s, severity=%s, source=%s, message=%s, details=%s",
            alert.getType(),
            alert.getSeverity(),
            alert.getSource(),
            alert.getMessage(),
            alert.getDetails()
        );
        
        switch (alert.getSeverity()) {
            case CRITICAL -> logger.error(logMessage);
            case HIGH -> logger.warn(logMessage);
            case MEDIUM, LOW, INFO, WARNING, NONE, INFORMATIONAL -> logger.info(logMessage);
        }
        
        return Promise.complete();
    }
}
