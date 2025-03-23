package org.rg.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class Asset {
	public static String DEFAULT_FONT_SIZE = "font-size:15px;";

	private static String TABLE_STYLE =
		"border-collapse: collapse;" +
		"border-spacing: 0px;"+
		"color: #606060;"+
		DEFAULT_FONT_SIZE;

	private static final String TABLE_DIV_STYLE =
		"overflow: auto;" +
		"height: 80%;" +
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
		String assetName,
		String collateral,
		Map<Interval, BarSeries> candleSticks
	) {
		values = new LinkedHashMap<>();
		values.put(Collection.LabelIndex.ASSET_NAME_LABEL_INDEX.toString(), assetName);
		values.put(Collection.LabelIndex.COLLATERAL_LABEL_INDEX.toString(), collateral);
		values.put(Collection.LabelIndex.LATEST_1D_BAR_LABEL_INDEX.toString(), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
		values.put(Collection.LabelIndex.LATEST_4H_BAR_LABEL_INDEX.toString(), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));
	}

	public Asset addRSI(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.RSI_LABEL_INDEX, values);
	}
	public Asset addStochasticRSI(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.STOCHASTIC_RSI_LABEL_INDEX, values);
	}
	public Asset addBollingerBands(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.BOLLINGER_BANDS_INDEX, values);
	}
	public Asset addSpikeSizePercentage(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.SPIKE_SIZE_PERCENTAGE, values);
	}
	public Asset addSupportAndResistance(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.SUPPORT_AND_RESISTANCE_LABEL_INDEX, values);
	}
	public Asset addVariationPercenages(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX, values);
	}
	private Asset addDynamicValues(Collection.LabelIndex label, Map<String, Double> values) {
		Map<String, Double> vals =
			(Map<String, Double>)this.values.get(label.toString());
		if (vals != null) {
			vals.putAll(values);
		} else {
			this.values.put(label.toString(), values);
		}
		return this;
	}

	public String getName() {
		return (String)values.get(Collection.LabelIndex.ASSET_NAME_LABEL_INDEX.toString());
	}
	public String getCollateral() {
		return (String)values.get(Collection.LabelIndex.COLLATERAL_LABEL_INDEX.toString());
	}
	public Bar getLatest4HBar() {
		return (Bar)values.get(Collection.LabelIndex.LATEST_4H_BAR_LABEL_INDEX.toString());
	}
	public Map<String, Double> getSpikeSizePercentage() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.SPIKE_SIZE_PERCENTAGE.toString());
	}
	public Map<String, Double> getRSI() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.RSI_LABEL_INDEX.toString());
	}
	public Map<String, Double> getStochasticRSI() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.STOCHASTIC_RSI_LABEL_INDEX.toString());
	}
	public Map<String, Double> getBollingerBands() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.BOLLINGER_BANDS_INDEX.toString());
	}
	public Map<String, Double> getVariationPercentages() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX.toString());
	}
	public Map<String, Double> getSupportAndResistance() {
		return (Map<String, Double>)values.get(Collection.LabelIndex.SUPPORT_AND_RESISTANCE_LABEL_INDEX.toString());
	}


	static class Collection {

		private static enum LabelIndex {
			ASSET_NAME_LABEL_INDEX("Asset name"),
			COLLATERAL_LABEL_INDEX("collateral"),
			LATEST_1D_BAR_LABEL_INDEX("Latest price from " + Interval.ONE_DAYS),
			LATEST_4H_BAR_LABEL_INDEX("Latest price"),
			//Dynamic values
			RSI_LABEL_INDEX("RSI"),
			STOCHASTIC_RSI_LABEL_INDEX("Stochastic RSI"),
			BOLLINGER_BANDS_INDEX("Bollinger Bands"),
			SPIKE_SIZE_PERCENTAGE("Spike size %"),
			VARIATION_PERCENTAGE_LABEL_INDEX("Price variation %"),
			SUPPORT_AND_RESISTANCE_LABEL_INDEX("Support and resistance levels");

			private String label;
			private LabelIndex(String label) {
				this.label = label;
			}

			@Override
			public String toString() {
				return label;
			}

			public static LabelIndex find(String lb) {
				return Stream.of(LabelIndex.values()).filter(lI -> lI.label.equals(lb)).findAny().orElseGet(() -> null);
			}

			public static LabelIndex find(int index) {
				return Stream.of(LabelIndex.values()).filter(lI -> lI.ordinal() == index).findAny().orElseGet(() -> null);
			}
		}

		private List<Asset> datas;
		private Set<String>[] dynamicLabelsGroup = new Set[6];
		private List<Map.Entry<Set<String>, Integer>> dynamicLabelsGroupToLabelIndex;

		public Collection() {
			datas = new ArrayList<>();
			for (int i = 0; i < dynamicLabelsGroup.length; i++) {
				dynamicLabelsGroup[i] = new LinkedHashSet<>();
			}
			dynamicLabelsGroupToLabelIndex = new ArrayList<>();
			for (
				int i = LabelIndex.values().length - 1,
				k = dynamicLabelsGroup.length - 1,
				iterationIndex = 0;
				iterationIndex < dynamicLabelsGroup.length;
				i--,
				k--,
				iterationIndex++
			) {
				LabelIndex labelIndex = LabelIndex.values()[i];
				dynamicLabelsGroupToLabelIndex.add(new AbstractMap.SimpleEntry<>(dynamicLabelsGroup[k], labelIndex.ordinal()));
			}
			Collections.reverse(dynamicLabelsGroupToLabelIndex);
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
			for (Map.Entry<Set<String>, Integer> dynamicLabelGroupToLabelIndex : dynamicLabelsGroupToLabelIndex) {
				Map<String, Object> map =
					(Map<String, Object>)data.values.get(LabelIndex.find(dynamicLabelGroupToLabelIndex.getValue()).toString());
				if (map != null) {
					dynamicLabelGroupToLabelIndex.getKey().addAll(map.keySet());
				}
			}
			Iterator<Asset> oldDataIterator = datas.iterator();
			while (oldDataIterator.hasNext()) {
				Asset dataAlreadyAdded = oldDataIterator.next();
				if (dataAlreadyAdded.getName().equals(data.getName()) && dataAlreadyAdded.getCollateral().equals(data.getCollateral())) {
					data = mergeInNewData(dataAlreadyAdded, data);
					oldDataIterator.remove();
					break;
				}
			}
			datas.add(data);
 			return this;
		}

		private Asset mergeInNewData(Asset oldD, Asset newD) {
			for (LabelIndex label : LabelIndex.values()) {
				newD.values.putIfAbsent(label.toString(), oldD.values.get(label.toString()));
			}
			for (Map.Entry<Set<String>, Integer> dynamicLabelGroupToLabelIndex : dynamicLabelsGroupToLabelIndex) {
				mergeDynamicValues(
					(Map<String, Object>)oldD.values.get(LabelIndex.find(dynamicLabelGroupToLabelIndex.getValue()).toString()),
					(Map<String, Object>)newD.values.get(LabelIndex.find(dynamicLabelGroupToLabelIndex.getValue()).toString())
				);
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
				return (assetOne.getName() + assetOne.getCollateral()).compareTo(assetTwo.getName() + assetTwo.getCollateral());
			});
			AtomicInteger rowCounter = new AtomicInteger(0);
			List<String> labels = Stream.of(LabelIndex.values()).map(LabelIndex::toString).collect(Collectors.toList());
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
				return !label.equals((LabelIndex.LATEST_1D_BAR_LABEL_INDEX.toString()));
			};
		}

		private String toHTML(Asset data, int rowCounter) {
			return "<tr style=\"" + (rowCounter % 2 == 0 ? EVEN_ROW_STYLE : ODD_ROW_STYLE) + "\">" +
					String.join(
    					"",Stream.of(LabelIndex.values()).map(LabelIndex::toString).filter(showColumnFilter()).map(label -> {
    						Object value = data.values.get(label);
    						String htmlCellValue = "";
    						if (value != null) {
        						if (label.equals(LabelIndex.ASSET_NAME_LABEL_INDEX.toString())) {
        							htmlCellValue = "<a href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(LabelIndex.COLLATERAL_LABEL_INDEX.toString()) + "?type=isolated" + "\">" + data.values.get(label) + "</a>";
        						} else if (value instanceof Double) {
        							htmlCellValue = Application.format((Double)value);
        						} else if (value instanceof Bar) {
        							htmlCellValue = "" + Application.format(((Bar)value).getClosePrice().doubleValue());
        						} else if (value instanceof Map) {
        							htmlCellValue = (((Map<String, Object>)value).entrySet()).stream().map(rec -> {
        								if (label.equals(LabelIndex.RSI_LABEL_INDEX.toString())||
    										label.equals(LabelIndex.STOCHASTIC_RSI_LABEL_INDEX.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span " + ((Double)rec.getValue() < 30 || ((Double)rec.getValue() > 70) ? (("style=\"color: " + ((Double)rec.getValue() < 30 ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else if (label.equals(LabelIndex.BOLLINGER_BANDS_INDEX.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span " + (rec.getKey().contains("low") || label.contains("up") ? (("style=\"color: " + (rec.getKey().contains("low") ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else if (label.equals(LabelIndex.SPIKE_SIZE_PERCENTAGE.toString()) ||
        									label.equals(LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX.toString())) {
        									return "<b>" + rec.getKey() + "</b>=" +
        										"<span style=\"color: " + ((Double)rec.getValue() <= 0 ? "green" : "red") +"\">" + Application.format((Double)rec.getValue()) + "</span>";
        								} else {
        									return "<b>" + rec.getKey() + "</b>=" + Application.format((Double)rec.getValue());
        								}
        							}).collect(Collectors.joining("</br>"));
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