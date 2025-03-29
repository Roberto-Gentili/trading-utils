package org.rg.finance;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import org.burningwave.core.assembler.StaticComponentContainer;
import org.burningwave.core.concurrent.QueuedTaskExecutor.ProducerTask;
import org.ta4j.core.BarSeries;

public class BurningwaveAssetDataLoader implements AssetDataLoader {
	private DataSupplier dataSupplier;
	private List<ProducerTask<Map.Entry<Interval, BarSeries>>> assetDataLoaders;
	private String mainAsset;
	private String collateralAsset;

	public BurningwaveAssetDataLoader(DataSupplier dataSupplier, String mainAsset, String collateralAsset) {
		this.dataSupplier = dataSupplier;
		this.assetDataLoaders = new CopyOnWriteArrayList<>();
		this.mainAsset = mainAsset;
		this.collateralAsset = collateralAsset;
	}

	@Override
	public BurningwaveAssetDataLoader loadInParallel(Interval interval, int quantiy) {
		ProducerTask<Map.Entry<Interval, BarSeries>> task = StaticComponentContainer.BackgroundExecutor.createProducerTask(tsk -> {
			Map.Entry<Interval, BarSeries> entry = new AbstractMap.SimpleEntry<>(interval, retrieveCandlestick(interval, quantiy));
			return entry;
		});
		task.submit();
		assetDataLoaders.add(
			task
		);
		return this;
	}

	@Override
	public Map<Interval, BarSeries> retrieve() {
		Map<Interval, BarSeries> data = new LinkedHashMap<>();
		for(ProducerTask<Entry<Interval, BarSeries>> assetDataLoader : assetDataLoaders) {
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