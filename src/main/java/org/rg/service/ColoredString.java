package org.rg.service;

public class ColoredString {

	private static final long serialVersionUID = 2917611752415418621L;

	private String value;
	private String color;

	public ColoredString(String value) {
		this.value = value;
		this.color = Color.DEFAULT.getCode();
	}


	public ColoredString color(String color) {
		this.color = color;
		return this;
	}

	public String value() {
		return this.value;
	}

	public static ColoredString valueOf(String value) {
		return new ColoredString(value);
	}

	public String getColor() {
		return color;
	}

	public boolean isDefaultColor() {
		return this.color.equals(Color.DEFAULT.getCode());
	}

	@Override
	public String toString() {
		return value;
	}
}
