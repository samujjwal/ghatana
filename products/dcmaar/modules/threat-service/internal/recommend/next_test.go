package recommend

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/mux"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNextBuildRecommender_GenerateRecommendations(t *testing.T) {
	recommender := NewNextBuildRecommender()

	// Add sample incidents
	incident1 := Incident{
		ID:          "inc-1",
		Timestamp:   time.Now().Add(-24 * time.Hour),
		Severity:    1,
		Category:    "performance",
		Component:   "agent",
		Description: "High CPU usage causing slowdown",
		TTR:         2 * time.Hour,
		Tags:        []string{"cpu", "performance"},
	}

	incident2 := Incident{
		ID:          "inc-2",
		Timestamp:   time.Now().Add(-12 * time.Hour),
		Severity:    2,
		Category:    "performance",
		Component:   "agent",
		Description: "Memory leak in processing pipeline",
		TTR:         4 * time.Hour,
		Tags:        []string{"memory", "performance"},
	}

	recommender.incidentAnalyzer.AddIncident(incident1)
	recommender.incidentAnalyzer.AddIncident(incident2)

	// Add sample toil tasks
	toilTask := ToilTask{
		ID:              "toil-1",
		Name:            "Manual log analysis",
		Description:     "Manually analyzing logs for error patterns",
		Category:        "monitoring",
		EstimatedEffort: 30 * time.Minute,
		Frequency:       10, // 10 times per week
		LastOccurrence:  time.Now().Add(-1 * time.Hour),
		AffectedTeams:   []string{"ops", "dev"},
		AutomationLevel: 20,
		BusinessImpact:  7,
		TechnicalDebt:   true,
		Tags:            []string{"logs", "manual"},
	}

	recommender.toilAnalyzer.AddTask(toilTask)

	// Generate recommendations
	recommendations, err := recommender.GenerateRecommendations(nil)
	require.NoError(t, err)
	assert.NotEmpty(t, recommendations)

	// Check that we have recommendations from different sources
	hasIncidentRec := false
	hasToilRec := false
	hasStrategicRec := false

	for _, rec := range recommendations {
		assert.NotEmpty(t, rec.ID)
		assert.NotEmpty(t, rec.Title)
		assert.NotEmpty(t, rec.Description)
		assert.NotEmpty(t, rec.Category)
		assert.True(t, rec.Priority >= 1 && rec.Priority <= 5)
		assert.NotEmpty(t, rec.RecommendedBy)

		switch rec.RecommendedBy {
		case "incident-analyzer":
			hasIncidentRec = true
		case "toil-analyzer":
			hasToilRec = true
		case "strategic-analyzer":
			hasStrategicRec = true
		}
	}

	assert.True(t, hasIncidentRec, "Should have incident-based recommendation")
	assert.True(t, hasToilRec, "Should have toil-based recommendation")
	assert.True(t, hasStrategicRec, "Should have strategic recommendations")
}

func TestIncidentAnalyzer_AnalyzePatterns(t *testing.T) {
	analyzer := NewIncidentAnalyzer()

	// Add multiple incidents with same pattern
	for i := 0; i < 5; i++ {
		incident := Incident{
			ID:        string(rune('a' + i)),
			Timestamp: time.Now().Add(-time.Duration(i) * time.Hour),
			Severity:  2,
			Category:  "performance",
			Component: "server",
			TTR:       time.Duration(i+1) * time.Hour,
		}
		analyzer.AddIncident(incident)
	}

	patterns := analyzer.AnalyzePatterns()
	require.NotEmpty(t, patterns)

	pattern := patterns["server-performance"]
	assert.Equal(t, "server", pattern.Component)
	assert.Equal(t, "performance", pattern.Category)
	assert.Equal(t, 5, pattern.Count)
	assert.Equal(t, 2.0, pattern.AverageSeverity)
	assert.True(t, pattern.Impact > 0)
}

func TestToilAnalyzer_AnalyzeToil(t *testing.T) {
	analyzer := NewToilAnalyzer()

	// Add high-impact toil task
	task := ToilTask{
		ID:              "toil-high",
		Name:            "Manual deployment",
		EstimatedEffort: 2 * time.Hour,
		Frequency:       5, // 5 times per week
		AutomationLevel: 10,
		BusinessImpact:  8,
	}
	analyzer.AddTask(task)

	// Add low-impact toil task
	task2 := ToilTask{
		ID:              "toil-low",
		Name:            "Update documentation",
		EstimatedEffort: 15 * time.Minute,
		Frequency:       2,
		AutomationLevel: 50,
		BusinessImpact:  3,
	}
	analyzer.AddTask(task2)

	impacts := analyzer.AnalyzeToil()
	require.Len(t, impacts, 2)

	// High-impact task should be first (higher priority)
	assert.Equal(t, "toil-high", impacts[0].Task.ID)
	assert.True(t, impacts[0].WeeklyHours > impacts[1].WeeklyHours)
	assert.True(t, impacts[0].Priority > impacts[1].Priority)
}

