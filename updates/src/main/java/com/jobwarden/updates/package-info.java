/**
 * Updates: the version of JobWarden that's running, and the latest one released on GitHub, so the pages and the
 * browser extension can say when there's a newer one to update to. Owns no table: the latest release is asked of
 * GitHub's API now and then, and kept in memory.
 * <p>
 * Public API: the {@code dto} and {@code service} packages, marked with {@code @NamedInterface}. {@code service.impl}
 * is internal to the module.
 */
package com.jobwarden.updates;
