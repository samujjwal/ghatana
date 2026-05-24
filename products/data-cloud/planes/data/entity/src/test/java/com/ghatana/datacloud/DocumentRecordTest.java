/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3: Contract tests for DocumentRecord.
 *
 * @doc.type class
 * @doc.purpose Tests for DocumentRecord document store functionality
 * @doc.layer test
 */
@DisplayName("DocumentRecord Tests")
class DocumentRecordTest {

    @Nested
    @DisplayName("Builder and Construction")
    class BuilderTests {

        @Test
        @DisplayName("builder creates document with all fields")
        void builderCreatesDocumentWithAllFields() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("knowledge-base")
                .title("Getting Started Guide")
                .slug("getting-started")
                .contentType("application/json")
                .tags("onboarding,guide")
                .language("en")
                .data(Map.of("sections", List.of("intro", "setup")))
                .build();

            assertThat(doc.getTenantId()).isEqualTo("tenant-123");
            assertThat(doc.getCollectionName()).isEqualTo("knowledge-base");
            assertThat(doc.getTitle()).isEqualTo("Getting Started Guide");
            assertThat(doc.getSlug()).isEqualTo("getting-started");
            assertThat(doc.getContentType()).isEqualTo("application/json");
            assertThat(doc.getTags()).isEqualTo("onboarding,guide");
            assertThat(doc.getLanguage()).isEqualTo("en");
            assertThat(doc.getRecordType()).isEqualTo(RecordType.DOCUMENT);
        }

        @Test
        @DisplayName("builder uses default contentType when not specified")
        void builderUsesDefaultContentType() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .build();

