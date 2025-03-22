package org.rg.service;

import java.util.ArrayList;
import java.util.Arrays;
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

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class Asset {
	private static String NOT_AVAILABLE = "<center><i style=\"color: #D3D3D3;\">na</i></center>";
	private static String EVEN_ROW_BACKGROUND_COLOR = "#D6EEEE";
	private static String ODD_ROW_BACKGROUND_COLOR = "#F8F8F8";
	private static String HEADER_ROW_COLOR = "#7393B3";
	private static String CELL_PADDING ="15px";
	private Map<String, Object> values;

	public Asset(
		String assetName,
		String collateral,
		Map<Interval, BarSeries> candleSticks,
		Double rSILevel,
		Double spikeSizePercentage
	) {
		values = new LinkedHashMap<>();
		values.put(Collection.LABELS.get(Collection.ASSET_NAME_LABEL_INDEX), assetName);
		values.put(Collection.LABELS.get(Collection.COLLATERAL_LABEL_INDEX), collateral);
		values.put(Collection.LABELS.get(Collection.LATEST_1D_BAR_LABEL_INDEX), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
		values.put(Collection.LABELS.get(Collection.LATEST_4H_BAR_LABEL_INDEX), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));
		values.put(Collection.LABELS.get(Collection.RSI_LABEL_INDEX), rSILevel);
		values.put(Collection.LABELS.get(Collection.SPIKE_SIZE_PERCENTAGE_LABEL_INDEX), spikeSizePercentage);
	}


	public Asset addSupportAndResistance(Map<String, Double> values) {
		Map<String, Double> supportAndResistance =
			(Map<String, Double>)this.values.get(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX));
		if (supportAndResistance != null) {
			supportAndResistance.putAll(values);
		} else {
			this.values.put(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX), values);
		}
		return this;
	}
	public Asset addVariationPercenages(Map<String, Double> values) {
		Map<String, Double> variationPercentages =
			(Map<String, Double>)this.values.get(Collection.LABELS.get(Collection.VARIATION_PERCENTAGES_LABEL_INDEX));
		if (variationPercentages != null) {
			variationPercentages.putAll(values);
		} else {
			this.values.put(Collection.LABELS.get(Collection.VARIATION_PERCENTAGES_LABEL_INDEX), values);
		}
		return this;
	}
	public String getAssetName() {
		return (String)values.get(Collection.LABELS.get(Collection.ASSET_NAME_LABEL_INDEX));
	}
	public String getCollateral() {
		return (String)values.get(Collection.LABELS.get(Collection.COLLATERAL_LABEL_INDEX));
	}
	public Bar getLatest4HBar() {
		return (Bar)values.get(Collection.LABELS.get(Collection.LATEST_4H_BAR_LABEL_INDEX));
	}
	public Double getRSIOn1D() {
		return (Double)values.get(Collection.LABELS.get(Collection.RSI_LABEL_INDEX));
	}
	public Double getSpikeSizePercentageOn4H() {
		return (Double)values.get(Collection.LABELS.get(Collection.SPIKE_SIZE_PERCENTAGE_LABEL_INDEX));
	}
	public Map<String, Double> getSupportAndResistance() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX));
	}
	public Map<String, Double> getVariationPercentages() {
		return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.VARIATION_PERCENTAGES_LABEL_INDEX));
	}




	static class Collection {
		private static List<String> LABELS = Arrays.asList(
			"Asset name",
			"collateral",
			"Latest price from " + Interval.ONE_DAYS,
			"Latest price",
			"RSI on " + Interval.ONE_DAYS,
			"Spike size in % on " + Interval.FOUR_HOURS,
			"Price variation %",
			"Support and resistance levels"
		);
		private static int ASSET_NAME_LABEL_INDEX = 0;
		private static int COLLATERAL_LABEL_INDEX = 1;
		private static int LATEST_1D_BAR_LABEL_INDEX = 2;
		private static int LATEST_4H_BAR_LABEL_INDEX = 3;
		private static int RSI_LABEL_INDEX = 4;
		private static int SPIKE_SIZE_PERCENTAGE_LABEL_INDEX = 5;
		private static int VARIATION_PERCENTAGES_LABEL_INDEX = 6;
		private static int SUPPORT_AND_RESISTANCE_LABEL_INDEX = 7;
		private List<Asset> datas;
		private Set<String> dynamicLabelsGroupOne;
		private Set<String> dynamicLabelsGroupTwo;

		public Collection() {
			datas = new ArrayList<>();
			dynamicLabelsGroupOne = new LinkedHashSet<>();
			dynamicLabelsGroupTwo = new LinkedHashSet<>();
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
			Map<String, Double> map = data.getVariationPercentages();
			if (map != null) {
				dynamicLabelsGroupOne.addAll(map.keySet());
			}
			map = data.getSupportAndResistance();
			if (map != null) {
				dynamicLabelsGroupTwo.addAll(map.keySet());
			}
			Iterator<Asset> oldDataIterator = datas.iterator();
			while (oldDataIterator.hasNext()) {
				Asset dataAlreadyAdded = oldDataIterator.next();
				if (dataAlreadyAdded.getAssetName().equals(data.getAssetName()) && dataAlreadyAdded.getCollateral().equals(data.getCollateral())) {
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
			Map<String, Double> oldDSupportAndResistance = oldD.getSupportAndResistance();
			Map<String, Double> newDSupportAndResistance = newD.getSupportAndResistance();
			if (oldDSupportAndResistance != null && oldDSupportAndResistance != newDSupportAndResistance) {
				for (String label : oldDSupportAndResistance.keySet()) {
					newDSupportAndResistance.putIfAbsent(label, oldDSupportAndResistance.get(label));
				}
			}
			return newD;
		}

		public boolean isEmpty() {
			return datas.isEmpty();
		}

		public String toHTML() {
			datas.sort((assetOne, assetTwo) -> {
				return (assetOne.getAssetName() + assetOne.getCollateral()).compareTo(assetTwo.getAssetName() + assetTwo.getCollateral());
			});
			AtomicInteger rowCounter = new AtomicInteger(0);
			List<String> labels = new ArrayList<>(LABELS);
			for (String label : Arrays.asList(
				LABELS.get(LATEST_1D_BAR_LABEL_INDEX),
				LABELS.get(LATEST_4H_BAR_LABEL_INDEX),
				LABELS.get(RSI_LABEL_INDEX),
				LABELS.get(SPIKE_SIZE_PERCENTAGE_LABEL_INDEX)
			)) {
				boolean foundStaticValue = false;
				for (Asset asset : datas) {
					if (asset.values.get(label) != null) {
						foundStaticValue = true;
						break;
					}
				}
				if (!foundStaticValue) {
					labels.remove(label);
				}
			}
			return "<table style=\"border-spacing: 0px;font-size:" + Application.mailFontSizeInPixel + ";\">" +
				"<tr style=\"background-color:" + HEADER_ROW_COLOR + "\">" +
					String.join("", labels.stream().filter(hideColumnFilter()).map(label -> "<td style=\"padding: " + CELL_PADDING + "\"><b>" + label + "</b></td>").collect(Collectors.toList())) +
					String.join("", dynamicLabelsGroupOne.stream().map(label -> "<td style=\"padding: " + CELL_PADDING + "\"><b>" + label + "</b></td>").collect(Collectors.toList())) +
					String.join("", dynamicLabelsGroupTwo.stream().map(label -> "<td style=\"padding: " + CELL_PADDING + "\"><b>" + label + "</b></td>").collect(Collectors.toList())) +
				"</tr>" +
				String.join("", datas.stream().map(dt -> toHTML(dt, rowCounter.incrementAndGet())).collect(Collectors.toList())) +
			"</table>";
		}

		private Predicate<String> hideColumnFilter() {
			return label -> {
				return !label.equals(LABELS.get(VARIATION_PERCENTAGES_LABEL_INDEX)) &&
					!label.equals(LABELS.get(SUPPORT_AND_RESISTANCE_LABEL_INDEX)) &&
					!label.equals(LABELS.get(COLLATERAL_LABEL_INDEX)) &&
					!label.equals(LABELS.get(LATEST_1D_BAR_LABEL_INDEX));
			};
		}

		private String toHTML(Asset data, int rowCounter) {
			return "<tr style=\"background-color:" + (rowCounter % 2 == 0 ? EVEN_ROW_BACKGROUND_COLOR : ODD_ROW_BACKGROUND_COLOR) + "\">" +
					String.join(
    					"",LABELS.stream().filter(hideColumnFilter()).map(label -> {
    						Object value = data.values.get(label);
    						String htmlCellValue;
    						if (value != null) {
        						if (label.equals(LABELS.get(ASSET_NAME_LABEL_INDEX))) {
        							htmlCellValue = "<a href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(LABELS.get(COLLATERAL_LABEL_INDEX)) + "?type=isolated" + "\">" + data.values.get(label) + "</a>";
        						} else if (label.equals(LABELS.get(RSI_LABEL_INDEX))) {
        							htmlCellValue = "<p " + ((Double)value < 30 || ((Double)value > 70) ? (("style=\"color: " + ((Double)value < 30 ? "green" : "red")) + "\"") : "") +">" + Application.format((Double)value) + "</p>";
        						} else if (label.equals(LABELS.get(SPIKE_SIZE_PERCENTAGE_LABEL_INDEX))) {
        							htmlCellValue = "<p style=\"color: " + ((Double)value <= 0 ? "green" : "red") +"\">" + Application.format((Double) value) + "</p>";
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
    						return "<td style=\"padding: " + CELL_PADDING + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
					String.join(
    					"",dynamicLabelsGroupOne.stream().filter(hideColumnFilter()).map(label -> {
    						Double value = Optional.ofNullable(data.getVariationPercentages())
    						.map(supAndRes -> supAndRes.get(label)).orElseGet(() -> null);
    						String htmlCellValue;
    						if (value != null) {
    							htmlCellValue = "<p style=\"color: " + ((Double)value <= 0 ? "green" : "red") +"\">" + Application.format((Double)value) + "</p>";
    						} else {
    							htmlCellValue = NOT_AVAILABLE;
    						}
    						return "<td style=\"padding: " + CELL_PADDING + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
					String.join(
    					"",dynamicLabelsGroupTwo.stream().filter(hideColumnFilter()).map(label -> {
    						Double value = Optional.ofNullable(data.getSupportAndResistance())
    						.map(supAndRes -> supAndRes.get(label)).orElseGet(() -> null);
    						String htmlCellValue;
    						if (value != null) {
    							htmlCellValue = Application.format((Double)value);
    						} else {
    							htmlCellValue = NOT_AVAILABLE;
    						}
    						return "<td style=\"padding: " + CELL_PADDING + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
			"</tr>";
		}
	}

}