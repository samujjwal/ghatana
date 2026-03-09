package search

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"sort"
	"strings"
	"time"
)

// SemanticSearchService provides semantic search across incidents and events
// Implements Capability 6: Search & Semantic Retrieval from Horizontal Slice AI Plan #5
// Uses Event2Vec embeddings for intelligent search and retrieval
type SemanticSearchService struct {
	indexStore  SearchIndexStore
	vectorStore VectorStore
	embedding   EmbeddingService
	config      SearchConfig
}

// SearchConfig configures the semantic search service
type SearchConfig struct {
	MaxResults             int     `json:"max_results"`              // Maximum results to return
	MinSimilarityThreshold float64 `json:"min_similarity_threshold"` // Minimum similarity score
	EnableReranking        bool    `json:"enable_reranking"`         // Enable query-based reranking
	CacheResults           bool    `json:"cache_results"`            // Cache search results
	VectorDimensions       int     `json:"vector_dimensions"`        // Embedding vector dimensions
	IndexingBatchSize      int     `json:"indexing_batch_size"`      // Batch size for indexing
}

// SearchableDocument represents a document that can be searched
type SearchableDocument struct {
	ID           string           `json:"id"`
	Type         string           `json:"type"` // incident, event, log, metric, policy
	Title        string           `json:"title"`
	Content      string           `json:"content"`
	Metadata     DocumentMetadata `json:"metadata"`
	Vector       []float64        `json:"vector"` // Event2Vec embedding
	IndexedAt    time.Time        `json:"indexed_at"`
	LastModified time.Time        `json:"last_modified"`
	AccessLevel  string           `json:"access_level"` // public, internal, restricted
	Tags         []string         `json:"tags"`
	RelatedDocs  []string         `json:"related_docs"` // Related document IDs
}

// DocumentMetadata contains metadata about a searchable document
type DocumentMetadata struct {
	Service       string                 `json:"service"`
	Environment   string                 `json:"environment"`
	Region        string                 `json:"region"`
	TenantID      string                 `json:"tenant_id"`
	Timestamp     time.Time              `json:"timestamp"`
	Severity      int                    `json:"severity"` // 1=Critical, 5=Low
	Category      string                 `json:"category"` // error, warning, info, debug
	Source        string                 `json:"source"`   // System that generated this
	CorrelationID string                 `json:"correlation_id"`
	ParentEventID string                 `json:"parent_event_id"`
	CustomFields  map[string]interface{} `json:"custom_fields"`
}

// SearchQuery represents a semantic search query
type SearchQuery struct {
	Text      string        `json:"text"`
	Filters   SearchFilters `json:"filters"`
	Options   SearchOptions `json:"options"`
	Context   QueryContext  `json:"context"`
	UserID    string        `json:"user_id"`
	SessionID string        `json:"session_id"`
}

// SearchFilters allows filtering search results
type SearchFilters struct {
	DocumentTypes  []string       `json:"document_types,omitempty"`
	Services       []string       `json:"services,omitempty"`
	Environments   []string       `json:"environments,omitempty"`
	Regions        []string       `json:"regions,omitempty"`
	TenantIDs      []string       `json:"tenant_ids,omitempty"`
	SeverityRange  *SeverityRange `json:"severity_range,omitempty"`
	TimeRange      *TimeRange     `json:"time_range,omitempty"`
	Categories     []string       `json:"categories,omitempty"`
	Tags           []string       `json:"tags,omitempty"`
	AccessLevels   []string       `json:"access_levels,omitempty"`
	Sources        []string       `json:"sources,omitempty"`
	HasCorrelation *bool          `json:"has_correlation,omitempty"`
}

