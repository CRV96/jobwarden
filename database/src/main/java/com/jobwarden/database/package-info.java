/**
 * Database: the PostgreSQL login JobWarden connects with. Its password can be changed from Settings: the database
 * gets the new one, the connection pool uses it at once, and a file keeps it, encrypted, for the next start, where it
 * takes the place of the configured one ({@code spring.datasource.password}, or the Docker Compose database's). Owns
 * no table.
 * <p>
 * Public API: the {@code dto}, {@code exception} and {@code service} packages, marked with {@code @NamedInterface}.
 * {@code service.impl} is internal to the module.
 */
@NullMarked
package com.jobwarden.database;

import org.jspecify.annotations.NullMarked;
