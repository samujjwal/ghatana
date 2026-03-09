package com.ghatana.security.alert.handler;

import com.ghatana.security.alert.SecurityAlert;
import com.ghatana.security.alert.SecurityAlertManager;
import com.ghatana.security.config.EmailProperties;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Date;
import java.util.Properties;

/**
 * Sends security alerts via email.
 
 *
 * @doc.type class
 * @doc.purpose Email alert handler
 * @doc.layer core
 * @doc.pattern Handler
*/
public class EmailAlertHandler implements SecurityAlertManager.SecurityAlertHandler {
    private static final Logger logger = LoggerFactory.getLogger(EmailAlertHandler.class);
    
    private final EmailProperties emailProperties;
    private final Session session;
    private final Eventloop eventloop;
    
    /**
     * Creates a new EmailAlertHandler with the specified email properties.
     *
     * @param emailProperties The email configuration properties
     */
    public EmailAlertHandler(Eventloop eventloop, EmailProperties emailProperties) {
        this.eventloop = eventloop;
        this.emailProperties = emailProperties;
        this.session = createSession();
    }
    
    @Override
    public Promise<Void> handle(SecurityAlert alert) {
        if (!emailProperties.isEnabled()) {
            logger.debug("Email alerts are disabled");
            return Promise.complete();
        }
        
        return Promise.ofBlocking(eventloop, () -> {
            try {
                Message message = createEmail(alert);
                Transport.send(message);
                logger.debug("Sent email alert for: {}", alert.getType());
            } catch (Exception e) {
                logger.error("Failed to send email alert", e);
                throw new RuntimeException("Failed to send email alert", e);
            }
        });
    }
    
    private Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host", emailProperties.getHost());
        props.put("mail.smtp.port", emailProperties.getPort());
        props.put("mail.smtp.auth", "true");
        
        if (emailProperties.isSslEnabled()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.trust", emailProperties.getHost());
        }
        
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    emailProperties.getUsername(), 
                    emailProperties.getPassword()
                );
            }
        });
    }
    
    private Message createEmail(SecurityAlert alert) throws MessagingException {
        Message message = new MimeMessage(session);
        
        // Set From: header
        message.setFrom(new InternetAddress(emailProperties.getFrom()));
        
        // Set To: header
        for (String recipient : emailProperties.getTo()) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }
        
        // Set Subject: header
        message.setSubject(emailProperties.getSubjectPrefix() + alert.getType());
        
        // Set the message body
        String text = String.format(
            "Security Alert: %s\n" +
            "Severity: %s\n" +
            "Source: %s\n" +
            "Time: %s\n" +
            "Message: %s\n" +
            "Details: %s",
            alert.getType(),
            alert.getSeverity(),
            alert.getSource(),
            new Date().toString(),
            alert.getMessage(),
            alert.getDetails().toString()
        );
        
        message.setText(text);
        
        return message;
    }
    
    /**
     * Creates a new builder for EmailAlertHandler.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for EmailAlertHandler.
     */
    public static class Builder {
        private Eventloop eventloop;
        private EmailProperties emailProperties;
        
        /**
         * Sets the event loop for async operations.
         *
         * @param eventloop The event loop to use
         * @return This builder instance
         */
        public Builder withEventloop(Eventloop eventloop) {
            this.eventloop = eventloop;
            return this;
        }
        
        /**
         * Sets the email properties.
         *
         * @param emailProperties The email properties
         * @return This builder instance
         */
        public Builder withEmailProperties(EmailProperties emailProperties) {
            this.emailProperties = emailProperties;
            return this;
        }
        
        /**
         * Builds the EmailAlertHandler.
         *
         * @return A new EmailAlertHandler instance
         */
        public EmailAlertHandler build() {
            if (eventloop == null) {
                throw new IllegalStateException("Eventloop must be provided");
            }
            if (emailProperties == null) {
                throw new IllegalStateException("Email properties must be provided");
            }
            return new EmailAlertHandler(eventloop, emailProperties);
        }
    }
}
