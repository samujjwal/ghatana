import * as React from 'react';
import { useState, useCallback, useEffect } from 'react';
import { cn } from '../../utils/cn';
import { Card, CardContent, CardHeader, CardTitle } from '../Card/Card';

// Types for NLQ functionality
export interface QueryIntent {
  metric: string;
  aggregation?: string;
  timeRange: {
    start: string;
    end: string;
    label: string;
  };
  filters: Record<string, string>;
  groupBy: string[];
  eventType?: string;
  threshold?: number;
  comparison?: {
    start: string;
    end: string;
    label: string;
  };
  confidence: number;
  originalText: string;
  parsedBy: string;
}

export interface QueryResult {
  queryId: string;
  intent: QueryIntent;
  generatedSQL: {
    sql: string;
    parameters: Record<string, any>;
    tables: string[];
    columns: string[];
    functions: string[];
    rationale: string;
    safety: {
      approved: boolean;
      violations: string[];
      riskLevel: string;
    };
  };
  data: unknown[][];
  columns: string[];
  rowCount: number;
  executionTime: number;
  chartConfig: ChartConfig;
  success: boolean;
  error?: string;
  timestamp: string;
}

export interface ChartConfig {
  type: 'line' | 'bar' | 'pie' | 'heatmap';
  xAxis: string;
  yAxis: string[];
  series: string[];
  title: string;
  description: string;
  annotations: Array<{
    type: string;
    timestamp: string;
    label: string;
    color: string;
    source: string;
  }>;
}

export interface QueryPaneProps {
  className?: string;
  onQuerySubmit?: (query: string) => Promise<QueryResult>;
  onQueryHistory?: () => Promise<QueryResult[]>;
  suggestions?: string[];
  examples?: string[];
  serverUrl?: string;
}

