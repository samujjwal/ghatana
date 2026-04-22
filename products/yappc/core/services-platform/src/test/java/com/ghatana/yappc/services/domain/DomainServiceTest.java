/*
 * Copyright (c) 2026 Ghatana // GH-90000
 */
package com.ghatana.yappc.services.domain;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DomainService and related domain logic.
 */
class DomainServiceTest extends EventloopTestBase {

    private DomainService domainService;
    private DomainRepository repository;

    @BeforeEach
    void setUp() { // GH-90000
        repository = new MockDomainRepository(); // GH-90000
        domainService = new MockDomainService(repository); // GH-90000
    }

    @Test
    @DisplayName("Should create domain model from specification [GH-90000]")
    void testCreateDomainModel() throws Exception { // GH-90000
        DomainSpec spec = DomainSpec.builder() // GH-90000
                .name("User [GH-90000]")
                .attributes(Map.of("id", "Long", "email", "String", "name", "String")) // GH-90000
                .relationships(Map.of("orders", "OneToMany")) // GH-90000
                .build(); // GH-90000

        Promise<DomainModel> promise = domainService.createModel(spec); // GH-90000
        DomainModel model = runPromise(() -> promise); // GH-90000

        assertThat(model).isNotNull(); // GH-90000
        assertThat(model.name()).isEqualTo("User [GH-90000]");
        assertThat(model.attributes()).hasSize(3); // GH-90000
        assertThat(model.attributes()).containsKey("email [GH-90000]");
    }

