/**
 * LaTeX/Math Canvas Element
 * 
 * @doc.type class
 * @doc.purpose Renders mathematical equations and LaTeX content on canvas
 * @doc.layer core
 * @doc.pattern ValueObject
 * 
 * Features:
 * - LaTeX equation rendering
 * - Display and inline math modes
 * - Equation numbering
 * - Color customization
 * - Common equation templates
 * - Export to LaTeX/MathML
 */

import { CanvasElement } from "./base.js";
import type { BaseElementProps, CanvasElementType } from "../types/index.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";

/**
 * Math display mode
 */
export type MathDisplayMode = "inline" | "display" | "block";

/**
 * Math element properties
 */
export interface LatexElementProps extends BaseElementProps {
  /** LaTeX expression */
  latex: string;
  /** Display mode */
  displayMode?: MathDisplayMode;
  /** Font size */
  fontSize?: number;
  /** Text color */
  color?: string;
  /** Background color */
  backgroundColor?: string;
  /** Border radius */
  borderRadius?: number;
  /** Padding */
  padding?: number;
  /** Show equation number */
  numbered?: boolean;
  /** Equation number */
  equationNumber?: number;
  /** Error message if parsing fails */
  errorMessage?: string;
  /** Placeholder text when empty */
  placeholder?: string;
  /** Whether to show border */
  showBorder?: boolean;
  /** Border color */
  borderColor?: string;
}

/**
 * Common math symbol mappings for simple rendering
 */
const MATH_SYMBOLS: Record<string, string> = {
  "\\alpha": "α",
  "\\beta": "β",
  "\\gamma": "γ",
  "\\delta": "δ",
  "\\epsilon": "ε",
  "\\zeta": "ζ",
  "\\eta": "η",
  "\\theta": "θ",
  "\\iota": "ι",
  "\\kappa": "κ",
  "\\lambda": "λ",
  "\\mu": "μ",
  "\\nu": "ν",
  "\\xi": "ξ",
  "\\pi": "π",
  "\\rho": "ρ",
  "\\sigma": "σ",
  "\\tau": "τ",
  "\\upsilon": "υ",
  "\\phi": "φ",
  "\\chi": "χ",
  "\\psi": "ψ",
  "\\omega": "ω",
  "\\Gamma": "Γ",
  "\\Delta": "Δ",
  "\\Theta": "Θ",
  "\\Lambda": "Λ",
  "\\Xi": "Ξ",
  "\\Pi": "Π",
  "\\Sigma": "Σ",
  "\\Phi": "Φ",
  "\\Psi": "Ψ",
  "\\Omega": "Ω",
  "\\infty": "∞",
  "\\partial": "∂",
  "\\nabla": "∇",
  "\\sum": "∑",
  "\\prod": "∏",
  "\\int": "∫",
  "\\oint": "∮",
  "\\sqrt": "√",
  "\\pm": "±",
  "\\mp": "∓",
  "\\times": "×",
  "\\div": "÷",
  "\\cdot": "·",
  "\\leq": "≤",
  "\\geq": "≥",
  "\\neq": "≠",
  "\\approx": "≈",
  "\\equiv": "≡",
  "\\propto": "∝",
  "\\forall": "∀",
  "\\exists": "∃",
  "\\in": "∈",
  "\\notin": "∉",
  "\\subset": "⊂",
  "\\supset": "⊃",
  "\\cup": "∪",
  "\\cap": "∩",
  "\\emptyset": "∅",
  "\\rightarrow": "→",
  "\\leftarrow": "←",
  "\\Rightarrow": "⇒",
  "\\Leftarrow": "⇐",
  "\\leftrightarrow": "↔",
  "\\Leftrightarrow": "⇔",
  "\\to": "→",
  "\\gets": "←",
  "\\mapsto": "↦",
  "\\ldots": "…",
  "\\cdots": "⋯",
  "\\vdots": "⋮",
  "\\ddots": "⋱",
  "\\therefore": "∴",
  "\\because": "∵",
  "\\angle": "∠",
  "\\perp": "⊥",
  "\\parallel": "∥",
  "\\triangle": "△",
  "\\square": "□",
  "\\diamond": "◇",
  "\\star": "⋆",
  "\\circ": "∘",
  "\\bullet": "•",
  "\\hbar": "ℏ",
  "\\ell": "ℓ",
  "\\Re": "ℜ",
  "\\Im": "ℑ",
  "\\aleph": "ℵ",
  "\\wp": "℘",
};

/**
 * LaTeX/Math Canvas Element
 */
export class LatexElement extends CanvasElement {
  latex: string;
  displayMode: MathDisplayMode;
  fontSize: number;
  color: string;
  backgroundColor: string;
  borderRadius: number;
  padding: number;
  numbered: boolean;
  equationNumber: number;
  errorMessage: string;
  placeholder: string;
  showBorder: boolean;
  borderColor: string;

