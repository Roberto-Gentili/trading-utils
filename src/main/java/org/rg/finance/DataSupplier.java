package org.rg.finance;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;

public interface DataSupplier {

	public Collection<Map<String, Object>> available();

	public Collection<Map<String, Object>> availableFiltered(Collection<String> dafaultAssets, List<Map<String, String>> assetFilter);

	public BarSeries getCandlesticks(
		String assetName,
		String collateral,
		Interval interval,
		Date endTime,
		Integer quantity
	);

}
