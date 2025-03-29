package org.rg.finance.detector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.Color;
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
		Asset data = new Asset(
			mainAsset,
			collateralAsset,
			candlesticks
		);
		Map<String, Object> values = new LinkedHashMap<>();
		if (latestRSIValue != 0) {
			ColoredNumber coloredNumber = ColoredNumber.valueOf(latestRSIValue);
			if (latestRSIValue > 80) {
				values.put(interval.toString(), coloredNumber.color(Color.DARK_RED.getCode()));
			} else if (latestRSIValue > 70) {
				values.put(interval.toString(), coloredNumber.color(Color.RED.getCode()));
			} else if (latestRSIValue < 20) {
				values.put(interval.toString(), coloredNumber.color(Color.DARK_GREEN.getCode()));
			} else if (latestRSIValue < 30)  {
				values.put(interval.toString(), coloredNumber.color(Color.GREEN.getCode()));
			} else if (shouldMantainData(data)) {
				if (latestRSIValue > 60) {
					coloredNumber.color(Color.ORANGE.getCode());
				} else if (latestRSIValue < 40) {
					coloredNumber.color(Color.CHART_REUSE.getCode());
				}
				values.put(interval.toString(), coloredNumber);
			}
		}
		if (!values.isEmpty()) {
			data.addDynamicValues(ValueName.RSI, values);
		} else {
			return null;
		}
		return data;
	}

}
