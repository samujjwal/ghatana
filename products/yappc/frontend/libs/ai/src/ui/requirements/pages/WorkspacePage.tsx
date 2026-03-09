import { useParams } from 'react-router-dom'
import { Box, Typography, Button, Grid, Card, CardContent, CardActions } from '@ghatana/ui';import { Plus as AddIcon } from 'lucide-react';const WorkspacePage = () => {
  const { workspaceId } = useParams<{ workspaceId: string }>()

  const handleCreateProject = () => {
    // NOTE: Navigate to create project
    console.log('Create project in workspace', workspaceId)
  }

  return (
    <Box className="grow p-6">
      <Box className="flex justify-between items-center mb-6">
        <Typography as="h4" component="h1">
          Workspace {workspaceId}
        </Typography>
        <Button
          variant="solid"
          startIcon={<AddIcon />}
          onClick={handleCreateProject}
        >
          Create Project
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                Project Alpha
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Sample project description
              </Typography>
            </CardContent>
            <CardActions>
              <Button size="sm">Open Project</Button>
            </CardActions>
          </Card>
        </Grid>

        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                Project Beta
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Another sample project
              </Typography>
            </CardContent>
            <CardActions>
              <Button size="sm">Open Project</Button>
            </CardActions>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default WorkspacePage