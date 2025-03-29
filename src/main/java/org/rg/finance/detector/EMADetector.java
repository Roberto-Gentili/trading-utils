package org.rg.finance.detector;

import static org.rg.service.CriticalIndicatorValueDetector.divide;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.Color;
import org.rg.service.ColoredNumber;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

public class EMADetector extends CriticalIndicatorValueDetectorAbst {
	private int[] periods;
	private boolean retrieveOnlyCritical;
	public EMADetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		boolean retrieveOnlyCritical,
		int... periods
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.periods = periods;
		this.retrieveOnlyCritical = retrieveOnlyCritical;
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		BarSeries barSeries = candlesticks.get(interval);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
		List<EMAIndicator> indicators = new ArrayList<>();
		for (int period : periods) {
			indicators.add(new EMAIndicator(closePrice, period));
		}
		Map<String, Object> values = new LinkedHashMap<>();
		for (EMAIndicator indicator : indicators) {
			BigDecimal maValue = toBigDecimal(indicator.getValue(barSeries.getEndIndex()).doubleValue());
			BigDecimal currentPrice = toBigDecimal(barSeries.getLastBar().getClosePrice().doubleValue());
			String key = interval.toString() + "-" + indicator.getBarCount();
			ColoredNumber value = toColoredNumber(
				key,
				maValue,
				currentPrice
			);
			if (retrieveOnlyCritical) {
				if (!value.getColor().equals(Color.DEFAULT.getCode())) {
					values.put(
						key,
						value
					);
				}
			} else {
				values.put(
					key,
					value
				);
			}
		}
		Asset data = null;
		if (!values.isEmpty()) {
			data = new Asset(
				mainAsset,
				collateralAsset,
				candlesticks
			).addDynamicValues(ValueName.EMA, values);
		}
		return data;
	}

	private ColoredNumber toColoredNumber(String key, BigDecimal maValue, BigDecimal currentPrice) {
		BigDecimal maRUpper = divide(
			maValue.multiply(BigDecimal.valueOf(100.75)),
			BigDecimal.valueOf(100d)
		);
		BigDecimal malower = divide(
			maValue.multiply(BigDecimal.valueOf(99.25)),
			BigDecimal.valueOf(100d)
		);
		ColoredNumber value = ColoredNumber.valueOf(maValue.doubleValue());
		if (currentPrice.compareTo(malower) >= 0 && currentPrice.compareTo(maValue) <= 0) {
			value.color(Color.RED.getCode());
		} else if (currentPrice.compareTo(maRUpper) <= 0 && currentPrice.compareTo(maValue) >= 0) {
			value.color(Color.GREEN.getCode());
		}
		return value;
	}

}