// SearchOptions configures search behavior
type SearchOptions struct {
	Limit               int     `json:"limit"`                 // Max results (default: 10)
	Offset              int     `json:"offset"`                // Pagination offset
	IncludeVectors      bool    `json:"include_vectors"`       // Include vector embeddings in response
	IncludeHighlights   bool    `json:"include_highlights"`    // Include search highlights
	BoostRecent         bool    `json:"boost_recent"`          // Boost recent documents
	EnableFuzzyMatching bool    `json:"enable_fuzzy_matching"` // Enable fuzzy text matching
	SimilarityThreshold float64 `json:"similarity_threshold"`  // Override default similarity threshold
	ExplainScoring      bool    `json:"explain_scoring"`       // Include scoring explanation
}

// QueryContext provides additional context for search queries
type QueryContext struct {
	CurrentIncidentID string    `json:"current_incident_id,omitempty"` // For finding related incidents
	RecentQueries     []string  `json:"recent_queries,omitempty"`      // User's recent search history
	UserPreferences   UserPrefs `json:"user_preferences"`              // User's search preferences
	IntentAnalysis    *Intent   `json:"intent_analysis,omitempty"`     // Detected search intent
}

// UserPrefs represents user search preferences
type UserPrefs struct {
	PreferredServices     []string `json:"preferred_services,omitempty"`
	PreferredEnvironments []string `json:"preferred_environments,omitempty"`
	DefaultTimeWindow     string   `json:"default_time_window,omitempty"` // "1h", "24h", "7d", etc.
	ShowDebugLogs         bool     `json:"show_debug_logs"`
}

// Intent represents detected search intent
type Intent struct {
	Type        string   `json:"type"`        // troubleshooting, investigation, monitoring, analysis
	Confidence  float64  `json:"confidence"`  // 0.0-1.0
	Entities    []Entity `json:"entities"`    // Extracted entities
	Suggestions []string `json:"suggestions"` // Query suggestions
}

// Entity represents an extracted entity from a search query
type Entity struct {
	Type  string `json:"type"` // service, error_code, timestamp, metric, etc.
	Value string `json:"value"`
	Start int    `json:"start"` // Character position in query
	End   int    `json:"end"`
}

// SearchResult represents the result of a semantic search
type SearchResult struct {
	TotalResults   int               `json:"total_results"`
	Results        []SearchMatch     `json:"results"`
	Facets         SearchFacets      `json:"facets"`          // Aggregated facets for filtering
	RelatedQueries []string          `json:"related_queries"` // Suggested related queries
	SearchTime     time.Duration     `json:"search_time"`
	Query          SearchQuery       `json:"query"`       // Original query for reference
	Suggestions    []QuerySuggestion `json:"suggestions"` // Query suggestions
}

// SearchMatch represents a single search result
type SearchMatch struct {
	Document    SearchableDocument `json:"document"`
	Score       float64            `json:"score"`                 // Relevance score 0.0-1.0
	Explanation *ScoreExplanation  `json:"explanation,omitempty"` // Scoring explanation
	Highlights  []Highlight        `json:"highlights"`            // Search term highlights
	Snippet     string             `json:"snippet"`               // Relevant content snippet
	Distance    float64            `json:"distance"`              // Vector distance
}

// ScoreExplanation explains how the relevance score was calculated
type ScoreExplanation struct {
	TotalScore      float64          `json:"total_score"`
	SemanticScore   float64          `json:"semantic_score"`   // Vector similarity
	TextScore       float64          `json:"text_score"`       // Traditional text matching
	RecencyBoost    float64          `json:"recency_boost"`    // Time-based boost
	PopularityBoost float64          `json:"popularity_boost"` // Usage-based boost
	Components      []ScoreComponent `json:"components"`       // Detailed score breakdown
}

// ScoreComponent represents a component of the relevance score
type ScoreComponent struct {
	Name   string  `json:"name"`
	Score  float64 `json:"score"`
	Weight float64 `json:"weight"`
}

// Highlight represents highlighted search terms in content
type Highlight struct {
	Field    string `json:"field"`    // Field that was highlighted
	Fragment string `json:"fragment"` // Highlighted content fragment
	Start    int    `json:"start"`    // Start position of highlight
	End      int    `json:"end"`      // End position of highlight
}

