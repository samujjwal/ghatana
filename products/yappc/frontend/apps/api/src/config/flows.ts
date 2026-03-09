export interface FlowDefinition {
    id: string;
    initialState: string;
    states: Record<string, FlowStateDefinition>;
}

export interface FlowStateDefinition {
    on: Record<string, string>; // event -> nextState
    associatedTasks?: string[];
    guards?: string[]; // permissions required
}

export const GoldenFlows: Record<string, FlowDefinition> = {
    'product.requirement': {
        id: 'product.requirement',
        initialState: 'DraftInput',
        states: {
            DraftInput: {
                on: { SUBMIT_NL: 'Suggesting' },
                associatedTasks: ['prob-001', 'req-001']
            },
            Suggesting: {
                on: { AI_READY: 'ReviewingSuggestions' }
            },
            ReviewingSuggestions: {
                on: {
                    ACCEPT_SUGGESTION: 'ReviewingSuggestions',
                    SAVE_REQUIREMENT: 'AcceptedAndPersisted',
                    REJECT: 'Rejected'
                },
                guards: ['requirements.define']
            },
            AcceptedAndPersisted: {
                on: { CREATE_SNAPSHOT: 'SnapshotCreated' }
            },
            SnapshotCreated: {
                on: { FINISH: 'Done' }
            },
            Done: { on: {} },
            Rejected: { on: {} }
        }
    },
    'development.pr': {
        id: 'development.pr',
        initialState: 'PRSelected',
        states: {
            PRSelected: { on: { START_REVIEW: 'ReviewingDiff' } },
            ReviewingDiff: {
                on: {
                    APPROVE: 'Approved',
                    REQUEST_CHANGES: 'ChangesRequested'
                },
                guards: ['code.approve']
            },
            Approved: { on: { MERGE: 'Merged' } },
            ChangesRequested: { on: { NEW_COMMIT: 'ReviewingDiff' } },
            Merged: { on: {} }
        }
    },
    'operations.deploy': {
        id: 'operations.deploy',
        initialState: 'ReleaseCandidateSelected',
        states: {
            ReleaseCandidateSelected: { on: { START_PREFLIGHT: 'PreflightChecks' } },
            PreflightChecks: {
                on: {
                    CHECKS_PASS: 'AwaitingApproval',
                    CHECKS_FAIL: 'Failed'
                }
            },
            AwaitingApproval: {
                on: { APPROVE: 'Deploying', REJECT: 'Failed' },
                guards: ['deploy.approve']
            },
            Deploying: {
                on: {
                    SUCCESS: 'Monitoring',
                    FAILURE: 'RollbackDeploying'
                }
            },
            Monitoring: {
                on: {
                    STABLE: 'Succeeded',
                    ALERT: 'RollbackDeploying'
                }
            },
            RollbackDeploying: {
                on: {
                    ROLLBACK_COMPLETE: 'RolledBack',
                    ROLLBACK_FAIL: 'Failed'
                }
            },
            Succeeded: { on: {} },
            RolledBack: { on: {} },
            Failed: { on: {} }
        }
    },
    'security.scan': {
        id: 'security.scan',
        initialState: 'ScanInitiated',
        states: {
            ScanInitiated: { on: { SCAN_COMPLETE: 'ReviewingFindings' } },
            ReviewingFindings: {
                on: {
                    MARK_FALSE_POSITIVE: 'ReviewingFindings',
                    CREATE_TICKET: 'RemediationPlanned',
                    IGNORE: 'RiskAccepted'
                },
                guards: ['security.review']
            },
            RemediationPlanned: { on: { REMEDIATION_COMPLETE: 'VerifyFix' } },
            VerifyFix: {
                on: {
                    FIX_VERIFIED: 'Resolved',
                    FIX_FAILED: 'RemediationPlanned'
                }
            },
            RiskAccepted: { on: {} },
            Resolved: { on: {} }
        }
    },
    'quality.test': {
        id: 'quality.test',
        initialState: 'TestRunInitiated',
        states: {
            TestRunInitiated: { on: { RUN_COMPLETE: 'AnalyzingResults' } },
            AnalyzingResults: {
                on: {
                    ALL_PASS: 'Passed',
                    FAILURES_FOUND: 'TriagingFailures'
                }
            },
            TriagingFailures: {
                on: {
                    FILE_BUG: 'BugFiled',
                    MARK_FLAKY: 'AnalyzingResults'
                },
                guards: ['qa.triage']
            },
            BugFiled: { on: { BUG_FIXED: 'Retesting' } },
            Retesting: {
                on: {
                    PASS: 'Passed',
                    FAIL: 'TriagingFailures'
                }
            },
            Passed: { on: {} }
        }
    }
};
