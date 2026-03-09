package miner

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"time"
)

// FrequentSequenceMiner implements sequence mining using frequent itemset algorithms
type FrequentSequenceMiner struct {
	minSupport        float64       // Minimum support threshold (0-1)
	maxGap            time.Duration // Maximum gap between actions in sequence
	minSequenceLength int           // Minimum sequence length to consider
	maxSequenceLength int           // Maximum sequence length to consider
}

// NewFrequentSequenceMiner creates a new sequence miner
func NewFrequentSequenceMiner() *FrequentSequenceMiner {
	return &FrequentSequenceMiner{
		minSupport:        0.1,              // 10% minimum support
		maxGap:            30 * time.Minute, // 30 minute max gap
		minSequenceLength: 2,                // At least 2 actions
		maxSequenceLength: 10,               // At most 10 actions
	}
}

// DiscoverSequences finds common action patterns using frequent sequence mining
func (m *FrequentSequenceMiner) DiscoverSequences(ctx context.Context, actions []ActionLog, config MiningConfig) ([]ActionSequence, error) {
	if len(actions) == 0 {
		return []ActionSequence{}, nil
	}

	// Group actions by incident/session
	sessions := m.groupActionsBySession(actions)

	// Extract action sequences from sessions
	sequences := m.extractSequences(sessions)

	// Find frequent patterns
	frequentPatterns := m.findFrequentPatterns(sequences, config)

	// Convert patterns to ActionSequence objects
	result := make([]ActionSequence, 0, len(frequentPatterns))
	for _, pattern := range frequentPatterns {
		actionSeq := m.createActionSequence(pattern, sessions)
		result = append(result, actionSeq)
	}

	// Sort by frequency and success rate
	sort.Slice(result, func(i, j int) bool {
		if result[i].Frequency != result[j].Frequency {
			return result[i].Frequency > result[j].Frequency
		}
		return result[i].SuccessRate > result[j].SuccessRate
	})

	return result, nil
}

// AnalyzePatterns identifies automation opportunities in sequences
func (m *FrequentSequenceMiner) AnalyzePatterns(ctx context.Context, sequences []ActionSequence) ([]AutomationCandidate, error) {
	candidates := make([]AutomationCandidate, 0)

	for _, seq := range sequences {
		// Check if sequence is a good automation candidate
		if m.isAutomationWorthy(seq) {
			candidate := m.createCandidate(seq)
			candidates = append(candidates, candidate)
		}
	}

	return candidates, nil
}

// ScoreCandidate calculates automation opportunity score
func (m *FrequentSequenceMiner) ScoreCandidate(ctx context.Context, candidate *AutomationCandidate, config MiningConfig) error {
	evidence := candidate.Evidence
	weights := config.ScoreWeights

	// Normalize frequency score (0-1)
	frequencyScore := normalizeFrequency(evidence.Frequency)

	// Calculate time saved score (0-1)
	timeSavedScore := normalizeTimeSaved(evidence.TimeSaved)

	// Success rate is already 0-1
	successRateScore := evidence.SuccessRate

	// Business impact score (subjective, 0-1)
	businessImpactScore := m.calculateBusinessImpact(evidence)

	// Complexity penalty (0-1, lower is better)
	complexityPenalty := evidence.ComplexityScore

	// Risk penalty (0-1, lower is better)
	riskPenalty := evidence.RiskScore

	// Weighted score calculation
	score := (weights.Frequency * frequencyScore) +
		(weights.TimeSaved * timeSavedScore) +
		(weights.SuccessRate * successRateScore) +
		(weights.BusinessImpact * businessImpactScore) -
		(weights.Complexity * complexityPenalty) -
		(weights.Risk * riskPenalty)

	// Normalize final score to 0-100
	candidate.Score = score * 100

	// Set priority based on score
	if candidate.Score >= 75 {
		candidate.Priority = PriorityHigh
	} else if candidate.Score >= 50 {
		candidate.Priority = PriorityMedium
	} else {
		candidate.Priority = PriorityLow
	}

	return nil
}