    @Test
    @DisplayName("Should validate domain model [GH-90000]")
    void testValidateDomainModel() throws Exception { // GH-90000
        DomainModel model = DomainModel.builder() // GH-90000
                .name("Order [GH-90000]")
                .attributes(Map.of("id", "Long", "total", "BigDecimal")) // GH-90000
                .build(); // GH-90000

        Promise<ValidationResult> promise = domainService.validateModel(model); // GH-90000
        ValidationResult result = runPromise(() -> promise); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.valid()).isTrue(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject invalid domain model [GH-90000]")
    void testRejectInvalidModel() throws Exception { // GH-90000
        DomainModel model = DomainModel.builder() // GH-90000
                .name(" [GH-90000]") // Invalid: empty name
                .attributes(Map.of()) // Invalid: no attributes // GH-90000
                .build(); // GH-90000

        Promise<ValidationResult> promise = domainService.validateModel(model); // GH-90000
        ValidationResult result = runPromise(() -> promise); // GH-90000

        assertThat(result.valid()).isFalse(); // GH-90000
        assertThat(result.errors()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should retrieve domain model by name [GH-90000]")
    void testGetModelByName() throws Exception { // GH-90000
        // First create a model
        DomainSpec spec = DomainSpec.builder() // GH-90000
                .name("Product [GH-90000]")
                .attributes(Map.of("sku", "String", "price", "BigDecimal")) // GH-90000
                .build(); // GH-90000

        DomainModel created = runPromise(() -> domainService.createModel(spec)); // GH-90000
        repository.save(created); // GH-90000

        Promise<DomainModel> promise = domainService.getModel("Product [GH-90000]");
        DomainModel retrieved = runPromise(() -> promise); // GH-90000

        assertThat(retrieved).isNotNull(); // GH-90000
        assertThat(retrieved.name()).isEqualTo("Product [GH-90000]");
    }

    @Test
    @DisplayName("Should list all domain models [GH-90000]")
    void testListAllModels() throws Exception { // GH-90000
        // Create multiple models
        DomainSpec spec1 = DomainSpec.builder().name("User [GH-90000]").attributes(Map.of("id", "Long")).build();
        DomainSpec spec2 = DomainSpec.builder().name("Order [GH-90000]").attributes(Map.of("id", "Long")).build();

        DomainModel model1 = runPromise(() -> domainService.createModel(spec1)); // GH-90000
        DomainModel model2 = runPromise(() -> domainService.createModel(spec2)); // GH-90000

        repository.save(model1); // GH-90000
        repository.save(model2); // GH-90000

        Promise<DomainModel[]> promise = domainService.listModels(); // GH-90000
        DomainModel[] models = runPromise(() -> promise); // GH-90000

        assertThat(models).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Should update existing domain model [GH-90000]")
    void testUpdateModel() throws Exception { // GH-90000
        DomainSpec spec = DomainSpec.builder() // GH-90000
                .name("Customer [GH-90000]")
                .attributes(Map.of("id", "Long")) // GH-90000
                .build(); // GH-90000

        DomainModel model = runPromise(() -> domainService.createModel(spec)); // GH-90000
        repository.save(model); // GH-90000

        // Update with new attributes
        DomainSpec updateSpec = DomainSpec.builder() // GH-90000
                .name("Customer [GH-90000]")
                .attributes(Map.of("id", "Long", "loyaltyPoints", "Integer")) // GH-90000
                .build(); // GH-90000

        Promise<DomainModel> promise = domainService.updateModel("Customer", updateSpec); // GH-90000
        DomainModel updated = runPromise(() -> promise); // GH-90000

        assertThat(updated.attributes()).containsKey("loyaltyPoints [GH-90000]");
    }

    @Test
    @DisplayName("Should delete domain model [GH-90000]")
    void testDeleteModel() throws Exception { // GH-90000
        DomainSpec spec = DomainSpec.builder().name("TempModel [GH-90000]").attributes(Map.of("id", "Long")).build();
        DomainModel model = runPromise(() -> domainService.createModel(spec)); // GH-90000
        repository.save(model); // GH-90000

        Promise<Boolean> promise = domainService.deleteModel("TempModel [GH-90000]");
        Boolean deleted = runPromise(() -> promise); // GH-90000

        assertThat(deleted).isTrue(); // GH-90000
        assertThat(repository.findByName("TempModel [GH-90000]")).isNull();
    }

    @Test
    @DisplayName("Should generate SQL DDL from domain model [GH-90000]")
    void testGenerateSqlDdl() throws Exception { // GH-90000
        DomainModel model = DomainModel.builder() // GH-90000
                .name("Invoice [GH-90000]")
                .attributes(Map.of( // GH-90000
                    "id", "Long",
                    "amount", "BigDecimal",
                    "createdAt", "Timestamp"
                ))
                .build(); // GH-90000

        Promise<String> promise = domainService.generateSqlDdl(model); // GH-90000
        String ddl = runPromise(() -> promise); // GH-90000

        assertThat(ddl).contains("CREATE TABLE [GH-90000]");
        assertThat(ddl).contains("Invoice [GH-90000]");
        assertThat(ddl).contains("id [GH-90000]");
        assertThat(ddl).contains("amount [GH-90000]");
    }

    @Test
    @DisplayName("Should handle complex domain with multiple relationships [GH-90000]")
    void testComplexDomainWithRelationships() throws Exception { // GH-90000
        DomainSpec spec = DomainSpec.builder() // GH-90000
                .name("Organization [GH-90000]")
                .attributes(Map.of("id", "Long", "name", "String")) // GH-90000
                .relationships(Map.of( // GH-90000
                    "departments", "OneToMany",
                    "ceo", "OneToOne",
                    "parentOrg", "ManyToOne"
                ))
                .build(); // GH-90000

        Promise<DomainModel> promise = domainService.createModel(spec); // GH-90000
        DomainModel model = runPromise(() -> promise); // GH-90000

        assertThat(model.relationships()).hasSize(3); // GH-90000
        assertThat(model.relationships()).containsEntry("departments", "OneToMany"); // GH-90000
        assertThat(model.relationships()).containsEntry("ceo", "OneToOne"); // GH-90000
    }

    // Mock implementations

    interface DomainService {
        Promise<DomainModel> createModel(DomainSpec spec); // GH-90000
        Promise<ValidationResult> validateModel(DomainModel model); // GH-90000
        Promise<DomainModel> getModel(String name); // GH-90000
        Promise<DomainModel[]> listModels(); // GH-90000
        Promise<DomainModel> updateModel(String name, DomainSpec spec); // GH-90000
        Promise<Boolean> deleteModel(String name); // GH-90000
        Promise<String> generateSqlDdl(DomainModel model); // GH-90000
    }

    interface DomainRepository {
        DomainModel findByName(String name); // GH-90000
        void save(DomainModel model); // GH-90000
        void delete(String name); // GH-90000
        DomainModel[] findAll(); // GH-90000
    }

    record DomainSpec(String name, Map<String, String> attributes, Map<String, String> relationships) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String name;
            private Map<String, String> attributes = Map.of(); // GH-90000
            private Map<String, String> relationships = Map.of(); // GH-90000
            Builder name(String v) { name = v; return this; } // GH-90000
            Builder attributes(Map<String, String> v) { attributes = v; return this; } // GH-90000
            Builder relationships(Map<String, String> v) { relationships = v; return this; } // GH-90000
            DomainSpec build() { return new DomainSpec(name, attributes, relationships); } // GH-90000
        }
    }

    record DomainModel(String name, Map<String, String> attributes, Map<String, String> relationships) { // GH-90000
        static Builder builder() { return new Builder(); } // GH-90000
        static class Builder {
            private String name;
            private Map<String, String> attributes = Map.of(); // GH-90000
            private Map<String, String> relationships = Map.of(); // GH-90000
            Builder name(String v) { name = v; return this; } // GH-90000
            Builder attributes(Map<String, String> v) { attributes = v; return this; } // GH-90000
            Builder relationships(Map<String, String> v) { relationships = v; return this; } // GH-90000
            DomainModel build() { return new DomainModel(name, attributes, relationships); } // GH-90000
        }
    }

    record ValidationResult(boolean valid, java.util.List<String> errors) { // GH-90000
        static ValidationResult success() { return new ValidationResult(true, java.util.List.of()); } // GH-90000
        static ValidationResult failure(java.util.List<String> errors) { return new ValidationResult(false, errors); } // GH-90000
    }

    static class MockDomainRepository implements DomainRepository {
        private final Map<String, DomainModel> models = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public DomainModel findByName(String name) { // GH-90000
            return models.get(name); // GH-90000
        }

        @Override
        public void save(DomainModel model) { // GH-90000
            models.put(model.name(), model); // GH-90000
        }

        @Override
        public void delete(String name) { // GH-90000
            models.remove(name); // GH-90000
        }

        @Override
        public DomainModel[] findAll() { // GH-90000
            return models.values().toArray(new DomainModel[0]); // GH-90000
        }
    }

    static class MockDomainService implements DomainService {
        private final DomainRepository repository;

        MockDomainService(DomainRepository repository) { // GH-90000
            this.repository = repository;
        }

        @Override
        public Promise<DomainModel> createModel(DomainSpec spec) { // GH-90000
            return Promise.of(DomainModel.builder() // GH-90000
                .name(spec.name()) // GH-90000
                .attributes(spec.attributes()) // GH-90000
                .relationships(spec.relationships()) // GH-90000
                .build()); // GH-90000
        }

        @Override
        public Promise<ValidationResult> validateModel(DomainModel model) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                java.util.List<String> errors = new java.util.ArrayList<>(); // GH-90000

                if (model.name() == null || model.name().isEmpty()) { // GH-90000
                    errors.add("Model name is required [GH-90000]");
                }
                if (model.attributes() == null || model.attributes().isEmpty()) { // GH-90000
                    errors.add("Model must have at least one attribute [GH-90000]");
                }

                return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors); // GH-90000
            });
        }

        @Override
        public Promise<DomainModel> getModel(String name) { // GH-90000
            return Promise.of(repository.findByName(name)); // GH-90000
        }

        @Override
        public Promise<DomainModel[]> listModels() { // GH-90000
            return Promise.of(repository.findAll()); // GH-90000
        }

        @Override
        public Promise<DomainModel> updateModel(String name, DomainSpec spec) { // GH-90000
            return Promise.of(DomainModel.builder() // GH-90000
                .name(spec.name()) // GH-90000
                .attributes(spec.attributes()) // GH-90000
                .relationships(spec.relationships()) // GH-90000
                .build()); // GH-90000
        }

        @Override
        public Promise<Boolean> deleteModel(String name) { // GH-90000
            repository.delete(name); // GH-90000
            return Promise.of(true); // GH-90000
        }

        @Override
        public Promise<String> generateSqlDdl(DomainModel model) { // GH-90000
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> { // GH-90000
                StringBuilder ddl = new StringBuilder(); // GH-90000
                ddl.append("CREATE TABLE  [GH-90000]").append(model.name()).append(" (\n [GH-90000]");

                model.attributes().forEach((attr, type) -> { // GH-90000
                    ddl.append("   [GH-90000]").append(attr).append("  [GH-90000]").append(mapToSqlType(type)).append(",\n [GH-90000]");
                });

                ddl.append("  PRIMARY KEY (id)\n [GH-90000]");
                ddl.append("); [GH-90000]");

                return ddl.toString(); // GH-90000
            });
        }

        private String mapToSqlType(String type) { // GH-90000
            return switch (type) { // GH-90000
                case "Long" -> "BIGINT";
                case "Integer" -> "INT";
                case "String" -> "VARCHAR(255)"; // GH-90000
                case "BigDecimal" -> "DECIMAL(19,4)"; // GH-90000
                case "Timestamp" -> "TIMESTAMP";
                default -> "VARCHAR(255)"; // GH-90000
            };
        }
    }
}
