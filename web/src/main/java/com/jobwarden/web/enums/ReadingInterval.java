package com.jobwarden.web.enums;

/**
 * How often JobWarden reads the profile's mailbox on its own, as Settings offers it: each is a message,
 * inbox.every.&lt;name&gt;, e.g. every 30 minutes.
 *
 * @param minutes as the inbox keeps it; 0 for only when the profile clicks Check now
 */
public enum ReadingInterval {

	MINUTES_5(5),

	MINUTES_10(10),

	MINUTES_30(30),

	HOUR(60),

	HOURS_3(180),

	HOURS_12(720),

	DAY(1440),

	ON_REQUEST(0);

	private final int minutes;

	ReadingInterval(int minutes) {
		this.minutes = minutes;
	}

	public int getMinutes() {
		return this.minutes;
	}

	/**
	 * @return the one of so many minutes; for a number Settings doesn't offer, the next longer one, or a day
	 */
	public static ReadingInterval of(int minutes) {
		if (minutes <= 0) {
			return ON_REQUEST;
		}
		for (ReadingInterval interval : values()) {
			if (interval != ON_REQUEST && interval.minutes >= minutes) {
				return interval;
			}
		}
		return DAY;
	}

}