// groupActionsBySession groups actions by incident ID or time-based sessions
func (m *FrequentSequenceMiner) groupActionsBySession(actions []ActionLog) map[string][]ActionLog {
	sessions := make(map[string][]ActionLog)

	// Sort actions by timestamp
	sort.Slice(actions, func(i, j int) bool {
		return actions[i].Timestamp.Before(actions[j].Timestamp)
	})

	for _, action := range actions {
		sessionKey := m.getSessionKey(action)
		sessions[sessionKey] = append(sessions[sessionKey], action)
	}

	return sessions
}

// getSessionKey determines session grouping for an action
func (m *FrequentSequenceMiner) getSessionKey(action ActionLog) string {
	// If incident ID exists, use it
	if action.IncidentID != "" {
		return fmt.Sprintf("incident_%s", action.IncidentID)
	}

	// Otherwise, group by user and time window
	window := action.Timestamp.Truncate(time.Hour)
	return fmt.Sprintf("user_%s_time_%s", action.UserID, window.Format("2006-01-02T15"))
}

// extractSequences converts action sessions to sequence patterns
func (m *FrequentSequenceMiner) extractSequences(sessions map[string][]ActionLog) [][]string {
	sequences := make([][]string, 0)

	for _, actions := range sessions {
		if len(actions) < m.minSequenceLength {
			continue
		}

		// Sort actions in session by timestamp
		sort.Slice(actions, func(i, j int) bool {
			return actions[i].Timestamp.Before(actions[j].Timestamp)
		})

		// Extract action type sequence with time gap filtering
		sequence := make([]string, 0)
		var lastTime time.Time

		for _, action := range actions {
			// Check time gap constraint
			if !lastTime.IsZero() && action.Timestamp.Sub(lastTime) > m.maxGap {
				// Gap too large, start new sequence if current is long enough
				if len(sequence) >= m.minSequenceLength {
					sequences = append(sequences, sequence)
				}
				sequence = make([]string, 0)
			}

			sequence = append(sequence, action.ActionType)
			lastTime = action.Timestamp

			// Limit sequence length
			if len(sequence) >= m.maxSequenceLength {
				break
			}
		}

		// Add final sequence if long enough
		if len(sequence) >= m.minSequenceLength {
			sequences = append(sequences, sequence)
		}
	}

	return sequences
}

// findFrequentPatterns finds patterns that meet minimum support threshold
func (m *FrequentSequenceMiner) findFrequentPatterns(sequences [][]string, config MiningConfig) []FrequentPattern {
	if len(sequences) == 0 {
		return []FrequentPattern{}
	}

	// Count pattern occurrences
	patternCounts := make(map[string]int)
	patternSequences := make(map[string][][]string)

	// Generate all possible subsequences and count them
	for _, sequence := range sequences {
		patterns := m.generateSubsequences(sequence)
		for _, pattern := range patterns {
			patternKey := strings.Join(pattern, "->")
			patternCounts[patternKey]++
			patternSequences[patternKey] = append(patternSequences[patternKey], pattern)
		}
	}

	// Filter by minimum frequency
	minCount := config.MinFrequency
	if minCount == 0 {
		minCount = max(1, int(float64(len(sequences))*m.minSupport))
	}

	frequentPatterns := make([]FrequentPattern, 0)
	for pattern, count := range patternCounts {
		if count >= minCount {
			support := float64(count) / float64(len(sequences))
			frequentPatterns = append(frequentPatterns, FrequentPattern{
				Pattern: pattern,
				Actions: strings.Split(pattern, "->"),
				Count:   count,
				Support: support,
			})
		}
	}

	// Sort by count and support
	sort.Slice(frequentPatterns, func(i, j int) bool {
		if frequentPatterns[i].Count != frequentPatterns[j].Count {
			return frequentPatterns[i].Count > frequentPatterns[j].Count
		}
		return frequentPatterns[i].Support > frequentPatterns[j].Support
	})

	return frequentPatterns
}

// generateSubsequences generates all contiguous subsequences of minimum length
func (m *FrequentSequenceMiner) generateSubsequences(sequence []string) [][]string {
	subsequences := make([][]string, 0)

	for length := m.minSequenceLength; length <= min(len(sequence), m.maxSequenceLength); length++ {
		for start := 0; start <= len(sequence)-length; start++ {
			subseq := sequence[start : start+length]
			subsequences = append(subsequences, subseq)
		}
	}

	return subsequences
}

