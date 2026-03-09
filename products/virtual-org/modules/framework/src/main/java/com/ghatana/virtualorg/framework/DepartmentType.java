package com.ghatana.virtualorg.framework;

/**
 * Enumeration of department types in software organizations.
 */
public enum DepartmentType {
    ENGINEERING("Engineering", "Software development and architecture"),
    QA("Quality Assurance", "Testing and quality verification"),
    DEVOPS("DevOps", "Infrastructure and deployment automation"),
    SUPPORT("Customer Support", "Customer issue resolution"),
    SALES("Sales", "Revenue generation and customer acquisition"),
    MARKETING("Marketing", "Brand and demand generation"),
    PRODUCT("Product Management", "Product strategy and roadmap"),
    FINANCE("Finance", "Financial planning and accounting"),
    HR("Human Resources", "Talent acquisition and employee management"),
    COMPLIANCE("Compliance", "Regulatory compliance and governance");

    private final String displayName;
    private final String description;

    DepartmentType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
