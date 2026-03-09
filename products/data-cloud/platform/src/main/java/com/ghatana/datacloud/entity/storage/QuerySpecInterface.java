package com.ghatana.datacloud.entity.storage;

import java.util.List;

/**
 * Interface for QuerySpec to avoid circular dependencies.
 * This interface defines the contract for query specifications
 * without requiring the concrete implementation.
 * 
 * <p>Supports two approaches for filtering and sorting:
 * <ol>
 *   <li><b>Legacy (backward compatible):</b> getFilters() returns Map&lt;String, Object&gt; 
 *       with simple equality filters, getSortFields() returns List&lt;String&gt;</li>
 *   <li><b>Enhanced (recommended):</b> getFilterCriteria() returns List&lt;FilterCriteria&gt; 
 *       with comparison operators, getSortSpecs() returns List&lt;SortSpec&gt; with direction</li>
 * </ol>
 * 
 * <p>Implementations should prefer enhanced methods when available, falling back to legacy.
 *
 * @doc.type interface
 * @doc.purpose Query spec interface with enhanced filtering
 * @doc.layer platform
 * @doc.pattern Interface
*/
public interface QuerySpecInterface {

    // Enhanced methods
    
    /**
     * Get filter criteria with comparison operators (EQ, GT, LT, etc.).
     * When null or empty, falls back to getFilters().
     */
    default List<FilterCriteria> getFilterCriteria() {
        return null;
    }
    
    default void setFilterCriteria(List<FilterCriteria> criteria) {
        // No-op by default, implementations should override
    }
    
    /**
     * Get sort specifications with direction control (ASC/DESC).
     * When null or empty, falls back to getSortFields().
     */
    default List<SortSpec> getSortSpecs() {
        return null;
    }
    
    default void setSortSpecs(List<SortSpec> sortSpecs) {
        // No-op by default, implementations should override
    }

    // Common methods
    
    Integer getLimit();

    void setLimit(Integer limit);

    Integer getOffset();

    void setOffset(Integer offset);

    String getQueryType();

    void setQueryType(String queryType);

    String getFilter();

    void setFilter(String filter);
}