// createActionSequence converts a frequent pattern to ActionSequence
func (m *FrequentSequenceMiner) createActionSequence(pattern FrequentPattern, sessions map[string][]ActionLog) ActionSequence {
	sequence := ActionSequence{
		ID:        generateSequenceID(pattern.Pattern),
		Pattern:   pattern.Pattern,
		Frequency: pattern.Count,
		FirstSeen: time.Now().Add(-30 * 24 * time.Hour), // Default lookback
		LastSeen:  time.Now(),
	}

	// Analyze matching sessions to get more details
	var totalDuration time.Duration
	var successCount int
	var incidentTypes []string

	for _, actions := range sessions {
		if m.matchesPattern(actions, pattern.Actions) {
			sessionDuration := m.calculateSessionDuration(actions, pattern.Actions)
			totalDuration += sessionDuration

			sessionSuccess := m.isSessionSuccessful(actions, pattern.Actions)
			if sessionSuccess {
				successCount++
			}

			// Collect incident types
			for _, action := range actions {
				if action.Context.IncidentType != "" {
					incidentTypes = append(incidentTypes, action.Context.IncidentType)
				}
			}
		}
	}

	if pattern.Count > 0 {
		sequence.AvgDuration = totalDuration / time.Duration(pattern.Count)
		sequence.SuccessRate = float64(successCount) / float64(pattern.Count)
	}

	// Set most common incident type
	if len(incidentTypes) > 0 {
		sequence.IncidentType = mostCommon(incidentTypes)
	}

	return sequence
}

// isAutomationWorthy determines if a sequence is worth automating
func (m *FrequentSequenceMiner) isAutomationWorthy(seq ActionSequence) bool {
	// Must occur frequently enough
	if seq.Frequency < 5 {
		return false
	}

	// Must have reasonable success rate
	if seq.SuccessRate < 0.7 {
		return false
	}

	// Must save significant time
	if seq.AvgDuration < 5*time.Minute {
		return false
	}

	// Pattern should have 2-6 steps (sweet spot for automation)
	steps := strings.Count(seq.Pattern, "->") + 1
	if steps < 2 || steps > 6 {
		return false
	}

	return true
}

// createCandidate creates an automation candidate from a sequence
func (m *FrequentSequenceMiner) createCandidate(seq ActionSequence) AutomationCandidate {
	candidate := AutomationCandidate{
		ID:          generateCandidateID(seq.Pattern),
		Title:       m.generateTitle(seq),
		Description: m.generateDescription(seq),
		Status:      StatusPending,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
		Tags:        []string{"toil-miner", "automated-discovery"},
		Evidence: CandidateEvidence{
			Sequences:       []ActionSequence{seq},
			Frequency:       seq.Frequency,
			TimeSaved:       seq.AvgDuration,
			SuccessRate:     seq.SuccessRate,
			ComplexityScore: m.estimateComplexity(seq),
			RiskScore:       m.estimateRisk(seq),
		},
	}

	return candidate
}

// Helper types and functions

type FrequentPattern struct {
	Pattern string
	Actions []string
	Count   int
	Support float64
}

func generateSequenceID(pattern string) string {
	return fmt.Sprintf("seq_%x", hashString(pattern))
}

func generateCandidateID(pattern string) string {
	return fmt.Sprintf("candidate_%x", hashString(pattern))
}

func hashString(s string) uint32 {
	h := uint32(2166136261)
	for _, c := range s {
		h = (h ^ uint32(c)) * 16777619
	}
	return h
}

func (m *FrequentSequenceMiner) generateTitle(seq ActionSequence) string {
	steps := strings.Split(seq.Pattern, "->")
	if len(steps) <= 2 {
		return fmt.Sprintf("Automate %s → %s", steps[0], steps[1])
	}
	return fmt.Sprintf("Automate %d-step %s workflow", len(steps), seq.IncidentType)
}

