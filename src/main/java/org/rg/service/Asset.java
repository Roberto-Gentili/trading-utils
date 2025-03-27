package org.rg.service;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.rg.finance.Interval;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

public class Asset {
	private final static String DECIMAL_SEPARATOR = Optional.of(Character.valueOf(
			new java.text.DecimalFormatSymbols().getDecimalSeparator()
		).toString()).map(sep -> sep.equals(".") ? "\\"+ sep : sep).get();

	public static enum ValueName {
		ASSET_NAME("Asset name"),
		COLLATERAL("Collateral"),
		LATEST_1D_BAR("Latest price from " + Interval.ONE_DAYS),
		LATEST_4H_BAR("Latest price"),
		//Dynamic values
		EMA("EMA"),
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

	public static final String DEFAULT_FONT_SIZE = "font-size: 15px;";

	static String DEFAULT_TEXT_COLOR = Color.DEFAULT.getCode();

	private static String TABLE_STYLE =
		"border-collapse: collapse;" +
		"border-spacing: 0px;"+
		"color: " + DEFAULT_TEXT_COLOR +";"+
		DEFAULT_FONT_SIZE;

	private static final String TABLE_DIV_STYLE =
		"overflow: auto;" +
		"height: 85%;" +
		"width: 95%;";

	private static final String BLOCKED_HEADER_STYLE =
		"position: sticky;" +
		"top: 0;" +
		"z-index: 1;";

	private static final String HEADER_CELL_STYLE =
		"background: #7393B3;" +
		"color: white;";

	private static final String HEADER_ROW_STYLE =
		"";

	private static final String NOT_AVAILABLE =
		"<i style=\"color: #C0C0C0;\">na</i>";

	private static final String EVEN_ROW_STYLE =
		"background-color: #D6EEEE;";

	private static final String ODD_ROW_STYLE =
		"background-color: #F8F8F8;";


	private static final String CELL_STYLE =
		"padding: 15px;" +
		"vertical-align: top;";

	private static final String ON_TOP_FIXED_HEADER_CELL_STYLE =
		CELL_STYLE +
		BLOCKED_HEADER_STYLE +
		HEADER_CELL_STYLE;



	private Map<String, Object> values;

	public Asset(
		String assetName,
		String collateral,
		Map<Interval, BarSeries> candleSticks
	) {
		values = new LinkedHashMap<>();
		values.put(ValueName.ASSET_NAME.toString(),
		ColoredString.valueOf(assetName));
		values.put(ValueName.COLLATERAL.toString(), collateral);
		values.put(ValueName.LATEST_1D_BAR.toString(), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
		values.put(ValueName.LATEST_4H_BAR.toString(), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));

	}