func TestROICalculator_CalculateROI(t *testing.T) {
	calculator := NewROICalculator()

	recommendation := Recommendation{
		Category:        "automation",
		EstimatedEffort: 40 * time.Hour, // 1 week
		Tags:            []string{"automation", "deployment"},
	}

	incidents := []Incident{
		{
			TTR:      2 * time.Hour,
			Severity: 2,
		},
	}

	toilTasks := []ToilTask{
		{
			EstimatedEffort: 1 * time.Hour,
			Frequency:       5,
			AutomationLevel: 20,
			Tags:            []string{"deployment"},
		},
	}

	roi := calculator.CalculateROI(recommendation, incidents, toilTasks)
	assert.True(t, roi > 0, "ROI should be positive for automation recommendations")
}

func TestRecommenderHTTPHandlers(t *testing.T) {
	recommender := NewNextBuildRecommender()
	router := mux.NewRouter()
	recommender.RegisterRoutes(router)

	// Test adding incident
	t.Run("AddIncident", func(t *testing.T) {
		incident := Incident{
			Severity:    1,
			Category:    "reliability",
			Component:   "server",
			Description: "Database connection timeout",
		}

		body, _ := json.Marshal(incident)
		req := httptest.NewRequest("POST", "/api/recommend/incidents", strings.NewReader(string(body)))
		req.Header.Set("Content-Type", "application/json")

		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]string
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)
		assert.Equal(t, "success", response["status"])
		assert.NotEmpty(t, response["id"])
	})

	// Test adding toil task
	t.Run("AddToilTask", func(t *testing.T) {
		task := ToilTask{
			Name:            "Manual testing",
			Description:     "Running manual regression tests",
			Category:        "testing",
			EstimatedEffort: 4 * time.Hour,
			Frequency:       3,
			AutomationLevel: 30,
			BusinessImpact:  6,
		}

		body, _ := json.Marshal(task)
		req := httptest.NewRequest("POST", "/api/recommend/toil", strings.NewReader(string(body)))
		req.Header.Set("Content-Type", "application/json")

		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]string
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)
		assert.Equal(t, "success", response["status"])
	})

	// Test getting recommendations
	t.Run("GetRecommendations", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/recommend/next", nil)
		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]interface{}
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)

		assert.Contains(t, response, "recommendations")
		assert.Contains(t, response, "total")
		assert.Contains(t, response, "generated_at")
	})

	// Test getting analytics
	t.Run("GetAnalytics", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/recommend/analytics", nil)
		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]interface{}
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)

		assert.Contains(t, response, "summary")
		assert.Contains(t, response, "incident_patterns")
		assert.Contains(t, response, "toil_impacts")
	})

	// Test filtering recommendations by category
	t.Run("FilterByCategory", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/recommend/next?category=automation", nil)
		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]interface{}
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)

		recommendations := response["recommendations"].([]interface{})
		for _, rec := range recommendations {
			recMap := rec.(map[string]interface{})
			assert.Equal(t, "automation", recMap["category"])
		}
	})

	// Test limiting recommendations
	t.Run("LimitRecommendations", func(t *testing.T) {
		req := httptest.NewRequest("GET", "/api/recommend/next?limit=3", nil)
		recorder := httptest.NewRecorder()
		router.ServeHTTP(recorder, req)

		assert.Equal(t, http.StatusOK, recorder.Code)

		var response map[string]interface{}
		err := json.Unmarshal(recorder.Body.Bytes(), &response)
		require.NoError(t, err)

		recommendations := response["recommendations"].([]interface{})
		assert.True(t, len(recommendations) <= 3)
	})
}

