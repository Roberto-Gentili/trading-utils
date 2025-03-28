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
        Asset data = null;
		Map<String, Object> values = new LinkedHashMap<>();
		if (latestRSIValue != 0) {
			if (latestRSIValue > 85) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue).color(Color.RED.getCode()));
			} else if (latestRSIValue < 15) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue).color(Color.GREEN.getCode()));
			} else if (checkIfIsBitcoin(mainAsset)) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue));
			}
		}
		if (!values.isEmpty()) {
			data = new Asset(
				mainAsset,
				collateralAsset,
				candlesticks
			).addDynamicValues(ValueName.STOCHASTIC_RSI, values);
		}
		return data;
	}

}
