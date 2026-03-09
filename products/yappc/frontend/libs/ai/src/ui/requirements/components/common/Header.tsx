import { AppBar, Toolbar, Typography, Button, Box } from '@ghatana/ui';import { useNavigate } from 'react-router-dom'

const Header = () => {
  const navigate = useNavigate()

  const handleLogout = () => {
    localStorage.removeItem('authToken')
    navigate('/login')
  }

  return (
    <AppBar position="static">
      <Toolbar>
        <Typography as="h6" component="div" className="grow">
          AI Requirements Tool
        </Typography>
        <Box className="flex gap-4">
          <Button tone="neutral" onClick={() => navigate('/')}>
            Dashboard
          </Button>
          <Button tone="neutral" onClick={handleLogout}>
            Logout
          </Button>
        </Box>
      </Toolbar>
    </AppBar>
  )
}

export default Header