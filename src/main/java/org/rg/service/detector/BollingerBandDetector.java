package org.rg.service.detector;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.Asset.ValueName;
import org.rg.service.Color;
import org.rg.service.ColoredNumber;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;

public class BollingerBandDetector extends CriticalIndicatorValueDetectorAbst {
	boolean considerOnlyBBContacts = true;
	int maPeriod;
	double deviation;
	public BollingerBandDetector(
		String mainAsset,
		String collateralAsset,
		Map<Interval, BarSeries> candlesticks,
		int period,
		double deviation
	) {
		super(mainAsset, collateralAsset, candlesticks);
		this.maPeriod = period;
		this.deviation = deviation;
	}

	@Override
	public Asset compute(
		Interval interval
	) {
		int lastCandleIndex = candlesticks.get(interval).getEndIndex();
		Bar latestBar = candlesticks.get(interval).getLastBar();
		DecimalNum deviationNum = DecimalNum.valueOf(deviation);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(candlesticks.get(interval));
		SMAIndicator ma = new SMAIndicator(closePrice, maPeriod);
        StandardDeviationIndicator deviation = new StandardDeviationIndicator(closePrice, maPeriod);
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(ma);
        BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, deviation, deviationNum);
        BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, deviation, deviationNum);
		//BollingerBandFacade bBFacade = new BollingerBandFacade(candlesticks, 20, 2);
		BigDecimal bBLower =
			//toBigDecimal(bBFacade.lower().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(lowBBand.getValue(lastCandleIndex).doubleValue());
		BigDecimal bBUpper =
			//toBigDecimal(bBFacade.upper().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(upBBand.getValue(lastCandleIndex).doubleValue());
		BigDecimal high = toBigDecimal(latestBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latestBar.getLowPrice().doubleValue());
		Asset data = null;
		if (low.compareTo(bBLower) <= 0 || high.compareTo(bBUpper) >= 0) {
			Map<String, Object> values = new LinkedHashMap<>();
			if (low.compareTo(bBLower) <= 0) {
				values.put(
					interval.toString() + "-l",
					ColoredNumber.valueOf(bBLower.doubleValue()).color(Color.GREEN.getCode())
				);
			} else if (high.compareTo(bBUpper) >= 0) {
				values.put(
					interval.toString() + "-u",
					ColoredNumber.valueOf(bBLower.doubleValue()).color(Color.RED.getCode())
				);
			} else if (checkIfIsBitcoin(mainAsset)) {
				values.put(
					interval.toString() + "-u",
					ColoredNumber.valueOf(bBLower.doubleValue())
				);
			}
			data = new Asset(
				this.mainAsset,
				this.collateralAsset,
				candlesticks
			).addDynamicValues(ValueName.BOLLINGER_BANDS, values);
		}
		return data;
	}

}
