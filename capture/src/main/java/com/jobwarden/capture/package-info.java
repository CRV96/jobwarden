/**
 * Capture: the REST API of the browser extension. It captures job postings, looks up whether a page is already
 * tracked, and moves an application back to bookmarked or removes it.
 * A thin adapter that turns a capture into calls on the jobs and identity modules; it owns no tables
 * and holds no domain logic. No other module depends on it.
 */
package com.jobwarden.capture;
