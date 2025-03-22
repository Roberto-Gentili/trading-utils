package org.rg.service.detector;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class SpikeDetector extends CriticalIndicatorValueDetectorAbst {
	public SpikeDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks
	) {
		super(mainAsset, collateralAsset, candlesticks);
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getBar(lastCandleIndex);
		BigDecimal spikePercentage = toBigDecimal(40d);
		BigDecimal comparingValue = toBigDecimal(3d);
		BigDecimal high = toBigDecimal(latestBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latestBar.getLowPrice().doubleValue());
		BigDecimal priceVariation = high.subtract(low);
		BigDecimal open = toBigDecimal(latestBar.getOpenPrice().doubleValue());
		BigDecimal close = toBigDecimal(latestBar.getClosePrice().doubleValue());
		BigDecimal lowSpikeValue = close.compareTo(open) < 0 ? close.subtract(low) : open.subtract(low);
		BigDecimal highSpikeValue = close.compareTo(open) > 0 ? high.subtract(close) : high.subtract(open);
		BigDecimal lowSpikePercentage = divide(lowSpikeValue.multiply(toBigDecimal(100d)), priceVariation);
		BigDecimal highSpikePercentage = divide(highSpikeValue.multiply(toBigDecimal(100d)), priceVariation);
		BigDecimal totalCandleVariation = divide(high.subtract(low),high).multiply(toBigDecimal(100d));
		//log.info('variation: {0}', totalCandleVariation)
		boolean buyCondition =
			lowSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && lowSpikeValue.compareTo(highSpikeValue) >= 0;
		boolean sellCondition =
			highSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && highSpikeValue.compareTo(lowSpikeValue) >= 0;
		Asset data = null;
		if (buyCondition || sellCondition) {
			Map<String, Double> variations = new LinkedHashMap<>();
			variations.put("Spike size % on " + interval.toString(),
				buyCondition? (lowSpikePercentage.negate().doubleValue()) : highSpikePercentage.doubleValue());
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addSpikeSizePercentage(variations);

		}
		return data;
	}

}
