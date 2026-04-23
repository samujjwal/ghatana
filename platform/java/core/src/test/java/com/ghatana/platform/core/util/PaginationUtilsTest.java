package com.ghatana.platform.core.util;

import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.platform.core.common.pagination.PageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PaginationUtils}.
 */
@DisplayName("PaginationUtils")
class PaginationUtilsTest {

    @Nested
    @DisplayName("validateRequest")
    class ValidateRequest {

        @Test
        void returnsDefaultsWhenBothNull() { // GH-90000
            PageRequest req = PaginationUtils.validateRequest(null, null); // GH-90000

            assertThat(req.pageNumber()).isEqualTo(0); // GH-90000
            assertThat(req.pageSize()).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE); // GH-90000
        }

        @Test
        void acceptsExplicitValidValues() { // GH-90000
            PageRequest req = PaginationUtils.validateRequest(3, 25); // GH-90000

            assertThat(req.pageNumber()).isEqualTo(3); // GH-90000
            assertThat(req.pageSize()).isEqualTo(25); // GH-90000
        }

        @Test
        void acceptsMaxPageSize() { // GH-90000
            PageRequest req = PaginationUtils.validateRequest(0, PaginationUtils.MAX_PAGE_SIZE); // GH-90000
            assertThat(req.pageSize()).isEqualTo(PaginationUtils.MAX_PAGE_SIZE); // GH-90000
        }

        @Test
        void rejectsNegativePage() { // GH-90000
            assertThatThrownBy(() -> PaginationUtils.validateRequest(-1, 10)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Page must be >= 0");
        }

        @Test
        void rejectsPageSizeAboveMax() { // GH-90000
            assertThatThrownBy(() -> PaginationUtils.validateRequest(0, PaginationUtils.MAX_PAGE_SIZE + 1)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Page size must be between");
        }

        @Test
        void rejectsZeroPageSize() { // GH-90000
            assertThatThrownBy(() -> PaginationUtils.validateRequest(0, 0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Page size must be between");
        }
    }

    @Nested
    @DisplayName("clampPageSize")
    class ClampPageSize {

        @Test
        void clampsBelowMin() { // GH-90000
            assertThat(PaginationUtils.clampPageSize(0)).isEqualTo(PaginationUtils.MIN_PAGE_SIZE); // GH-90000
        }

        @Test
        void clampsAboveMax() { // GH-90000
            assertThat(PaginationUtils.clampPageSize(99999)).isEqualTo(PaginationUtils.MAX_PAGE_SIZE); // GH-90000
        }

        @Test
        void preservesValidValue() { // GH-90000
            assertThat(PaginationUtils.clampPageSize(100)).isEqualTo(100); // GH-90000
        }
    }

    @Nested
    @DisplayName("calculateOffset")
    class CalculateOffset {

        @Test
        void isZeroOnFirstPage() { // GH-90000
            assertThat(PaginationUtils.calculateOffset(0, 20)).isEqualTo(0L); // GH-90000
        }

        @Test
        void isCorrectOnSecondPage() { // GH-90000
            assertThat(PaginationUtils.calculateOffset(1, 20)).isEqualTo(20L); // GH-90000
        }

        @Test
        void handlesLargePageNumbers() { // GH-90000
            assertThat(PaginationUtils.calculateOffset(10_000, 1000)).isEqualTo(10_000_000L); // GH-90000
        }
    }

    @Nested
    @DisplayName("calculateTotalPages")
    class CalculateTotalPages {

        @Test
        void exactDivisionYieldsCorrectPages() { // GH-90000
            assertThat(PaginationUtils.calculateTotalPages(100, 20)).isEqualTo(5); // GH-90000
        }

        @Test
        void remainderAddsOnePage() { // GH-90000
            assertThat(PaginationUtils.calculateTotalPages(101, 20)).isEqualTo(6); // GH-90000
        }

        @Test
        void zeroElementsYieldsZeroPages() { // GH-90000
            assertThat(PaginationUtils.calculateTotalPages(0, 20)).isEqualTo(0); // GH-90000
        }
    }

    @Nested
    @DisplayName("toPage")
    class ToPage {

        @Test
        void wrapsContentAndMetadata() { // GH-90000
            PageRequest req = PageRequest.of(2, 10); // GH-90000
            List<String> items = List.of("a", "b", "c"); // GH-90000

            Page<String> page = PaginationUtils.toPage(items, req, 55); // GH-90000

            assertThat(page.content()).containsExactly("a", "b", "c"); // GH-90000
            assertThat(page.pageNumber()).isEqualTo(2); // GH-90000
            assertThat(page.pageSize()).isEqualTo(10); // GH-90000
            assertThat(page.totalElements()).isEqualTo(55); // GH-90000
        }

        @Test
        void hasNextWhenNotLastPage() { // GH-90000
            PageRequest req = PageRequest.of(0, 10); // GH-90000
            Page<String> page = PaginationUtils.toPage(List.of("x"), req, 25);
            assertThat(page.hasNext()).isTrue(); // GH-90000
        }

        @Test
        void hasNoPreviousOnFirstPage() { // GH-90000
            PageRequest req = PageRequest.of(0, 10); // GH-90000
            Page<String> page = PaginationUtils.toPage(List.of("x"), req, 5);
            assertThat(page.hasPrevious()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("cursor encoding")
    class CursorEncoding {

        @Test
        void roundTripWithSortValue() { // GH-90000
            String cursor = PaginationUtils.encodeCursor("entity-42", "2026-01-15T10:00:00Z"); // GH-90000
            PaginationUtils.CursorData data = PaginationUtils.decodeCursor(cursor); // GH-90000

            assertThat(data.lastId()).isEqualTo("entity-42");
            assertThat(data.lastSortValue()).isEqualTo("2026-01-15T10:00:00Z");
        }

        @Test
        void roundTripWithoutSortValue() { // GH-90000
            String cursor = PaginationUtils.encodeCursor("entity-99", null); // GH-90000
            PaginationUtils.CursorData data = PaginationUtils.decodeCursor(cursor); // GH-90000

            assertThat(data.lastId()).isEqualTo("entity-99");
            assertThat(data.lastSortValue()).isNull(); // GH-90000
        }

        @Test
        void encodedCursorIsUrlSafe() { // GH-90000
            String cursor = PaginationUtils.encodeCursor("abc", "2026-01-15T10:00:00Z"); // GH-90000
            assertThat(cursor).doesNotContain("+", "/", "="); // GH-90000
        }

        @Test
        void decodingInvalidCursorThrows() { // GH-90000
            assertThatThrownBy(() -> PaginationUtils.decodeCursor("not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Invalid cursor");
        }

        @Test
        void encodingNullIdThrows() { // GH-90000
            assertThatThrownBy(() -> PaginationUtils.encodeCursor(null, "sort")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
