package com.jobwarden.database.dto;

import java.nio.file.Path;

/**
 * How JobWarden connects to its database, for Settings: never the password itself.
 *
 * @param user the database role it logs in as, e.g. jobwarden
 * @param address where the database is, e.g. localhost:5432/jobwarden: the JDBC address without its parameters, which
 * may hold a password
 * @param changedHere whether the password in use is the one changed in Settings, rather than the configured one
 * @param defaultPassword whether the password in use is still the one JobWarden's Compose files start with, which
 * anyone can read
 * @param keptIn the file the password changed in Settings is kept in, encrypted, for the next start
 */
public record DatabaseLogin(String user, String address, boolean changedHere, boolean defaultPassword, Path keptIn) {
}
