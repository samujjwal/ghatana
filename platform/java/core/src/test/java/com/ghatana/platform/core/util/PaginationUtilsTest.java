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
        void returnsDefaultsWhenBothNull() { 
            PageRequest req = PaginationUtils.validateRequest(null, null); 

            assertThat(req.pageNumber()).isEqualTo(0); 
            assertThat(req.pageSize()).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE); 
        }

        @Test
        void acceptsExplicitValidValues() { 
            PageRequest req = PaginationUtils.validateRequest(3, 25); 

            assertThat(req.pageNumber()).isEqualTo(3); 
            assertThat(req.pageSize()).isEqualTo(25); 
        }

        @Test
        void acceptsMaxPageSize() { 
            PageRequest req = PaginationUtils.validateRequest(0, PaginationUtils.MAX_PAGE_SIZE); 
            assertThat(req.pageSize()).isEqualTo(PaginationUtils.MAX_PAGE_SIZE); 
        }

        @Test
        void rejectsNegativePage() { 
            assertThatThrownBy(() -> PaginationUtils.validateRequest(-1, 10)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("Page must be >= 0");
        }

        @Test
        void rejectsPageSizeAboveMax() { 
            assertThatThrownBy(() -> PaginationUtils.validateRequest(0, PaginationUtils.MAX_PAGE_SIZE + 1)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("Page size must be between");
        }

        @Test
        void rejectsZeroPageSize() { 
            assertThatThrownBy(() -> PaginationUtils.validateRequest(0, 0)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("Page size must be between");
        }
    }

    @Nested
    @DisplayName("clampPageSize")
    class ClampPageSize {

        @Test
        void clampsBelowMin() { 
            assertThat(PaginationUtils.clampPageSize(0)).isEqualTo(PaginationUtils.MIN_PAGE_SIZE); 
        }

        @Test
        void clampsAboveMax() { 
            assertThat(PaginationUtils.clampPageSize(99999)).isEqualTo(PaginationUtils.MAX_PAGE_SIZE); 
        }

        @Test
        void preservesValidValue() { 
            assertThat(PaginationUtils.clampPageSize(100)).isEqualTo(100); 
        }
    }

    @Nested
    @DisplayName("calculateOffset")
    class CalculateOffset {

        @Test
        void isZeroOnFirstPage() { 
            assertThat(PaginationUtils.calculateOffset(0, 20)).isEqualTo(0L); 
        }

        @Test
        void isCorrectOnSecondPage() { 
            assertThat(PaginationUtils.calculateOffset(1, 20)).isEqualTo(20L); 
        }

        @Test
        void handlesLargePageNumbers() { 
            assertThat(PaginationUtils.calculateOffset(10_000, 1000)).isEqualTo(10_000_000L); 
        }
    }

    @Nested
    @DisplayName("calculateTotalPages")
    class CalculateTotalPages {

        @Test
        void exactDivisionYieldsCorrectPages() { 
            assertThat(PaginationUtils.calculateTotalPages(100, 20)).isEqualTo(5); 
        }

        @Test
        void remainderAddsOnePage() { 
            assertThat(PaginationUtils.calculateTotalPages(101, 20)).isEqualTo(6); 
        }

        @Test
        void zeroElementsYieldsZeroPages() { 
            assertThat(PaginationUtils.calculateTotalPages(0, 20)).isEqualTo(0); 
        }
    }

    @Nested
    @DisplayName("toPage")
    class ToPage {

        @Test
        void wrapsContentAndMetadata() { 
            PageRequest req = PageRequest.of(2, 10); 
            List<String> items = List.of("a", "b", "c"); 

            Page<String> page = PaginationUtils.toPage(items, req, 55); 

            assertThat(page.content()).containsExactly("a", "b", "c"); 
            assertThat(page.pageNumber()).isEqualTo(2); 
            assertThat(page.pageSize()).isEqualTo(10); 
            assertThat(page.totalElements()).isEqualTo(55); 
        }

        @Test
        void hasNextWhenNotLastPage() { 
            PageRequest req = PageRequest.of(0, 10); 
            Page<String> page = PaginationUtils.toPage(List.of("x"), req, 25);
            assertThat(page.hasNext()).isTrue(); 
        }

        @Test
        void hasNoPreviousOnFirstPage() { 
            PageRequest req = PageRequest.of(0, 10); 
            Page<String> page = PaginationUtils.toPage(List.of("x"), req, 5);
            assertThat(page.hasPrevious()).isFalse(); 
        }
    }

    @Nested
    @DisplayName("cursor encoding")
    class CursorEncoding {

        @Test
        void roundTripWithSortValue() { 
            String cursor = PaginationUtils.encodeCursor("entity-42", "2026-01-15T10:00:00Z"); 
            PaginationUtils.CursorData data = PaginationUtils.decodeCursor(cursor); 

            assertThat(data.lastId()).isEqualTo("entity-42");
            assertThat(data.lastSortValue()).isEqualTo("2026-01-15T10:00:00Z");
        }

        @Test
        void roundTripWithoutSortValue() { 
            String cursor = PaginationUtils.encodeCursor("entity-99", null); 
            PaginationUtils.CursorData data = PaginationUtils.decodeCursor(cursor); 

            assertThat(data.lastId()).isEqualTo("entity-99");
            assertThat(data.lastSortValue()).isNull(); 
        }

        @Test
        void encodedCursorIsUrlSafe() { 
            String cursor = PaginationUtils.encodeCursor("abc", "2026-01-15T10:00:00Z"); 
            assertThat(cursor).doesNotContain("+", "/", "="); 
        }

        @Test
        void decodingInvalidCursorThrows() { 
            assertThatThrownBy(() -> PaginationUtils.decodeCursor("not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("Invalid cursor");
        }

        @Test
        void encodingNullIdThrows() { 
            assertThatThrownBy(() -> PaginationUtils.encodeCursor(null, "sort")) 
                .isInstanceOf(NullPointerException.class); 
        }
    }
}
