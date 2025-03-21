package org.rg.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.rg.finance.BinanceWallet;
import org.rg.finance.Interval;
import org.rg.finance.Wallet;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNum;

class AssetDataLoader {
	private Wallet wallet;
	private List<CompletableFuture<Map.Entry<Interval, BarSeries>>> assetDataLoaders;
	private String mainAsset;
	private String collateralAsset;

	public AssetDataLoader(Wallet wallet, String mainAsset, String collateralAsset) {
		this.wallet = wallet;
		this.assetDataLoaders = new CopyOnWriteArrayList<>();
		this.mainAsset = mainAsset;
		this.collateralAsset = collateralAsset;
	}

	public AssetDataLoader loadInParallel(Interval interval, int quantiy) {
		assetDataLoaders.add(
			CompletableFuture.supplyAsync(() -> {
				return new AbstractMap.SimpleEntry<>(interval, retrieveCandlestick(interval, quantiy));
			})
		);
		return this;
	}

	public Map<Interval, BarSeries> retrieve() {
		Map<Interval, BarSeries> data = new LinkedHashMap<>();
		for(CompletableFuture<Map.Entry<Interval, BarSeries>> assetDataLoader : assetDataLoaders) {
			Map.Entry<Interval, BarSeries> output = assetDataLoader.join();
			data.put(output.getKey(), output.getValue());
		}
		assetDataLoaders.clear();
		return data;
	}

	protected BarSeries retrieveCandlestick(
		Interval analysisInterval, int quantiy
	) {
		return ((BinanceWallet)wallet).getCandlesticks(
			mainAsset + collateralAsset,
			analysisInterval,
			null,
			quantiy,
			new BinanceWallet.CandleStick.Converter<BarSeries>() {
				@Override
				public BarSeries convert(Collection<List<?>> input) {
					BarSeries series = new BaseBarSeriesBuilder().withName(mainAsset + "-" + analysisInterval + "-" + quantiy).build();
			        for (List<?> candlestickData : input) {
			        	series.addBar(
		    	        	BaseBar.builder(DecimalNum::valueOf, Number.class)
		                    .timePeriod(analysisInterval.toDuration())
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

}