# DMOS Connector Setup Guide

## Google Ads Connector

### Prerequisites

- Google Ads Developer Token
- OAuth 2.0 Client ID and Secret
- Google Ads Manager Account ID

### Setup Steps

1. **Create OAuth Client**
   - Go to Google Cloud Console
   - Create OAuth 2.0 credentials
   - Set redirect URI to `http://localhost:8080/oauth/callback`

2. **Configure Environment Variables**
   ```bash
   export GOOGLE_ADS_CLIENT_ID="your-client-id"
   export GOOGLE_ADS_CLIENT_SECRET="your-client-secret"
   export GOOGLE_ADS_DEVELOPER_TOKEN="your-developer-token"
   export GOOGLE_ADS_MANAGER_ACCOUNT_ID="your-manager-account-id"
   ```

3. **Connect Account**
   - Navigate to `/connectors/google-ads`
   - Click "Connect Account"
   - Authorize the application
   - Select the manager account

4. **Verify Connection**
   - Check connector status in `/connectors`
   - Ensure status shows "CONNECTED"

### Rate Limits

The connector respects Google Ads rate limits:
- 5000 operations per minute per API
- Automatic backoff on rate limit errors
- Retry logic with exponential backoff

### Troubleshooting

**OAuth Errors**
- Verify redirect URI matches
- Check client credentials are correct

**Rate Limit Errors**
- Reduce query frequency
- Check API usage in Google Ads console

**Account Not Found**
- Verify manager account ID
- Ensure account is accessible with provided credentials