// SearchFacets provides aggregated facet information for filtering
type SearchFacets struct {
	DocumentTypes map[string]int   `json:"document_types"` // type -> count
	Services      map[string]int   `json:"services"`       // service -> count
	Environments  map[string]int   `json:"environments"`   // environment -> count
	Categories    map[string]int   `json:"categories"`     // category -> count
	Severities    map[int]int      `json:"severities"`     // severity -> count
	TimeRanges    []TimeRangeFacet `json:"time_ranges"`    // Time-based facets
}

// TimeRangeFacet represents a time-based facet
type TimeRangeFacet struct {
	Label string    `json:"label"` // "Last hour", "Last 24 hours", etc.
	From  time.Time `json:"from"`
	To    time.Time `json:"to"`
	Count int       `json:"count"`
}

// QuerySuggestion represents a suggested query
type QuerySuggestion struct {
	Text   string  `json:"text"`
	Score  float64 `json:"score"`  // Relevance of suggestion
	Type   string  `json:"type"`   // "correction", "completion", "related"
	Reason string  `json:"reason"` // Why this was suggested
}

// SeverityRange represents a range of severities
type SeverityRange struct {
	Min int `json:"min"` // Minimum severity (1=Critical)
	Max int `json:"max"` // Maximum severity (5=Low)
}

// TimeRange represents a time range filter
type TimeRange struct {
	From time.Time `json:"from"`
	To   time.Time `json:"to"`
}

// Interfaces for external dependencies

// SearchIndexStore manages the search index
type SearchIndexStore interface {
	IndexDocument(ctx context.Context, doc SearchableDocument) error
	IndexDocuments(ctx context.Context, docs []SearchableDocument) error
	UpdateDocument(ctx context.Context, id string, doc SearchableDocument) error
	DeleteDocument(ctx context.Context, id string) error
	GetDocument(ctx context.Context, id string) (*SearchableDocument, error)
	SearchText(ctx context.Context, query string, filters SearchFilters, limit int) ([]SearchableDocument, error)
}

// VectorStore manages vector embeddings
type VectorStore interface {
	StoreVector(ctx context.Context, id string, vector []float64, metadata map[string]interface{}) error
	SearchSimilar(ctx context.Context, vector []float64, limit int, threshold float64) ([]VectorMatch, error)
	GetVector(ctx context.Context, id string) ([]float64, error)
	DeleteVector(ctx context.Context, id string) error
}

// VectorMatch represents a vector similarity match
type VectorMatch struct {
	ID         string                 `json:"id"`
	Similarity float64                `json:"similarity"` // Cosine similarity 0.0-1.0
	Metadata   map[string]interface{} `json:"metadata"`
}

// EmbeddingService generates vector embeddings
type EmbeddingService interface {
	GenerateEmbedding(ctx context.Context, text string) ([]float64, error)
	GenerateEmbeddings(ctx context.Context, texts []string) ([][]float64, error)
	GetEmbeddingDimensions() int
	GetModelInfo() EmbeddingModelInfo
}

// EmbeddingModelInfo provides information about the embedding model
type EmbeddingModelInfo struct {
	Name        string `json:"name"`
	Version     string `json:"version"`
	Dimensions  int    `json:"dimensions"`
	MaxTokens   int    `json:"max_tokens"`
	Description string `json:"description"`
}

// NewSemanticSearchService creates a new semantic search service
func NewSemanticSearchService(indexStore SearchIndexStore, vectorStore VectorStore,
	embedding EmbeddingService) *SemanticSearchService {

	config := SearchConfig{
		MaxResults:             50,   // Return up to 50 results
		MinSimilarityThreshold: 0.3,  // Minimum 30% similarity
		EnableReranking:        true, // Enable query-based reranking
		CacheResults:           true, // Cache results for performance
		VectorDimensions:       384,  // Common embedding dimension
		IndexingBatchSize:      100,  // Process 100 docs at a time
	}

	return &SemanticSearchService{
		indexStore:  indexStore,
		vectorStore: vectorStore,
		embedding:   embedding,
		config:      config,
	}
}

