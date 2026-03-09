package miner

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"strings"
	"time"
)

// ToilMinerService implements the complete toil mining service
type ToilMinerService struct {
	repository    Repository
	sequenceMiner SequenceMiner
	generator     CandidateGenerator
	config        MiningConfig
}

// NewToilMinerService creates a new toil mining service
func NewToilMinerService(clickhouseDSN string) (*ToilMinerService, error) {
	repo, err := NewClickHouseRepository(clickhouseDSN)
	if err != nil {
		return nil, fmt.Errorf("failed to create repository: %w", err)
	}

	service := &ToilMinerService{
		repository:    repo,
		sequenceMiner: NewFrequentSequenceMiner(),
		generator:     NewSmartCandidateGenerator(),
		config:        getDefaultMiningConfig(),
	}

	return service, nil
}

// LogAction records an operator action for analysis
func (s *ToilMinerService) LogAction(ctx context.Context, log *ActionLog) error {
	// Generate ID if not provided
	if log.ID == "" {
		log.ID = generateID()
	}

	// Set timestamp if not provided
	if log.Timestamp.IsZero() {
		log.Timestamp = time.Now()
	}

	// Validate required fields
	if log.UserID == "" {
		return fmt.Errorf("user_id is required")
	}
	if log.ActionType == "" {
		return fmt.Errorf("action_type is required")
	}

	// Initialize empty maps if nil
	if log.Parameters == nil {
		log.Parameters = make(map[string]interface{})
	}

	return s.repository.SaveActionLog(ctx, log)
}

// RunMining performs toil mining analysis
func (s *ToilMinerService) RunMining(ctx context.Context, config MiningConfig) (*MiningResult, error) {
	startTime := time.Now()

	result := &MiningResult{
		ID:        generateID(),
		Timestamp: startTime,
		Config:    config,
		Statistics: MiningStatistics{
			ActionsByType:         make(map[string]int),
			SequencesByPattern:    make(map[string]int),
			TeamActivity:          make(map[string]int),
			IncidentTypeFrequency: make(map[string]int),
		},
	}

	// Get action logs for analysis
	since := time.Now().Add(-config.LookbackPeriod)
	filters := ActionLogFilters{
		Since: &since,
		Limit: 100000, // Reasonable limit for analysis
	}

	actions, err := s.repository.GetActionLogs(ctx, filters)
	if err != nil {
		return nil, fmt.Errorf("failed to get action logs: %w", err)
	}

	result.TotalActions = len(actions)

	// Filter actions based on config
	filteredActions := s.filterActions(actions, config)

	// Discover action sequences
	sequences, err := s.sequenceMiner.DiscoverSequences(ctx, filteredActions, config)
	if err != nil {
		return nil, fmt.Errorf("failed to discover sequences: %w", err)
	}

	result.AnalyzedSequences = len(sequences)

	// Analyze patterns and generate candidates
	candidates, err := s.sequenceMiner.AnalyzePatterns(ctx, sequences)
	if err != nil {
		return nil, fmt.Errorf("failed to analyze patterns: %w", err)
	}

	// Score and validate candidates
	validCandidates := make([]AutomationCandidate, 0)
	for _, candidate := range candidates {
		// Score the candidate
		if err := s.sequenceMiner.ScoreCandidate(ctx, &candidate, config); err != nil {
			continue // Skip candidates that can't be scored
		}

		// Validate candidate
		if err := s.generator.ValidateCandidate(ctx, &candidate); err != nil {
			continue // Skip invalid candidates
		}

		validCandidates = append(validCandidates, candidate)

		// Save candidate to database
		if err := s.repository.SaveCandidate(ctx, &candidate); err != nil {
			// Log error but continue processing
			fmt.Printf("Failed to save candidate %s: %v\n", candidate.ID, err)
		}
	}

	result.Candidates = validCandidates
	result.ProcessingTime = time.Since(startTime)

	// Calculate statistics
	s.calculateStatistics(result, actions, sequences)

	// Save mining result
	if err := s.repository.SaveMiningResult(ctx, result); err != nil {
		return nil, fmt.Errorf("failed to save mining result: %w", err)
	}

	return result, nil
}

// DiscoverSequences implements SequenceMiner interface
func (s *ToilMinerService) DiscoverSequences(ctx context.Context, actions []ActionLog, config MiningConfig) ([]ActionSequence, error) {
	return s.sequenceMiner.DiscoverSequences(ctx, actions, config)
}

// AnalyzePatterns implements SequenceMiner interface
func (s *ToilMinerService) AnalyzePatterns(ctx context.Context, sequences []ActionSequence) ([]AutomationCandidate, error) {
	return s.sequenceMiner.AnalyzePatterns(ctx, sequences)
}

// ScoreCandidate implements SequenceMiner interface
func (s *ToilMinerService) ScoreCandidate(ctx context.Context, candidate *AutomationCandidate, config MiningConfig) error {
	return s.sequenceMiner.ScoreCandidate(ctx, candidate, config)
}

// GenerateCandidate implements CandidateGenerator interface
func (s *ToilMinerService) GenerateCandidate(ctx context.Context, sequence ActionSequence) (*AutomationCandidate, error) {
	return s.generator.GenerateCandidate(ctx, sequence)
}

// GenerateRecommendation implements CandidateGenerator interface
func (s *ToilMinerService) GenerateRecommendation(ctx context.Context, candidate *AutomationCandidate) (*Recommendation, error) {
	return s.generator.GenerateRecommendation(ctx, candidate)
}

// ValidateCandidate implements CandidateGenerator interface
func (s *ToilMinerService) ValidateCandidate(ctx context.Context, candidate *AutomationCandidate) error {
	return s.generator.ValidateCandidate(ctx, candidate)
}

