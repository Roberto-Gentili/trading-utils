package org.rg.service;

public enum Color {
	DEFAULT("#606060"),
	DARK_GREEN("darkgreen"),
	DARK_RED("darkred"),
	GREEN("green"),
	DARK_OLIVE_GREEN("DarkOliveGreen"),
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
}