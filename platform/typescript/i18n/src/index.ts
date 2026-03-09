/**
 * @fileoverview Shared i18n framework for Ghatana React applications.
 *
 * Provides a pre-configured i18next instance with:
 * - Browser language detection
 * - HTTP backend for loading translations
 * - React integration via react-i18next
 * - Namespace support for per-product translations
 *
 * @doc.type module
 * @doc.purpose Shared internationalization framework
 * @doc.layer platform
 */

export { initI18n, type I18nConfig } from './init';
export { I18nProvider } from './I18nProvider';
export { useTranslation, Trans } from 'react-i18next';
