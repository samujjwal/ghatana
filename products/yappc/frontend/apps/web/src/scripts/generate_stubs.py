import os

base_path = "/home/samujjwal/Developments/ghatana/products/yappc/frontend/apps/web/src/pages"
stub_component_path = "../../components/common/StubPage"

files_to_create = {
    "auth": [
        "LoginPage.tsx", "RegisterPage.tsx", "ForgotPasswordPage.tsx", 
        "ResetPasswordPage.tsx", "SSOCallbackPage.tsx"
    ],
    ".": [ # Root of pages
        "LandingPage.tsx", "PricingPage.tsx"
    ],
    "dashboard": [
        "ProjectsPage.tsx"
    ],
    "settings": [
        "SettingsPage.tsx", "ProfilePage.tsx"
    ],
    "bootstrapping": [
        "TemplateGalleryPage.tsx", "ProjectPreviewPage.tsx"
    ],
    "initialization": [
        "SetupWizardPage.tsx", "InfrastructureConfigPage.tsx", 
        "EnvironmentSetupPage.tsx", "TeamInvitePage.tsx", "SetupProgressPage.tsx"
    ],
    "development": [
        "EpicsPage.tsx", "PullRequestsPage.tsx", "PullRequestDetailPage.tsx",
        "VelocityPage.tsx", "CodeReviewPage.tsx"
    ],
    "operations": [
        "IncidentsPage.tsx", "WarRoomPage.tsx", "DashboardsPage.tsx",
        "DashboardEditorPage.tsx", "RunbooksPage.tsx", "RunbookDetailPage.tsx",
        "OnCallPage.tsx", "ServiceMapPage.tsx", "PostmortemsPage.tsx"
    ],
    "collaboration": [
        "TeamHubPage.tsx", "CalendarPage.tsx", "ArticlePage.tsx", 
        "ArticleEditorPage.tsx", "StandupsPage.tsx", "RetrosPage.tsx",
        "MessagesPage.tsx", "ChannelPage.tsx", "DirectMessagePage.tsx",
        "GoalsPage.tsx", "ActivityFeedPage.tsx"
    ],
    "security": [
        "VulnerabilityDetailPage.tsx", "SecurityScansPage.tsx", "ScanResultsPage.tsx",
        "ComplianceFrameworkPage.tsx", "SecretsPage.tsx", "PoliciesPage.tsx",
        "PolicyDetailPage.tsx", "SecurityAlertsPage.tsx", "ThreatModelPage.tsx"
    ],
    "admin": [
        "AdminDashboardPage.tsx", "UsersPage.tsx", "TeamsPage.tsx",
        "BillingPage.tsx", "AuditPage.tsx", "IntegrationsPage.tsx"
    ]
}

def create_stub_file(path, name):
    component_name = name.replace(".tsx", "")
    content = f"""import React from 'react';
import StubPage from '{stub_component_path}';

const {component_name}: React.FC = () => {{
  return <StubPage title="{component_name}" />;
}};

export default {component_name};
"""
    with open(path, "w") as f:
        f.write(content)
    print(f"Created {path}")

for folder, files in files_to_create.items():
    folder_path = os.path.join(base_path, folder)
    if not os.path.exists(folder_path):
        os.makedirs(folder_path)
    
    for file_name in files:
        file_path = os.path.join(folder_path, file_name)
        if not os.path.exists(file_path):
            create_stub_file(file_path, file_name)
        else:
            print(f"Skipped {file_path} (Exists)")
