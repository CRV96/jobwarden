/**
 * Capture: the REST API of the browser extension. It captures job postings, looks up whether a page is already
 * tracked, and moves an application back to bookmarked or removes it; says which version the server runs, and the
 * latest released; and gives the extension's Easy Apply run the answers forms ask for, keeping the ones the user
 * types during a run.
 * A thin adapter that turns these into calls on the jobs, identity, updates, apply and cv modules; it owns no tables
 * and holds no domain logic. No other module depends on it.
 */
package com.jobwarden.capture;