  // Cached rendered content
  private cachedLatex: string = "";
  private cachedRenderedText: string = "";

  constructor(props: LatexElementProps) {
    super(props);
    this.latex = props.latex || "";
    this.displayMode = props.displayMode || "display";
    this.fontSize = props.fontSize || 18;
    this.color = props.color || themeManager.getTheme().colors.text.primary;
    this.backgroundColor = props.backgroundColor || "transparent";
    this.borderRadius = props.borderRadius || 4;
    this.padding = props.padding || 12;
    this.numbered = props.numbered || false;
    this.equationNumber = props.equationNumber || 1;
    this.errorMessage = props.errorMessage || "";
    this.placeholder = props.placeholder || "Enter LaTeX equation...";
    this.showBorder = props.showBorder || false;
    this.borderColor = props.borderColor || themeManager.getTheme().colors.border.light;
  }

  get type(): CanvasElementType {
    return "latex";
  }

  /**
   * Simple LaTeX to Unicode conversion
   * Note: For production, use a proper LaTeX renderer like KaTeX or MathJax
   */
  private latexToUnicode(latex: string): string {
    if (this.cachedLatex === latex) {
      return this.cachedRenderedText;
    }

    let result = latex;

    // Replace Greek letters and symbols
    for (const [cmd, symbol] of Object.entries(MATH_SYMBOLS)) {
      result = result.replace(new RegExp(cmd.replace(/\\/g, "\\\\") + "(?![a-zA-Z])", "g"), symbol);
    }

    // Handle fractions: \frac{a}{b} -> a/b
    result = result.replace(/\\frac\{([^}]+)\}\{([^}]+)\}/g, "($1)/($2)");

    // Handle superscripts: ^{x} or ^x
    result = result.replace(/\^{([^}]+)}/g, (_, exp) => this.toSuperscript(exp));
    result = result.replace(/\^(\d)/g, (_, d) => this.toSuperscript(d));

    // Handle subscripts: _{x} or _x
    result = result.replace(/_{([^}]+)}/g, (_, sub) => this.toSubscript(sub));
    result = result.replace(/_(\d)/g, (_, d) => this.toSubscript(d));

    // Handle sqrt: \sqrt{x} -> √(x)
    result = result.replace(/\\sqrt\{([^}]+)\}/g, "√($1)");

    // Remove remaining braces and whitespace commands
    result = result.replace(/\\[,;:!]/g, " ");
    result = result.replace(/[{}]/g, "");
    result = result.replace(/\\\s/g, " ");

    // Clean up multiple spaces
    result = result.replace(/\s+/g, " ").trim();

    this.cachedLatex = latex;
    this.cachedRenderedText = result;

    return result;
  }

  /**
   * Convert to superscript Unicode
   */
  private toSuperscript(text: string): string {
    const superscripts: Record<string, string> = {
      "0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴",
      "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹",
      "+": "⁺", "-": "⁻", "=": "⁼", "(": "⁽", ")": "⁾",
      "n": "ⁿ", "i": "ⁱ",
    };
    
    return text.split("").map(c => superscripts[c] || `^${c}`).join("");
  }

  /**
   * Convert to subscript Unicode
   */
  private toSubscript(text: string): string {
    const subscripts: Record<string, string> = {
      "0": "₀", "1": "₁", "2": "₂", "3": "₃", "4": "₄",
      "5": "₅", "6": "₆", "7": "₇", "8": "₈", "9": "₉",
      "+": "₊", "-": "₋", "=": "₌", "(": "₍", ")": "₎",
      "a": "ₐ", "e": "ₑ", "i": "ᵢ", "o": "ₒ", "x": "ₓ",
    };
    
    return text.split("").map(c => subscripts[c] || `_${c}`).join("");
  }

  render(ctx: CanvasRenderingContext2D, _viewport: unknown): void {
    const bounds = this.getBounds();
    const { x, y, w, h } = bounds;

    // Background
    if (this.backgroundColor !== "transparent") {
      ctx.fillStyle = this.backgroundColor;
      this.roundRect(ctx, x, y, w, h, this.borderRadius);
      ctx.fill();
    }

    // Border
    if (this.showBorder) {
      ctx.strokeStyle = this.borderColor;
      ctx.lineWidth = 1;
      this.roundRect(ctx, x, y, w, h, this.borderRadius);
      ctx.stroke();
    }

    // Render content
    const contentX = x + this.padding;
    const contentY = y + this.padding;
    const contentWidth = w - this.padding * 2;
    const contentHeight = h - this.padding * 2;

    if (this.errorMessage) {
      // Show error
      ctx.fillStyle = "#ef4444";
      ctx.font = `12px monospace`;
      ctx.textBaseline = "top";
      ctx.fillText(`Error: ${this.errorMessage}`, contentX, contentY);
    } else if (!this.latex) {
      // Show placeholder
      ctx.fillStyle = themeManager.getTheme().colors.text.muted;
      ctx.font = `italic ${this.fontSize}px serif`;
      ctx.textBaseline = "middle";
      ctx.textAlign = this.displayMode === "display" ? "center" : "left";
      
      const textX = this.displayMode === "display" ? x + w / 2 : contentX;
      ctx.fillText(this.placeholder, textX, y + h / 2);
    } else {
      // Render equation
      const renderedText = this.latexToUnicode(this.latex);
      
      ctx.fillStyle = this.color;
      ctx.font = `${this.fontSize}px "Times New Roman", serif`;
      ctx.textBaseline = "middle";
      
      if (this.displayMode === "display") {
        ctx.textAlign = "center";
        ctx.fillText(renderedText, x + w / 2, y + h / 2);
      } else {
        ctx.textAlign = "left";
        ctx.fillText(renderedText, contentX, y + h / 2);
      }

      // Equation number
      if (this.numbered) {
        ctx.fillStyle = themeManager.getTheme().colors.text.muted;
        ctx.font = `${this.fontSize * 0.8}px serif`;
        ctx.textAlign = "right";
        ctx.fillText(`(${this.equationNumber})`, x + w - this.padding, y + h / 2);
      }
    }
  }

  /**
   * Helper to draw rounded rectangles
   */
  private roundRect(
    ctx: CanvasRenderingContext2D,
    x: number,
    y: number,
    w: number,
    h: number,
    r: number
  ): void {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  includesPoint(px: number, py: number): boolean {
    const bounds = this.getBounds();
    return (
      px >= bounds.x &&
      px <= bounds.x + bounds.w &&
      py >= bounds.y &&
      py <= bounds.y + bounds.h
    );
  }

  /**
   * Set LaTeX expression
   */
  setLatex(latex: string): void {
    this.latex = latex;
    this.errorMessage = "";
    // Invalidate cache
    this.cachedLatex = "";
  }

  /**
   * Get raw LaTeX
   */
  toLatex(): string {
    if (this.displayMode === "display") {
      return `$$${this.latex}$$`;
    }
    return `$${this.latex}$`;
  }

  /**
   * Export as MathML (basic implementation)
   */
  toMathML(): string {
    const rendered = this.latexToUnicode(this.latex);
    return `<math xmlns="http://www.w3.org/1998/Math/MathML">
  <mrow>
    <mi>${rendered}</mi>
  </mrow>
</math>`;
  }

  /**
   * Common equation templates
   */
  static TEMPLATES = {
    quadratic: "x = \\frac{-b \\pm \\sqrt{b^2 - 4ac}}{2a}",
    pythagorean: "a^2 + b^2 = c^2",
    euler: "e^{i\\pi} + 1 = 0",
    integral: "\\int_a^b f(x) dx",
    derivative: "\\frac{df}{dx}",
    limit: "\\lim_{x \\to \\infty} f(x)",
    sum: "\\sum_{i=1}^{n} x_i",
    product: "\\prod_{i=1}^{n} x_i",
    matrix2x2: "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}",
    binomial: "\\binom{n}{k} = \\frac{n!}{k!(n-k)!}",
    gauss: "f(x) = \\frac{1}{\\sigma\\sqrt{2\\pi}} e^{-\\frac{(x-\\mu)^2}{2\\sigma^2}}",
    einstein: "E = mc^2",
    schrodinger: "i\\hbar\\frac{\\partial}{\\partial t}\\Psi = \\hat{H}\\Psi",
    maxwell: "\\nabla \\cdot \\mathbf{E} = \\frac{\\rho}{\\epsilon_0}",
  };

  /**
   * Create from template
   */
  static fromTemplate(
    templateName: keyof typeof LatexElement.TEMPLATES,
    baseProps: BaseElementProps
  ): LatexElement {
    return new LatexElement({
      ...baseProps,
      latex: LatexElement.TEMPLATES[templateName],
    });
  }

  /**
   * Create inline math
   */
  static inline(latex: string, baseProps: BaseElementProps): LatexElement {
    return new LatexElement({
      ...baseProps,
      latex,
      displayMode: "inline",
      fontSize: 14,
      padding: 4,
    });
  }

  /**
   * Create display math (centered, larger)
   */
  static display(latex: string, baseProps: BaseElementProps): LatexElement {
    return new LatexElement({
      ...baseProps,
      latex,
      displayMode: "display",
      fontSize: 20,
      padding: 16,
    });
  }
}
