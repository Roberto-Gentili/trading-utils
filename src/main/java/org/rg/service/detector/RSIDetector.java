package org.rg.service.detector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.ColoredNumber;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

public class RSIDetector extends CriticalIndicatorValueDetectorAbst {
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
		List<Num> rsiOutput = rSIIndicator.stream().collect(Collectors.toList());
		Double latestRSIValue = rsiOutput.get(barSeries.getEndIndex()).doubleValue();
		Asset data = null;
		Map<String, Object> values = new LinkedHashMap<>();
		if (latestRSIValue != 0) {
			if (latestRSIValue > 70) {
				values.put(interval.toString(), ColoredNumber.valueOf(latestRSIValue).color(ColoredNumber.RED_COLOR));
			} else if (latestRSIValue < 30) {
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
			).addDynamicValues(ValueName.RSI, values);
		}
		return data;
	}

}
