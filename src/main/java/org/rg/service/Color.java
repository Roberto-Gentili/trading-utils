package org.rg.service;

public enum Color {
	DEFAULT("#606060"),
	CHART_REUSE("Chartreuse"),
	DARK_GREEN("darkgreen"),
	DARK_RED("darkred"),
	GREEN("green"),
	ORANGE("orangered"),
	RED("red"),
	YELLOW("yellow");

	private String code;

	private Color(String code) {
		this.code = code;
	}

	public String getCode() {
		return this.code;
	}

	@Override
	public String toString() {
		return this.code;
	}

	public boolean isGreen() {
		return this == DARK_GREEN || this == CHART_REUSE || this == GREEN;
	}

	public boolean isRed() {
		return this == ORANGE || this == RED || this == DARK_RED;
	}

	public static boolean isGreen(String code) {
		for (Color value : Color.values()) {
			if (value.isGreen() && value.getCode().equals(code)) {
				return true;
			}
		}
		return false;
	}

	public static boolean isRed(String code) {
		for (Color value : Color.values()) {
			if (value.isRed() && value.getCode().equals(code)) {
				return true;
			}
		}
		return false;
	}
}