func (m *FrequentSequenceMiner) generateDescription(seq ActionSequence) string {
	return fmt.Sprintf(
		"Frequently performed sequence: %s. Occurs %d times with %.1f%% success rate, taking an average of %s to complete.",
		seq.Pattern,
		seq.Frequency,
		seq.SuccessRate*100,
		seq.AvgDuration.String(),
	)
}

func (m *FrequentSequenceMiner) estimateComplexity(seq ActionSequence) float64 {
	steps := strings.Count(seq.Pattern, "->") + 1
	// More steps = higher complexity
	complexity := float64(steps) / 10.0 // Normalize to 0-1
	if complexity > 1.0 {
		complexity = 1.0
	}
	return complexity
}

func (m *FrequentSequenceMiner) estimateRisk(seq ActionSequence) float64 {
	// Higher risk for lower success rates
	risk := 1.0 - seq.SuccessRate

	// Higher risk for longer sequences (more can go wrong)
	steps := strings.Count(seq.Pattern, "->") + 1
	riskMultiplier := 1.0 + (float64(steps-2) * 0.1)

	return minFloat(risk*riskMultiplier, 1.0)
}

func (m *FrequentSequenceMiner) calculateBusinessImpact(evidence CandidateEvidence) float64 {
	// Simple heuristic: more frequent + more time saved = higher impact
	frequencyScore := minFloat(float64(evidence.Frequency)/100.0, 1.0)
	timeSavedScore := minFloat(evidence.TimeSaved.Minutes()/60.0, 1.0) // Normalize to hours

	return (frequencyScore + timeSavedScore) / 2.0
}

func (m *FrequentSequenceMiner) matchesPattern(actions []ActionLog, pattern []string) bool {
	if len(actions) < len(pattern) {
		return false
	}

	// Look for pattern as subsequence
	for i := 0; i <= len(actions)-len(pattern); i++ {
		match := true
		for j, actionType := range pattern {
			if actions[i+j].ActionType != actionType {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}

	return false
}

func (m *FrequentSequenceMiner) calculateSessionDuration(actions []ActionLog, pattern []string) time.Duration {
	if len(actions) < len(pattern) {
		return 0
	}

	// Find pattern match and calculate duration
	for i := 0; i <= len(actions)-len(pattern); i++ {
		match := true
		for j, actionType := range pattern {
			if actions[i+j].ActionType != actionType {
				match = false
				break
			}
		}
		if match {
			start := actions[i].Timestamp
			end := actions[i+len(pattern)-1].Timestamp.Add(actions[i+len(pattern)-1].Duration)
			return end.Sub(start)
		}
	}

	return 0
}

func (m *FrequentSequenceMiner) isSessionSuccessful(actions []ActionLog, pattern []string) bool {
	for i := 0; i <= len(actions)-len(pattern); i++ {
		match := true
		allSuccess := true
		for j, actionType := range pattern {
			if actions[i+j].ActionType != actionType {
				match = false
				break
			}
			if !actions[i+j].Success {
				allSuccess = false
			}
		}
		if match {
			return allSuccess
		}
	}
	return false
}

// Utility functions
func normalizeFrequency(frequency int) float64 {
	// Logarithmic normalization for frequency
	if frequency <= 1 {
		return 0.0
	}
	// Map [2, 100+] to [0.1, 1.0]
	normalized := 0.1 + (0.9 * (1.0 - 1.0/float64(frequency)))
	return minFloat(normalized, 1.0)
}

func normalizeTimeSaved(duration time.Duration) float64 {
	minutes := duration.Minutes()
	// Map [0, 60+] minutes to [0, 1]
	return minFloat(minutes/60.0, 1.0)
}

func mostCommon(items []string) string {
	counts := make(map[string]int)
	for _, item := range items {
		counts[item]++
	}

	var maxItem string
	var maxCount int
	for item, count := range counts {
		if count > maxCount {
			maxCount = count
			maxItem = item
		}
	}

	return maxItem
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func minFloat(a, b float64) float64 {
	if a < b {
		return a
	}
	return b
}

func maxFloat(a, b float64) float64 {
	if a > b {
		return a
	}
	return b
}
