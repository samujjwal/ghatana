package com.ghatana.security.config;

import io.activej.config.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration properties for email notifications.
 
 *
 * @doc.type class
 * @doc.purpose Email properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class EmailProperties {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean sslEnabled;
    private final String from;
    private final List<String> to;
    private final String subjectPrefix;

    private EmailProperties(boolean enabled, String host, int port, String username, 
                          String password, boolean sslEnabled, String from, 
                          List<String> to, String subjectPrefix) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.sslEnabled = sslEnabled;
        this.from = from;
        this.to = new ArrayList<>(to);
        this.subjectPrefix = subjectPrefix != null ? subjectPrefix : "[Security Alert] ";
    }

    /**
     * Creates EmailProperties from a Config object.
     *
     * @param config The configuration object
     * @return A new EmailProperties instance
     */
    public static EmailProperties fromConfig(Config config) {
        // Parse comma-separated list of recipients
        List<String> recipients = new ArrayList<>();
        String toList = config.get("to", "admin@example.com");
        if (!toList.isEmpty()) {
            String[] toArray = toList.split(",");
            for (String recipient : toArray) {
                String trimmed = recipient.trim();
                if (!trimmed.isEmpty()) {
                    recipients.add(trimmed);
                }
            }
        }
        
        return new Builder()
                .enabled(Boolean.parseBoolean(config.get("enabled", "false")))
                .host(config.get("host", "localhost"))
                .port(Integer.parseInt(config.get("port", "25")))
                .username(config.get("username", ""))
                .password(config.get("password", ""))
                .sslEnabled(Boolean.parseBoolean(config.get("ssl.enabled", "false")))
                .from(config.get("from", "security@example.com"))
                .to(recipients.isEmpty() ? List.of("admin@example.com") : recipients)
                .subjectPrefix(config.get("subject.prefix", "[Security Alert] "))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public String getFrom() {
        return from;
    }

    public List<String> getTo() {
        return new ArrayList<>(to);
    }

    public String getSubjectPrefix() {
        return subjectPrefix;
    }

    public static class Builder {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 25;
        private String username = "";
        private String password = "";
        private boolean sslEnabled = false;
        private String from = "security@example.com";
        private List<String> to = new ArrayList<>();
        private String subjectPrefix = "[Security Alert] ";

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host);
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder username(String username) {
            this.username = Objects.requireNonNull(username);
            return this;
        }

        public Builder password(String password) {
            this.password = Objects.requireNonNull(password);
            return this;
        }

        public Builder sslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
            return this;
        }

        public Builder from(String from) {
            this.from = Objects.requireNonNull(from);
            return this;
        }

        public Builder to(List<String> to) {
            this.to = new ArrayList<>(to);
            return this;
        }

        public Builder addTo(String recipient) {
            this.to.add(Objects.requireNonNull(recipient));
            return this;
        }

        public Builder subjectPrefix(String prefix) {
            this.subjectPrefix = Objects.requireNonNull(prefix);
            return this;
        }

        public EmailProperties build() {
            return new EmailProperties(
                enabled, host, port, username, password, sslEnabled, 
                from, to, subjectPrefix
            );
        }
    }
}
