package org.rg.service.detector;

import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.Color;
import org.rg.service.ColoredNumber;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticRSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

public class StochasticRSIDetector extends CriticalIndicatorValueDetectorAbst {
	private int period;
	private int barCount;

	public StochasticRSIDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		int period,
		int barCount
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.period = period;
		this.barCount = barCount;
	}


	@Override
	public Asset compute(
		Interval interval
	) {
		BarSeries barSeries = candlesticks.get(interval);
		StochasticRSIIndicator sRSI = new StochasticRSIIndicator(
			new RSIIndicator(
				new ClosePriceIndicator(barSeries),period
			), period
		);
		SMAIndicator k = new SMAIndicator(sRSI, this.barCount);
		//Indicator d = new SMAIndicator(k, this.barCount);

        Double latestRSIValue = k.getValue(barSeries.getEndIndex()).doubleValue() * 100d;
        Asset data = new Asset(
			mainAsset,
			collateralAsset,
			candlesticks
		);
		Map<String, Object> values = new LinkedHashMap<>();
		if (latestRSIValue != 0) {
			ColoredNumber coloredNumber = ColoredNumber.valueOf(latestRSIValue);
			if (latestRSIValue > 90) {
				values.put(interval.toString(), coloredNumber.color(Color.DARK_RED.getCode()));
			} else if (latestRSIValue > 80) {
				values.put(interval.toString(), coloredNumber.color(Color.RED.getCode()));
			} else if (latestRSIValue < 10) {
				values.put(interval.toString(), coloredNumber.color(Color.GREEN.getCode()));
			} else if (latestRSIValue < 20)  {
				values.put(interval.toString(), coloredNumber.color(Color.DARK_GREEN.getCode()));
			} else if (shouldMantainData(data)) {
				if (latestRSIValue > 70) {
					coloredNumber.color(Color.ORANGE.getCode());
				} else if (latestRSIValue < 30) {
					coloredNumber.color(Color.CHART_REUSE.getCode());
				}
				values.put(interval.toString(), coloredNumber);
			}
		}
		if (!values.isEmpty()) {
			data.addDynamicValues(ValueName.STOCHASTIC_RSI, values);
		} else {
			return null;
		}
		return data;
	}

}
