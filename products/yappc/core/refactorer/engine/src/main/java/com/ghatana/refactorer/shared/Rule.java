/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import com.ghatana.platform.domain.domain.Severity;
import java.util.Objects;

/**
 * Represents a validation or linting rule that can be applied to source code. 
 * @doc.type class
 * @doc.purpose Handles rule operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public class Rule {
    private final String id;
    private final String name;
    private final String description;
    private final Severity severity;

    public Rule(String id, String name, String description, Severity severity) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.severity = severity;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Severity getSeverity() {
        return severity;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Rule)) {
            return false;
        }
        Rule other = (Rule) obj;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name + " (" + severity + ")";
    }
}