// IndexDocument indexes a single document for search
func (s *SemanticSearchService) IndexDocument(ctx context.Context, doc SearchableDocument) error {
	// Generate vector embedding if not provided
	if len(doc.Vector) == 0 {
		content := s.extractSearchableContent(doc)
		vector, err := s.embedding.GenerateEmbedding(ctx, content)
		if err != nil {
			return fmt.Errorf("failed to generate embedding: %w", err)
		}
		doc.Vector = vector
	}

	doc.IndexedAt = time.Now()

	// Store in search index
	if err := s.indexStore.IndexDocument(ctx, doc); err != nil {
		return fmt.Errorf("failed to index document: %w", err)
	}

	// Store vector
	metadata := map[string]interface{}{
		"type":        doc.Type,
		"service":     doc.Metadata.Service,
		"environment": doc.Metadata.Environment,
		"timestamp":   doc.Metadata.Timestamp,
		"severity":    doc.Metadata.Severity,
	}

	if err := s.vectorStore.StoreVector(ctx, doc.ID, doc.Vector, metadata); err != nil {
		return fmt.Errorf("failed to store vector: %w", err)
	}

	return nil
}

// Search performs semantic search across indexed documents
func (s *SemanticSearchService) Search(ctx context.Context, query SearchQuery) (*SearchResult, error) {
	startTime := time.Now()

	// Analyze query intent
	if query.Context.IntentAnalysis == nil {
		intent := s.analyzeIntent(query.Text)
		query.Context.IntentAnalysis = &intent
	}

	// Generate query embedding
	queryVector, err := s.embedding.GenerateEmbedding(ctx, query.Text)
	if err != nil {
		return nil, fmt.Errorf("failed to generate query embedding: %w", err)
	}

	// Set default search options
	if query.Options.Limit == 0 {
		query.Options.Limit = 10
	}
	if query.Options.SimilarityThreshold == 0 {
		query.Options.SimilarityThreshold = s.config.MinSimilarityThreshold
	}

	// Perform vector similarity search
	vectorMatches, err := s.vectorStore.SearchSimilar(ctx, queryVector,
		query.Options.Limit*3, query.Options.SimilarityThreshold) // Get 3x for reranking
	if err != nil {
		return nil, fmt.Errorf("vector search failed: %w", err)
	}

	// Get full documents for vector matches
	var documents []SearchableDocument
	for _, match := range vectorMatches {
		if doc, err := s.indexStore.GetDocument(ctx, match.ID); err == nil && doc != nil {
			documents = append(documents, *doc)
		}
	}

	// Apply filters
	documents = s.applyFilters(documents, query.Filters)

	// Create search matches with scoring
	var matches []SearchMatch
	for i, doc := range documents {
		if i < len(vectorMatches) {
			match := SearchMatch{
				Document: doc,
				Score:    s.calculateRelevanceScore(doc, query, vectorMatches[i].Similarity),
				Distance: 1.0 - vectorMatches[i].Similarity,
				Snippet:  s.generateSnippet(doc, query.Text),
			}

			if query.Options.IncludeHighlights {
				match.Highlights = s.generateHighlights(doc, query.Text)
			}

			if query.Options.ExplainScoring {
				match.Explanation = s.explainScore(doc, query, vectorMatches[i].Similarity)
			}

			matches = append(matches, match)
		}
	}

	// Sort by relevance score
	sort.Slice(matches, func(i, j int) bool {
		return matches[i].Score > matches[j].Score
	})

	// Apply limit and offset
	totalResults := len(matches)
	start := query.Options.Offset
	end := start + query.Options.Limit

	if start > len(matches) {
		matches = []SearchMatch{}
	} else if end > len(matches) {
		matches = matches[start:]
	} else {
		matches = matches[start:end]
	}

	// Generate facets
	facets := s.generateFacets(documents)

	// Generate related queries and suggestions
	relatedQueries := s.generateRelatedQueries(query)
	suggestions := s.generateQuerySuggestions(query)

	searchTime := time.Since(startTime)

	return &SearchResult{
		TotalResults:   totalResults,
		Results:        matches,
		Facets:         facets,
		RelatedQueries: relatedQueries,
		SearchTime:     searchTime,
		Query:          query,
		Suggestions:    suggestions,
	}, nil
}

