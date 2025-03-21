package org.rg.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.rg.finance.Interval;
import org.rg.service.Application.Asset;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

public class RSIDetector extends CriticalIndicatorValueDetectorAbst  {
	private Map<Interval, BarSeries> candlesticks;
	private String mainAsset;
	private String collateralAsset;
	private int period;

	public RSIDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		int period
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.period = period;
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		BarSeries barSeries = candlesticks.get(interval);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
		RSIIndicator rSIIndicator = new RSIIndicator(closePrice, period);
		List<Num> values = rSIIndicator.stream().collect(Collectors.toList());
		Double latestRSIValue = values.get(barSeries.getEndIndex()).doubleValue();
		Asset data = null;
		if ((latestRSIValue > 70 || latestRSIValue < 30) && latestRSIValue != 0) {
			data = new Asset(
				mainAsset,
				collateralAsset,
				candlesticks,
				latestRSIValue,
				null,
				null
			);
		}
		return data;
	}

}
