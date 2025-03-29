package org.rg.finance;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.ta4j.core.BarSeries;

public class DefaultDataLoader implements AssetDataLoader {
	private DataSupplier dataSupplier;
	private List<CompletableFuture<Map.Entry<Interval, BarSeries>>> assetDataLoaders;
	private String mainAsset;
	private String collateralAsset;

	public DefaultDataLoader(DataSupplier dataSupplier, String mainAsset, String collateralAsset) {
		this.dataSupplier = dataSupplier;
		this.assetDataLoaders = new CopyOnWriteArrayList<>();
		this.mainAsset = mainAsset;
		this.collateralAsset = collateralAsset;
	}

	@Override
	public DefaultDataLoader loadInParallel(Interval interval, int quantiy) {
		assetDataLoaders.add(
			CompletableFuture.supplyAsync(() -> {
				return new AbstractMap.SimpleEntry<>(interval, retrieveCandlestick(interval, quantiy));
			})
		);
		return this;
	}

	@Override
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
		Interval analysisInterval,
		int quantiy
	) {
		return dataSupplier.getCandlesticks(
			mainAsset,
			collateralAsset,
			analysisInterval,
			null,
			quantiy
		);
	}

}