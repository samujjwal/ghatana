/**
 * E2E Page Objects - Bootstrapping Pages
 *
 * @description Page object model for bootstrapping-related pages.
 * Covers the entire bootstrapping flow from idea input to canvas export.
 *
 * @doc.type test-page-object
 * @doc.purpose E2E testing page objects
 * @doc.layer e2e
 * @doc.phase bootstrapping
 */

import { Page, Locator, expect } from '@playwright/test';

// =============================================================================
// Start Project Page
// =============================================================================

export class StartProjectPage {
  readonly page: Page;
  readonly ideaInput: Locator;
  readonly submitButton: Locator;
  readonly voiceInputButton: Locator;
  readonly uploadCard: Locator;
  readonly importCard: Locator;
  readonly templateCard: Locator;
  readonly savedSessionsList: Locator;
  readonly industrySelect: Locator;
  readonly timelineSelect: Locator;

  constructor(page: Page) {
    this.page = page;
    this.ideaInput = page.getByPlaceholder(/what would you like to build|describe your idea/i);
    this.submitButton = page.getByRole('button', { name: /let's go|start|begin/i });
    this.voiceInputButton = page.getByRole('button', { name: /voice|microphone/i });
    this.uploadCard = page.getByRole('button', { name: /upload documents/i });
    this.importCard = page.getByRole('button', { name: /import from url/i });
    this.templateCard = page.getByRole('button', { name: /use template/i });
    this.savedSessionsList = page.locator('[data-testid="saved-sessions"]');
    this.industrySelect = page.getByLabel(/industry/i);
    this.timelineSelect = page.getByLabel(/timeline/i);
  }

  async goto() {
    await this.page.goto('/start');
  }

  async enterIdea(idea: string) {
    await this.ideaInput.fill(idea);
  }

  async submitIdea() {
    await this.submitButton.click();
  }

  async startWithIdea(idea: string) {
    await this.enterIdea(idea);
    await this.submitIdea();
  }

  async selectIndustry(industry: string) {
    await this.industrySelect.selectOption(industry);
  }

  async selectTimeline(timeline: string) {
    await this.timelineSelect.selectOption(timeline);
  }

  async goToUpload() {
    await this.uploadCard.click();
    await expect(this.page).toHaveURL(/\/start\/upload/);
  }

  async goToImport() {
    await this.importCard.click();
    await expect(this.page).toHaveURL(/\/start\/import/);
  }

  async goToTemplates() {
    await this.templateCard.click();
    await expect(this.page).toHaveURL(/\/start\/template/);
  }

  async getSavedSessionCount(): Promise<number> {
    const sessions = this.savedSessionsList.locator('[data-testid="saved-session-card"]');
    return await sessions.count();
  }

  async resumeSession(index: number) {
    const session = this.savedSessionsList
      .locator('[data-testid="saved-session-card"]')
      .nth(index);
    await session.getByRole('button', { name: /resume/i }).click();
  }
}

// =============================================================================
// Upload Documents Page
// =============================================================================

export class UploadDocsPage {
  readonly page: Page;
  readonly fileInput: Locator;
  readonly dropZone: Locator;
  readonly uploadedFilesList: Locator;
  readonly continueButton: Locator;
  readonly removeFileButton: (index: number) => Locator;

  constructor(page: Page) {
    this.page = page;
    this.fileInput = page.locator('input[type="file"]');
    this.dropZone = page.getByTestId('drop-zone');
    this.uploadedFilesList = page.getByTestId('uploaded-files');
    this.continueButton = page.getByRole('button', { name: /continue|next|proceed/i });
    this.removeFileButton = (index: number) =>
      this.uploadedFilesList.locator('[data-testid="uploaded-file"]').nth(index)
        .getByRole('button', { name: /remove|delete/i });
  }

  async goto() {
    await this.page.goto('/start/upload');
  }

  async uploadFile(filePath: string) {
    await this.fileInput.setInputFiles(filePath);
  }

  async uploadFiles(filePaths: string[]) {
    await this.fileInput.setInputFiles(filePaths);
  }

  async getUploadedFileCount(): Promise<number> {
    return await this.uploadedFilesList
      .locator('[data-testid="uploaded-file"]')
      .count();
  }

  async removeFile(index: number) {
    await this.removeFileButton(index).click();
  }

  async continue() {
    await this.continueButton.click();
  }
}

// =============================================================================
// Template Selection Page
// =============================================================================

export class TemplateSelectionPage {
  readonly page: Page;
  readonly searchInput: Locator;
  readonly categoryFilter: Locator;
  readonly templateGrid: Locator;
  readonly selectedTemplate: Locator;
  readonly useTemplateButton: Locator;
  readonly previewButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.searchInput = page.getByPlaceholder(/search templates/i);
    this.categoryFilter = page.getByTestId('category-filter');
    this.templateGrid = page.getByTestId('template-grid');
    this.selectedTemplate = page.locator('[data-selected="true"]');
    this.useTemplateButton = page.getByRole('button', { name: /use template|select/i });
    this.previewButton = page.getByRole('button', { name: /preview/i });
  }

  async goto() {
    await this.page.goto('/start/template');
  }

  async searchTemplates(query: string) {
    await this.searchInput.fill(query);
  }

  async selectCategory(category: string) {
    await this.categoryFilter.getByRole('button', { name: category }).click();
  }

  async selectTemplate(name: string) {
    await this.templateGrid.getByText(name).click();
  }

  async getTemplateCount(): Promise<number> {
    return await this.templateGrid.locator('[data-testid="template-card"]').count();
  }

  async useSelectedTemplate() {
    await this.useTemplateButton.click();
  }

  async previewSelectedTemplate() {
    await this.previewButton.click();
  }
}

// =============================================================================
// Import from URL Page
// =============================================================================

export class ImportFromURLPage {
  readonly page: Page;
  readonly urlInput: Locator;
  readonly importButton: Locator;
  readonly importProgress: Locator;
  readonly importStatus: Locator;
  readonly githubTab: Locator;
  readonly figmaTab: Locator;
  readonly notionTab: Locator;

