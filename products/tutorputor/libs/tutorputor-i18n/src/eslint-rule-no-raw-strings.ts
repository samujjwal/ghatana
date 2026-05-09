/**
 * ESLint Rule: No Raw Strings
 *
 * Detects user-facing strings that should be in i18n catalogs.
 * Suggests using the i18n translation function instead.
 *
 * @doc.type rule
 * @doc.purpose Enforce i18n for user-facing strings
 * @doc.layer platform
 * @doc.pattern LintRule
 */

import { Rule } from "eslint";
import { AST } from "eslint";

const rule: Rule.RuleModule = {
  meta: {
    type: "suggestion",
    docs: {
      description: "Enforce i18n for user-facing strings",
      category: "Best Practices",
      recommended: false,
    },
    schema: [
      {
        type: "object",
        properties: {
          allowedWords: {
            type: "array",
            items: { type: "string" },
            description: "Words that are allowed as raw strings",
          },
          ignorePatterns: {
            type: "array",
            items: { type: "string" },
            description: "Patterns to ignore (regex)",
          },
        },
        additionalProperties: false,
      },
    ],
    messages: {
      useI18n: "Use i18n translation function instead of raw string: '{{string}}'",
      useI18nWithSuggestion: "Use i18n translation function instead: t('{{key}}')",
    },
  },
  create(context: Rule.RuleContext) {
    const options = context.options[0] || {};
    const allowedWords = new Set(options.allowedWords || [
      // Common technical terms
      "div", "span", "button", "input", "form", "a", "img", "svg",
      "path", "circle", "rect", "line", "polygon",
      // Common attributes
      "id", "class", "className", "style", "href", "src", "alt", "type",
      "name", "value", "placeholder", "disabled", "required",
      // React specific
      "key", "ref", "children", "dangerouslySetInnerHTML",
      // Data attributes
      "data-testid", "data-cy",
      // ARIA attributes
      "aria-label", "aria-hidden", "aria-expanded", "aria-pressed",
      // Common units
      "px", "em", "rem", "%", "vh", "vw",
      // Common colors
      "red", "green", "blue", "black", "white", "transparent",
      // URLs and protocols
      "http", "https", "ftp", "mailto", "tel",
    ]);
    const ignorePatterns = options.ignorePatterns || [
      // URLs
      /^https?:\/\//,
      /^mailto:/,
      /^tel:/,
      // Data attributes
      /^data-[a-z-]+$/,
      // ARIA attributes
      /^aria-[a-z-]+$/,
      // CSS units
      /^\d+(px|em|rem|%|vh|vw|s|ms)$/,
      // IDs and classes
      /^[a-z][a-z0-9_-]*$/,
      // Technical strings
      /^[A-Z_][A-Z0-9_]*$/, // Constants
      /^[a-z][a-z0-9]*$/, // Short identifiers
      // Empty strings
      /^$/,
      // Single character
      /^.$/,
    ];

    function isUserFacingString(value: string): boolean {
      // Check if string matches ignore patterns
      for (const pattern of ignorePatterns) {
        if (new RegExp(pattern).test(value)) {
          return false;
        }
      }

      // Check if all words are allowed
      const words = value.split(/\s+/);
      const allWordsAllowed = words.every((word) => allowedWords.has(word.toLowerCase()));
      if (allWordsAllowed) {
        return false;
      }

      // Check if string is likely user-facing
      const hasLetters = /[a-zA-Z]/.test(value);
      const hasMultipleWords = words.length > 1;
      const isSentence = /^[A-Z]/.test(value) && /[.!?]$/.test(value);
      const isPhrase = words.length > 2;

      return hasLetters && (hasMultipleWords || isSentence || isPhrase);
    }

    function generateTranslationKey(value: string): string {
      // Generate a reasonable translation key from the string
      const normalized = value
        .toLowerCase()
        .replace(/[^a-z0-9\s]/g, "")
        .replace(/\s+/g, ".");
      return `common.${normalized}`;
    }

    return {
      // Check JSX text content
      JSXText(node: AST.JSXText) {
        const value = node.value.trim();
        if (value && isUserFacingString(value)) {
          const key = generateTranslationKey(value);
          context.report({
            node,
            messageId: "useI18nWithSuggestion",
            data: { string: value, key },
            fix: (fixer) => {
              return fixer.replaceText(node, `{t('${key}')}`);
            },
          });
        }
      },

      // Check string literals in JSX attributes
      JSXAttribute(node: AST.JSXAttribute) {
        if (node.value?.type === "Literal" && typeof node.value.value === "string") {
          const value = node.value.value;
          const attrName = node.name.name;

          // Skip certain attributes
          const skipAttributes = ["id", "className", "style", "href", "src", "alt", "type", "name", "value", "placeholder"];
          if (skipAttributes.includes(attrName)) {
            return;
          }

          if (isUserFacingString(value)) {
            const key = generateTranslationKey(value);
            context.report({
              node: node.value,
              messageId: "useI18nWithSuggestion",
              data: { string: value, key },
              fix: (fixer) => {
                return fixer.replaceText(node.value, `{t('${key}')}`);
              },
            });
          }
        }
      },

      // Check string literals in template literals
      TemplateLiteral(node: AST.TemplateLiteral) {
        // Check if template literal is mostly text (not many expressions)
        if (node.expressions.length === 0 && node.quasis.length === 1) {
          const value = node.quasis[0].value.cooked.trim();
          if (value && isUserFacingString(value)) {
            const key = generateTranslationKey(value);
            context.report({
              node,
              messageId: "useI18nWithSuggestion",
              data: { string: value, key },
            });
          }
        }
      },

      // Check string literals in certain contexts
      Literal(node: AST.Literal) {
        if (typeof node.value === "string" && isUserFacingString(node.value)) {
          // Only report if parent is likely a user-facing context
          const parent = node.parent;
          if (!parent) return;

          // Skip if in certain contexts
          if (
            parent.type === "VariableDeclarator" ||
            parent.type === "Property" ||
            parent.type === "ObjectExpression" ||
            parent.type === "ArrayExpression" ||
            parent.type === "CallExpression" ||
            parent.type === "ReturnStatement"
          ) {
            return;
          }

          const key = generateTranslationKey(node.value);
          context.report({
            node,
            messageId: "useI18nWithSuggestion",
            data: { string: node.value, key },
          });
        }
      },
    };
  },
};

export default rule;
