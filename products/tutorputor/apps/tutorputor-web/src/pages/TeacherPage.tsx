import { Box } from "@/components/ui";
import { TeacherDashboard } from "../components/teacher";

/**
 * Page for teachers to manage classrooms and track student progress.
 */
export function TeacherPage() {
    return (
        <Box className="p-6">
            <TeacherDashboard />
        </Box>
    );
}
