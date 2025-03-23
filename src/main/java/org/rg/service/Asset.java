package org.rg.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class Asset {

	public static enum ValueName {
		ASSET_NAME("Asset name"),
		COLLATERAL("collateral"),
		LATEST_1D_BAR("Latest price from " + Interval.ONE_DAYS),
		LATEST_4H_BAR("Latest price"),
		//Dynamic values
		RSI("RSI"),
		STOCHASTIC_RSI("Stochastic RSI"),
		BOLLINGER_BANDS("Bollinger Bands"),
		SPIKE_SIZE("Spike size %"),
		VARIATION_PERCENTAGE("Price variation %"),
		SUPPORT_AND_RESISTANCE("Support and resistance levels");

		private String label;
		private ValueName(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}

		public static ValueName find(String lb) {
			return Stream.of(ValueName.values()).filter(lI -> lI.label.equals(lb)).findAny().orElseGet(() -> null);
		}

		public static ValueName find(int index) {
			return Stream.of(ValueName.values()).filter(lI -> lI.ordinal() == index).findAny().orElseGet(() -> null);
		}
	}

	public static String DEFAULT_FONT_SIZE = "font-size:15px;";

	private static String TABLE_STYLE =
		"border-collapse: collapse;" +
		"border-spacing: 0px;"+
		"color: #606060;"+
		DEFAULT_FONT_SIZE;

	private static final String TABLE_DIV_STYLE =
		"overflow: auto;" +
		"height: 475px;" +
		"width: 100%;";

	private static final String NOT_AVAILABLE =
		"<center><i style=\"color: #C0C0C0;\">na</i></center>";

	private static final String EVEN_ROW_STYLE =
		"background-color: #D6EEEE;";

	private static final String ODD_ROW_STYLE =
		"background-color: #F8F8F8;";

	private static final String HEADER_ROW_STYLE =
		"";

	private static final String CELL_STYLE =
		"padding: 15px;" +
		"vertical-align: top;";

	private static final String BLOCKED_HEADER_STYLE =
		"position: sticky;" +
		"top: 0;" +
		"z-index: 1;";

	private static final String HEADER_CELL_STYLE =
		CELL_STYLE +
		BLOCKED_HEADER_STYLE +
		"background: #7393B3;" +
		"color: white;";

	private Map<String, Object> values;

	public Asset(
		String assetName, String collateral,
		Map<Interval, BarSeries> candleSticks
	) {
		values = new LinkedHashMap<>();
		values.put(ValueName.ASSET_NAME.toString(), assetName);
		values.put(ValueName.COLLATERAL.toString(), collateral);
		values.put(ValueName.LATEST_1D_BAR.toString(), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
		values.put(ValueName.LATEST_4H_BAR.toString(), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));
	}

	public Asset addDynamicValues(ValueName label, Map<String, Double> values) {
		Map<String, Double> vals =
			(Map<String, Double>)this.values.get(label.toString());
		if (vals != null) {
			vals.putAll(values);
		} else {
			this.values.put(label.toString(), values);
		}
		return this;
	}

	public <O> O get(ValueName key) {
		return (O)values.get(key.toString());
	}

	public String getName() {
		return get(ValueName.ASSET_NAME);
	}
	public String getCollateral() {
		return get(ValueName.COLLATERAL);
	}
	public Bar getLatest4HBar() {
		return get(ValueName.LATEST_4H_BAR);
	}
	public Map<String, Double> getSpikeSizePercentage() {
		return get(ValueName.SPIKE_SIZE);
	}
	public Map<String, Double> getRSI() {
		return get(ValueName.RSI);
	}
	public Map<String, Double> getStochasticRSI() {
		return get(ValueName.STOCHASTIC_RSI);
	}
	public Map<String, Double> getBollingerBands() {
		return get(ValueName.BOLLINGER_BANDS);
	}
	public Map<String, Double> getVariationPercentages() {
		return get(ValueName.VARIATION_PERCENTAGE);
	}
	public Map<String, Double> getSupportAndResistance() {
		return get(ValueName.SUPPORT_AND_RESISTANCE);
	}

	static class Collection {

		private List<Asset> datas;

		public Collection() {
			datas = new ArrayList<>();
		}

		public int size() {
			return datas.size();
		}

		public void clear() {
			datas.clear();
		}

		public synchronized Asset.Collection addOrMergeAndReplace(Asset data) {
			if (data == null) {
				return this;
			}
			Iterator<Asset> oldDataIterator = datas.iterator();
			while (oldDataIterator.hasNext()) {
				Asset dataAlreadyAdded = oldDataIterator.next();
				if (dataAlreadyAdded.get(ValueName.ASSET_NAME).equals(data.get(ValueName.ASSET_NAME)) &&
						dataAlreadyAdded.get(ValueName.COLLATERAL).equals(data.get(ValueName.COLLATERAL))) {
					data = mergeInNewData(dataAlreadyAdded, data);
					oldDataIterator.remove();
					break;
				}
			}
			datas.add(data);
 			return this;
		}

		private Asset mergeInNewData(Asset oldD, Asset newD) {
			for (ValueName label : ValueName.values()) {
				Object oldValue = oldD.values.get(label.toString());
				newD.values.putIfAbsent(label.toString(), oldValue);
				Object newValue = newD.values.get(label.toString());
				if (oldValue instanceof Map) {
					mergeDynamicValues(
						(Map<String, Object>)oldValue,
						(Map<String, Object>)newValue
					);
				}
			}
			return newD;
		}

		private void mergeDynamicValues(
			Map<String, Object> oldDynamicValues,
			Map<String, Object> newDynamicValues
		) {
			if (oldDynamicValues != null && oldDynamicValues != newDynamicValues) {
				for (String label : oldDynamicValues.keySet()) {
					newDynamicValues.putIfAbsent(label, oldDynamicValues.get(label));
				}
			}
		}

		public boolean isEmpty() {
			return datas.isEmpty();
		}

		public void filter(Predicate<Asset> assetPredicate) {
			Iterator<Asset> assetIterator = datas.iterator();
			while (assetIterator.hasNext()) {
				Asset asset = assetIterator.next();
				if (!assetPredicate.test(asset)) {
					assetIterator.remove();
				}
			}
		}

		public String toHTML() {
			datas.sort((assetOne, assetTwo) -> {
				return ((String)assetOne.get(ValueName.ASSET_NAME) + assetOne.get(ValueName.COLLATERAL))
					.compareTo((String)assetTwo.get(ValueName.ASSET_NAME) + assetTwo.get(ValueName.COLLATERAL));
			});
			AtomicInteger rowCounter = new AtomicInteger(0);
			List<String> labels = Stream.of(ValueName.values()).map(ValueName::toString).collect(Collectors.toList());
			return
				"<div style=\"" + TABLE_DIV_STYLE + "\">" +
					"<table style=\"" + TABLE_STYLE + "\">" +
						"<thead style=\"" + BLOCKED_HEADER_STYLE + "\">" +
							"<tr style=\"" + HEADER_ROW_STYLE + "\">" +
								String.join("", labels.stream().filter(showColumnFilter()).map(label -> "<th style=\"" + HEADER_CELL_STYLE + "\"><b>" + label + "</b></th>").collect(Collectors.toList())) +
							"</tr>" +
						"</thead>" +
						String.join("", datas.stream().map(dt -> toHTML(dt, rowCounter.incrementAndGet())).collect(Collectors.toList())) +
					"</table>" +
				"</div>";
		}

		private Predicate<String> showColumnFilter() {
			return label -> {
				return !label.equals((ValueName.LATEST_1D_BAR.toString()));
			};
		}

		private String toHTML(Asset data, int rowCounter) {
			return "<tr style=\"" + (rowCounter % 2 == 0 ? EVEN_ROW_STYLE : ODD_ROW_STYLE) + "\">" +
					String.join(
    					"",Stream.of(ValueName.values()).map(ValueName::toString).filter(showColumnFilter()).map(label -> {
    						Object value = data.values.get(label);
    						String htmlCellValue = "";
    						if (value != null) {
        						if (label.equals(ValueName.ASSET_NAME.toString())) {
        							htmlCellValue = "<a href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(ValueName.COLLATERAL.toString()) + "?type=isolated" + "\">" + data.values.get(label) + "</a>";
        						} else if (value instanceof Double) {
        							htmlCellValue = Application.format((Double)value);
        						} else if (value instanceof Bar) {
        							htmlCellValue = "" + Application.format(((Bar)value).getClosePrice().doubleValue());
        						} else if (value instanceof Map) {
        							htmlCellValue = (((Map<String, Object>)value).entrySet()).stream().map(rec -> {
        								if (label.equals(ValueName.RSI.toString())||
    										label.equals(ValueName.STOCHASTIC_RSI.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span " + ((Double)rec.getValue() < 30 || ((Double)rec.getValue() > 70) ? (("style=\"color: " + ((Double)rec.getValue() < 30 ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else if (label.equals(ValueName.BOLLINGER_BANDS.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span " + (rec.getKey().contains("l") || rec.getKey().contains("u") ? (("style=\"color: " + (rec.getKey().contains("l") ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else if (label.equals(ValueName.SPIKE_SIZE.toString()) ||
        									label.equals(ValueName.VARIATION_PERCENTAGE.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span style=\"color: " + ((Double)rec.getValue() <= 0 ? "green" : "red") +"\">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else {
        									return "<b>" + rec.getKey() + "</b>=" + Application.format((Double)rec.getValue());
        								}
        							}).collect(Collectors.joining("<br/>"));
        						} else {
        							htmlCellValue = value.toString();
        						}
    						} else {
    							htmlCellValue = NOT_AVAILABLE;
    						}
    						return "<td style=\"" + CELL_STYLE + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
			"</tr>";
		}

	}

}