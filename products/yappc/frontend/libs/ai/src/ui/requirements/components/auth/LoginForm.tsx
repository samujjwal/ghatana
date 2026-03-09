import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TextField, Button, Box, Alert, Link, Typography } from '@ghatana/ui';interface LoginFormProps {
  onSwitchToRegister?: () => void
}

const LoginForm = ({ onSwitchToRegister }: LoginFormProps) => {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      // NOTE: Implement actual login logic with GraphQL mutation
      if (email && password) {
        // Mock login for now
        localStorage.setItem('authToken', 'mock-token')
        navigate('/')
      } else {
        setError('Please fill in all fields')
      }
    } catch (err) {
      setError('Login failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box component="form" onSubmit={handleSubmit} className="mt-2">
      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        autoFocus
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        disabled={loading}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="password"
        label="Password"
        type="password"
        id="password"
        autoComplete="current-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        disabled={loading}
      />
      <Button
        type="submit"
        fullWidth
        variant="solid"
        className="mt-6 mb-4"
        disabled={loading}
      >
        {loading ? 'Signing In...' : 'Sign In'}
      </Button>
      <Box className="text-center">
        <Link href="#" as="p" className="text-sm">
          Forgot password?
        </Link>
      </Box>
      {onSwitchToRegister && (
        <Box className="text-center mt-4">
          <Typography as="p" className="text-sm">
            Don't have an account?{' '}
            <Link href="#" onClick={onSwitchToRegister}>
              Sign up
            </Link>
          </Typography>
        </Box>
      )}
    </Box>
  )
}

export default LoginForm