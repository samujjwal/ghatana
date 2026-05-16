/**
 * Preview Security Health Panel
 *
 * Displays preview token scope, trust level, acknowledgement requirements,
 * token scope mismatches, and expiration status for a ProductUnit.
 */

import React from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/Card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Shield, Lock, AlertTriangle, Clock, CheckCircle, XCircle } from 'lucide-react';

export interface TokenScope {
  id: string;
  name: string;
  required: boolean;
  granted: boolean;
}

export interface PreviewSecurity {
  previewTokenId: string;
  tokenScope: TokenScope[];
  trustLevel: 'trusted' | 'semi-trusted' | 'untrusted';
  acknowledgementRequired: boolean;
  acknowledgementStatus: 'acknowledged' | 'pending' | 'expired';
  scopeMismatches: string[];
  expiresAt?: string;
  lastRefreshed?: string;
}

interface PreviewSecurityHealthPanelProps {
  productUnitId: string;
  previewSecurity: PreviewSecurity;
}

export const PreviewSecurityHealthPanel: React.FC<PreviewSecurityHealthPanelProps> = ({
  productUnitId,
  previewSecurity,
}) => {
  const getTrustLevelIcon = (level: string) => {
    switch (level) {
      case 'trusted':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'semi-trusted':
        return <Shield className="h-4 w-4 text-yellow-500" />;
      case 'untrusted':
        return <XCircle className="h-4 w-4 text-red-500" />;
      default:
        return <Lock className="h-4 w-4 text-gray-500" />;
    }
  };

  const getTrustLevelBadgeVariant = (level: string): 'default' | 'secondary' | 'destructive' | 'outline' => {
    switch (level) {
      case 'trusted':
        return 'default';
      case 'semi-trusted':
        return 'secondary';
      case 'untrusted':
        return 'destructive';
      default:
        return 'outline';
    }
  };

  const getAcknowledgementStatusIcon = (status: string) => {
    switch (status) {
      case 'acknowledged':
        return <CheckCircle className="h-4 w-4 text-green-500" />;
      case 'expired':
        return <Clock className="h-4 w-4 text-orange-500" />;
      default:
        return <AlertTriangle className="h-4 w-4 text-yellow-500" />;
    }
  };

  if (!previewSecurity) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Preview Security</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground">
            No preview security data available for {productUnitId}
          </p>
        </CardContent>
      </Card>
    );
  }

  const grantedScopes = previewSecurity.tokenScope.filter(s => s.granted).length;
  const requiredScopes = previewSecurity.tokenScope.filter(s => s.required).length;
  const missingRequiredScopes = previewSecurity.tokenScope.filter(s => s.required && !s.granted).length;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">Preview Security</h3>
        <div className="flex items-center gap-2">
          <Badge variant={getTrustLevelBadgeVariant(previewSecurity.trustLevel)}>
            {previewSecurity.trustLevel}
          </Badge>
          <Badge variant={previewSecurity.acknowledgementStatus === 'acknowledged' ? 'default' : 'destructive'}>
            {previewSecurity.acknowledgementStatus}
          </Badge>
        </div>
      </div>

      {previewSecurity.scopeMismatches.length > 0 && (
        <Alert variant="destructive">
          <AlertTriangle className="h-4 w-4" />
          <AlertTitle>Token Scope Mismatches Detected</AlertTitle>
          <AlertDescription>
            {previewSecurity.scopeMismatches.length} scope mismatch(es) found. Review the scope details below.
          </AlertDescription>
        </Alert>
      )}

      {missingRequiredScopes > 0 && (
        <Alert variant="destructive">
          <Lock className="h-4 w-4" />
          <AlertTitle>Missing Required Scopes</AlertTitle>
          <AlertDescription>
            {missingRequiredScopes} required scope(s) are not granted. Preview functionality may be limited.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid grid-cols-2 gap-4">
        <div className="p-4 border rounded-lg space-y-2">
          <div className="flex items-center gap-2">
            {getTrustLevelIcon(previewSecurity.trustLevel)}
            <h4 className="font-semibold">Trust Level</h4>
          </div>
          <p className="text-sm text-muted-foreground">{previewSecurity.trustLevel}</p>
        </div>

        <div className="p-4 border rounded-lg space-y-2">
          <div className="flex items-center gap-2">
            {getAcknowledgementStatusIcon(previewSecurity.acknowledgementStatus)}
            <h4 className="font-semibold">Acknowledgement</h4>
          </div>
          <p className="text-sm text-muted-foreground">
            {previewSecurity.acknowledgementRequired ? 'Required' : 'Not Required'} · {previewSecurity.acknowledgementStatus}
          </p>
        </div>
      </div>

      <div className="p-4 border rounded-lg space-y-3">
        <div className="flex items-center justify-between">
          <h4 className="font-semibold">Token Scopes</h4>
          <div className="flex gap-2 text-sm">
            <span>{grantedScopes}/{requiredScopes} required granted</span>
          </div>
        </div>

        <div className="space-y-2">
          {previewSecurity.tokenScope.map((scope) => (
            <div key={scope.id} className="flex items-center justify-between p-2 bg-muted rounded">
              <div className="flex items-center gap-2">
                <Lock className="h-3 w-3" />
                <span className="text-sm font-medium">{scope.name}</span>
                {scope.required && <Badge variant="outline" className="text-xs">Required</Badge>}
              </div>
              <div className="flex items-center gap-2">
                {scope.granted ? (
                  <CheckCircle className="h-3 w-3 text-green-500" />
                ) : (
                  <XCircle className="h-3 w-3 text-red-500" />
                )}
                <Badge variant={scope.granted ? 'default' : 'destructive'} className="text-xs">
                  {scope.granted ? 'Granted' : 'Not Granted'}
                </Badge>
              </div>
            </div>
          ))}
        </div>
      </div>

      {previewSecurity.scopeMismatches.length > 0 && (
        <div className="p-4 border rounded-lg space-y-3">
          <h4 className="font-semibold flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 text-orange-500" />
            Scope Mismatches
          </h4>
          <div className="space-y-2">
            {previewSecurity.scopeMismatches.map((mismatch, index) => (
              <div key={index} className="flex items-center gap-2 text-sm text-muted-foreground p-2 bg-muted rounded">
                <XCircle className="h-3 w-3 text-red-500" />
                {mismatch}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-2 gap-4 text-sm">
        {previewSecurity.expiresAt && (
          <div>
            <p className="text-muted-foreground">Expires At</p>
            <p className="font-medium">{new Date(previewSecurity.expiresAt).toLocaleString()}</p>
          </div>
        )}
        {previewSecurity.lastRefreshed && (
          <div>
            <p className="text-muted-foreground">Last Refreshed</p>
            <p className="font-medium">{new Date(previewSecurity.lastRefreshed).toLocaleString()}</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default PreviewSecurityHealthPanel;
