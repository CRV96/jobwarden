/**
 * Watchlist: companies a profile watches, by their job boards on Greenhouse, Lever and Ashby, whose public feeds
 * JobWarden reads itself every few hours, with no Claude tokens. The postings that match one of the profile's saved
 * searches (the discover module), by their title, location, work and employment type, without AI, become found jobs on
 * the AI Assistant page, to save or discard. Owns the {@code watched_companies} and {@code watched_company_finds}
 * tables.
 * <p>
 * Public API: the {@code dto}, {@code enums}, {@code exception} and {@code service} packages, marked with
 * {@code @NamedInterface}. {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.watchlist;
