package org.rg.service;

import java.util.Map;

import org.rg.finance.Interval;
import org.ta4j.core.BarSeries;

interface ParallelIterator {

	ParallelIterator loadInParallel(Interval interval, int quantiy);

	Map<Interval, BarSeries> retrieve();

}