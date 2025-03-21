package org.rg.service;

import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Application.Asset;
import org.ta4j.core.BarSeries;

public interface CriticalIndicatorValueDetector {
	public Asset compute(
		Interval interval
	);
	public Map<Interval, BarSeries> getCandlesticks();
	public String getMainAsset();
	public String getCollateralAsset();
}