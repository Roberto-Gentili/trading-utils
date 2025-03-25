package org.rg.service;

public enum Color {
	DEFAULT("#606060"),
	RED("red"),
	GREEN("green");

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