  constructor(page: Page) {
    this.page = page;
    this.urlInput = page.getByPlaceholder(/enter url|paste url/i);
    this.importButton = page.getByRole('button', { name: /import/i });
    this.importProgress = page.getByTestId('import-progress');
    this.importStatus = page.getByTestId('import-status');
    this.githubTab = page.getByRole('tab', { name: /github/i });
    this.figmaTab = page.getByRole('tab', { name: /figma/i });
    this.notionTab = page.getByRole('tab', { name: /notion/i });
  }

  async goto() {
    await this.page.goto('/start/import');
  }

  async enterUrl(url: string) {
    await this.urlInput.fill(url);
  }

  async import() {
    await this.importButton.click();
  }

  async importFromUrl(url: string) {
    await this.enterUrl(url);
    await this.import();
  }

  async selectGitHub() {
    await this.githubTab.click();
  }

  async selectFigma() {
    await this.figmaTab.click();
  }

  async selectNotion() {
    await this.notionTab.click();
  }

  async waitForImportComplete() {
    await expect(this.importStatus).toContainText(/complete|success/i, { timeout: 30000 });
  }
}

// =============================================================================
// Resume Session Page
// =============================================================================

export class ResumeSessionPage {
  readonly page: Page;
  readonly sessionsList: Locator;
  readonly newProjectButton: Locator;
  readonly searchInput: Locator;
  readonly sortSelect: Locator;

  constructor(page: Page) {
    this.page = page;
    this.sessionsList = page.getByTestId('sessions-list');
    this.newProjectButton = page.getByRole('button', { name: /new project/i });
    this.searchInput = page.getByPlaceholder(/search sessions/i);
    this.sortSelect = page.getByLabel(/sort by/i);
  }

  async goto() {
    await this.page.goto('/bootstrap/resume');
  }

  async getSessionCount(): Promise<number> {
    return await this.sessionsList.locator('[data-testid="session-card"]').count();
  }

  async resumeSession(name: string) {
    await this.sessionsList.getByText(name).click();
    await this.page.getByRole('button', { name: /resume/i }).click();
  }

  async deleteSession(name: string) {
    const card = this.sessionsList.locator('[data-testid="session-card"]').filter({
      hasText: name,
    });
    await card.getByRole('button', { name: /delete|remove/i }).click();
    // Confirm deletion
    await this.page.getByRole('button', { name: /confirm|yes/i }).click();
  }

  async exportSession(name: string) {
    const card = this.sessionsList.locator('[data-testid="session-card"]').filter({
      hasText: name,
    });
    await card.getByRole('button', { name: /export/i }).click();
  }

  async searchSessions(query: string) {
    await this.searchInput.fill(query);
  }

  async sortBy(option: string) {
    await this.sortSelect.selectOption(option);
  }
}

// =============================================================================
// Bootstrap Session Page (Main Split View)
// =============================================================================

export class BootstrapSessionPage {
  readonly page: Page;
  // Phase progress
  readonly phaseProgress: Locator;
  readonly currentPhase: Locator;
  // Conversation panel
  readonly conversationPanel: Locator;
  readonly messageInput: Locator;
  readonly sendButton: Locator;
  readonly messageList: Locator;
  readonly aiTypingIndicator: Locator;
  // Canvas panel
  readonly canvasPanel: Locator;
  readonly canvasNodes: Locator;
  readonly canvasEdges: Locator;
  readonly canvasControls: Locator;
  // Actions
  readonly saveButton: Locator;
  readonly shareButton: Locator;
  readonly exportButton: Locator;
  readonly settingsButton: Locator;

