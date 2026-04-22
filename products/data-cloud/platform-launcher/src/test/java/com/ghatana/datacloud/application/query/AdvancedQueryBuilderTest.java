package com.ghatana.datacloud.application.query;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdvancedQueryBuilder [GH-90000]")
class AdvancedQueryBuilderTest extends EventloopTestBase {

    @Test
    @DisplayName("executes filters sorting and pagination against supplied dataset [GH-90000]")
    void executesFiltersSortingAndPaginationAgainstSuppliedDataset() { // GH-90000
        MetaCollection collection = MetaCollection.builder() // GH-90000
            .tenantId("tenant-a [GH-90000]")
            .name("orders [GH-90000]")
            .fields(List.of( // GH-90000
                MetaField.builder().collectionId(UUID.randomUUID()).name("customerId [GH-90000]").type(DataType.STRING).uniqueConstraint(true).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("amount [GH-90000]").type(DataType.NUMBER).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("status [GH-90000]").type(DataType.STRING).build()))
            .build(); // GH-90000

        AdvancedQueryBuilder builder = new AdvancedQueryBuilder(collection) // GH-90000
            .filter("status", "=", "active") // GH-90000
            .filterBetween("amount", 100, 400) // GH-90000
            .sort("amount", "DESC") // GH-90000
            .offset(1) // GH-90000
            .limit(2) // GH-90000
            .withExecutionDataset(List.of( // GH-90000
                Map.of("customerId", "cust-1", "status", "active", "amount", 180), // GH-90000
                Map.of("customerId", "cust-2", "status", "active", "amount", 320), // GH-90000
                Map.of("customerId", "cust-3", "status", "inactive", "amount", 250), // GH-90000
                Map.of("customerId", "cust-4", "status", "active", "amount", 220))); // GH-90000

        AdvancedQueryBuilder.QueryResults results = runPromise(builder::buildAndExecute); // GH-90000

        assertThat(results.totalCount()).isEqualTo(3); // GH-90000
        assertThat(results.rows()).extracting(row -> row.get("customerId [GH-90000]"))
            .containsExactly("cust-4", "cust-1"); // GH-90000
    }

    @Test
    @DisplayName("uses collection metadata to generate index hints [GH-90000]")
    void usesCollectionMetadataToGenerateIndexHints() { // GH-90000
        MetaCollection collection = MetaCollection.builder() // GH-90000
            .tenantId("tenant-a [GH-90000]")
            .name("profiles [GH-90000]")
            .fields(List.of( // GH-90000
                MetaField.builder().collectionId(UUID.randomUUID()).name("email [GH-90000]").type(DataType.STRING).uniqueConstraint(true).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("managerId [GH-90000]").type(DataType.REFERENCE).build()))
            .build(); // GH-90000

        AdvancedQueryBuilder.QueryPlan plan = new AdvancedQueryBuilder(collection) // GH-90000
            .filterLike("email", "%@ghatana.ai") // GH-90000
            .withOptimization(true) // GH-90000
            .build(); // GH-90000

        assertThat(plan.hints()).isNotNull(); // GH-90000
        assertThat(plan.hints().indexedFields()).contains("id", "email", "managerId"); // GH-90000
    }
}