// GetCandidates retrieves automation candidates
func (s *ToilMinerService) GetCandidates(ctx context.Context, filters CandidateFilters) ([]AutomationCandidate, error) {
	return s.repository.GetCandidates(ctx, filters)
}

// ReviewCandidate updates candidate status after review
func (s *ToilMinerService) ReviewCandidate(ctx context.Context, id string, status CandidateStatus, reviewedBy string) error {
	return s.repository.UpdateCandidateStatus(ctx, id, status, reviewedBy)
}

// GetStatistics returns mining statistics and insights
func (s *ToilMinerService) GetStatistics(ctx context.Context, since time.Time) (*MiningStatistics, error) {
	filters := ActionLogFilters{
		Since: &since,
		Limit: 50000,
	}

	actions, err := s.repository.GetActionLogs(ctx, filters)
	if err != nil {
		return nil, fmt.Errorf("failed to get action logs: %w", err)
	}

	stats := &MiningStatistics{
		ActionsByType:         make(map[string]int),
		SequencesByPattern:    make(map[string]int),
		TeamActivity:          make(map[string]int),
		IncidentTypeFrequency: make(map[string]int),
	}

	// Calculate basic statistics
	var totalDuration time.Duration
	for _, action := range actions {
		stats.ActionsByType[action.ActionType]++
		stats.TeamActivity[action.Context.TeamOnCall]++
		if action.Context.IncidentType != "" {
			stats.IncidentTypeFrequency[action.Context.IncidentType]++
		}
		totalDuration += action.Duration
	}

	// Get candidates for ROI calculation
	candidateFilters := CandidateFilters{
		Since: &since,
		Limit: 1000,
	}
	candidates, err := s.repository.GetCandidates(ctx, candidateFilters)
	if err != nil {
		return nil, fmt.Errorf("failed to get candidates: %w", err)
	}

	// Calculate potential time savings and ROI
	var totalTimeSavings time.Duration
	for _, candidate := range candidates {
		if candidate.Status == StatusApproved || candidate.Status == StatusImplemented {
			savings := time.Duration(candidate.Evidence.Frequency) * candidate.Evidence.TimeSaved
			totalTimeSavings += savings
		}
	}

	stats.TotalTimeSavings = totalTimeSavings
	stats.PotentialROI = calculateROI(totalTimeSavings, len(candidates))

	return stats, nil
}

// Helper methods

func (s *ToilMinerService) filterActions(actions []ActionLog, config MiningConfig) []ActionLog {
	filtered := make([]ActionLog, 0)

	for _, action := range actions {
		// Skip excluded action types
		if contains(config.ExcludeActionTypes, action.ActionType) {
			continue
		}

		// Filter by included teams if specified
		if len(config.IncludeTeams) > 0 && !contains(config.IncludeTeams, action.Context.TeamOnCall) {
			continue
		}

		filtered = append(filtered, action)
	}

	return filtered
}

func (s *ToilMinerService) calculateStatistics(result *MiningResult, actions []ActionLog, sequences []ActionSequence) {
	stats := &result.Statistics

	// Action type frequency
	for _, action := range actions {
		stats.ActionsByType[action.ActionType]++
		stats.TeamActivity[action.Context.TeamOnCall]++
		if action.Context.IncidentType != "" {
			stats.IncidentTypeFrequency[action.Context.IncidentType]++
		}
	}

	// Sequence patterns
	totalSequenceLength := 0
	for _, sequence := range sequences {
		stats.SequencesByPattern[sequence.Pattern] = sequence.Frequency
		actionCount := len(strings.Split(sequence.Pattern, "->"))
		totalSequenceLength += actionCount * sequence.Frequency
	}

	if len(sequences) > 0 {
		stats.AvgSequenceLength = float64(totalSequenceLength) / float64(len(sequences))
	}

	// Calculate potential time savings
	for _, candidate := range result.Candidates {
		savings := time.Duration(candidate.Evidence.Frequency) * candidate.Evidence.TimeSaved
		stats.TotalTimeSavings += savings
	}

	stats.PotentialROI = calculateROI(stats.TotalTimeSavings, len(result.Candidates))
}

func calculateROI(timeSavings time.Duration, candidateCount int) float64 {
	if candidateCount == 0 {
		return 0.0
	}

	// Assume $50/hour for operator time
	hourlyCost := 50.0
	savedHours := timeSavings.Hours()
	potentialSavings := savedHours * hourlyCost

	// Assume $5000 implementation cost per automation
	implementationCost := float64(candidateCount) * 5000.0

	if implementationCost == 0 {
		return 0.0
	}

	return (potentialSavings - implementationCost) / implementationCost
}

func getDefaultMiningConfig() MiningConfig {
	return MiningConfig{
		LookbackPeriod:     30 * 24 * time.Hour, // 30 days
		MinFrequency:       3,                   // At least 3 occurrences
		MinTimeSaved:       5 * time.Minute,     // At least 5 minutes saved
		MaxComplexity:      0.8,                 // Maximum 80% complexity
		MaxRisk:            0.7,                 // Maximum 70% risk
		IncludeTeams:       []string{},          // All teams
		ExcludeActionTypes: []string{},          // No exclusions
		ScoreWeights: ScoreWeights{
			Frequency:      0.3,
			TimeSaved:      0.3,
			SuccessRate:    0.2,
			BusinessImpact: 0.1,
			Complexity:     0.05,
			Risk:           0.05,
		},
	}
}

func generateID() string {
	bytes := make([]byte, 8)
	rand.Read(bytes)
	return hex.EncodeToString(bytes)
}

func contains(slice []string, item string) bool {
	for _, s := range slice {
		if s == item {
			return true
		}
	}
	return false
}
