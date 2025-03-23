package org.rg.service;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class Asset {
	private static String TABLE_STYLE =
		"border-collapse: collapse;" +
 		"width:100%!important;" +
		"border-spacing: 0px;"+
		"color: #606060;"+
		"font-size:" + Application.mailFontSizeInPixel + ";";

	private static String NOT_AVAILABLE =
		"<center><i style=\"color: #C0C0C0;\">na</i></center>";

	private static String EVEN_ROW_STYLE =
		"background-color: #D6EEEE;";

	private static String ODD_ROW_STYLE =
		"background-color: #F8F8F8;";

	private static String HEADER_ROW_STYLE =
		"";

	private static String CELL_STYLE =
		"padding: 15px;";

	private static String HEADER_CELL_STYLE =
		CELL_STYLE +
		"position: sticky;" +
		"top: 0;" +
		"z-index: 1;" +
		"background: #7393B3;" +
		"color: white;";

	private Map<String, Object> values;

	public Asset(
		String assetName,
		String collateral,
		Map<Interval, BarSeries> candleSticks
	) {
		values = new LinkedHashMap<>();
		values.put(Collection.LABELS.get(Collection.LabelIndex.ASSET_NAME_LABEL_INDEX.ordinal()), assetName);
		values.put(Collection.LABELS.get(Collection.LabelIndex.COLLATERAL_LABEL_INDEX.ordinal()), collateral);
		values.put(Collection.LABELS.get(Collection.LabelIndex.LATEST_1D_BAR_LABEL_INDEX.ordinal()), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
		values.put(Collection.LABELS.get(Collection.LabelIndex.LATEST_4H_BAR_LABEL_INDEX.ordinal()), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));
	}

	public Asset addRSI(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.RSI_LABEL_INDEX.ordinal(), values);
	}
	public Asset addStochasticRSI(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.STOCHASTIC_RSI_LABEL_INDEX.ordinal(), values);
	}
	public Asset addBollingerBands(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.BOLLINGER_BANDS_INDEX.ordinal(), values);
	}
	public Asset addSpikeSizePercentage(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.SPIKE_SIZE_PERCENTAGE.ordinal(), values);
	}
	public Asset addSupportAndResistance(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.SUPPORT_AND_RESISTANCE_LABEL_INDEX.ordinal(), values);
	}
	public Asset addVariationPercenages(Map<String, Double> values) {
		return addDynamicValues(Collection.LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX.ordinal(), values);
	}
	private Asset addDynamicValues(int index, Map<String, Double> values) {
		Map<String, Double> vals =
			(Map<String, Double>)this.values.get(Collection.LABELS.get(index));
		if (vals != null) {
			vals.putAll(values);
		} else {
			this.values.put(Collection.LABELS.get(index), values);
		}
		return this;
	}

	public String getName() {
		return (String)values.get(Collection.LABELS.get(Collection.LabelIndex.ASSET_NAME_LABEL_INDEX.ordinal()));
	}
	public String getCollateral() {
		return (String)values.get(Collection.LABELS.get(Collection.LabelIndex.COLLATERAL_LABEL_INDEX.ordinal()));
	}
	public Bar getLatest4HBar() {
		return (Bar)values.get(Collection.LABELS.get(Collection.LabelIndex.LATEST_4H_BAR_LABEL_INDEX.ordinal()));
	}
	public Map<String, Double> getSpikeSizePercentage() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.SPIKE_SIZE_PERCENTAGE.ordinal()));
	}
	public Map<String, Double> getRSI() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.RSI_LABEL_INDEX.ordinal()));
	}
	public Map<String, Double> getStochasticRSI() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.STOCHASTIC_RSI_LABEL_INDEX.ordinal()));
	}
	public Map<String, Double> getBollingerBands() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.BOLLINGER_BANDS_INDEX.ordinal()));
	}
	public Map<String, Double> getVariationPercentages() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX.ordinal()));
	}
	public Map<String, Double> getSupportAndResistance() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.LabelIndex.SUPPORT_AND_RESISTANCE_LABEL_INDEX.ordinal()));
	}


	static class Collection {
		private static List<String> LABELS = Arrays.asList(
			"Asset name",
			"collateral",
			"Latest price from " + Interval.ONE_DAYS,
			"Latest price",
			"RSI",
			"Stochastic RSI",
			"Bollinger Bands",
			"Spike size %",
			"Price variation %",
			"Support and resistance levels"
		);
		private static enum LabelIndex {
			ASSET_NAME_LABEL_INDEX,
			COLLATERAL_LABEL_INDEX,
			LATEST_1D_BAR_LABEL_INDEX,
			LATEST_4H_BAR_LABEL_INDEX,
			//Dynamic values
			RSI_LABEL_INDEX,
			STOCHASTIC_RSI_LABEL_INDEX,
			BOLLINGER_BANDS_INDEX,
			SPIKE_SIZE_PERCENTAGE,
			VARIATION_PERCENTAGE_LABEL_INDEX,
			SUPPORT_AND_RESISTANCE_LABEL_INDEX
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
					(Map<String, Object>)data.values.get(Collection.LABELS.get(dynamicLabelGroupToLabelIndex.getValue()));
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
			for (String label : LABELS) {
				newD.values.putIfAbsent(label, oldD.values.get(label));
			}
			for (Map.Entry<Set<String>, Integer> dynamicLabelGroupToLabelIndex : dynamicLabelsGroupToLabelIndex) {
				mergeDynamicValues(
					(Map<String, Object>)oldD.values.get(Collection.LABELS.get(dynamicLabelGroupToLabelIndex.getValue())),
					(Map<String, Object>)newD.values.get(Collection.LABELS.get(dynamicLabelGroupToLabelIndex.getValue()))
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
			List<String> labels = new ArrayList<>(LABELS);
			return
				"<div style=\"overflow: auto; height: 450px;\">" +
					"<table style=\"" + TABLE_STYLE + "\">" +
						"<thead>" +
							"<tr style=\"" + HEADER_ROW_STYLE + "\">" +
								String.join("", labels.stream().filter(showColumnFilter()).map(label -> "<th style=\"" + HEADER_CELL_STYLE + "\"><b>" + label + "</b></th>").collect(Collectors.toList())) +
								String.join("", Stream.of(dynamicLabelsGroup).map(dynamicLabelGroup -> {
									return
										String.join("", dynamicLabelGroup.stream().map(label ->
											"<th style=\"" + HEADER_CELL_STYLE + "\"><b>" + label + "</b></th>"
										).collect(Collectors.toList()));
								}).collect(Collectors.toList())) +
							"</tr>" +
						"</thead>" +
						String.join("", datas.stream().map(dt -> toHTML(dt, rowCounter.incrementAndGet())).collect(Collectors.toList())) +
					"</table>" +
				"</div>";
		}

		private Predicate<String> showColumnFilter() {
			return label -> {
				for (Map.Entry<Set<String>, Integer> dynamicLabelGroupToLabelIndex : dynamicLabelsGroupToLabelIndex) {
					if (label.equals(LABELS.get(dynamicLabelGroupToLabelIndex.getValue()))) {
						return false;
					}
				}
				return !label.equals(LABELS.get(LabelIndex.LATEST_1D_BAR_LABEL_INDEX.ordinal()));
			};
		}

		private String toHTML(Asset data, int rowCounter) {
			return "<tr style=\"" + (rowCounter % 2 == 0 ? EVEN_ROW_STYLE : ODD_ROW_STYLE) + "\">" +
					String.join(
    					"",LABELS.stream().filter(showColumnFilter()).map(label -> {
    						Object value = data.values.get(label);
    						String htmlCellValue;
    						if (value != null) {
        						if (label.equals(LABELS.get(LabelIndex.ASSET_NAME_LABEL_INDEX.ordinal()))) {
        							htmlCellValue = "<a href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(LABELS.get(LabelIndex.COLLATERAL_LABEL_INDEX.ordinal())) + "?type=isolated" + "\">" + data.values.get(label) + "</a>";
        						} else if (value instanceof Double) {
        							htmlCellValue = Application.format((Double)value);
        						} else if (value instanceof Bar) {
        							htmlCellValue = "" + Application.format(((Bar)value).getClosePrice().doubleValue());
        						} else {
        							htmlCellValue = value.toString();
        						}
    						} else {
    							htmlCellValue = NOT_AVAILABLE;
    						}
    						return "<td style=\"" + CELL_STYLE + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
					String.join(
						"",
						dynamicLabelsGroupToLabelIndex.stream().map(dynamicLabelGroup -> {
							return String.join(
		    					"",dynamicLabelGroup.getKey().stream().map(label -> {
		    						Object value = Optional.ofNullable((Map<String, Object>)data.values.get(Collection.LABELS.get(dynamicLabelGroup.getValue())))
		    						.map(keyAndVal -> keyAndVal.get(label)).orElseGet(() -> null);
		    						String htmlCellValue;
		    						if (value != null) {
		    							if (
	    									dynamicLabelGroup.getValue().compareTo(LabelIndex.RSI_LABEL_INDEX.ordinal()) == 0 ||
	    									dynamicLabelGroup.getValue().compareTo(LabelIndex.STOCHASTIC_RSI_LABEL_INDEX.ordinal()) == 0
	    								) {
		    								htmlCellValue = "<p " + ((Double)value < 30 || ((Double)value > 70) ? (("style=\"color: " + ((Double)value < 30 ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)value) + "</p>";
		    							} else if (dynamicLabelGroup.getValue().compareTo(LabelIndex.BOLLINGER_BANDS_INDEX.ordinal()) == 0) {
		    								htmlCellValue = "<p " + (label.contains("low") || label.contains("up") ? (("style=\"color: " + (label.contains("low") ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)value) + "</p>";
		    							} else if (
	    									dynamicLabelGroup.getValue().compareTo(LabelIndex.SPIKE_SIZE_PERCENTAGE.ordinal()) == 0 ||
	    									dynamicLabelGroup.getValue().compareTo(LabelIndex.VARIATION_PERCENTAGE_LABEL_INDEX.ordinal()) == 0
    									) {
		    								htmlCellValue = "<p style=\"color: " + ((Double)value <= 0 ? "green" : "red") +"\">" + Application.format((Double)value) + "</p>";
		    							} else {
		    								htmlCellValue = Application.format((Double)value);
		    							}
		    						} else {
		    							htmlCellValue = NOT_AVAILABLE;
		    						}
		    						return "<td style=\"" + CELL_STYLE + "\">" + htmlCellValue + "</td>";
		    					}).collect(Collectors.toList())
		    				);
						}).collect(Collectors.toList())
					) +
			"</tr>";
		}

	}

}