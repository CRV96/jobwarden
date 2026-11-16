// ESLint, for the JavaScript the pages run (web/src/main/resources/static/js) and the browser extension's (extension/).
// `npm run lint`, which `./mvnw verify` runs too: any warning fails it. It runs from the repository's root, where both
// are, with this file as its config: the patterns below are from there.
import js from '@eslint/js';
import globals from 'globals';

export default [
	{
		ignores: ['**/node_modules/**', '**/target/**'],
	},
	js.configs.recommended,
	{
		rules: {
			curly: 'error',
			// `== null` means null or undefined, and says so
			eqeqeq: ['error', 'always', { null: 'ignore' }],
			'no-implicit-globals': 'error',
			'no-var': 'error',
			'prefer-const': 'error',
			// A caught error that's left unused says it's expected: `catch {}` or `catch (failed)` both read fine
			'no-unused-vars': ['error', { caughtErrors: 'none' }],
		},
	},
	{
		files: ['web/src/main/resources/static/js/**/*.js'],
		languageOptions: {
			sourceType: 'module',
			globals: { ...globals.browser, htmx: 'readonly' },
		},
	},
	{
		// Runs in <head> before the page paints, as a classic script
		files: ['web/src/main/resources/static/js/theme.js'],
		languageOptions: { sourceType: 'script' },
	},
	{
		files: ['extension/**/*.js'],
		languageOptions: {
			sourceType: 'module',
			globals: { ...globals.browser, ...globals.webextensions },
		},
	},
	{
		// Injected into the page they read (chrome.scripting.executeScript), as classic scripts
		files: ['extension/extract.js', 'extension/extract/**/*.js', 'extension/easyapply.js', 'extension/easyapply/**/*.js'],
		languageOptions: { sourceType: 'script' },
	},
];
