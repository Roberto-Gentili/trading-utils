package org.rg.finance;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

public class BinanceDataSupplier implements DataSupplier {
	private BinanceWallet wallet;
	public BinanceDataSupplier(BinanceWallet wallet) {
		this.wallet = wallet;
	}

	@Override
	public Collection<Map<String, Object>> available() {
		return wallet.getAllMarginAssetPairs();
	}

	@Override
	public BarSeries getCandlesticks(
		String assetName, String collateral, Interval interval, Date endTime,
		Integer quantity) {
		return ((BinanceWallet)wallet).getCandlesticks(
			assetName + collateral,
			interval,
			endTime,
			quantity,
			new BinanceWallet.CandleStick.Converter<BarSeries>() {
				@Override
				public BarSeries convert(Collection<List<?>> input) {
					BarSeries series = new BaseBarSeriesBuilder().withName(assetName + "-" + interval + "-" + quantity).build();
			        for (List<?> candlestickData : input) {
			        	series.addBar(
		    	        	BaseBar.builder(DecimalNum::valueOf, Number.class)
		                    .timePeriod(interval.toDuration())
		                    .endTime(
		                		ZonedDateTime.ofInstant(
		                			Instant.ofEpochMilli(
		            					(long)candlestickData.get(6)
		            				),ZoneId.systemDefault()
		            			)
		                	)
		                    .openPrice(Double.parseDouble((String)candlestickData.get(1)))
		                    .highPrice(Double.parseDouble((String)candlestickData.get(2)))
		                    .lowPrice(Double.parseDouble((String)candlestickData.get(3)))
		                    .closePrice(Double.parseDouble((String)candlestickData.get(4)))
		                    .volume(Double.parseDouble((String)candlestickData.get(5)))
		                    .build()
		                );
			        }
			        return series;
				}
			}
		);
	}

	@Override
	public Collection<Map<String, Object>> availableFiltered(Collection<String> dafaultAssets, List<Map<String, String>> assetFilter) {
		return available().stream().filter(asset -> {
			if (dafaultAssets.contains((String)asset.get("quote"))) {
				return true;
			} else {
				return assetFilter.stream().filter(entry -> {
					String collateral = entry.get((String)asset.get("base"));
					return collateral != null && collateral.equals((String)asset.get("quote"));
				}).findAny().isPresent();
			}
		}).collect(Collectors.toList());
	}

}
