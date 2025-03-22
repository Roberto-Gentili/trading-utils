package org.rg.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

public class BigCandleDetector extends CriticalIndicatorValueDetectorAbst {
	boolean considerOnlyBBContacts = true;
	BigDecimal variationPercentage;
	public BigCandleDetector(
		String mainAsset,
		String collateralAsset, Map<Interval, BarSeries> candlesticks,
		boolean considerOnlyBBContacts,
		double variationPercentage
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.considerOnlyBBContacts = considerOnlyBBContacts;
		this.variationPercentage = BigDecimal.valueOf(variationPercentage);
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getBar(lastCandleIndex);
		int bBMaPeriod = 20;
		DecimalNum bBDev = DecimalNum.valueOf(2);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(candlesticks.get(interval));
		SMAIndicator ma = new SMAIndicator(closePrice, bBMaPeriod);
        StandardDeviationIndicator deviation = new StandardDeviationIndicator(closePrice, bBMaPeriod);
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(ma);
        BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, deviation, bBDev);
        BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, deviation, bBDev);

		//BollingerBandFacade bBFacade = new BollingerBandFacade(candlesticks, 20, 2);
		BigDecimal bBLower =
			//toBigDecimal(bBFacade.lower().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(lowBBand.getValue(lastCandleIndex).doubleValue());
		BigDecimal bBUpper =
			//toBigDecimal(bBFacade.upper().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(upBBand.getValue(lastCandleIndex).doubleValue());

		BigDecimal high = toBigDecimal(latestBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latestBar.getLowPrice().doubleValue());
		BigDecimal open = toBigDecimal(latestBar.getOpenPrice().doubleValue());
		BigDecimal close = toBigDecimal(latestBar.getClosePrice().doubleValue());

		BigDecimal priceVariation = high.subtract(low);
		BigDecimal totalForComputation = priceVariation.compareTo(BigDecimal.ZERO) >=0 ? low : high;
		BigDecimal variationPerc = divide(priceVariation.abs().multiply(BigDecimal.valueOf(100d)),totalForComputation);

		boolean buyCondition =
			variationPerc.compareTo(variationPercentage) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) <0 && (considerOnlyBBContacts ? (low.compareTo(bBLower) <= 0) : true);
		boolean sellCondition =
			variationPerc.compareTo(variationPercentage) >= 0 && priceVariation.compareTo(BigDecimal.ZERO) >= 0 && (considerOnlyBBContacts ? (high.compareTo(bBUpper) >= 0) : true);
		Asset data = null;
		if (buyCondition || sellCondition) {
			variationPerc = priceVariation.compareTo(BigDecimal.ZERO) >= 0 ? variationPerc : variationPerc.negate();
			Map<String, Double> variations = new LinkedHashMap<>();
			variations.put("Price variation % on " + interval.toString(), variationPerc.doubleValue());
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addVariationPercenages(variations);

		}
		return data;
	}

}
