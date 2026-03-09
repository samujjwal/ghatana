import { Box } from "@/components/ui";
import { SearchPage } from "../components/search";

/**
 * Search results page with filters and faceted search.
 */
export function SearchResultsPage() {
    return (
        <Box className="p-6">
            <SearchPage />
        </Box>
    );
}
