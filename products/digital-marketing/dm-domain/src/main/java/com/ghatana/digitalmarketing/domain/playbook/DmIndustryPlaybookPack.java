package com.ghatana.digitalmarketing.domain.playbook;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an industry-specific playbook pack.
 *
 * @doc.type class
 * @doc.purpose Domain entity for industry playbook packs (DMOS-F4-004)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmIndustryPlaybookPack {

    private final String id;
    private final String name;
    private final String industry;
    private final String description;
    private final String version;
    private final List<String> playbookIds;
    private final boolean published;
    private final Instant publishedAt;
    private final Instant createdAt;

    private DmIndustryPlaybookPack(Builder b) {
        this.id          = b.id;
        this.name        = b.name;
        this.industry    = b.industry;
        this.description = b.description;
        this.version     = b.version;
        this.playbookIds = List.copyOf(b.playbookIds);
        this.published   = b.published;
        this.publishedAt = b.publishedAt;
        this.createdAt   = b.createdAt;
    }

    public String getId()                { return id; }
    public String getName()              { return name; }
    public String getIndustry()          { return industry; }
    public String getDescription()       { return description; }
    public String getVersion()           { return version; }
    public List<String> getPlaybookIds() { return playbookIds; }
    public boolean isPublished()         { return published; }
    public Instant getPublishedAt()      { return publishedAt; }
    public Instant getCreatedAt()        { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmIndustryPlaybookPack)) return false;
        return id.equals(((DmIndustryPlaybookPack) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmIndustryPlaybookPack{id='" + id + "', industry='" + industry + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, name, industry, description, version;
        private List<String> playbookIds = List.of();
        private boolean published;
        private Instant publishedAt, createdAt;

        public Builder id(String v)              { this.id = v; return this; }
        public Builder name(String v)            { this.name = v; return this; }
        public Builder industry(String v)        { this.industry = v; return this; }
        public Builder description(String v)     { this.description = v; return this; }
        public Builder version(String v)         { this.version = v; return this; }
        public Builder playbookIds(List<String> v) { this.playbookIds = v; return this; }
        public Builder published(boolean v)      { this.published = v; return this; }
        public Builder publishedAt(Instant v)    { this.publishedAt = v; return this; }
        public Builder createdAt(Instant v)      { this.createdAt = v; return this; }

        public DmIndustryPlaybookPack build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (industry == null || industry.isBlank()) throw new IllegalArgumentException("industry must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(playbookIds, "playbookIds must not be null");
            return new DmIndustryPlaybookPack(this);
        }
    }
}
