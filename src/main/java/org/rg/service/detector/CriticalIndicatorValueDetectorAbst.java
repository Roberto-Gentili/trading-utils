package org.rg.service.detector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.rg.finance.Interval;
import org.rg.service.CriticalIndicatorValueDetector;
import org.ta4j.core.BarSeries;

public abstract class CriticalIndicatorValueDetectorAbst implements CriticalIndicatorValueDetector  {

	protected Map<Interval, BarSeries> candlesticks;
	protected String mainAsset;
	protected String collateralAsset;

	public CriticalIndicatorValueDetectorAbst(String mainAsset, String collateralAsset, Map<Interval, BarSeries> candlesticks) {
		this.candlesticks = candlesticks;
		this.mainAsset = mainAsset;
		this.collateralAsset = collateralAsset;
	}
	protected BigDecimal toBigDecimal(double value) {
		return new BigDecimal(value).setScale(50, RoundingMode.HALF_DOWN);
	}

	protected BigDecimal divide(BigDecimal a, BigDecimal b) {
		if (b.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		return a.divide(b, 50, RoundingMode.HALF_DOWN);
	}

	@Override
	public Map<Interval, BarSeries> getCandlesticks() {
		return candlesticks;
	}
	@Override
	public String getMainAsset() {
		return mainAsset;
	}
	@Override
	public String getCollateralAsset() {
		return collateralAsset;
	}

	public static boolean checkIfIsBitcoin(String coin) {
		return coin.equals("BTC");
	}

}