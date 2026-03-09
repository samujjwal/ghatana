import { Container, Surface as Paper, Typography, Box } from '@ghatana/ui';import LoginForm from '../components/auth/LoginForm'

const LoginPage = () => {
  return (
    <Container component="main" size="sm">
      <Box
        className="flex flex-col items-center mt-16"
      >
        <Paper elevation={3} className="w-full p-8">
          <Typography component="h1" as="h4" align="center" gutterBottom>
            AI Requirements Tool
          </Typography>
          <Typography component="h2" as="h5" align="center" gutterBottom>
            Sign In
          </Typography>
          <LoginForm />
        </Paper>
      </Box>
    </Container>
  )
}

export default LoginPage