package org.rg.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.rg.finance.Interval;
import org.ta4j.core.BarSeries;

public interface CriticalIndicatorValueDetector {
	public Asset compute(
		Interval interval
	);
	public Map<Interval, BarSeries> getCandlesticks();
	public String getMainAsset();
	public String getCollateralAsset();

	public static BigDecimal divide(BigDecimal a, BigDecimal b) {
		if (b.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		return a.divide(b, 50, RoundingMode.HALF_DOWN);
	}
}