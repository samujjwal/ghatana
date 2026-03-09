import { useParams } from 'react-router-dom'
import { Box, Typography, Button, Grid, Card, CardContent, CardActions, Chip } from '@ghatana/ui';import { Plus as AddIcon } from 'lucide-react';const ProjectPage = () => {
  const { projectId } = useParams<{ projectId: string }>()

  const handleCreateRequirement = () => {
    // NOTE: Navigate to create requirement
    console.log('Create requirement in project', projectId)
  }

  return (
    <Box className="grow p-6">
      <Box className="flex justify-between items-center mb-6">
        <Typography as="h4" component="h1">
          Project {projectId}
        </Typography>
        <Button
          variant="solid"
          startIcon={<AddIcon />}
          onClick={handleCreateRequirement}
        >
          Create Requirement
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                User Authentication
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                The system shall allow users to authenticate using email and password
              </Typography>
              <Box className="flex gap-2">
                <Chip label="Functional" size="sm" tone="primary" />
                <Chip label="Must Have" size="sm" tone="secondary" />
              </Box>
            </CardContent>
            <CardActions>
              <Button size="sm">Edit</Button>
              <Button size="sm">View AI Suggestions</Button>
            </CardActions>
          </Card>
        </Grid>

        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                Data Export
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary" className="mb-2">
                Users shall be able to export requirements in multiple formats
              </Typography>
              <Box className="flex gap-2">
                <Chip label="Functional" size="sm" tone="primary" />
                <Chip label="Should Have" size="sm" tone="warning" />
              </Box>
            </CardContent>
            <CardActions>
              <Button size="sm">Edit</Button>
              <Button size="sm">View AI Suggestions</Button>
            </CardActions>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default ProjectPage