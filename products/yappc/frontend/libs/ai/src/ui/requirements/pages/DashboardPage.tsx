import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, Typography, Button, Grid, Card, CardContent, CardActions } from '@ghatana/ui';import { Plus as AddIcon } from 'lucide-react';const DashboardPage = () => {
  const navigate = useNavigate()

  useEffect(() => {
    // Check if user is authenticated
    const token = localStorage.getItem('authToken')
    if (!token) {
      navigate('/login')
    }
  }, [navigate])

  const handleCreateWorkspace = () => {
    navigate('/workspaces/new')
  }

  return (
    <Box className="grow p-6">
      <Box className="flex justify-between items-center mb-6">
        <Typography as="h4" component="h1">
          Dashboard
        </Typography>
        <Button
          variant="solid"
          startIcon={<AddIcon />}
          onClick={handleCreateWorkspace}
        >
          Create Workspace
        </Button>
      </Box>

      <Grid container spacing={3}>
        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                My Workspaces
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Manage your workspaces and projects
              </Typography>
            </CardContent>
            <CardActions>
              <Button size="sm">View All</Button>
            </CardActions>
          </Card>
        </Grid>

        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                Recent Projects
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Continue working on your projects
              </Typography>
            </CardContent>
            <CardActions>
              <Button size="sm">View All</Button>
            </CardActions>
          </Card>
        </Grid>

        <Grid item xs={12} md={6} lg={4}>
          <Card>
            <CardContent>
              <Typography as="h6" component="h2">
                AI Suggestions
              </Typography>
              <Typography as="p" className="text-sm" color="text.secondary">
                Review AI-generated requirement suggestions
              </Typography>
            </CardContent>
            <CardActions>
              <Button size="sm">View All</Button>
            </CardActions>
          </Card>
        </Grid>
      </Grid>
    </Box>
  )
}

export default DashboardPage