	public Asset addDynamicValues(ValueName label, Map<String, Object> values) {
		Map<String, Object> vals =
			(Map<String, Object>)this.values.get(label.toString());
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

	public Asset highligtName(String color) {
		getColoredName().color(color);
		return this;
	}

	public ColoredString getColoredName() {
		return (ColoredString)get(ValueName.ASSET_NAME);
	}
	public String getName() {
		return getColoredName().value();
	}

	public String getCollateral() {
		return get(ValueName.COLLATERAL);
	}
	public Bar getLatest4HBar() {
		return get(ValueName.LATEST_4H_BAR);
	}
	public Map<String, Number> getSpikeSizePercentage() {
		return get(ValueName.SPIKE_SIZE);
	}
	public Map<String, Number> getEMA() {
		return get(ValueName.EMA);
	}
	public Map<String, Number> getRSI() {
		return get(ValueName.RSI);
	}
	public Map<String, Number> getStochasticRSI() {
		return get(ValueName.STOCHASTIC_RSI);
	}
	public Map<String, Number> getBollingerBands() {
		return get(ValueName.BOLLINGER_BANDS);
	}
	public Map<String, Number> getVariationPercentages() {
		return get(ValueName.VARIATION_PERCENTAGE);
	}
	public Map<String, Number> getSupportAndResistance() {
		return get(ValueName.SUPPORT_AND_RESISTANCE);
	}

	public static String format(Number value) {
		String output = String.format("%1$,.8f", value);
		String[] outputValueSplitted = output.split(DECIMAL_SEPARATOR);
		if (outputValueSplitted[0].length() >= 3) {
			return String.format("%1$,.4f", value);
		} else {
			return output;
		}
	}

	static class Collection {

		private List<Asset> datas;
		private boolean onTopFixedHeader;

		public Collection() {
			datas = new ArrayList<>();
			onTopFixedHeader = true;
		}

		public Collection setOnTopFixedHeader(boolean flag) {
			this.onTopFixedHeader = flag;
			return this;
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
				if (dataAlreadyAdded.getName().equals(data.getName()) &&
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

		public Collection filter(Predicate<Asset> assetPredicate) {
			Iterator<Asset> assetIterator = datas.iterator();
			while (assetIterator.hasNext()) {
				Asset asset = assetIterator.next();
				if (!assetPredicate.test(asset)) {
					assetIterator.remove();
				}
			}
			return this;
		}

		public List<Asset> getAssetList() {
			return this.datas;
		}

		public String toHTML() {
			datas.sort((assetOne, assetTwo) -> {
				return (assetOne.getColoredName().value() + assetOne.get(ValueName.COLLATERAL))
					.compareTo(assetTwo.getColoredName().value() + assetTwo.get(ValueName.COLLATERAL));
			});
			AtomicInteger rowCounter = new AtomicInteger(0);
			List<String> header = Stream.of(ValueName.values()).map(ValueName::toString).collect(Collectors.toList());
			return
				String.join(", ", datas.stream().map(dt -> {
					return "<a href=\"#" + dt.getName() + dt.getCollateral() + "\">" + dt.getName() + "/" + dt.getCollateral() + "</a>";
				}).collect(Collectors.toList())) + "<br/><br/>" +
				"<center>"+"" +
					"<div style=\"" + TABLE_DIV_STYLE + "\">" +
						"<table style=\"" + TABLE_STYLE + "\">" +
							(onTopFixedHeader ?
								"<thead style=\"" + BLOCKED_HEADER_STYLE + "\">" +
									"<tr style=\"" + HEADER_ROW_STYLE + "\">" +
										String.join("", header.stream().filter(showColumnFilter()).map(label -> "<th style=\"" + ON_TOP_FIXED_HEADER_CELL_STYLE + "\"><b>" + label + "</b></th>").collect(Collectors.toList())) +
									"</tr>" +
								"</thead>"
							: "") +
							String.join("", datas.stream().map(dt -> toHTML(header, dt, rowCounter.incrementAndGet(), onTopFixedHeader)).collect(Collectors.toList())) +
						"</table>" +
					"</div>"+
				"</center>";
		}

		private Predicate<String> showColumnFilter() {
			return label -> {
				return !label.equals((ValueName.LATEST_1D_BAR.toString())) &&
					!label.equals((ValueName.COLLATERAL.toString()));
			};
		}

		private String toHTML(List<String> header, Asset data, int rowCounter, boolean onTopFixedHeader) {
			return
				(onTopFixedHeader ? "" :
					"<tr style=\"" + HEADER_ROW_STYLE + "\">" +
						String.join("", header.stream().filter(showColumnFilter()).map(label -> "<td style=\"" + HEADER_CELL_STYLE + "\"><b>" + label + "</b></td>").collect(Collectors.toList())) +
					"</tr>"
				) +
				"<tr style=\"" + (rowCounter % 2 == 0 ? EVEN_ROW_STYLE : ODD_ROW_STYLE) + "\">" +
					String.join(
    					"",Stream.of(ValueName.values()).map(ValueName::toString).filter(showColumnFilter()).map(label -> {
    						Object value = data.values.get(label);
    						String htmlCellValue = "";
    						String cellStyle = CELL_STYLE;
    						if (value != null) {
    							if (label.equals(ValueName.ASSET_NAME.toString())) {
    								ColoredString assetColoredName = data.getColoredName();
    								if (!assetColoredName.getColor().equals(Color.DEFAULT.getCode())) {
    									cellStyle += "background-color: " + assetColoredName.getColor() +";";
    								}
        							htmlCellValue = "<a name=\"" + value + data.values.get(ValueName.COLLATERAL.toString()) + "\" href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(ValueName.COLLATERAL.toString()) + "?type=isolated\" target=\"_blank\">" + value + "/" + data.values.get(ValueName.COLLATERAL.toString()) + "</a>";
        						} else if (value instanceof Number) {
        							htmlCellValue = Asset.format((Number)value);
        						} else if (value instanceof Bar) {
        							htmlCellValue = "" + Asset.format(((Bar)value).getClosePrice().doubleValue());
        						} else if (value instanceof Map) {
        							htmlCellValue = (((Map<String, Object>)value).entrySet()).stream().map(rec -> {
        								Object vl = rec.getValue();
        								String htmlCV = "<b>" + rec.getKey() + "</b>=";
        								if (vl instanceof ColoredNumber) {
        									ColoredNumber vlcd = (ColoredNumber)vl;
        									return htmlCV + "<span style=\"color: " + vlcd.getColor() + ";\">" + (!vlcd.isDefaultColor() ? "<b>" : "") + Asset.format(((Number)rec.getValue()).doubleValue())+ (!vlcd.isDefaultColor() ? "</b>" : "") + "</span>";
        								} else {
        									return htmlCV + Asset.format(((Number)rec.getValue()).doubleValue());
        								}
        							}).collect(Collectors.joining("<br/>"));
        						} else {
        							htmlCellValue = value.toString();
        						}
    						} else {
    							cellStyle += "text-align: center;";
    							htmlCellValue = NOT_AVAILABLE;
    						}
    						return "<td style=\"" + cellStyle + "\">" + htmlCellValue + "</td>";
    					}).collect(Collectors.toList())
    				) +
			"</tr>";
		}

	}

}