// Helper methods

func (s *SemanticSearchService) extractSearchableContent(doc SearchableDocument) string {
	content := doc.Title + " " + doc.Content

	// Add relevant metadata to searchable content
	if doc.Metadata.Service != "" {
		content += " service:" + doc.Metadata.Service
	}
	if doc.Metadata.Environment != "" {
		content += " environment:" + doc.Metadata.Environment
	}
	if doc.Metadata.Category != "" {
		content += " category:" + doc.Metadata.Category
	}

	return content
}

func (s *SemanticSearchService) analyzeIntent(query string) Intent {
	// Simplified intent analysis - in reality this would use NLP
	queryLower := strings.ToLower(query)

	var intentType string
	var confidence float64
	var entities []Entity

	// Detect intent patterns
	if strings.Contains(queryLower, "error") || strings.Contains(queryLower, "fail") {
		intentType = "troubleshooting"
		confidence = 0.8
	} else if strings.Contains(queryLower, "investigate") || strings.Contains(queryLower, "root cause") {
		intentType = "investigation"
		confidence = 0.9
	} else if strings.Contains(queryLower, "monitor") || strings.Contains(queryLower, "track") {
		intentType = "monitoring"
		confidence = 0.7
	} else {
		intentType = "analysis"
		confidence = 0.6
	}

	// Extract simple entities (service names, error codes, etc.)
	words := strings.Fields(queryLower)
	for i, word := range words {
		if strings.HasSuffix(word, "-service") || strings.HasSuffix(word, "svc") {
			entities = append(entities, Entity{
				Type:  "service",
				Value: word,
				Start: i,
				End:   i + len(word),
			})
		}
		if strings.Contains(word, "error") && len(word) > 5 {
			entities = append(entities, Entity{
				Type:  "error_code",
				Value: word,
				Start: i,
				End:   i + len(word),
			})
		}
	}

	return Intent{
		Type:        intentType,
		Confidence:  confidence,
		Entities:    entities,
		Suggestions: s.generateIntentSuggestions(intentType),
	}
}

func (s *SemanticSearchService) generateIntentSuggestions(intentType string) []string {
	switch intentType {
	case "troubleshooting":
		return []string{
			"recent errors in production",
			"failed deployments last 24 hours",
			"service outages this week",
		}
	case "investigation":
		return []string{
			"root cause analysis for incident",
			"correlation between events",
			"timeline of related failures",
		}
	case "monitoring":
		return []string{
			"performance metrics trends",
			"error rate patterns",
			"system health indicators",
		}
	default:
		return []string{
			"system overview",
			"recent activity summary",
			"operational metrics",
		}
	}
}

func (s *SemanticSearchService) applyFilters(docs []SearchableDocument, filters SearchFilters) []SearchableDocument {
	var filtered []SearchableDocument

	for _, doc := range docs {
		// Apply document type filter
		if len(filters.DocumentTypes) > 0 && !contains(filters.DocumentTypes, doc.Type) {
			continue
		}

		// Apply service filter
		if len(filters.Services) > 0 && !contains(filters.Services, doc.Metadata.Service) {
			continue
		}

		// Apply environment filter
		if len(filters.Environments) > 0 && !contains(filters.Environments, doc.Metadata.Environment) {
			continue
		}

		// Apply severity filter
		if filters.SeverityRange != nil {
			if doc.Metadata.Severity < filters.SeverityRange.Min ||
				doc.Metadata.Severity > filters.SeverityRange.Max {
				continue
			}
		}

		// Apply time range filter
		if filters.TimeRange != nil {
			if doc.Metadata.Timestamp.Before(filters.TimeRange.From) ||
				doc.Metadata.Timestamp.After(filters.TimeRange.To) {
				continue
			}
		}

		// Apply category filter
		if len(filters.Categories) > 0 && !contains(filters.Categories, doc.Metadata.Category) {
			continue
		}

		// Apply tags filter
		if len(filters.Tags) > 0 {
			hasTag := false
			for _, tag := range filters.Tags {
				if contains(doc.Tags, tag) {
					hasTag = true
					break
				}
			}
			if !hasTag {
				continue
			}
		}

		filtered = append(filtered, doc)
	}

	return filtered
}

