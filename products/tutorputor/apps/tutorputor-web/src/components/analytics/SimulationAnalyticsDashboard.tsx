/**
 * Simulation Analytics Dashboard
 * 
 * @doc.type component
 * @doc.purpose Instructor dashboard for simulation performance analytics
 * @doc.layer product
 * @doc.pattern Dashboard
 */

import { useState, useEffect } from "react";

interface SimulationMetrics {
    simulationId: string;
    title: string;
    totalAttempts: number;
    averageScore: number;
    averageTimeSpent: number;
    completionRate: number;
    evidenceSatisfaction: {
        claimId: string;
        claimDescription: string;
        satisfactionRate: number;
    }[];
    commonErrors: {
        error: string;
        frequency: number;
    }[];
}

interface StudentPerformance {
    studentId: string;
    studentName: string;
    attempts: number;
    bestScore: number;
    averageScore: number;
    timeSpent: number;
    lastAttempt: Date;
    strugglingAreas: string[];
}

export function SimulationAnalyticsDashboard({ courseId }: { courseId: string }) {
    const [metrics, setMetrics] = useState<SimulationMetrics[]>([]);
    const [students, setStudents] = useState<StudentPerformance[]>([]);
    const [selectedSimulation, setSelectedSimulation] = useState<string | null>(null);
    const [timeRange, setTimeRange] = useState<'week' | 'month' | 'all'>('week');

    useEffect(() => {
        // Fetch analytics data
        fetchAnalytics();
    }, [courseId, timeRange]);

    const fetchAnalytics = async () => {
        // Mock data - replace with actual API call
        setMetrics([
            {
                simulationId: 'sim-1',
                title: 'Binary Search Algorithm',
                totalAttempts: 45,
                averageScore: 82.5,
                averageTimeSpent: 180000,
                completionRate: 0.89,
                evidenceSatisfaction: [
                    { claimId: 'claim-1', claimDescription: 'Understands binary search', satisfactionRate: 0.85 },
                    { claimId: 'claim-2', claimDescription: 'Can optimize search', satisfactionRate: 0.72 }
                ],
                commonErrors: [
                    { error: 'Incorrect midpoint calculation', frequency: 12 },
                    { error: 'Off-by-one error', frequency: 8 }
                ]
            }
        ]);

        setStudents([
            {
                studentId: 'student-1',
                studentName: 'Alice Johnson',
                attempts: 3,
                bestScore: 95,
                averageScore: 88,
                timeSpent: 540000,
                lastAttempt: new Date(),
                strugglingAreas: ['Efficiency optimization']
            }
        ]);
    };

    return (
        <div className="simulation-analytics-dashboard p-6 space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold">Simulation Analytics</h1>
                    <p className="text-gray-600 mt-1">Track student performance and engagement</p>
                </div>
                <div className="flex gap-2">
                    <select
                        className="px-4 py-2 border rounded-md"
                        value={timeRange}
                        onChange={(e) => setTimeRange(e.target.value as any)}
                    >
                        <option value="week">Last Week</option>
                        <option value="month">Last Month</option>
                        <option value="all">All Time</option>
                    </select>
                </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-4 gap-4">
                <div className="bg-white p-4 rounded-lg border shadow-sm">
                    <div className="text-sm text-gray-600">Total Simulations</div>
                    <div className="text-3xl font-bold mt-2">{metrics.length}</div>
                </div>
                <div className="bg-white p-4 rounded-lg border shadow-sm">
                    <div className="text-sm text-gray-600">Total Attempts</div>
                    <div className="text-3xl font-bold mt-2">
                        {metrics.reduce((sum, m) => sum + m.totalAttempts, 0)}
                    </div>
                </div>
                <div className="bg-white p-4 rounded-lg border shadow-sm">
                    <div className="text-sm text-gray-600">Average Score</div>
                    <div className="text-3xl font-bold mt-2">
                        {Math.round(metrics.reduce((sum, m) => sum + m.averageScore, 0) / metrics.length)}%
                    </div>
                </div>
                <div className="bg-white p-4 rounded-lg border shadow-sm">
                    <div className="text-sm text-gray-600">Completion Rate</div>
                    <div className="text-3xl font-bold mt-2">
                        {Math.round((metrics.reduce((sum, m) => sum + m.completionRate, 0) / metrics.length) * 100)}%
                    </div>
                </div>
            </div>

            {/* Simulation Performance Table */}
            <div className="bg-white rounded-lg border shadow-sm">
                <div className="p-4 border-b">
                    <h2 className="text-lg font-semibold">Simulation Performance</h2>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead className="bg-gray-50">
                            <tr>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Simulation</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Attempts</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Avg Score</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Completion</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Avg Time</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y">
                            {metrics.map((metric) => (
                                <tr key={metric.simulationId} className="hover:bg-gray-50">
                                    <td className="px-4 py-3">
                                        <div className="font-medium">{metric.title}</div>
                                    </td>
                                    <td className="px-4 py-3">{metric.totalAttempts}</td>
                                    <td className="px-4 py-3">
                                        <span className={`font-medium ${metric.averageScore >= 80 ? 'text-green-600' :
                                                metric.averageScore >= 60 ? 'text-yellow-600' : 'text-red-600'
                                            }`}>
                                            {metric.averageScore.toFixed(1)}%
                                        </span>
                                    </td>
                                    <td className="px-4 py-3">{(metric.completionRate * 100).toFixed(0)}%</td>
                                    <td className="px-4 py-3">{Math.round(metric.averageTimeSpent / 1000)}s</td>
                                    <td className="px-4 py-3">
                                        <button
                                            className="text-blue-600 hover:text-blue-800 text-sm"
                                            onClick={() => setSelectedSimulation(metric.simulationId)}
                                        >
                                            View Details
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Student Performance */}
            <div className="bg-white rounded-lg border shadow-sm">
                <div className="p-4 border-b">
                    <h2 className="text-lg font-semibold">Student Performance</h2>
                </div>
                <div className="overflow-x-auto">
                    <table className="w-full">
                        <thead className="bg-gray-50">
                            <tr>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Student</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Attempts</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Best Score</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Avg Score</th>
                                <th className="px-4 py-3 text-left text-sm font-medium text-gray-600">Struggling Areas</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y">
                            {students.map((student) => (
                                <tr key={student.studentId} className="hover:bg-gray-50">
                                    <td className="px-4 py-3">
                                        <div className="font-medium">{student.studentName}</div>
                                    </td>
                                    <td className="px-4 py-3">{student.attempts}</td>
                                    <td className="px-4 py-3 font-medium text-green-600">{student.bestScore}%</td>
                                    <td className="px-4 py-3">{student.averageScore}%</td>
                                    <td className="px-4 py-3">
                                        {student.strugglingAreas.length > 0 ? (
                                            <div className="flex gap-1">
                                                {student.strugglingAreas.map((area, idx) => (
                                                    <span key={idx} className="px-2 py-1 text-xs bg-yellow-100 text-yellow-800 rounded">
                                                        {area}
                                                    </span>
                                                ))}
                                            </div>
                                        ) : (
                                            <span className="text-gray-400">None</span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Evidence Satisfaction (ECD) */}
            {selectedSimulation && (
                <div className="bg-white rounded-lg border shadow-sm">
                    <div className="p-4 border-b">
                        <h2 className="text-lg font-semibold">Learning Evidence Satisfaction</h2>
                        <p className="text-sm text-gray-600 mt-1">Evidence-Centered Design metrics</p>
                    </div>
                    <div className="p-4 space-y-3">
                        {metrics
                            .find(m => m.simulationId === selectedSimulation)
                            ?.evidenceSatisfaction.map((evidence) => (
                                <div key={evidence.claimId}>
                                    <div className="flex items-center justify-between mb-1">
                                        <span className="text-sm font-medium">{evidence.claimDescription}</span>
                                        <span className="text-sm text-gray-600">
                                            {(evidence.satisfactionRate * 100).toFixed(0)}%
                                        </span>
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                        <div
                                            className={`h-2 rounded-full ${evidence.satisfactionRate >= 0.8 ? 'bg-green-500' :
                                                    evidence.satisfactionRate >= 0.6 ? 'bg-yellow-500' : 'bg-red-500'
                                                }`}
                                            style={{ width: `${evidence.satisfactionRate * 100}%` }}
                                        />
                                    </div>
                                </div>
                            ))}
                    </div>
                </div>
            )}
        </div>
    );
}
