package com.ghatana.security.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;

/**
 * Configuration for email alerting settings.
 
 *
 * @doc.type class
 * @doc.purpose Email config
 * @doc.layer core
 * @doc.pattern Configuration
*/
public class EmailConfig {
    private final String smtpHost;
    private final int smtpPort;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String toAddress;
    
    public EmailConfig(String smtpHost, int smtpPort, String username, 
                      String password, String fromAddress, String toAddress) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
    }
    
    public static EmailConfig fromConfig(Config config) {
        return new EmailConfig(
            config.get("smtp-host", ""),
            config.get(ConfigConverters.ofInteger(), "smtp-port", 587),
            config.get("username", ""),
            config.get("password", ""),
            config.get("from-address", ""),
            config.get("to-address", "")
        );
    }
    
    // Getters for all fields
    public String getSmtpHost() {
        return smtpHost;
    }
    public int getSmtpPort() {
        return smtpPort;
    }
    public String getUsername() {
        return username;
    }
    public String getPassword() {
        return password;
    }
    public String getFromAddress() {
        return fromAddress;
    }
    public String getToAddress() {
        return toAddress;
    }
    
    public Config getChild(String path) {
        return Config.create()
            .with("smtp-host", smtpHost)
            .with("smtp-port", String.valueOf(smtpPort))
            .with("username", username)
            .with("password", password)
            .with("from-address", fromAddress)
            .with("to-address", toAddress);
    }
}