func (s *SemanticSearchService) calculateRelevanceScore(doc SearchableDocument, query SearchQuery,
	similarity float64) float64 {

	score := similarity // Start with vector similarity

	// Text matching boost
	textScore := s.calculateTextScore(doc, query.Text)
	score += textScore * 0.3

	// Recency boost if enabled
	if query.Options.BoostRecent {
		recencyBoost := s.calculateRecencyBoost(doc.Metadata.Timestamp)
		score += recencyBoost * 0.2
	}

	// Severity boost (higher severity = higher score)
	severityBoost := float64(6-doc.Metadata.Severity) / 5.0 * 0.1
	score += severityBoost

	// Intent-based boost
	if query.Context.IntentAnalysis != nil {
		intentBoost := s.calculateIntentBoost(doc, *query.Context.IntentAnalysis)
		score += intentBoost * 0.2
	}

	// Normalize to 0-1 range
	return math.Min(score, 1.0)
}

func (s *SemanticSearchService) calculateTextScore(doc SearchableDocument, query string) float64 {
	// Simple text matching score based on term overlap
	queryTerms := strings.Fields(strings.ToLower(query))
	docText := strings.ToLower(doc.Title + " " + doc.Content)

	matches := 0
	for _, term := range queryTerms {
		if strings.Contains(docText, term) {
			matches++
		}
	}

	if len(queryTerms) == 0 {
		return 0
	}

	return float64(matches) / float64(len(queryTerms))
}

func (s *SemanticSearchService) calculateRecencyBoost(timestamp time.Time) float64 {
	// Boost recent documents (within last 24 hours get full boost)
	age := time.Since(timestamp)
	if age < 24*time.Hour {
		return 1.0
	} else if age < 7*24*time.Hour {
		return 0.5
	} else {
		return 0.1
	}
}

func (s *SemanticSearchService) calculateIntentBoost(doc SearchableDocument, intent Intent) float64 {
	// Boost documents that match the detected intent
	switch intent.Type {
	case "troubleshooting":
		if doc.Type == "incident" || doc.Metadata.Category == "error" {
			return intent.Confidence
		}
	case "investigation":
		if doc.Type == "event" || doc.Metadata.CorrelationID != "" {
			return intent.Confidence
		}
	case "monitoring":
		if doc.Type == "metric" || doc.Metadata.Category == "warning" {
			return intent.Confidence
		}
	}
	return 0.0
}

func (s *SemanticSearchService) generateSnippet(doc SearchableDocument, query string) string {
	// Generate a relevant snippet from the document content
	content := doc.Content
	if len(content) == 0 {
		content = doc.Title
	}

	// Find the best matching portion of content
	queryTerms := strings.Fields(strings.ToLower(query))
	sentences := strings.Split(content, ".")

	bestSentence := ""
	bestScore := 0

	for _, sentence := range sentences {
		sentence = strings.TrimSpace(sentence)
		if len(sentence) == 0 {
			continue
		}

		score := 0
		sentenceLower := strings.ToLower(sentence)
		for _, term := range queryTerms {
			if strings.Contains(sentenceLower, term) {
				score++
			}
		}

		if score > bestScore {
			bestScore = score
			bestSentence = sentence
		}
	}

	if bestSentence != "" {
		// Truncate if too long
		if len(bestSentence) > 200 {
			bestSentence = bestSentence[:200] + "..."
		}
		return bestSentence
	}

	// Fallback to first part of content
	if len(content) > 200 {
		return content[:200] + "..."
	}
	return content
}

