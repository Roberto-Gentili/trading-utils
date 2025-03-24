package org.rg.service;

public class ColoredNumber extends Number {

	private static final long serialVersionUID = 2917611752415418621L;

	public static final String DEFAULT_COLOR = "#606060";
	public static final String RED_COLOR = "red";
	public static final String GREEN_COLOR = "green";

	private Double value;
	private String color;

	public ColoredNumber(Double value) {
		this.value = value;
		this.color = DEFAULT_COLOR;
	}

	public ColoredNumber(long vl) {
		this(Double.valueOf(vl));
	}

	public ColoredNumber(int vl) {
		this(Double.valueOf(vl));
	}

	public ColoredNumber color(String color) {
		this.color = color;
		return this;
	}

	@Override
	public int intValue() {
		return value.intValue();
	}

	@Override
	public long longValue() {
		return value.intValue();
	}

	@Override
	public float floatValue() {
		return value.floatValue();
	}

	@Override
	public double doubleValue() {
		return value;
	}

	public static ColoredNumber valueOf(Double value) {
		return new ColoredNumber(value);
	}

	public String getColor() {
		return color;
	}
}