  constructor(page: Page) {
    this.page = page;
    // Phase progress
    this.phaseProgress = page.getByTestId('phase-progress');
    this.currentPhase = page.getByTestId('current-phase');
    // Conversation panel
    this.conversationPanel = page.getByTestId('conversation-panel');
    this.messageInput = page.getByPlaceholder(/type your response|enter message/i);
    this.sendButton = page.getByRole('button', { name: /send/i });
    this.messageList = page.getByTestId('message-list');
    this.aiTypingIndicator = page.getByTestId('ai-typing');
    // Canvas panel
    this.canvasPanel = page.getByTestId('canvas-panel');
    this.canvasNodes = page.locator('.react-flow__node');
    this.canvasEdges = page.locator('.react-flow__edge');
    this.canvasControls = page.getByTestId('canvas-controls');
    // Actions
    this.saveButton = page.getByRole('button', { name: /save/i });
    this.shareButton = page.getByRole('button', { name: /share/i });
    this.exportButton = page.getByRole('button', { name: /export/i });
    this.settingsButton = page.getByRole('button', { name: /settings/i });
  }

  async goto(sessionId: string) {
    await this.page.goto(`/bootstrap/${sessionId}`);
  }

  async sendMessage(message: string) {
    await this.messageInput.fill(message);
    await this.sendButton.click();
  }

  async waitForAIResponse() {
    // Wait for typing indicator to appear then disappear
    await expect(this.aiTypingIndicator).toBeVisible({ timeout: 5000 });
    await expect(this.aiTypingIndicator).toBeHidden({ timeout: 60000 });
  }

  async getMessageCount(): Promise<number> {
    return await this.messageList.locator('[data-testid="message"]').count();
  }

  async getLastMessage(): Promise<string> {
    const messages = this.messageList.locator('[data-testid="message"]');
    const count = await messages.count();
    return await messages.nth(count - 1).textContent() || '';
  }

  async getCurrentPhase(): Promise<string> {
    return await this.currentPhase.textContent() || '';
  }

  async getNodeCount(): Promise<number> {
    return await this.canvasNodes.count();
  }

  async getEdgeCount(): Promise<number> {
    return await this.canvasEdges.count();
  }

  async selectOption(optionText: string) {
    await this.conversationPanel.getByRole('button', { name: optionText }).click();
  }

  async save() {
    await this.saveButton.click();
  }

  async share() {
    await this.shareButton.click();
  }

  async export() {
    await this.exportButton.click();
  }

  async openSettings() {
    await this.settingsButton.click();
  }

  async zoomIn() {
    await this.canvasControls.getByRole('button', { name: /zoom in/i }).click();
  }

  async zoomOut() {
    await this.canvasControls.getByRole('button', { name: /zoom out/i }).click();
  }

  async fitView() {
    await this.canvasControls.getByRole('button', { name: /fit view/i }).click();
  }
}

// =============================================================================
// Bootstrap Collaborate Page
// =============================================================================

export class BootstrapCollaboratePage {
  readonly page: Page;
  readonly collaboratorsList: Locator;
  readonly inviteButton: Locator;
  readonly inviteInput: Locator;
  readonly sendInviteButton: Locator;
  readonly cursorList: Locator;
  readonly commentButton: Locator;
  readonly commentsPanel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.collaboratorsList = page.getByTestId('collaborators-list');
    this.inviteButton = page.getByRole('button', { name: /invite/i });
    this.inviteInput = page.getByPlaceholder(/email address/i);
    this.sendInviteButton = page.getByRole('button', { name: /send invite/i });
    this.cursorList = page.getByTestId('cursor-list');
    this.commentButton = page.getByRole('button', { name: /comment/i });
    this.commentsPanel = page.getByTestId('comments-panel');
  }

  async goto(sessionId: string) {
    await this.page.goto(`/bootstrap/${sessionId}/collaborate`);
  }

  async inviteCollaborator(email: string) {
    await this.inviteButton.click();
    await this.inviteInput.fill(email);
    await this.sendInviteButton.click();
  }

  async getCollaboratorCount(): Promise<number> {
    return await this.collaboratorsList.locator('[data-testid="collaborator"]').count();
  }

  async openComments() {
    await this.commentButton.click();
    await expect(this.commentsPanel).toBeVisible();
  }

