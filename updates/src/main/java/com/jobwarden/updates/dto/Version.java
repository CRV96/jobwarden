package com.jobwarden.updates.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * A version as releases are named: 1.4.2, or v1.4.2 as a tag. The same with a suffix, e.g. 1.5.0-SNAPSHOT, comes
 * before it. Compared, 1.4 is 1.4.0, though the two aren't equal.
 *
 * @param numbers the numbers, major first
 * @param suffix what comes after a hyphen, or {@code null}
 */
public record Version(List<Integer> numbers, @Nullable String suffix) implements Comparable<Version> {

	private static final Pattern VERSION = Pattern.compile("v?(\\d{1,9}(?:\\.\\d{1,9}){0,3})(?:-([0-9A-Za-z.-]+))?");

	public Version {
		numbers = List.copyOf(numbers);
	}

	/**
	 * @return the version, or empty when the text isn't one, e.g. "dev" when the build didn't set it
	 */
	public static Optional<Version> parse(@Nullable String text) {
		Matcher matcher = (text != null) ? VERSION.matcher(text.strip()) : null;
		if (matcher == null || !matcher.matches()) {
			return Optional.empty();
		}
		List<Integer> numbers = Arrays.stream(matcher.group(1).split("\\.")).map(Integer::valueOf).toList();
		return Optional.of(new Version(numbers, matcher.group(2)));
	}

	/**
	 * @return whether {@code newer} is a later version than {@code older}; false when either isn't a version
	 */
	public static boolean isNewer(@Nullable String newer, @Nullable String older) {
		Optional<Version> a = parse(newer);
		Optional<Version> b = parse(older);
		return a.isPresent() && b.isPresent() && a.get().compareTo(b.get()) > 0;
	}

	@Override
	public int compareTo(Version other) {
		for (int i = 0; i < Math.max(this.numbers.size(), other.numbers.size()); i++) {
			int compared = Integer.compare(number(i), other.number(i));
			if (compared != 0) {
				return compared;
			}
		}
		if (this.suffix == null || other.suffix == null) {
			// A release comes after its pre-releases
			return (this.suffix == null) ? ((other.suffix == null) ? 0 : 1) : -1;
		}
		return this.suffix.compareTo(other.suffix);
	}

	@Override
	public String toString() {
		return this.numbers.stream().map(String::valueOf).collect(Collectors.joining("."))
				+ ((this.suffix != null) ? "-" + this.suffix : "");
	}

	private int number(int index) {
		return (index < this.numbers.size()) ? this.numbers.get(index) : 0;
	}

}
