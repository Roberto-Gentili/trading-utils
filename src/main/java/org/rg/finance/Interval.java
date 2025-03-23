package org.rg.finance;

import java.time.Duration;

public enum Interval {
	MONTH("1M"),
	WEEK("1w"),
	THREE_DAYS("3d"),
	ONE_DAYS("1d"),
	TWELVE_HOURS("12h"),
	EIGHT_HOURS("8h"),
	SIX_HOURS("6h"),
	FOUR_HOURS("4h"),
	TWO_HOURS("2h"),
	ONE_HOURS("1h"),
	THIRTY_MINUTES("30m"),
	FIFTEEN_MINUTES("15m");

	private Interval(String value) {
		this.value = value;
	}
	private String value;

    @Override
    public String toString() {
        return value;
    }

	public Duration toDuration() {
		Duration duration = null;
		if (this.compareTo(Interval.WEEK) == 0) {
			duration = Duration.ofDays(7);
		} else if (this.compareTo(Interval.THREE_DAYS) == 0) {
			duration =  Duration.ofDays(3);
		} else if (this.compareTo(Interval.ONE_DAYS) == 0) {
			duration =  Duration.ofDays(1);
		} else if (this.compareTo(Interval.TWELVE_HOURS) == 0) {
			duration =  Duration.ofHours(12);
		} else if (this.compareTo(Interval.EIGHT_HOURS) == 0) {
			duration =  Duration.ofHours(8);
		} else if (this.compareTo(Interval.SIX_HOURS) == 0) {
			duration =  Duration.ofHours(6);
		} else if (this.compareTo(Interval.FOUR_HOURS) == 0) {
			duration =  Duration.ofHours(4);
		} else if (this.compareTo(Interval.TWO_HOURS) == 0) {
			duration =  Duration.ofHours(2);
		} else if (this.compareTo(Interval.ONE_HOURS) == 0) {
			duration =  Duration.ofHours(1);
		} else if (this.compareTo(Interval.THIRTY_MINUTES) == 0) {
			duration =  Duration.ofMinutes(30);
		} else if (this.compareTo(Interval.FIFTEEN_MINUTES) == 0) {
			duration =  Duration.ofMinutes(15);
		}
		return duration.minus(Duration.ofMillis(1));
	}
}