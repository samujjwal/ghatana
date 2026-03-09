/**
 * Email Service Mock for Testing
 *
 * Provides mock email functionality to test email-related features
 */

export interface SentEmail {
  to: string;
  subject: string;
  html: string;
  text?: string;
  from?: string;
  timestamp: Date;
}

/**
 * Email service mock
 */
class EmailServiceMock {
  private sentEmails: SentEmail[] = [];

  /**
   * Mock send email function
   */
  async sendEmail(
    to: string,
    subject: string,
    html: string,
    text?: string
  ): Promise<boolean> {
    this.sentEmails.push({
      to,
      subject,
      html,
      text,
      timestamp: new Date(),
    });
    return true;
  }

  /**
   * Mock send verification email
   */
  async sendVerificationEmail(email: string, token: string): Promise<boolean> {
    return this.sendEmail(
      email,
      'Verify your email',
      `<p>Click here to verify: http://example.com/verify?token=${token}</p>`,
      `Click here to verify: http://example.com/verify?token=${token}`
    );
  }

  /**
   * Mock send password reset email
   */
  async sendPasswordResetEmail(email: string, token: string): Promise<boolean> {
    return this.sendEmail(
      email,
      'Reset your password',
      `<p>Click here to reset: http://example.com/reset?token=${token}</p>`,
      `Click here to reset: http://example.com/reset?token=${token}`
    );
  }

  /**
   * Get all sent emails
   */
  getSentEmails(): SentEmail[] {
    return this.sentEmails;
  }

  /**
   * Get last sent email
   */
  getLastSentEmail(): SentEmail | undefined {
    return this.sentEmails[this.sentEmails.length - 1];
  }

  /**
   * Find emails by recipient
   */
  findEmailsByRecipient(email: string): SentEmail[] {
    return this.sentEmails.filter(e => e.to === email);
  }

  /**
   * Clear all sent emails
   */
  clearSentEmails(): void {
    this.sentEmails = [];
  }

  /**
   * Check if email was sent
   */
  wasEmailSent(to: string, subjectContains: string): boolean {
    return this.sentEmails.some(
      e => e.to === to && e.subject.includes(subjectContains)
    );
  }
}

export const emailServiceMock = new EmailServiceMock();

export default emailServiceMock;
