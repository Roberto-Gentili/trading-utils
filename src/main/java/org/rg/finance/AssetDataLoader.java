package org.rg.finance;

import java.util.Map;

import org.ta4j.core.BarSeries;

public interface AssetDataLoader {

	AssetDataLoader loadInParallel(Interval interval, int quantiy);

	Map<Interval, BarSeries> retrieve();

}