import { Clock as AccessTimeIcon } from 'lucide-react';
import { Heart as FavoriteIcon } from 'lucide-react';
import { Heart as FavoriteBorderIcon } from 'lucide-react';
import { MoreVertical as MoreVertIcon } from 'lucide-react';
import { Typography, IconButton, Box, Chip } from '@ghatana/ui';
import { resolveMuiColor } from '../../utils/safePalette';
import React from 'react';

import { Button } from '../Button';
import { Card, CardContent, CardActions } from '../Card';


/**
 *
 */
export interface WorkspaceCardProps {
  /**
   * Workspace ID
   */
  id: string;
  
  /**
   * Workspace name
   */
  name: string;
  
  /**
   * Workspace description
   */
  description?: string;
  
  /**
   * Last modified timestamp
   */
  lastModified: string;
  
  /**
   * Whether the workspace is marked as favorite
   */
  favorite?: boolean;
  
  /**
   * Callback when the favorite button is clicked
   */
  onFavoriteToggle?: (id: string, favorite: boolean) => void;
  
  /**
   * Callback when the open button is clicked
   */
  onOpen?: (id: string) => void;
  
  /**
   * Callback when the more options button is clicked
   */
  onMoreOptions?: (id: string, event: React.MouseEvent<HTMLButtonElement>) => void;
}

/**
 * WorkspaceCard component
 * 
 * Displays information about a workspace in a card format.
 */
export const WorkspaceCard: React.FC<WorkspaceCardProps> = ({
  id,
  name,
  description,
  lastModified,
  favorite = false,
  onFavoriteToggle,
  onOpen,
  onMoreOptions,
}) => {
  // Format the lastModified date
  const formattedDate = React.useMemo(() => {
    try {
      const date = new Date(lastModified);
      return new Intl.DateTimeFormat('en-US', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date);
    } catch (error) {
      return lastModified;
    }
  }, [lastModified]);
  
  // Calculate time ago
  const timeAgo = React.useMemo(() => {
    try {
      const date = new Date(lastModified);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffSecs = Math.floor(diffMs / 1000);
      const diffMins = Math.floor(diffSecs / 60);
      const diffHours = Math.floor(diffMins / 60);
      const diffDays = Math.floor(diffHours / 24);
      
      if (diffDays > 0) {
        return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
      } else if (diffHours > 0) {
        return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
      } else if (diffMins > 0) {
        return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`;
      } else {
        return 'Just now';
      }
    } catch (error) {
      return '';
    }
  }, [lastModified]);
  
  // Handle favorite toggle
  const handleFavoriteToggle = () => {
    if (onFavoriteToggle) {
      onFavoriteToggle(id, !favorite);
    }
  };
  
  // Handle open
  const handleOpen = () => {
    if (onOpen) {
      onOpen(id);
    }
  };
  
  // Handle more options
  const handleMoreOptions = (event: React.MouseEvent<HTMLButtonElement>) => {
    if (onMoreOptions) {
      onMoreOptions(id, event);
    }
  };
  
  return (
    <Card hover>
      <CardContent>
        <Box className="flex justify-between items-start mb-2">
          <Typography as="h6" component="h3" className="font-bold">
            {name}
          </Typography>
          <Box>
            <IconButton
              size="sm"
              onClick={handleFavoriteToggle}
              color={resolveMuiColor(useTheme(), (favorite ? 'primary' : 'default'), 'default') as unknown}
              aria-label={favorite ? 'Remove from favorites' : 'Add to favorites'}
            >
              {favorite ? <FavoriteIcon /> : <FavoriteBorderIcon />}
            </IconButton>
            <IconButton
              size="sm"
              onClick={handleMoreOptions}
              aria-label="More options"
            >
              <MoreVertIcon />
            </IconButton>
          </Box>
        </Box>
        
        {description && (
          <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
            {description}
          </Typography>
        )}
        
        <Box className="flex items-center mb-2">
          <AccessTimeIcon size={16} color="action" className="mr-1" />
          <Typography as="span" className="text-xs text-gray-500" color="text.secondary" title={formattedDate}>
            {timeAgo}
          </Typography>
        </Box>
        
        <Box className="flex flex-wrap gap-1">
          <Chip size="sm" label="React" />
          <Chip size="sm" label="TypeScript" />
        </Box>
      </CardContent>
      
      <CardActions>
        <Button size="sm" onClick={handleOpen}>
          Open
        </Button>
        <Button size="sm" variant="outline">
          Details
        </Button>
      </CardActions>
    </Card>
  );
};
