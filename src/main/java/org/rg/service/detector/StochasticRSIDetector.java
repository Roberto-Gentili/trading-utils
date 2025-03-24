package org.rg.service.detector;

import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.ColoredNumber;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;

public class StochasticRSIDetector extends CriticalIndicatorValueDetectorAbst {
	private int period;

	public StochasticRSIDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		int period
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.period = period;
	}

//	@Override
//	public Asset compute(
//		Interval interval
//	) {
//		BarSeries barSeries = candlesticks.get(interval);
//		StochasticRSIIndicator rSIIndicator = new StochasticRSIIndicator(new ClosePriceIndicator(barSeries), period);
//		List<Num> values = rSIIndicator.stream().collect(Collectors.toList());
//		Double latestRSIValue = values.get(barSeries.getEndIndex()).doubleValue() * 100d;
//		Asset data = null;
//		if (checkIfIsBitcoin(mainAsset) || ((latestRSIValue > 85 || latestRSIValue < 15) && latestRSIValue != 0)) {
//			Map<String, Double> variations = new LinkedHashMap<>();
//			variations.put("Stoch. RSI value on " + interval.toString(), latestRSIValue.doubleValue());
//			data = new Asset(
//				mainAsset,
//				collateralAsset,
//				candlesticks
//			).addRSI(variations);
//		}
//		return data;
//	}

	@Override
	public Asset compute(
		Interval interval
	) {
		BarSeries barSeries = candlesticks.get(interval);
		StochasticOscillatorKIndicator sRSI = new StochasticOscillatorKIndicator(barSeries, period);
        Double latestRSIValue = sRSI.getValue(barSeries.getEndIndex()).doubleValue();
        Asset data = null;
		Map<String, Object> values = new LinkedHashMap<>();
		if (latestRSIValue != 0) {
			if (latestRSIValue > 85) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue).color(ColoredNumber.RED_COLOR));
			} else if (latestRSIValue < 15) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue).color(ColoredNumber.GREEN_COLOR));
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
