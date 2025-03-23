package org.rg.service.detector;

import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_3;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_3;

import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.PivotPointIndicator;
import org.ta4j.core.indicators.pivotpoints.StandardReversalIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;

public class ResistanceAndSupportDetector extends CriticalIndicatorValueDetectorAbst {

	public ResistanceAndSupportDetector(
		String mainAsset, String collateralAsset,
		Map<Interval, BarSeries> candlesticks) {
		super(mainAsset, collateralAsset, candlesticks);
	}

	@Override
	public Asset compute(Interval interval) {
		Map<String, Double> resistanceAndSupportLevels = new LinkedHashMap<>();
		BarSeries candlestick = candlesticks.get(interval);
		PivotPointIndicator pivotPoint = new PivotPointIndicator(
			candlestick,
			interval.equals(Interval.WEEK) ?
				TimeLevel.WEEK :
				interval.equals(Interval.ONE_DAYS) ?
					TimeLevel.DAY: TimeLevel.BARBASED);
		StandardReversalIndicator s1 = new StandardReversalIndicator(pivotPoint, SUPPORT_1);
		StandardReversalIndicator s2 = new StandardReversalIndicator(pivotPoint, SUPPORT_2);
		StandardReversalIndicator s3 = new StandardReversalIndicator(pivotPoint, SUPPORT_3);
		StandardReversalIndicator r1 = new StandardReversalIndicator(pivotPoint, RESISTANCE_1);
		StandardReversalIndicator r2 = new StandardReversalIndicator(pivotPoint, RESISTANCE_2);
		StandardReversalIndicator r3 = new StandardReversalIndicator(pivotPoint, RESISTANCE_3);
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Price: {} - s1: {}; r1: {} - s2: {}; r2: {} - s3: {}; r3: {}  ",
//			dailyCandleSticks.getBar(dailyCandleSticks.getEndIndex()).getClosePrice().doubleValue(),
//			s1.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r1.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			s2.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r2.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			s3.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r3.getValue(dailyCandleSticks.getEndIndex()).doubleValue()
//		);                                                    -
		resistanceAndSupportLevels.put(interval.toString() + "-S1", s1.getValue(candlestick.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put(interval.toString() + "-R1", r1.getValue(candlestick.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put(interval.toString() + "-S2", s2.getValue(candlestick.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put(interval.toString() + "-R2", r2.getValue(candlestick.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put(interval.toString() + "-S3", s3.getValue(candlestick.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put(interval.toString() + "-R3", r3.getValue(candlestick.getEndIndex()).doubleValue());
		return new Asset(
			this.mainAsset,
			this.collateralAsset,
			candlesticks
		).addSupportAndResistance(resistanceAndSupportLevels);
	}


}
