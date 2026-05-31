plugins {
    id("ghatana.java-module")
}

dependencies {
    // Shared SPI for context contracts
    implementation(project(":platform:java:shared-spi"))
    
    // Data plane public contracts
    implementation(project(":products:data-cloud:planes:data:public-contracts"))
    
    // Event plane public contracts
    implementation(project(":products:data-cloud:planes:event:public-contracts"))
    
    // Governance plane public contracts
    implementation(project(":products:data-cloud:planes:governance:public-contracts"))
    
    // Platform observability for context tracking
    implementation(project(":platform:java:observability"))
    
    // Platform testing
    testImplementation(project(":platform:java:testing"))
}

description = "Context Plane: lineage, provenance, semantic context, freshness, memory/RAG grounding, and retrieval policies"

// Boundary rule: Context Plane must not depend on Action Plane implementation internals
// Only shared SPI and public contracts are allowed
