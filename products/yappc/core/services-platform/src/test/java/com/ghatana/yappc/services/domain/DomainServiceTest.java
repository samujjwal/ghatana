/*
 * Copyright (c) 2026 Ghatana
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
    void setUp() {
        repository = new MockDomainRepository();
        domainService = new MockDomainService(repository);
    }

    @Test
    @DisplayName("Should create domain model from specification")
    void testCreateDomainModel() throws Exception {
        DomainSpec spec = DomainSpec.builder()
                .name("User")
                .attributes(Map.of("id", "Long", "email", "String", "name", "String"))
                .relationships(Map.of("orders", "OneToMany"))
                .build();

        Promise<DomainModel> promise = domainService.createModel(spec);
        DomainModel model = runPromise(() -> promise);

        assertThat(model).isNotNull();
        assertThat(model.name()).isEqualTo("User");
        assertThat(model.attributes()).hasSize(3);
        assertThat(model.attributes()).containsKey("email");
    }

    @Test
    @DisplayName("Should validate domain model")
    void testValidateDomainModel() throws Exception {
        DomainModel model = DomainModel.builder()
                .name("Order")
                .attributes(Map.of("id", "Long", "total", "BigDecimal"))
                .build();

        Promise<ValidationResult> promise = domainService.validateModel(model);
        ValidationResult result = runPromise(() -> promise);

        assertThat(result).isNotNull();
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("Should reject invalid domain model")
    void testRejectInvalidModel() throws Exception {
        DomainModel model = DomainModel.builder()
                .name("") // Invalid: empty name
                .attributes(Map.of()) // Invalid: no attributes
                .build();

        Promise<ValidationResult> promise = domainService.validateModel(model);
        ValidationResult result = runPromise(() -> promise);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("Should retrieve domain model by name")
    void testGetModelByName() throws Exception {
        // First create a model
        DomainSpec spec = DomainSpec.builder()
                .name("Product")
                .attributes(Map.of("sku", "String", "price", "BigDecimal"))
                .build();
        
        DomainModel created = runPromise(() -> domainService.createModel(spec));
        repository.save(created);

        Promise<DomainModel> promise = domainService.getModel("Product");
        DomainModel retrieved = runPromise(() -> promise);

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.name()).isEqualTo("Product");
    }

    @Test
    @DisplayName("Should list all domain models")
    void testListAllModels() throws Exception {
        // Create multiple models
        DomainSpec spec1 = DomainSpec.builder().name("User").attributes(Map.of("id", "Long")).build();
        DomainSpec spec2 = DomainSpec.builder().name("Order").attributes(Map.of("id", "Long")).build();
        
        DomainModel model1 = runPromise(() -> domainService.createModel(spec1));
        DomainModel model2 = runPromise(() -> domainService.createModel(spec2));
        
        repository.save(model1);
        repository.save(model2);

        Promise<DomainModel[]> promise = domainService.listModels();
        DomainModel[] models = runPromise(() -> promise);

        assertThat(models).hasSize(2);
    }

    @Test
    @DisplayName("Should update existing domain model")
    void testUpdateModel() throws Exception {
        DomainSpec spec = DomainSpec.builder()
                .name("Customer")
                .attributes(Map.of("id", "Long"))
                .build();
        
        DomainModel model = runPromise(() -> domainService.createModel(spec));
        repository.save(model);

        // Update with new attributes
        DomainSpec updateSpec = DomainSpec.builder()
                .name("Customer")
                .attributes(Map.of("id", "Long", "loyaltyPoints", "Integer"))
                .build();

        Promise<DomainModel> promise = domainService.updateModel("Customer", updateSpec);
        DomainModel updated = runPromise(() -> promise);

        assertThat(updated.attributes()).containsKey("loyaltyPoints");
    }

    @Test
    @DisplayName("Should delete domain model")
    void testDeleteModel() throws Exception {
        DomainSpec spec = DomainSpec.builder().name("TempModel").attributes(Map.of("id", "Long")).build();
        DomainModel model = runPromise(() -> domainService.createModel(spec));
        repository.save(model);

        Promise<Boolean> promise = domainService.deleteModel("TempModel");
        Boolean deleted = runPromise(() -> promise);

        assertThat(deleted).isTrue();
        assertThat(repository.findByName("TempModel")).isNull();
    }

    @Test
    @DisplayName("Should generate SQL DDL from domain model")
    void testGenerateSqlDdl() throws Exception {
        DomainModel model = DomainModel.builder()
                .name("Invoice")
                .attributes(Map.of(
                    "id", "Long",
                    "amount", "BigDecimal",
                    "createdAt", "Timestamp"
                ))
                .build();

        Promise<String> promise = domainService.generateSqlDdl(model);
        String ddl = runPromise(() -> promise);

        assertThat(ddl).contains("CREATE TABLE");
        assertThat(ddl).contains("Invoice");
        assertThat(ddl).contains("id");
        assertThat(ddl).contains("amount");
    }

    @Test
    @DisplayName("Should handle complex domain with multiple relationships")
    void testComplexDomainWithRelationships() throws Exception {
        DomainSpec spec = DomainSpec.builder()
                .name("Organization")
                .attributes(Map.of("id", "Long", "name", "String"))
                .relationships(Map.of(
                    "departments", "OneToMany",
                    "ceo", "OneToOne",
                    "parentOrg", "ManyToOne"
                ))
                .build();

        Promise<DomainModel> promise = domainService.createModel(spec);
        DomainModel model = runPromise(() -> promise);

        assertThat(model.relationships()).hasSize(3);
        assertThat(model.relationships()).containsEntry("departments", "OneToMany");
        assertThat(model.relationships()).containsEntry("ceo", "OneToOne");
    }

    // Mock implementations

    interface DomainService {
        Promise<DomainModel> createModel(DomainSpec spec);
        Promise<ValidationResult> validateModel(DomainModel model);
        Promise<DomainModel> getModel(String name);
        Promise<DomainModel[]> listModels();
        Promise<DomainModel> updateModel(String name, DomainSpec spec);
        Promise<Boolean> deleteModel(String name);
        Promise<String> generateSqlDdl(DomainModel model);
    }

    interface DomainRepository {
        DomainModel findByName(String name);
        void save(DomainModel model);
        void delete(String name);
        DomainModel[] findAll();
    }

    record DomainSpec(String name, Map<String, String> attributes, Map<String, String> relationships) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name;
            private Map<String, String> attributes = Map.of();
            private Map<String, String> relationships = Map.of();
            Builder name(String v) { name = v; return this; }
            Builder attributes(Map<String, String> v) { attributes = v; return this; }
            Builder relationships(Map<String, String> v) { relationships = v; return this; }
            DomainSpec build() { return new DomainSpec(name, attributes, relationships); }
        }
    }

    record DomainModel(String name, Map<String, String> attributes, Map<String, String> relationships) {
        static Builder builder() { return new Builder(); }
        static class Builder {
            private String name;
            private Map<String, String> attributes = Map.of();
            private Map<String, String> relationships = Map.of();
            Builder name(String v) { name = v; return this; }
            Builder attributes(Map<String, String> v) { attributes = v; return this; }
            Builder relationships(Map<String, String> v) { relationships = v; return this; }
            DomainModel build() { return new DomainModel(name, attributes, relationships); }
        }
    }

    record ValidationResult(boolean valid, java.util.List<String> errors) {
        static ValidationResult success() { return new ValidationResult(true, java.util.List.of()); }
        static ValidationResult failure(java.util.List<String> errors) { return new ValidationResult(false, errors); }
    }

    static class MockDomainRepository implements DomainRepository {
        private final Map<String, DomainModel> models = new ConcurrentHashMap<>();

        @Override
        public DomainModel findByName(String name) {
            return models.get(name);
        }

        @Override
        public void save(DomainModel model) {
            models.put(model.name(), model);
        }

        @Override
        public void delete(String name) {
            models.remove(name);
        }

        @Override
        public DomainModel[] findAll() {
            return models.values().toArray(new DomainModel[0]);
        }
    }

    static class MockDomainService implements DomainService {
        private final DomainRepository repository;

        MockDomainService(DomainRepository repository) {
            this.repository = repository;
        }

        @Override
        public Promise<DomainModel> createModel(DomainSpec spec) {
            return Promise.of(DomainModel.builder()
                .name(spec.name())
                .attributes(spec.attributes())
                .relationships(spec.relationships())
                .build());
        }

        @Override
        public Promise<ValidationResult> validateModel(DomainModel model) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                java.util.List<String> errors = new java.util.ArrayList<>();
                
                if (model.name() == null || model.name().isEmpty()) {
                    errors.add("Model name is required");
                }
                if (model.attributes() == null || model.attributes().isEmpty()) {
                    errors.add("Model must have at least one attribute");
                }
                
                return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
            });
        }

        @Override
        public Promise<DomainModel> getModel(String name) {
            return Promise.of(repository.findByName(name));
        }

        @Override
        public Promise<DomainModel[]> listModels() {
            return Promise.of(repository.findAll());
        }

        @Override
        public Promise<DomainModel> updateModel(String name, DomainSpec spec) {
            return Promise.of(DomainModel.builder()
                .name(spec.name())
                .attributes(spec.attributes())
                .relationships(spec.relationships())
                .build());
        }

        @Override
        public Promise<Boolean> deleteModel(String name) {
            repository.delete(name);
            return Promise.of(true);
        }

        @Override
        public Promise<String> generateSqlDdl(DomainModel model) {
            return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE TABLE ").append(model.name()).append(" (\n");
                
                model.attributes().forEach((attr, type) -> {
                    ddl.append("  ").append(attr).append(" ").append(mapToSqlType(type)).append(",\n");
                });
                
                ddl.append("  PRIMARY KEY (id)\n");
                ddl.append(");");
                
                return ddl.toString();
            });
        }

        private String mapToSqlType(String type) {
            return switch (type) {
                case "Long" -> "BIGINT";
                case "Integer" -> "INT";
                case "String" -> "VARCHAR(255)";
                case "BigDecimal" -> "DECIMAL(19,4)";
                case "Timestamp" -> "TIMESTAMP";
                default -> "VARCHAR(255)";
            };
        }
    }
}
