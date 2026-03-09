/**
 * useDataSource Hook Examples
 *
 * Comprehensive examples demonstrating various use cases of the useDataSource hook
 */

import { Trash2 as DeleteIcon } from 'lucide-react';
import { RefreshCw as RefreshIcon } from 'lucide-react';
import { Box, Card, CardContent, Typography, Button, Spinner as CircularProgress, Alert, Stack, TextField, Chip, Grid, InteractiveList as List, ListItem, ListItemText, Divider } from '@ghatana/ui';
import { resolveMuiColor } from '../utils/safePalette';
import React, { useState } from 'react';

import { useDataSource } from './useDataSource';

// ============================================================================
// Example 1: Basic REST GET Request
// ============================================================================

/**
 *
 */
export function BasicRESTExample() {
  const { data, isLoading, error, refetch } = useDataSource<{ userId: number; title: string }>({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/todos/1',
  });

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Basic REST GET Request
        </Typography>
        {isLoading && <CircularProgress size={24} />}
        {error && <Alert severity="error">{error.message}</Alert>}
        {data && (
          <Box>
            <Typography as="p" className="text-sm">User ID: {data.userId}</Typography>
            <Typography as="p" className="text-sm">Title: {data.title}</Typography>
            <Button startIcon={<RefreshIcon />} onClick={refetch} className="mt-4">
              Refetch
            </Button>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 2: REST POST Request
// ============================================================================

/**
 *
 */
export function RESTPostExample() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [shouldFetch, setShouldFetch] = useState(false);

  const { data, isLoading, error } = useDataSource({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/posts',
    method: 'POST',
    body: { title, body, userId: 1 },
    cache: false, // Don't cache POST requests
    ...(shouldFetch ? {} : { url: undefined }), // Only fetch when button clicked
  });

  const handleSubmit = () => {
    if (title && body) {
      setShouldFetch(true);
    }
  };

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          REST POST Request
        </Typography>
        <Stack spacing={2}>
          <TextField
            label="Title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            fullWidth
          />
          <TextField
            label="Body"
            value={body}
            onChange={(e) => setBody(e.target.value)}
            multiline
            rows={3}
            fullWidth
          />
          <Button
            variant="solid"
            onClick={handleSubmit}
            disabled={!title || !body || isLoading}
          >
            {isLoading ? 'Creating...' : 'Create Post'}
          </Button>
        </Stack>
        {error && <Alert severity="error" className="mt-4">{error.message}</Alert>}
        {data && (
            <Alert severity="success" className="mt-4">
            Post created with ID: {(data as unknown).id}
          </Alert>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 3: Static Data
// ============================================================================

/**
 *
 */
export function StaticDataExample() {
  const staticUsers = [
    { id: 1, name: 'Alice', role: 'Admin' },
    { id: 2, name: 'Bob', role: 'User' },
    { id: 3, name: 'Charlie', role: 'User' },
  ];

  const { data, mutate } = useDataSource({
    type: 'static',
    data: staticUsers,
  });

  const handleAddUser = () => {
    mutate((current) => [
      ...(current || []),
      { id: Date.now(), name: 'New User', role: 'User' },
    ]);
  };

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Static Data with Local Mutations
        </Typography>
        <List>
          {data?.map((user: unknown) => (
            <ListItem key={user.id}>
              <ListItemText primary={user.name} secondary={user.role} />
              <Chip label={user.role} size="sm" />
            </ListItem>
          ))}
        </List>
        <Button variant="outlined" onClick={handleAddUser} className="mt-4">
          Add User
        </Button>
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 4: Data with Transform
// ============================================================================

/**
 *
 */
export function TransformDataExample() {
  const { data, isLoading } = useDataSource({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/users/1',
    transformResponse: (rawData) => ({
      fullName: rawData.name,
      email: rawData.email,
      city: rawData.address.city,
      company: rawData.company.name,
    }),
  });

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Data Transformation
        </Typography>
        {isLoading && <CircularProgress size={24} />}
        {data && (
          <Stack spacing={1}>
            <Typography as="p" className="text-sm">
              <strong>Name:</strong> {(data as unknown).fullName}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Email:</strong> {(data as unknown).email}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>City:</strong> {(data as unknown).city}
            </Typography>
            <Typography as="p" className="text-sm">
              <strong>Company:</strong> {(data as unknown).company}
            </Typography>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 5: List with Caching
// ============================================================================

/**
 *
 */
export function CachedListExample() {
  const [page, setPage] = useState(1);

  const { data, isLoading, isValidating } = useDataSource({
    type: 'rest',
    url: `https://jsonplaceholder.typicode.com/posts?_page=${page}&_limit=5`,
    cache: true,
    cacheTTL: 60000, // 1 minute
    cacheKey: `posts-page-${page}`,
  });

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Cached List with Pagination
        </Typography>
        {isLoading && <CircularProgress size={24} />}
        {isValidating && <Chip label="Revalidating..." size="sm" tone="primary" />}
        {data && (
          <>
            <List>
              {(data as unknown[]).map((post: unknown) => (
                <React.Fragment key={post.id}>
                  <ListItem>
                    <ListItemText primary={post.title} secondary={`Post #${post.id}`} />
                  </ListItem>
                  <Divider />
                </React.Fragment>
              ))}
            </List>
            <Stack direction="row" spacing={2} className="mt-4">
              <Button disabled={page === 1} onClick={() => setPage(page - 1)}>
                Previous
              </Button>
              <Chip label={`Page ${page}`} />
              <Button onClick={() => setPage(page + 1)}>Next</Button>
            </Stack>
          </>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 6: Auto-refresh Data
// ============================================================================

/**
 *
 */
export function AutoRefreshExample() {
  const { data, isLoading, isValidating } = useDataSource({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/posts/1',
    refetchInterval: 10000, // Refetch every 10 seconds
  });

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Auto-Refresh Every 10 Seconds
        </Typography>
        {isValidating && <Chip label="Refreshing..." size="sm" tone="primary" className="mb-4" />}
        {isLoading && <CircularProgress size={24} />}
        {data && (
          <Box>
            <Typography as="p" className="text-sm">
              <strong>Title:</strong> {(data as unknown).title}
            </Typography>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-2 block">
              Last updated: {new Date().toLocaleTimeString()}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 7: Optimistic Updates
// ============================================================================

/**
 *
 */
export function OptimisticUpdateExample() {
  const { data, mutate, isLoading } = useDataSource({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/todos/1',
  });

  const handleToggle = () => {
    // Optimistically update UI
    mutate((current: unknown) => ({
      ...current,
      completed: !current?.completed,
    }));

    // In real app, would also make API call here
    // fetch(...).then(result => mutate(result))
  };

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Optimistic UI Updates
        </Typography>
        {isLoading && <CircularProgress size={24} />}
        {data && (
          <Box>
            <Typography as="p" className="text-sm">{(data as unknown).title}</Typography>
            <Chip
              label={(data as unknown).completed ? 'Completed' : 'Pending'}
              color={resolveMuiColor(useTheme(), (data as unknown).completed ? 'success' : 'default', 'default')}
              className="mt-4"
            />
            <Button variant="outlined" onClick={handleToggle} className="mt-4 ml-4">
              Toggle Status
            </Button>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 8: Error Handling
// ============================================================================

/**
 *
 */
export function ErrorHandlingExample() {
  const [url, setUrl] = useState('https://jsonplaceholder.typicode.com/posts/1');
  const [errorMessage, setErrorMessage] = useState('');

  const { data, isLoading, error, refetch } = useDataSource({
    type: 'rest',
    url,
    onError: (err) => {
      setErrorMessage(`Custom error handler: ${err.message}`);
    },
    onSuccess: () => {
      setErrorMessage('');
    },
  });

  const triggerError = () => {
    setUrl('https://jsonplaceholder.typicode.com/invalid-endpoint');
  };

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Error Handling
        </Typography>
        <Stack spacing={2}>
          <Button variant="outlined" tone="danger" onClick={triggerError}>
            Trigger Error
          </Button>
          {isLoading && <CircularProgress size={24} />}
          {error && <Alert severity="error">{error.message}</Alert>}
          {errorMessage && <Alert severity="warning">{errorMessage}</Alert>}
          {data && (
            <Alert severity="success">
              Data loaded successfully: {(data as unknown).title}
            </Alert>
          )}
          <Button startIcon={<RefreshIcon />} onClick={refetch}>
            Retry
          </Button>
        </Stack>
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Example 9: Cache Management
// ============================================================================

/**
 *
 */
export function CacheManagementExample() {
  const { data, isLoading, clearCache, refetch } = useDataSource({
    type: 'rest',
    url: 'https://jsonplaceholder.typicode.com/users/1',
    cache: true,
    cacheKey: 'user-1',
  });

  return (
    <Card>
      <CardContent>
        <Typography as="h6" gutterBottom>
          Cache Management
        </Typography>
        {isLoading && <CircularProgress size={24} />}
        {data && (
          <Box>
            <Typography as="p" className="text-sm">Name: {(data as unknown).name}</Typography>
            <Stack direction="row" spacing={2} className="mt-4">
              <Button startIcon={<RefreshIcon />} onClick={refetch}>
                Refetch
              </Button>
              <Button
                startIcon={<DeleteIcon />}
                tone="danger"
                onClick={() => {
                  clearCache();
                  refetch();
                }}
              >
                Clear Cache & Refetch
              </Button>
            </Stack>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary" className="mt-4 block">
              Try navigating away and back - data should load from cache
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================================================
// Combined Demo
// ============================================================================

/**
 *
 */
export function DataSourceExamplesDemo() {
  return (
    <Box className="p-6">
      <Typography as="h4" gutterBottom>
        useDataSource Hook Examples
      </Typography>
      <Typography as="p" className="text-sm" color="text.secondary" paragraph>
        Comprehensive examples demonstrating REST, GraphQL, caching, mutations, and more.
      </Typography>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6}>
          <BasicRESTExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <StaticDataExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <TransformDataExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <CachedListExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <AutoRefreshExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <OptimisticUpdateExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <ErrorHandlingExample />
        </Grid>
        <Grid item xs={12} md={6}>
          <CacheManagementExample />
        </Grid>
        <Grid item xs={12}>
          <RESTPostExample />
        </Grid>
      </Grid>
    </Box>
  );
}

export default DataSourceExamplesDemo;
