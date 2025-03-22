package org.rg.service;

import java.math.BigDecimal;
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

public class SpikeDetector extends CriticalIndicatorValueDetectorAbst {
	boolean considerOnlyBBContacts = true;
	public SpikeDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		boolean considerOnlyBBContacts
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.considerOnlyBBContacts = considerOnlyBBContacts;
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getBar(lastCandleIndex);
		BigDecimal spikePercentage = toBigDecimal(40d);
		BigDecimal comparingValue = toBigDecimal(3d);
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
			lowSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && lowSpikeValue.compareTo(highSpikeValue) >= 0 && (considerOnlyBBContacts ? (low.compareTo(bBLower) <= 0) : true);
		boolean sellCondition =
			highSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && highSpikeValue.compareTo(lowSpikeValue) >= 0 && (considerOnlyBBContacts ? (high.compareTo(bBUpper) >= 0) : true);
		Asset data = null;
		if (buyCondition || sellCondition) {
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks,
				null,
				buyCondition? (lowSpikePercentage.negate().doubleValue()) : highSpikePercentage.doubleValue()
			);

		}
		return data;
	}

}