func TestPriorityCalculation(t *testing.T) {
	recommender := NewNextBuildRecommender()

	testCases := []struct {
		name     string
		rec      Recommendation
		expected int
	}{
		{
			name: "High ROI, Low Risk, Low Effort",
			rec: Recommendation{
				ROIScore:        250,
				RiskLevel:       1,
				EstimatedEffort: 20 * time.Hour,
				Category:        "reliability",
			},
			expected: 1, // Highest priority
		},
		{
			name: "Low ROI, High Risk, High Effort",
			rec: Recommendation{
				ROIScore:        25,
				RiskLevel:       3,
				EstimatedEffort: 200 * time.Hour,
				Category:        "other",
			},
			expected: 5, // Lowest priority
		},
		{
			name: "Medium ROI, Medium Risk, Medium Effort",
			rec: Recommendation{
				ROIScore:        75,
				RiskLevel:       2,
				EstimatedEffort: 80 * time.Hour,
				Category:        "automation",
			},
			expected: 3, // Medium priority
		},
	}

	for _, tc := range testCases {
		t.Run(tc.name, func(t *testing.T) {
			priority := recommender.calculatePriority(tc.rec)
			assert.Equal(t, tc.expected, priority)
		})
	}
}

func TestRecommendationGeneration(t *testing.T) {
	recommender := NewNextBuildRecommender()

	// Test incident-based recommendation generation
	t.Run("IncidentRecommendation", func(t *testing.T) {
		pattern := IncidentPattern{
			Component:       "agent",
			Category:        "performance",
			Count:           5,
			AverageSeverity: 2.0,
			AverageTTR:      2 * time.Hour,
			Impact:          25.0,
		}

		rec := recommender.generateIncidentRecommendation(pattern)
		require.NotNil(t, rec)
		assert.Contains(t, rec.Title, "agent")
		assert.Contains(t, rec.Title, "performance")
		assert.Equal(t, "reliability", rec.Category)
		assert.Contains(t, rec.Tags, "agent")
		assert.Contains(t, rec.Tags, "performance")
		assert.NotEmpty(t, rec.SupportingData)
		assert.NotEmpty(t, rec.Rationale)
	})

	// Test toil-based recommendation generation
	t.Run("ToilRecommendation", func(t *testing.T) {
		impact := ToilImpact{
			Task: ToilTask{
				ID:          "test-toil",
				Name:        "Manual deployment",
				Description: "Deploy application manually",
				Tags:        []string{"deployment", "manual"},
			},
			WeeklyHours:   5.0,
			AutomationROI: 150.0,
			Priority:      50.0,
		}

		rec := recommender.generateToilRecommendation(impact)
		require.NotNil(t, rec)
		assert.Contains(t, rec.Title, "Manual deployment")
		assert.Equal(t, "automation", rec.Category)
		assert.Contains(t, rec.Tags, "automation")
		assert.Contains(t, rec.Tags, "toil")
		assert.NotEmpty(t, rec.SupportingData)
		assert.NotEmpty(t, rec.Rationale)
	})

	// Test strategic recommendation generation
	t.Run("StrategicRecommendations", func(t *testing.T) {
		strategic := recommender.generateStrategicRecommendations()
		assert.NotEmpty(t, strategic)

		for _, rec := range strategic {
			assert.NotEmpty(t, rec.ID)
			assert.NotEmpty(t, rec.Title)
			assert.NotEmpty(t, rec.Description)
			assert.Equal(t, "strategic-analyzer", rec.RecommendedBy)
			assert.Contains(t, rec.Tags, "strategic")
		}
	})
}

func BenchmarkRecommendationGeneration(b *testing.B) {
	recommender := NewNextBuildRecommender()

	// Add sample data
	for i := 0; i < 100; i++ {
		incident := Incident{
			ID:        fmt.Sprintf("inc-%d", i),
			Timestamp: time.Now().Add(-time.Duration(i) * time.Hour),
			Severity:  (i % 4) + 1,
			Category:  []string{"performance", "reliability", "security"}[i%3],
			Component: []string{"agent", "server", "desktop"}[i%3],
			TTR:       time.Duration(i+1) * time.Minute,
		}
		recommender.incidentAnalyzer.AddIncident(incident)

		if i < 50 {
			task := ToilTask{
				ID:              fmt.Sprintf("toil-%d", i),
				Name:            fmt.Sprintf("Task %d", i),
				EstimatedEffort: time.Duration(i+1) * time.Minute,
				Frequency:       (i % 10) + 1,
				AutomationLevel: (i * 2) % 100,
				BusinessImpact:  (i % 10) + 1,
			}
			recommender.toilAnalyzer.AddTask(task)
		}
	}

	b.ResetTimer()

	for i := 0; i < b.N; i++ {
		_, err := recommender.GenerateRecommendations(nil)
		if err != nil {
			b.Fatalf("Error generating recommendations: %v", err)
		}
	}
}
