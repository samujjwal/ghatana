import { useState } from 'react'
import { TextField, Button, Box, Alert, Typography } from '@ghatana/ui';interface PasswordResetProps {
  onBackToLogin?: () => void
}

const PasswordReset = ({ onBackToLogin }: PasswordResetProps) => {
  const [email, setEmail] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [emailSent, setEmailSent] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setMessage('')
    setLoading(true)

    try {
      // NOTE: Implement actual password reset logic
      console.log('Sending password reset email to:', email)
      // Mock success
      setMessage('Password reset email sent. Please check your inbox.')
      setEmailSent(true)
    } catch (err) {
      setError('Failed to send password reset email. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  if (emailSent) {
    return (
      <Box className="text-center">
        <Typography as="h6" gutterBottom>
          Check Your Email
        </Typography>
        <Typography as="p" className="text-sm" color="text.secondary" className="mb-4">
          We've sent a password reset link to {email}
        </Typography>
        <Button
          variant="outlined"
          onClick={onBackToLogin}
          className="mt-4"
        >
          Back to Sign In
        </Button>
      </Box>
    )
  }

  return (
    <Box component="form" onSubmit={handleSubmit} className="mt-2">
      <Typography as="p" className="text-sm" color="text.secondary" className="mb-4 text-center">
        Enter your email address and we'll send you a link to reset your password.
      </Typography>
      {error && (
        <Alert severity="error" className="mb-4">
          {error}
        </Alert>
      )}
      {message && (
        <Alert severity="success" className="mb-4">
          {message}
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
      <Button
        type="submit"
        fullWidth
        variant="solid"
        className="mt-6 mb-4"
        disabled={loading}
      >
        {loading ? 'Sending...' : 'Send Reset Link'}
      </Button>
      {onBackToLogin && (
        <Box className="text-center">
          <Button
            variant="ghost"
            onClick={onBackToLogin}
            className="mt-2"
          >
            Back to Sign In
          </Button>
        </Box>
      )}
    </Box>
  )
}

export default PasswordReset