func (s *SemanticSearchService) generateHighlights(doc SearchableDocument, query string) []Highlight {
	var highlights []Highlight
	queryTerms := strings.Fields(strings.ToLower(query))

	// Highlight in title
	titleLower := strings.ToLower(doc.Title)
	for _, term := range queryTerms {
		if idx := strings.Index(titleLower, term); idx != -1 {
			highlights = append(highlights, Highlight{
				Field:    "title",
				Fragment: doc.Title,
				Start:    idx,
				End:      idx + len(term),
			})
		}
	}

	// Highlight in content
	contentLower := strings.ToLower(doc.Content)
	for _, term := range queryTerms {
		if idx := strings.Index(contentLower, term); idx != -1 {
			// Create a fragment around the match
			start := maxInt(0, idx-50)
			end := minInt(len(doc.Content), idx+len(term)+50)
			fragment := doc.Content[start:end]

			highlights = append(highlights, Highlight{
				Field:    "content",
				Fragment: fragment,
				Start:    idx - start,
				End:      (idx - start) + len(term),
			})
		}
	}

	return highlights
}

func (s *SemanticSearchService) explainScore(doc SearchableDocument, query SearchQuery,
	similarity float64) *ScoreExplanation {

	textScore := s.calculateTextScore(doc, query.Text)
	recencyBoost := 0.0
	if query.Options.BoostRecent {
		recencyBoost = s.calculateRecencyBoost(doc.Metadata.Timestamp)
	}

	intentBoost := 0.0
	if query.Context.IntentAnalysis != nil {
		intentBoost = s.calculateIntentBoost(doc, *query.Context.IntentAnalysis)
	}

	severityBoost := float64(6-doc.Metadata.Severity) / 5.0 * 0.1

	totalScore := similarity + textScore*0.3 + recencyBoost*0.2 + intentBoost*0.2 + severityBoost

	return &ScoreExplanation{
		TotalScore:      math.Min(totalScore, 1.0),
		SemanticScore:   similarity,
		TextScore:       textScore,
		RecencyBoost:    recencyBoost,
		PopularityBoost: 0.0, // Not implemented
		Components: []ScoreComponent{
			{Name: "semantic_similarity", Score: similarity, Weight: 1.0},
			{Name: "text_match", Score: textScore, Weight: 0.3},
			{Name: "recency", Score: recencyBoost, Weight: 0.2},
			{Name: "intent", Score: intentBoost, Weight: 0.2},
			{Name: "severity", Score: severityBoost, Weight: 0.1},
		},
	}
}

func (s *SemanticSearchService) generateFacets(docs []SearchableDocument) SearchFacets {
	facets := SearchFacets{
		DocumentTypes: make(map[string]int),
		Services:      make(map[string]int),
		Environments:  make(map[string]int),
		Categories:    make(map[string]int),
		Severities:    make(map[int]int),
	}

	for _, doc := range docs {
		facets.DocumentTypes[doc.Type]++
		facets.Services[doc.Metadata.Service]++
		facets.Environments[doc.Metadata.Environment]++
		facets.Categories[doc.Metadata.Category]++
		facets.Severities[doc.Metadata.Severity]++
	}

	// Generate time range facets
	now := time.Now()
	facets.TimeRanges = []TimeRangeFacet{
		{Label: "Last hour", From: now.Add(-time.Hour), To: now, Count: 0},
		{Label: "Last 24 hours", From: now.Add(-24 * time.Hour), To: now, Count: 0},
		{Label: "Last week", From: now.Add(-7 * 24 * time.Hour), To: now, Count: 0},
		{Label: "Last month", From: now.Add(-30 * 24 * time.Hour), To: now, Count: 0},
	}

	// Count documents in each time range
	for _, doc := range docs {
		for i := range facets.TimeRanges {
			if doc.Metadata.Timestamp.After(facets.TimeRanges[i].From) &&
				doc.Metadata.Timestamp.Before(facets.TimeRanges[i].To) {
				facets.TimeRanges[i].Count++
			}
		}
	}

	return facets
}

