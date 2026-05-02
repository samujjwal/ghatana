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

@DisplayName("AdvancedQueryBuilder")
class AdvancedQueryBuilderTest extends EventloopTestBase {

    @Test
    @DisplayName("executes filters sorting and pagination against supplied dataset")
    void executesFiltersSortingAndPaginationAgainstSuppliedDataset() { 
        MetaCollection collection = MetaCollection.builder() 
            .tenantId("tenant-a")
            .name("orders")
            .fields(List.of( 
                MetaField.builder().collectionId(UUID.randomUUID()).name("customerId").type(DataType.STRING).uniqueConstraint(true).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("amount").type(DataType.NUMBER).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("status").type(DataType.STRING).build()))
            .build(); 

        AdvancedQueryBuilder builder = new AdvancedQueryBuilder(collection) 
            .filter("status", "=", "active") 
            .filterBetween("amount", 100, 400) 
            .sort("amount", "DESC") 
            .offset(1) 
            .limit(2) 
            .withExecutionDataset(List.of( 
                Map.of("customerId", "cust-1", "status", "active", "amount", 180), 
                Map.of("customerId", "cust-2", "status", "active", "amount", 320), 
                Map.of("customerId", "cust-3", "status", "inactive", "amount", 250), 
                Map.of("customerId", "cust-4", "status", "active", "amount", 220))); 

        AdvancedQueryBuilder.QueryResults results = runPromise(builder::buildAndExecute); 

        assertThat(results.totalCount()).isEqualTo(3); 
        assertThat(results.rows()).extracting(row -> row.get("customerId"))
            .containsExactly("cust-4", "cust-1"); 
    }

    @Test
    @DisplayName("uses collection metadata to generate index hints")
    void usesCollectionMetadataToGenerateIndexHints() { 
        MetaCollection collection = MetaCollection.builder() 
            .tenantId("tenant-a")
            .name("profiles")
            .fields(List.of( 
                MetaField.builder().collectionId(UUID.randomUUID()).name("email").type(DataType.STRING).uniqueConstraint(true).build(),
                MetaField.builder().collectionId(UUID.randomUUID()).name("managerId").type(DataType.REFERENCE).build()))
            .build(); 

        AdvancedQueryBuilder.QueryPlan plan = new AdvancedQueryBuilder(collection) 
            .filterLike("email", "%@ghatana.ai") 
            .withOptimization(true) 
            .build(); 

        assertThat(plan.hints()).isNotNull(); 
        assertThat(plan.hints().indexedFields()).contains("id", "email", "managerId"); 
    }
}