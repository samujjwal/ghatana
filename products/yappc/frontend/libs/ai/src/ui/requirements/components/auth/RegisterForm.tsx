import { useState } from 'react'
import { TextField, Button, Box, Alert, Link, Typography } from '@ghatana/ui';interface RegisterFormProps {
  onSwitchToLogin?: () => void
}

const RegisterForm = ({ onSwitchToLogin }: RegisterFormProps) => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    name: '',
  })
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleChange = (field: string) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setFormData(prev => ({
      ...prev,
      [field]: event.target.value
    }))
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setLoading(true)

    if (formData.password !== formData.confirmPassword) {
      setError('Passwords do not match')
      setLoading(false)
      return
    }

    try {
      // NOTE: Implement actual registration logic with GraphQL mutation
      console.log('Registering user:', formData)
      // Mock success
      if (onSwitchToLogin) {
        onSwitchToLogin()
      }
    } catch (err) {
      setError('Registration failed. Please try again.')
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
        id="name"
        label="Full Name"
        name="name"
        autoComplete="name"
        autoFocus
        value={formData.name}
        onChange={handleChange('name')}
        disabled={loading}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        id="email"
        label="Email Address"
        name="email"
        autoComplete="email"
        value={formData.email}
        onChange={handleChange('email')}
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
        autoComplete="new-password"
        value={formData.password}
        onChange={handleChange('password')}
        disabled={loading}
      />
      <TextField
        margin="normal"
        required
        fullWidth
        name="confirmPassword"
        label="Confirm Password"
        type="password"
        id="confirmPassword"
        autoComplete="new-password"
        value={formData.confirmPassword}
        onChange={handleChange('confirmPassword')}
        disabled={loading}
      />
      <Button
        type="submit"
        fullWidth
        variant="solid"
        className="mt-6 mb-4"
        disabled={loading}
      >
        {loading ? 'Creating Account...' : 'Create Account'}
      </Button>
      {onSwitchToLogin && (
        <Box className="text-center">
          <Typography as="p" className="text-sm">
            Already have an account?{' '}
            <Link href="#" onClick={onSwitchToLogin}>
              Sign in
            </Link>
          </Typography>
        </Box>
      )}
    </Box>
  )
}

export default RegisterForm