            assertThat(doc.getContentType()).isEqualTo("application/json");
        }

        @Test
        @DisplayName("builder uses default version 1")
        void builderUsesDefaultVersionOne() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .build();

            assertThat(doc.getVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("builder uses default active true")
        void builderUsesDefaultActiveTrue() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .build();

            assertThat(doc.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("Tag Management")
    class TagManagementTests {

        @Test
        @DisplayName("getTagList splits comma-separated tags")
        void getTagListSplitsCommaSeparatedTags() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("onboarding, guide, quickstart")
                .build();

            List<String> tags = doc.getTagList();

            assertThat(tags).containsExactly("onboarding", "guide", "quickstart");
        }

        @Test
        @DisplayName("getTagList trims whitespace")
        void getTagListTrimsWhitespace() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags(" onboarding , guide , quickstart ")
                .build();

            List<String> tags = doc.getTagList();

            assertThat(tags).containsExactly("onboarding", "guide", "quickstart");
        }

        @Test
        @DisplayName("getTagList returns empty list for null tags")
        void getTagListReturnsEmptyForNullTags() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags(null)
                .build();

            assertThat(doc.getTagList()).isEmpty();
        }

        @Test
        @DisplayName("getTagList returns empty list for blank tags")
        void getTagListReturnsEmptyForBlankTags() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("")
                .build();

            assertThat(doc.getTagList()).isEmpty();
        }

        @Test
        @DisplayName("getTagList filters empty tags")
        void getTagListFiltersEmptyTags() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("onboarding,,guide")
                .build();

            List<String> tags = doc.getTagList();

            assertThat(tags).containsExactly("onboarding", "guide");
        }

        @Test
        @DisplayName("setTagList joins tags with comma")
        void setTagListJoinsTagsWithComma() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .build();

            doc.setTagList(List.of("onboarding", "guide", "quickstart"));

            assertThat(doc.getTags()).isEqualTo("onboarding,guide,quickstart");
        }

        @Test
        @DisplayName("setTagList sets null for empty collection")
        void setTagListSetsNullForEmptyCollection() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("existing")
                .build();

            doc.setTagList(List.of());

            assertThat(doc.getTags()).isNull();
        }

        @Test
        @DisplayName("hasTag returns true for matching tag")
        void hasTagReturnsTrueForMatchingTag() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("onboarding,guide")
                .build();

            assertThat(doc.hasTag("onboarding")).isTrue();
        }

        @Test
        @DisplayName("hasTag is case-insensitive")
        void hasTagIsCaseInsensitive() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("Onboarding,Guide")
                .build();

            assertThat(doc.hasTag("onboarding")).isTrue();
            assertThat(doc.hasTag("GUIDE")).isTrue();
        }

        @Test
        @DisplayName("hasTag returns false for non-matching tag")
        void hasTagReturnsFalseForNonMatchingTag() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("onboarding,guide")
                .build();

            assertThat(doc.hasTag("tutorial")).isFalse();
        }

        @Test
        @DisplayName("hasTag returns false for null tag")
        void hasTagReturnsFalseForNullTag() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .tags("onboarding")
                .build();

            assertThat(doc.hasTag(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Content Type Helpers")
    class ContentTypeHelperTests {

        @Test
        @DisplayName("isJson returns true for application/json")
        void isJsonReturnsTrueForApplicationJson() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType("application/json")
                .build();

            assertThat(doc.isJson()).isTrue();
        }

        @Test
        @DisplayName("isJson returns false for text/plain")
        void isJsonReturnsFalseForTextPlain() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType("text/plain")
                .build();

            assertThat(doc.isJson()).isFalse();
        }

        @Test
        @DisplayName("isJson returns false for null contentType")
        void isJsonReturnsFalseForNullContentType() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType(null)
                .build();

            assertThat(doc.isJson()).isFalse();
        }

        @Test
        @DisplayName("isText returns true for text/plain")
        void isTextReturnsTrueForTextPlain() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType("text/plain")
                .build();

            assertThat(doc.isText()).isTrue();
        }

        @Test
        @DisplayName("isText returns true for text/markdown")
        void isTextReturnsTrueForTextMarkdown() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType("text/markdown")
                .build();

            assertThat(doc.isText()).isTrue();
        }

        @Test
        @DisplayName("isText returns false for application/json")
        void isTextReturnsFalseForApplicationJson() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .contentType("application/json")
                .build();

            assertThat(doc.isText()).isFalse();
        }
    }

    @Nested
    @DisplayName("Soft Delete and Restore")
    class SoftDeleteRestoreTests {

        @Test
        @DisplayName("softDelete sets active to false")
        void softDeleteSetsActiveToFalse() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .active(true)
                .build();

            doc.softDelete("user-1");

            assertThat(doc.getActive()).isFalse();
            assertThat(doc.getUpdatedBy()).isEqualTo("user-1");
            assertThat(doc.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("restore sets active to true")
        void restoreSetsActiveToTrue() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .active(false)
                .build();

            doc.restore("user-1");

            assertThat(doc.getActive()).isTrue();
            assertThat(doc.getUpdatedBy()).isEqualTo("user-1");
            assertThat(doc.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("isDeleted returns true when active is false")
        void isDeletedReturnsTrueWhenActiveIsFalse() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .active(false)
                .build();

            assertThat(doc.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("isDeleted returns false when active is true")
        void isDeletedReturnsFalseWhenActiveIsTrue() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .active(true)
                .build();

            assertThat(doc.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("isDeleted returns true when active is null")
        void isDeletedReturnsTrueWhenActiveIsNull() {
            DocumentRecord doc = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Test")
                .active(null)
                .build();

            assertThat(doc.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("toBuilder")
    class ToBuilderTests {

        @Test
        @DisplayName("toBuilder creates builder with existing values")
        void toBuilderCreatesBuilderWithExistingValues() {
            DocumentRecord original = DocumentRecord.builder()
                .tenantId("tenant-123")
                .collectionName("docs")
                .title("Original Title")
                .slug("original-slug")
                .build();

            DocumentRecord updated = original.toBuilder()
                .title("Updated Title")
                .slug("updated-slug")
                .build();

            assertThat(updated.getTenantId()).isEqualTo("tenant-123");
            assertThat(updated.getCollectionName()).isEqualTo("docs");
            assertThat(updated.getTitle()).isEqualTo("Updated Title");
            assertThat(updated.getSlug()).isEqualTo("updated-slug");
        }
    }
}
