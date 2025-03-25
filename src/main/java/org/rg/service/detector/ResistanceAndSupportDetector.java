package org.rg.service.detector;

import static org.rg.service.CriticalIndicatorValueDetector.divide;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.Color;
import org.rg.service.ColoredNumber;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.PivotLevel;
import org.ta4j.core.indicators.pivotpoints.PivotPointIndicator;
import org.ta4j.core.indicators.pivotpoints.StandardReversalIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;

public class ResistanceAndSupportDetector extends CriticalIndicatorValueDetectorAbst {
	boolean retrieveOnlyCritical;
	public ResistanceAndSupportDetector(
		String mainAsset, String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		boolean retrieveOnlyCritical
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.retrieveOnlyCritical = retrieveOnlyCritical;
	}

	@Override
	public Asset compute(Interval interval) {
		Map<String, Object> values = new LinkedHashMap<>();
		BarSeries candlestick = candlesticks.get(interval);
		PivotPointIndicator indicator = new PivotPointIndicator(
			candlestick,
			interval.equals(Interval.WEEK) ?
				TimeLevel.WEEK :
				interval.equals(Interval.ONE_DAYS) ?
					TimeLevel.DAY: TimeLevel.BARBASED);
		for (PivotLevel level : PivotLevel.values()) {
			String levelName = level.name();
			StandardReversalIndicator rI = new StandardReversalIndicator(indicator, level);
			String key = interval.toString() + "-" + levelName.charAt(0)+ levelName.charAt(levelName.length()-1);
			ColoredNumber value = toColoredNumber(
				key,
				rI.getValue(candlestick.getEndIndex()).doubleValue(),
				candlestick.getLastBar().getClosePrice().doubleValue()
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
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addDynamicValues(ValueName.SUPPORT_AND_RESISTANCE, values);
		}
		return data;
	}


	private ColoredNumber toColoredNumber(String key, Double sOrR, Double cP) {
		BigDecimal currentPrice = BigDecimal.valueOf(cP);
		BigDecimal sOrRUpper = divide(
			BigDecimal.valueOf(sOrR).multiply(BigDecimal.valueOf(100.75)),
			BigDecimal.valueOf(100d)
		);
		BigDecimal sOrRlower = divide(
			BigDecimal.valueOf(sOrR).multiply(BigDecimal.valueOf(99.25)),
			BigDecimal.valueOf(100d)
		);
		if (key.contains("S")) {
			return
				currentPrice.compareTo(sOrRlower) >= 0 && currentPrice.compareTo(sOrRUpper) <= 0 ?
				ColoredNumber.valueOf(sOrR).color("green") : ColoredNumber.valueOf(sOrR);
		} else if (key.contains("R")) {
			return
				currentPrice.compareTo(sOrRlower) >= 0 && currentPrice.compareTo(sOrRUpper) <= 0 ?
				ColoredNumber.valueOf(sOrR).color("red") : ColoredNumber.valueOf(sOrR);
		}
		return ColoredNumber.valueOf(sOrR);
	}

}
