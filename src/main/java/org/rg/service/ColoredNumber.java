package org.rg.service;

public class ColoredNumber extends Number {

	private static final long serialVersionUID = 2917611752415418621L;

	public static enum Color {
		DEFAULT("#606060"),
		RED("red"),
		GREEN("green");

		private String code;

		private Color(String code) {
			this.code = code;
		}

		public String getCode() {
			return this.getCode();
		}

		@Override
		public String toString() {
			return this.code;
		}
	}

	private Double value;
	private String color;

	public ColoredNumber(Double value) {
		this.value = value;
		this.color = Color.DEFAULT.getCode();
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