func (s *SemanticSearchService) generateRelatedQueries(query SearchQuery) []string {
	// Generate related queries based on intent and entities
	var related []string

	if query.Context.IntentAnalysis != nil {
		related = append(related, query.Context.IntentAnalysis.Suggestions...)
	}

	// Add service-specific queries if service entities found
	for _, entity := range query.Context.IntentAnalysis.Entities {
		if entity.Type == "service" {
			related = append(related, fmt.Sprintf("errors in %s", entity.Value))
			related = append(related, fmt.Sprintf("%s performance issues", entity.Value))
		}
	}

	return related
}

func (s *SemanticSearchService) generateQuerySuggestions(query SearchQuery) []QuerySuggestion {
	var suggestions []QuerySuggestion

	// Simple query completion suggestions
	text := strings.ToLower(query.Text)

	if strings.Contains(text, "error") {
		suggestions = append(suggestions, QuerySuggestion{
			Text:   query.Text + " in production",
			Score:  0.8,
			Type:   "completion",
			Reason: "Common pattern: filter by environment",
		})
	}

	if strings.Contains(text, "service") {
		suggestions = append(suggestions, QuerySuggestion{
			Text:   query.Text + " last 24 hours",
			Score:  0.7,
			Type:   "completion",
			Reason: "Common pattern: add time filter",
		})
	}

	return suggestions
}

// Utility functions
func contains(slice []string, item string) bool {
	for _, s := range slice {
		if s == item {
			return true
		}
	}
	return false
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// HTTP Handlers

// HandleSearch handles POST /search
func (s *SemanticSearchService) HandleSearch(w http.ResponseWriter, r *http.Request) {
	var query SearchQuery
	if err := json.NewDecoder(r.Body).Decode(&query); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	result, err := s.Search(r.Context(), query)
	if err != nil {
		http.Error(w, fmt.Sprintf("Search failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// HandleIndexDocument handles POST /search/index
func (s *SemanticSearchService) HandleIndexDocument(w http.ResponseWriter, r *http.Request) {
	var doc SearchableDocument
	if err := json.NewDecoder(r.Body).Decode(&doc); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := s.IndexDocument(r.Context(), doc); err != nil {
		http.Error(w, fmt.Sprintf("Indexing failed: %v", err), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status":      "success",
		"message":     "Document indexed successfully",
		"document_id": doc.ID,
	})
}

// HandleGetSuggestions handles GET /search/suggestions
func (s *SemanticSearchService) HandleGetSuggestions(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	if query == "" {
		http.Error(w, "Query parameter 'q' is required", http.StatusBadRequest)
		return
	}

	// Generate intent analysis and suggestions
	intent := s.analyzeIntent(query)

	searchQuery := SearchQuery{
		Text: query,
		Context: QueryContext{
			IntentAnalysis: &intent,
		},
	}

	suggestions := s.generateQuerySuggestions(searchQuery)
	relatedQueries := s.generateRelatedQueries(searchQuery)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"query":           query,
		"intent":          intent,
		"suggestions":     suggestions,
		"related_queries": relatedQueries,
	})
}

// HandleGetModelInfo handles GET /search/model
func (s *SemanticSearchService) HandleGetModelInfo(w http.ResponseWriter, r *http.Request) {
	modelInfo := s.embedding.GetModelInfo()

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"embedding_model": modelInfo,
		"config":          s.config,
		"capabilities": map[string]bool{
			"semantic_search":     true,
			"intent_detection":    true,
			"query_suggestions":   true,
			"faceted_search":      true,
			"result_highlighting": true,
			"score_explanation":   true,
		},
	})
}
