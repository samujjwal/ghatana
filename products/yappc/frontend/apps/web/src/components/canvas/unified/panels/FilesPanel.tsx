import { useState, useEffect, useMemo } from 'react';
import {
  Box,
  Typography,
  ListItem,
  ListItemText,
  ListItemIcon,
  IconButton,
  InputAdornment,
  Alert,
  Stack,
  InteractiveList as List,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { ListItemButton, TextField } from '@ghatana/ui';
import { FolderOpen, File as InsertDriveFile, MoreVertical as MoreVert, Search, Upload, FolderPlus as CreateNewFolder } from 'lucide-react';
import type { FileItem } from '../panel-types';
import { railService } from '@/services/rail/RailServiceClient';

/**
 * Files Panel - File explorer for project
 * Browse project files and folders, upload new files
 */
export function FilesPanel() {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentPath, setCurrentPath] = useState('/');

  // Fetch files via RailDataService
  useEffect(() => {
    const fetchFiles = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await railService.getFiles(currentPath);
        setFiles(data);
      } catch {
        setError('Failed to load files');
      } finally {
        setLoading(false);
      }
    };

    fetchFiles();
  }, [currentPath]);

  const filteredFiles = useMemo(() => {
    if (!searchQuery) return files;
    const query = searchQuery.toLowerCase();
    return files.filter((file) => file.name.toLowerCase().includes(query));
  }, [files, searchQuery]);

  const formatSize = (bytes?: number) => {
    if (!bytes) return '';
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
  };

  const getFileIcon = (file: FileItem) => {
    if (file.type === 'folder') {
      return <FolderOpen className="text-blue-600 text-lg" />;
    }
    return <InsertDriveFile className="opacity-[0.6] text-lg" />;
  };

  if (loading) {
    return (
      <Box className="p-4 flex justify-center">
        <CircularProgress size={32} />
      </Box>
    );
  }

  return (
    <Box
      className="p-4 flex flex-col h-full"
    >
      <Stack direction="row" gap={1} className="mb-4" alignItems="center">
        <Typography variant="subtitle2" fontWeight={600} className="flex-1">
          Files
        </Typography>
        <IconButton size="small" title="New Folder">
          <CreateNewFolder className="text-lg" />
        </IconButton>
        <IconButton size="small" title="Upload">
          <Upload className="text-lg" />
        </IconButton>
      </Stack>

      {currentPath !== '/' && (
        <Typography
          variant="caption"
          className="mb-2 p-2 bg-gray-100 dark:bg-gray-800 rounded-sm cursor-pointer hover:bg-blue-50 hover:dark:bg-blue-900/20"
          onClick={() => setCurrentPath('/')}
        >
          📂 {currentPath}
        </Typography>
      )}

      <TextField
        size="small"
        placeholder="Search files..."
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Search className="text-lg" />
            </InputAdornment>
          ),
        }}
        className="mb-4"
        fullWidth
      />

      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}

      <List dense className="flex-1 overflow-auto">
        {filteredFiles.length === 0 ? (
          <Typography variant="body2" color="text.secondary" className="mt-4">
            No files yet
          </Typography>
        ) : (
          filteredFiles.map((file) => (
            <ListItem
              key={file.id}
              disablePadding
              secondaryAction={
                <IconButton size="small" edge="end">
                  <MoreVert className="text-base" />
                </IconButton>
              }
            >
              <ListItemButton dense>
                <ListItemIcon className="min-w-[28px]">
                  {getFileIcon(file)}
                </ListItemIcon>
                <ListItemText
                  primary={file.name}
                  secondary={
                    file.type === 'file' ? `${formatSize(file.size)}` : 'Folder'
                  }
                  primaryTypographyProps={{ variant: 'body2' }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItemButton>
            </ListItem>
          ))
        )}
      </List>
    </Box>
  );
}
