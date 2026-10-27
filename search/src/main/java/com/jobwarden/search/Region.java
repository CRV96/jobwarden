package com.jobwarden.search;

import org.jspecify.annotations.Nullable;

/**
 * A location to suggest.
 *
 * @param countryCode the country, or the one that stands for the region, in ISO 3166-1 alpha-2 and lower case, as
 * flag icons name them; {@code null} for a region no flag stands for, such as EMEA
 */
public record Region(String name, @Nullable String countryCode) {
}
