/**
 * Market: what positions pay across countries, from public sources, to compare with what the profile's own jobs pay.
 * The Stack Overflow Developer Survey's answers, imported once into the {@code market_salaries} table; live salaries
 * from job ads, from the Adzuna API, when the app has a key for it (saved in Settings, in {@code market_adzuna_key},
 * or in the environment); and Eurostat's Structure of Earnings Survey. Every
 * figure is converted with the jobs module's exchange rates.
 * <p>
 * Public API: the {@code dto}, {@code enums} and {@code service} packages, marked with {@code @NamedInterface}.
 * {@code entity}, {@code repository} and {@code service.impl} are internal to the module.
 */
package com.jobwarden.market;
