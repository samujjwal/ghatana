import { Card, CardContent, CardActions, Typography, Button, Box, Chip } from '@ghatana/ui';import { useNavigate } from 'react-router-dom'
import { Users as GroupIcon } from 'lucide-react';interface WorkspaceCardProps {
  id: string
  name: string
  description?: string
  memberCount: number
  projectCount: number
  createdAt: string
}

const WorkspaceCard = ({
  id,
  name,
  description,
  memberCount,
  projectCount,
  createdAt
}: WorkspaceCardProps) => {
  const navigate = useNavigate()

  const handleOpenWorkspace = () => {
    navigate(`/workspace/${id}`)
  }

  return (
    <Card className="h-full flex flex-col">
      <CardContent className="grow">
        <Typography as="h6" component="h2" gutterBottom>
          {name}
        </Typography>
        {description && (
          <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
            {description}
          </Typography>
        )}
        <Box className="flex gap-2 mb-4">
          <Chip
            icon={<GroupIcon />}
            label={`${memberCount} members`}
            size="sm"
            variant="outlined"
          />
          <Chip
            label={`${projectCount} projects`}
            size="sm"
            variant="outlined"
          />
        </Box>
        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
          Created {new Date(createdAt).toLocaleDateString()}
        </Typography>
      </CardContent>
      <CardActions>
        <Button size="sm" onClick={handleOpenWorkspace}>
          Open Workspace
        </Button>
      </CardActions>
    </Card>
  )
}

export default WorkspaceCard