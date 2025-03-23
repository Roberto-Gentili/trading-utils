package org.rg.service.detector;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class BigCandleDetector extends CriticalIndicatorValueDetectorAbst {
	BigDecimal variationPercentage;
	public BigCandleDetector(
		String mainAsset,
		String collateralAsset, Map<Interval, BarSeries> candlesticks,
		double variationPercentage
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.variationPercentage = BigDecimal.valueOf(variationPercentage);
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getBar(lastCandleIndex);

		BigDecimal high = toBigDecimal(latestBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latestBar.getLowPrice().doubleValue());
		BigDecimal open = toBigDecimal(latestBar.getOpenPrice().doubleValue());
		BigDecimal close = toBigDecimal(latestBar.getClosePrice().doubleValue());

		BigDecimal priceVariation = high.subtract(low);
		BigDecimal totalForComputation = priceVariation.compareTo(BigDecimal.ZERO) >=0 ? low : high;
		BigDecimal variationPerc = divide(priceVariation.abs().multiply(BigDecimal.valueOf(100d)),totalForComputation);

		boolean buyCondition =
			variationPerc.compareTo(variationPercentage) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) < 0;
		boolean sellCondition =
			variationPerc.compareTo(variationPercentage) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) >= 0;
		Asset data = null;
		if (buyCondition || sellCondition) {
			variationPerc = priceVariation.compareTo(BigDecimal.ZERO) >= 0 ? variationPerc : variationPerc.negate();
			Map<String, Double> variations = new LinkedHashMap<>();
			variations.put(interval.toString(), variationPerc.doubleValue());
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addVariationPercenages(variations);

		}
		return data;
	}

}
