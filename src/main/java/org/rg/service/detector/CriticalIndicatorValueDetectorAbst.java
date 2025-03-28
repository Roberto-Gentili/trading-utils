package org.rg.service.detector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;

import org.rg.finance.Interval;
import org.rg.service.Asset;
import org.rg.service.CriticalIndicatorValueDetector;
import org.ta4j.core.BarSeries;

public abstract class CriticalIndicatorValueDetectorAbst implements CriticalIndicatorValueDetector  {

	protected Map<Interval, BarSeries> candlesticks;
	protected String mainAsset;
	protected String collateralAsset;
	protected Function<Asset, Boolean> mantainAssetDataFilter;

	public CriticalIndicatorValueDetectorAbst(
		String mainAsset, String collateralAsset, Map<Interval, BarSeries> candlesticks
	) {
		this.candlesticks = candlesticks;
		this.mainAsset = mainAsset;
		this.collateralAsset = collateralAsset;
	}
	protected BigDecimal toBigDecimal(double value) {
		return new BigDecimal(value).setScale(50, RoundingMode.HALF_DOWN);
	}

	public CriticalIndicatorValueDetectorAbst setMantainAssetDataFilter(Function<Asset, Boolean> mantainAssetDataFilter) {
		this.mantainAssetDataFilter = mantainAssetDataFilter;
		return this;
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

	public boolean shouldMantainData(Asset asset) {
		return mantainAssetDataFilter != null && mantainAssetDataFilter.apply(asset);
	}

}