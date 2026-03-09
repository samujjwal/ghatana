import { useState } from "react";
import { Box, Card } from "@/components/ui";
import {
    useClassrooms,
    useCreateClassroom,
    useClassroomProgress
} from "../../hooks/useTeacher";

// Local type definitions
interface Classroom {
    id: string;
    tenantId: string;
    name: string;
    description?: string;
    code: string;
    teacherId: string;
    createdAt: string;
    studentCount?: number;
    moduleCount?: number;
    enrolledModuleIds?: string[];
}

interface StudentProgress {
    studentId: string;
    studentName: string;
    overallProgress: number;
    averageScore?: number;
    lastActiveAt?: string;
    completedModules?: number;
    totalModules?: number;
}

// For classroom progress API response - an array of StudentProgress items
type ClassroomProgressItem = StudentProgress;

/**
 * Dashboard for teachers to manage classrooms and track student progress.
 */
export function TeacherDashboard() {
    const [selectedClassroom, setSelectedClassroom] = useState<string | null>(null);
    const [showCreateModal, setShowCreateModal] = useState(false);

    const { data: classroomsData, isLoading } = useClassrooms();
    const classrooms = classroomsData?.classrooms ?? [];

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="text-lg text-gray-600 dark:text-gray-300">Loading classrooms...</div>
            </div>
        );
    }

    return (
        <Box className="p-6 space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold">Teacher Dashboard</h1>
                <button
                    onClick={() => setShowCreateModal(true)}
                    className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                    + Create Classroom
                </button>
            </div>

            {classrooms.length === 0 ? (
                <Card className="p-8 text-center">
                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                        No classrooms yet
                    </h3>
                    <p className="text-gray-600 dark:text-gray-300 mb-4">
                        Create your first classroom to start tracking student progress.
                    </p>
                    <button
                        onClick={() => setShowCreateModal(true)}
                        className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                    >
                        Create Classroom
                    </button>
                </Card>
            ) : (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Classroom List */}
                    <div className="lg:col-span-1 space-y-4">
                        <h2 className="text-lg font-semibold">Your Classrooms</h2>
                        {classrooms.map((classroom) => (
                            <ClassroomCard
                                key={classroom.id}
                                classroom={classroom}
                                isSelected={selectedClassroom === classroom.id}
                                onClick={() => setSelectedClassroom(classroom.id)}
                            />
                        ))}
                    </div>

                    {/* Progress Panel */}
                    <div className="lg:col-span-2">
                        {selectedClassroom ? (
                            <ClassroomProgressPanel classroomId={selectedClassroom} />
                        ) : (
                            <Card className="p-8 text-center text-gray-500">
                                Select a classroom to view student progress
                            </Card>
                        )}
                    </div>
                </div>
            )}

            {showCreateModal && (
                <CreateClassroomModal onClose={() => setShowCreateModal(false)} />
            )}
        </Box>
    );
}

interface ClassroomCardProps {
    classroom: Classroom;
    isSelected: boolean;
    onClick: () => void;
}

function ClassroomCard({ classroom, isSelected, onClick }: ClassroomCardProps) {
    return (
        <Card
            onClick={onClick}
            className={`p-4 cursor-pointer transition-all ${isSelected
                ? "ring-2 ring-blue-500 dark:ring-blue-400 bg-blue-50 dark:bg-blue-900/30"
                : "hover:bg-gray-50 dark:hover:bg-gray-800"
                }`}
        >
            <h3 className="font-medium">{classroom.name}</h3>
            {classroom.description && (
                <p className="text-sm text-gray-600 dark:text-gray-300 mt-1">{classroom.description}</p>
            )}
            <div className="flex gap-4 mt-3 text-sm text-gray-500 dark:text-gray-400">
                <span>{classroom.studentCount ?? 0} students</span>
                <span>{classroom.moduleCount ?? 0} modules</span>
            </div>
        </Card>
    );
}

interface ClassroomProgressPanelProps {
    classroomId: string;
}

function ClassroomProgressPanel({ classroomId }: ClassroomProgressPanelProps) {
    const { data, isLoading } = useClassroomProgress(classroomId);

    if (isLoading) {
        return <Card className="p-8 text-center">Loading progress...</Card>;
    }

    const progressList = data?.progress ?? [];

    return (
        <Card className="p-6">
            <h2 className="text-lg font-semibold mb-4">Student Progress</h2>

            {progressList.length === 0 ? (
                <p className="text-gray-500 dark:text-gray-400 text-center py-4">
                    No students enrolled yet
                </p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead>
                            <tr className="border-b">
                                <th className="text-left py-2 px-4">Student</th>
                                <th className="text-left py-2 px-4">Progress</th>
                                <th className="text-left py-2 px-4">Avg. Score</th>
                                <th className="text-left py-2 px-4">Last Active</th>
                            </tr>
                        </thead>
                        <tbody>
                            {progressList.map((progress: ClassroomProgressItem) => (
                                <tr key={progress.studentId} className="border-b hover:bg-gray-50">
                                    <td className="py-3 px-4">
                                        <span className="font-medium">{progress.studentName}</span>
                                    </td>
                                    <td className="py-3 px-4">
                                        <div className="flex items-center gap-2">
                                            <div className="w-24 bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                                                <div
                                                    className="bg-green-500 h-2 rounded-full"
                                                    style={{ width: `${progress.overallProgress}%` }}
                                                />
                                            </div>
                                            <span className="text-sm text-gray-600 dark:text-gray-300">
                                                {progress.overallProgress}%
                                            </span>
                                        </div>
                                    </td>
                                    <td className="py-3 px-4">
                                        <span
                                            className={`font-medium ${(progress.averageScore ?? 0) >= 70
                                                ? "text-green-600"
                                                : "text-yellow-600"
                                                }`}
                                        >
                                            {progress.averageScore?.toFixed(0) ?? "—"}%
                                        </span>
                                    </td>
                                    <td className="py-3 px-4 text-sm text-gray-600 dark:text-gray-300">
                                        {progress.lastActiveAt
                                            ? new Date(progress.lastActiveAt).toLocaleDateString()
                                            : "Never"}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </Card>
    );
}

interface CreateClassroomModalProps {
    onClose: () => void;
}

function CreateClassroomModal({ onClose }: CreateClassroomModalProps) {
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const createMutation = useCreateClassroom();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!name.trim()) return;

        try {
            await createMutation.mutateAsync({
                name: name.trim(),
                description: description.trim() || undefined
            });
            onClose();
        } catch (error) {
            console.error("Failed to create classroom:", error);
        }
    };

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <Card className="w-full max-w-md p-6">
                <h2 className="text-xl font-semibold mb-4">Create Classroom</h2>
                <form onSubmit={handleSubmit} className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-200 mb-1">
                            Classroom Name *
                        </label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            placeholder="e.g., Math 101 - Fall 2024"
                            className="w-full px-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-blue-500"
                            required
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 dark:text-gray-200 mb-1">
                            Description
                        </label>
                        <textarea
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="Optional description..."
                            className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500"
                            rows={3}
                        />
                    </div>
                    <div className="flex justify-end gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-4 py-2 text-gray-600 dark:text-gray-300 hover:text-gray-800 dark:hover:text-gray-100"
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={createMutation.isPending}
                            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                        >
                            {createMutation.isPending ? "Creating..." : "Create"}
                        </button>
                    </div>
                </form>
            </Card>
        </div>
    );
}