export const QueryPane = React.forwardRef<HTMLDivElement, QueryPaneProps>(
  ({ 
    className, 
    onQuerySubmit: _onQuerySubmit, 
    onQueryHistory: _onQueryHistory, 
    suggestions = [], 
    examples = [],
    serverUrl = 'http://localhost:8081',
    ...props 
  }, ref) => {
    const [query, setQuery] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [result, setResult] = useState<QueryResult | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [showSuggestions, setShowSuggestions] = useState(false);
  const [currentSuggestions, setCurrentSuggestions] = useState<string[]>(suggestions.slice(0, 6));
    const [history, setHistory] = useState<QueryResult[]>([]);
    const [showHistory, setShowHistory] = useState(false);
    
    // Auto-complete suggestions based on partial input
    const handleInputChange = useCallback(async (value: string) => {
      setQuery(value);
      
      if (value.length > 2) {
        try {
          const response = await fetch(`${serverUrl}/api/nlq/suggestions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ partial: value }),
          });
          
          if (response.ok) {
            const data = await response.json();
            setCurrentSuggestions(data.suggestions || []);
            setShowSuggestions(true);
          }
        } catch (err) {
          console.warn('Failed to fetch suggestions:', err);
        }
      } else {
        setShowSuggestions(false);
      }
    }, [serverUrl]);
    
    // Submit natural language query
  const handleSubmit = useCallback(async (queryText: string = query) => {
      if (!queryText.trim()) return;
      
      setIsLoading(true);
      setError(null);
      setShowSuggestions(false);
      
      try {
        const response = await fetch(`${serverUrl}/api/nlq/query`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ 
            query: queryText,
            user_id: 'desktop-user' // TODO: Get actual user ID
          }),
        });
        
  const data = await response.json();
        
        if (data.success && data.result) {
          setResult(data.result);
          setError(null);
          
          // Add to history
          setHistory(prev => [data.result, ...prev.slice(0, 9)]); // Keep last 10
        } else {
          setError(data.error || 'Query failed');
          setResult(null);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Network error');
        setResult(null);
      } finally {
        setIsLoading(false);
      }
    }, [query, serverUrl]);
    
    // Load query history
    const loadHistory = useCallback(async () => {
      try {
        const response = await fetch(
          `${serverUrl}/api/nlq/history?user_id=desktop-user&limit=20`
        );
        
        if (response.ok) {
          const data = await response.json();
          if (data.success && data.history) {
            setHistory(data.history);
          }
        }
      } catch (err) {
        // Intentionally log but avoid unused-var warnings when linters
        // enforce no-unused-vars on catch bindings in some configs.
         
        console.warn('Failed to load history:', err);
      }
    }, [serverUrl]);
    
    // Load history on mount
    useEffect(() => {
      loadHistory();
    }, [loadHistory]);
    
    // Handle example queries
    const handleExampleClick = (example: string) => {
      setQuery(example);
      handleSubmit(example);
    };
    
    // Handle suggestion selection
    const handleSuggestionClick = (suggestion: string) => {
      setQuery(suggestion);
      setShowSuggestions(false);
      handleSubmit(suggestion);
    };
    
    return (
      <div ref={ref} className={cn('nlq-query-pane', className)} {...props}>
        {/* Query Input */}
        <Card className="mb-4">
          <CardHeader>
            <CardTitle>Natural Language Query</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="relative">
              <textarea
                value={query}
                onChange={(e) => handleInputChange(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                    e.preventDefault();
                    handleSubmit();
                  }
                }}
                placeholder="Ask a question about your metrics... e.g., 'show error rate for service-api in last 24 hours'"
                className="w-full p-3 border rounded-md resize-none min-h-[80px] focus:outline-none focus:ring-2 focus:ring-blue-500"
                disabled={isLoading}
              />
              
              {/* Suggestions Dropdown */}
              {showSuggestions && currentSuggestions.length > 0 && (
                <div className="absolute z-10 w-full mt-1 bg-white border rounded-md shadow-lg max-h-48 overflow-y-auto">
                  {currentSuggestions.map((suggestion, index) => (
                    <button
                      key={index}
                      onClick={() => handleSuggestionClick(suggestion)}
                      className="w-full px-3 py-2 text-left hover:bg-gray-100 focus:bg-gray-100 focus:outline-none"
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              )}
            </div>
            
            <div className="flex justify-between items-center mt-3">
              <div className="flex gap-2">
                <button
                  onClick={() => handleSubmit()}
                  disabled={isLoading || !query.trim()}
                  className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {isLoading ? 'Processing...' : 'Ask'}
                </button>
                
                <button
                  onClick={() => setShowHistory(!showHistory)}
                  className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                >
                  History
                </button>
              </div>
              
              <div className="text-sm text-gray-500">
                Press ⌘+Enter to submit
              </div>
            </div>
          </CardContent>
        </Card>
        
        {/* Example Queries */}
        {examples.length > 0 && (
          <Card className="mb-4">
            <CardHeader>
              <CardTitle>Example Queries</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex flex-wrap gap-2">
                {examples.slice(0, 6).map((example, index) => (
                  <button
                    key={index}
                    onClick={() => handleExampleClick(example)}
                    className="px-3 py-1 text-sm bg-gray-100 hover:bg-gray-200 rounded-full transition-colors"
                  >
                    {example}
                  </button>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
        
        {/* Query History */}
        {showHistory && history.length > 0 && (
          <Card className="mb-4">
            <CardHeader>
              <CardTitle>Query History</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {history.map((item, index) => (
                  <button
                    key={index}
                    onClick={() => handleExampleClick(item.intent.originalText)}
                    className="w-full text-left px-3 py-2 border rounded-md hover:bg-gray-50 transition-colors"
                  >
                    <div className="text-sm font-medium">{item.intent.originalText}</div>
                    <div className="text-xs text-gray-500">
                      {item.rowCount} rows • {item.executionTime}ms • {new Date(item.timestamp).toLocaleString()}
                    </div>
                  </button>
                ))}
              </div>
            </CardContent>
          </Card>
        )}
        
        {/* Error Display */}
        {error && (
          <Card className="mb-4 border-red-200">
            <CardContent className="pt-6">
              <div className="text-red-600">
                <div className="font-medium">Error</div>
                <div className="text-sm mt-1">{error}</div>
              </div>
            </CardContent>
          </Card>
        )}
        
        {/* Query Results */}
        {result && !error && (
          <QueryResultsDisplay result={result} />
        )}
      </div>
    );
  }
);

QueryPane.displayName = 'QueryPane';

// Query Results Display Component
interface QueryResultsDisplayProps {
  result: QueryResult;
}

const QueryResultsDisplay: React.FC<QueryResultsDisplayProps> = ({ result }) => {
  const [activeTab, setActiveTab] = useState<'data' | 'chart' | 'sql'>('data');
  
  return (
    <Card>
      <CardHeader>
        <div className="flex justify-between items-start">
          <div>
            <CardTitle>Query Results</CardTitle>
            <div className="text-sm text-gray-500 mt-1">
              {result.rowCount} rows returned in {result.executionTime}ms
            </div>
          </div>
          
          <div className="text-right text-sm">
            <div className="text-gray-600">Confidence: {(result.intent.confidence * 100).toFixed(0)}%</div>
            <div className="text-xs text-gray-500">Parsed by: {result.intent.parsedBy}</div>
          </div>
        </div>
      </CardHeader>
      
      <CardContent>
        {/* Tab Navigation */}
        <div className="flex space-x-1 border-b mb-4">
          {['data', 'chart', 'sql'].map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab as typeof activeTab)}
              className={cn(
                'px-4 py-2 text-sm font-medium capitalize transition-colors',
                activeTab === tab
                  ? 'border-b-2 border-blue-500 text-blue-600'
                  : 'text-gray-500 hover:text-gray-700'
              )}
            >
              {tab}
            </button>
          ))}
        </div>
        
        {/* Data Table */}
        {activeTab === 'data' && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  {result.columns.map((column, index) => (
                    <th key={index} className="text-left py-2 px-3 font-medium">
                      {column}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {result.data.slice(0, 100).map((row, rowIndex) => (
                  <tr key={rowIndex} className="border-b hover:bg-gray-50">
                    {row.map((cell, cellIndex) => (
                      <td key={cellIndex} className="py-2 px-3">
                        {formatCellValue(cell)}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
            
            {result.data.length > 100 && (
              <div className="text-center py-4 text-gray-500">
                Showing first 100 rows of {result.rowCount}
              </div>
            )}
          </div>
        )}
        
        {/* Chart Visualization */}
        {activeTab === 'chart' && (
          <div className="h-64 flex items-center justify-center border-2 border-dashed border-gray-300 rounded-md">
            <div className="text-center text-gray-500">
              <div className="text-lg font-medium">Chart Visualization</div>
              <div className="text-sm">
                {result.chartConfig.type} chart • {result.chartConfig.title}
              </div>
              <div className="text-xs mt-2">
                Chart implementation coming soon...
              </div>
            </div>
          </div>
        )}
        
        {/* SQL Details */}
        {activeTab === 'sql' && (
          <div className="space-y-4">
            <div>
              <div className="text-sm font-medium mb-2">Generated SQL</div>
              <pre className="bg-gray-100 p-3 rounded-md text-sm overflow-x-auto">
                {result.generatedSQL.sql}
              </pre>
            </div>
            
            <div>
              <div className="text-sm font-medium mb-2">Query Analysis</div>
              <div className="text-sm text-gray-600">
                {result.generatedSQL.rationale}
              </div>
            </div>
            
            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <div className="font-medium mb-1">Tables</div>
                <div className="text-gray-600">
                  {result.generatedSQL.tables.join(', ')}
                </div>
              </div>
              
              <div>
                <div className="font-medium mb-1">Functions</div>
                <div className="text-gray-600">
                  {result.generatedSQL.functions.join(', ')}
                </div>
              </div>
            </div>
            
            {/* Safety Information */}
            <div>
              <div className="text-sm font-medium mb-2">Security Check</div>
              <div className={cn(
                'inline-flex px-2 py-1 rounded-full text-xs font-medium',
                result.generatedSQL.safety.approved
                  ? 'bg-green-100 text-green-800'
                  : 'bg-red-100 text-red-800'
              )}>
                {result.generatedSQL.safety.approved ? 'Approved' : 'Rejected'} • {result.generatedSQL.safety.riskLevel} Risk
              </div>
              
              {result.generatedSQL.safety.violations.length > 0 && (
                <div className="mt-2 text-sm text-red-600">
                  <div className="font-medium">Violations:</div>
                  <ul className="list-disc list-inside">
                    {result.generatedSQL.safety.violations.map((violation, index) => (
                      <li key={index}>{violation}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// Helper function to format cell values
const formatCellValue = (value: unknown): string => {
  if (value === null || value === undefined) {
    return '-';
  }
  
  if (typeof value === 'number') {
    return value.toLocaleString();
  }
  
  if (typeof value === 'string') {
    // Format timestamps
    if (value.match(/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}/)) {
      return new Date(value).toLocaleString();
    }
  }
  
  return String(value);
};

export default QueryPane;