  async addComment(text: string) {
    await this.commentsPanel.getByPlaceholder(/add comment/i).fill(text);
    await this.commentsPanel.getByRole('button', { name: /post|submit/i }).click();
  }
}

// =============================================================================
// Bootstrap Review Page
// =============================================================================

export class BootstrapReviewPage {
  readonly page: Page;
  readonly validationStatus: Locator;
  readonly issuesList: Locator;
  readonly approvalButton: Locator;
  readonly requestChangesButton: Locator;
  readonly commentInput: Locator;
  readonly submitReviewButton: Locator;
  readonly approvalProgress: Locator;

  constructor(page: Page) {
    this.page = page;
    this.validationStatus = page.getByTestId('validation-status');
    this.issuesList = page.getByTestId('issues-list');
    this.approvalButton = page.getByRole('button', { name: /approve/i });
    this.requestChangesButton = page.getByRole('button', { name: /request changes/i });
    this.commentInput = page.getByPlaceholder(/add review comment/i);
    this.submitReviewButton = page.getByRole('button', { name: /submit review/i });
    this.approvalProgress = page.getByTestId('approval-progress');
  }

  async goto(sessionId: string) {
    await this.page.goto(`/bootstrap/${sessionId}/review`);
  }

  async getIssueCount(): Promise<number> {
    return await this.issuesList.locator('[data-testid="issue"]').count();
  }

  async approve() {
    await this.approvalButton.click();
  }

  async requestChanges(comment: string) {
    await this.requestChangesButton.click();
    await this.commentInput.fill(comment);
    await this.submitReviewButton.click();
  }

  async getApprovalStatus(): Promise<string> {
    return await this.approvalProgress.textContent() || '';
  }
}

// =============================================================================
// Bootstrap Export Page
// =============================================================================

export class BootstrapExportPage {
  readonly page: Page;
  readonly formatSelect: Locator;
  readonly qualitySelect: Locator;
  readonly includeBackgroundCheckbox: Locator;
  readonly includeMetadataCheckbox: Locator;
  readonly previewPanel: Locator;
  readonly exportButton: Locator;
  readonly downloadLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.formatSelect = page.getByTestId('format-select');
    this.qualitySelect = page.getByTestId('quality-select');
    this.includeBackgroundCheckbox = page.getByLabel(/include background/i);
    this.includeMetadataCheckbox = page.getByLabel(/include metadata/i);
    this.previewPanel = page.getByTestId('export-preview');
    this.exportButton = page.getByRole('button', { name: /export/i });
    this.downloadLink = page.getByRole('link', { name: /download/i });
  }

  async goto(sessionId: string) {
    await this.page.goto(`/bootstrap/${sessionId}/export`);
  }

  async selectFormat(format: 'png' | 'svg' | 'json' | 'pdf') {
    await this.formatSelect.getByRole('button', { name: format.toUpperCase() }).click();
  }

  async selectQuality(quality: 'low' | 'medium' | 'high' | 'maximum') {
    await this.qualitySelect.getByRole('button', { name: quality }).click();
  }

  async toggleBackground(checked: boolean) {
    if (checked) {
      await this.includeBackgroundCheckbox.check();
    } else {
      await this.includeBackgroundCheckbox.uncheck();
    }
  }

  async toggleMetadata(checked: boolean) {
    if (checked) {
      await this.includeMetadataCheckbox.check();
    } else {
      await this.includeMetadataCheckbox.uncheck();
    }
  }

  async export() {
    await this.exportButton.click();
  }

  async waitForDownload() {
    await expect(this.downloadLink).toBeVisible({ timeout: 30000 });
  }
}

// =============================================================================
// Bootstrap Complete Page
// =============================================================================

export class BootstrapCompletePage {
  readonly page: Page;
  readonly successMessage: Locator;
  readonly projectSummary: Locator;
  readonly goToProjectButton: Locator;
  readonly createAnotherButton: Locator;
  readonly shareButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.successMessage = page.getByText(/project created|bootstrap complete/i);
    this.projectSummary = page.getByTestId('project-summary');
    this.goToProjectButton = page.getByRole('button', { name: /go to project|view project/i });
    this.createAnotherButton = page.getByRole('button', { name: /create another|new project/i });
    this.shareButton = page.getByRole('button', { name: /share/i });
  }

  async goto(sessionId: string) {
    await this.page.goto(`/bootstrap/${sessionId}/complete`);
  }

  async goToProject() {
    await this.goToProjectButton.click();
  }

  async createAnother() {
    await this.createAnotherButton.click();
  }

  async share() {
    await this.shareButton.click();
  }
}
