package org.rg.service.detector;
import static org.rg.service.CriticalIndicatorValueDetector.divide;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.ColoredNumber;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class BigCandleDetector extends CriticalIndicatorValueDetectorAbst {
	BigDecimal minimumCandlePercentageSize;
	public BigCandleDetector(
		String mainAsset,
		String collateralAsset, Map<Interval, BarSeries> candlesticks,
		double minimumCandlePercentageSize
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.minimumCandlePercentageSize = BigDecimal.valueOf(minimumCandlePercentageSize);
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getBar(lastCandleIndex);

		BigDecimal high = toBigDecimal(latestBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latestBar.getLowPrice().doubleValue());
		BigDecimal priceVariation = high.subtract(low);
		BigDecimal totalForComputation = priceVariation.compareTo(BigDecimal.ZERO) >=0 ? low : high;
		BigDecimal variationPerc = divide(priceVariation.abs().multiply(BigDecimal.valueOf(100d)),totalForComputation);

		boolean buyCondition =
			variationPerc.compareTo(minimumCandlePercentageSize) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) < 0;
		boolean sellCondition =
			variationPerc.compareTo(minimumCandlePercentageSize) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) >= 0;
		Asset data = null;
		Map<String, Object> values = new LinkedHashMap<>();
		priceVariation = priceVariation.compareTo(BigDecimal.ZERO) >= 0 ? variationPerc : variationPerc.negate();
		if (buyCondition) {
			values.put(interval.toString(), ColoredNumber.valueOf(variationPerc.doubleValue()).color(ColoredNumber.GREEN_COLOR));
		} else if (sellCondition) {
			values.put(interval.toString(), ColoredNumber.valueOf(variationPerc.doubleValue()).color(ColoredNumber.RED_COLOR));
		}
		if (!values.isEmpty()) {
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addDynamicValues(ValueName.VARIATION_PERCENTAGE, values);
		}
		return data;
	}

}
