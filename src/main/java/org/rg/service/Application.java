package org.rg.service;

import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_3;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_3;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.burningwave.core.concurrent.QueuedTaskExecutor.ProducerTask;
import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
import org.rg.finance.Interval;
import org.rg.finance.Wallet;
import org.rg.util.RestTemplateSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.client.RestTemplate;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.PivotPointIndicator;
import org.ta4j.core.indicators.pivotpoints.StandardReversalIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;

@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@SpringBootApplication
@SuppressWarnings({ "null" })
public class Application implements CommandLineRunner {
	static final String mailFontSizeInPixel = "35px";

	@Autowired
	private ApplicationContext appContext;

	@Autowired
	private Environment environment;

	public static void main(String[] args) {
		new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE).run(args);
	}

	@Bean("restTemplate")
	public RestTemplate restTemplate() {
		return RestTemplateSupplier.getSharedInstance().get();
	}

	@Bean("binanceCoinCollaterals")
	@ConfigurationProperties("binance.coin.collateral")
	public Map<String, String> binanceCoinCollaterals(){
		return createMap();
	}

	@Bean("cryptoComCoinCollaterals")
	@ConfigurationProperties("crypto-com.coin.collateral")
	public Map<String, String> cryptoComCoinCollaterals(){
		return createMap();
	}

	@Bean("binanceAliasForCoinName")
	@ConfigurationProperties("binance.coin.alias-for-name")
	public Map<String, String> binanceAliasForCoinName(){
		return createMap();
	}

	@Bean("cryptoComAliasForCoinName")
	@ConfigurationProperties("crypto-com.coin.alias-for-name")
	public Map<String, String> cryptoComAliasForCoinName(){
		return createMap();
	}

	@Bean("cryptoComWallet")
	public Wallet cryptoComWallet(RestTemplate restTemplate, Environment environment, @Qualifier("cryptoComAliasForCoinName")Map<String, String> cryptoComAliasForCoinName, @Qualifier("cryptoComCoinCollaterals")Map<String, String> coinCollaterals) {
		return new CryptoComWallet(restTemplate, environment.getProperty("cryptoComApiKey"), environment.getProperty("cryptoComApiSecret"), cryptoComAliasForCoinName, coinCollaterals);
	}

	@Bean("binanceWallet")
	public Wallet binanceWallet(RestTemplate restTemplate, Environment environment, @Qualifier("binanceAliasForCoinName")Map<String, String> binanceAliasForCoinName, @Qualifier("binanceCoinCollaterals")Map<String, String> coinCollaterals) {
		BinanceWallet wallet = new BinanceWallet(restTemplate, environment.getProperty("binanceApiKey"), environment.getProperty("binanceApiSecret"), binanceAliasForCoinName, coinCollaterals);
		wallet.enableCurrentTimeMillisFromBinanceServersRetriever();
		return wallet;
	}

	@Bean("burningwave.core.staticComponentContainer.config")
	@ConfigurationProperties("burningwave.core.static-component-container")
	public Map<String, String> staticComponentContainerConfig(){
	    return new LinkedHashMap<>();
	}

	@Bean
	public Class<org.burningwave.core.assembler.StaticComponentContainer> staticComponentContainer(
		@Qualifier("burningwave.core.staticComponentContainer.config") Map<String, String> configMap
	) {
		org.burningwave.core.assembler.StaticComponentContainer.Configuration.Default.add(configMap);
	    return org.burningwave.core.assembler.StaticComponentContainer.class;
	}

	@Bean("burningwave.core.componentContainer.config")
	@ConfigurationProperties("burningwave.core.component-container")
	public Map<String, String> componentContainerConfig(){
	    return new LinkedHashMap<>();
	}

	@Bean(name="burningwave.core.componentContainer", destroyMethod="")
	public org.burningwave.core.assembler.ComponentContainer componentContainer(@Qualifier("burningwave.core.componentContainer.config") Map<String, String> configMap) {
		org.burningwave.core.assembler.ComponentContainer.Configuration.Default.add(configMap);
	    return org.burningwave.core.assembler.ComponentContainer.getInstance();
	}

	private Map<String, String> createMap() {
		return new LinkedHashMap<String, String>() {

			private static final long serialVersionUID = 3144130249105970181L;

			@Override
			public void putAll(Map<? extends String, ? extends String> src) {
				Map<String, String> cleanedValues = new LinkedHashMap<>();
				for (Map.Entry<? extends String, ? extends String> entry : src.entrySet()) {
					String key = entry.getKey().substring(entry.getKey().indexOf(".") + 1);
					String value = entry.getValue();
					cleanedValues.put(key, value);
				}
				super.putAll(cleanedValues);
			}
		};
	}

	@Override
	public void run(String... args) throws Exception {
		LocalDate today = LocalDate.now();
		Map<Wallet, ProducerTask<Collection<String>>> walletsForAvailableCoins = new LinkedHashMap<>();
		for(String beanName : appContext.getBeanNamesForType(Wallet.class)) {
			Wallet wallet = appContext.getBean(beanName, Wallet.class);
			walletsForAvailableCoins.put(
				wallet,
				org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor.createProducerTask(()-> {
					try {
						return wallet.getAvailableCoins();
					} catch (Throwable exc) {
						org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logError(getClass()::getName, exc);
						return new ArrayList<String>();
					}
				}).submit()
			);
		}
		Map<String, Bar> rSIForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> spikeForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> suddenMovementForCoinAlreadyNotified = new ConcurrentHashMap<>();
		while (true) {
			Asset.Collection dataCollection = new Asset.Collection();
			int oneDayCandleStickQuantity = 370;
			int fourHoursCandleStickSize = 200;
			int oneHoursCandleStickSize = 200;
			for (Map.Entry<Wallet, ProducerTask<Collection<String>>> walletForAvailableCoins : walletsForAvailableCoins.entrySet()) {
				if (walletForAvailableCoins.getKey() instanceof BinanceWallet) {
					String defaultCollateral = walletForAvailableCoins.getKey().getCollateralForCoin("DEFAULT");
					Collection<String> marginUSDCCoins = ((BinanceWallet)walletForAvailableCoins.getKey()).getAllMarginAssetPairs()
						.stream().filter(asset -> asset.get("quote").equals(defaultCollateral)).map(asset -> asset.get("base")).
						map(String.class::cast).collect(Collectors.toList());

					marginUSDCCoins.parallelStream().forEach(coin -> {
						try {
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"Loading data from remote for asset {}...",
								coin
							);
							Map<Interval, BarSeries> candlesticks = new AssetDataLoader(
								walletForAvailableCoins.getKey(),
								coin,
								defaultCollateral
							).loadInParallel(Interval.ONE_DAYS, oneDayCandleStickQuantity)
							.loadInParallel(Interval.FOUR_HOURS, fourHoursCandleStickSize)
							.loadInParallel(Interval.ONE_HOURS, oneHoursCandleStickSize)
							.retrieve();
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"... All data loaded from remote for asset {}",
								coin
							);
							Asset detected =
								process(
									new RSIDetector(
										coin,
										defaultCollateral,
										candlesticks,
										14
									),
									Interval.ONE_DAYS,
									rSIForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new SpikeDetector(
										coin,
										defaultCollateral,
										candlesticks,
										true //Enable Bollinger bands check
									),
									Interval.FOUR_HOURS,
									spikeForCoinAlreadyNotified,
									dataCollection,
									detected
								);
								detected =
									process(
										new BigCandleDetector(
											coin,
											defaultCollateral,
											candlesticks,
											false, //Enable Bollinger bands check,
											3d
										),
										Interval.ONE_HOURS,
										suddenMovementForCoinAlreadyNotified,
										dataCollection,
										detected
									);
								detected =
									process(
										new BigCandleDetector(
											coin,
											defaultCollateral,
											candlesticks,
											false, //Enable Bollinger bands check,
											10d
										),
										Interval.FOUR_HOURS,
										suddenMovementForCoinAlreadyNotified,
										dataCollection,
										detected
									);
							List<Supplier<Map<String, Double>>> supportAndResistanceSuppliers = new ArrayList<>();
							if (candlesticks.get(Interval.ONE_DAYS).getBarCount() >= oneDayCandleStickQuantity) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(
											candlesticks.get(Interval.ONE_DAYS),
											TimeLevel.DAY,
											Interval.ONE_DAYS
										)
								);
							} else 	if (candlesticks.get(Interval.FOUR_HOURS).getBarCount() >= fourHoursCandleStickSize) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(
										candlesticks.get(Interval.FOUR_HOURS),
										TimeLevel.BARBASED,
										Interval.FOUR_HOURS
									)
								);
							} else if (candlesticks.get(Interval.ONE_HOURS).getBarCount() >= oneHoursCandleStickSize) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(
										candlesticks.get(
										Interval.ONE_HOURS),
										TimeLevel.BARBASED,
										Interval.ONE_HOURS
									)
								);
							}
							dataCollection.addSupportAndResistanceFor(
								detected,
								supportAndResistanceSuppliers
							);
						} catch (Throwable exc) {
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logError(
								getClass()::getName,
								"Exception occurred while processing asset {}",
								exc,
								coin
							);
						}
					});
					StringBuffer presentation = new StringBuffer("<p style=\"font-size:" + mailFontSizeInPixel + ";\">Ciao!</br>Sono stati rilevati i seguenti asset con variazioni rilevanti</p>");
					if (!dataCollection.isEmpty()) {
						sendMail(
							"roberto.gentili.1980@gmail.com"
							//+ ",fercoletti@gmail.com"
							,
							"Segnalazione asset",
							presentation.append(dataCollection.toHTML()).toString(),
							(String[])null
						);
					}
					dataCollection.clear();
//					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//						getClass()::getName,
//						"Waiting 10 seconds"
//					);
//					Thread.sleep(10000);
				}
			}
		}
	}

	private Asset process(
		CriticalIndicatorValueDetector dataSupplier,
		Interval interval,
		Map<String, Bar> alreadyNotified,
		Asset.Collection dataCollection,
		Asset... previousProcessedData
	) throws Throwable {
		Asset newData = checkIfAlreadyNotified(
			dataSupplier.compute(interval),
			interval,
			dataSupplier.getCandlesticks(),
			alreadyNotified
		);
		dataCollection.addOrMergeAndReplace(newData);
		return newData != null?
			newData :
			previousProcessedData.length > 0 ?
				previousProcessedData[previousProcessedData.length -1] :
				null;
	}

	private static Map<String, Double> checkSupportAndResistanceCrossing(
		BarSeries dailyCandleSticks,
		TimeLevel timeLevel,
		Interval interval
	) {
		Map<String, Double> resistanceAndSupportLevels = new LinkedHashMap<>();
		PivotPointIndicator pivotPoint = new PivotPointIndicator(dailyCandleSticks, timeLevel);
		StandardReversalIndicator s1 = new StandardReversalIndicator(pivotPoint, SUPPORT_1);
		StandardReversalIndicator s2 = new StandardReversalIndicator(pivotPoint, SUPPORT_2);
		StandardReversalIndicator s3 = new StandardReversalIndicator(pivotPoint, SUPPORT_3);
		StandardReversalIndicator r1 = new StandardReversalIndicator(pivotPoint, RESISTANCE_1);
		StandardReversalIndicator r2 = new StandardReversalIndicator(pivotPoint, RESISTANCE_2);
		StandardReversalIndicator r3 = new StandardReversalIndicator(pivotPoint, RESISTANCE_3);
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//			getClass()::getName,
//			"Price: {} - s1: {}; r1: {} - s2: {}; r2: {} - s3: {}; r3: {}  ",
//			dailyCandleSticks.getBar(dailyCandleSticks.getEndIndex()).getClosePrice().doubleValue(),
//			s1.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r1.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			s2.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r2.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			s3.getValue(dailyCandleSticks.getEndIndex()).doubleValue(),
//			r3.getValue(dailyCandleSticks.getEndIndex()).doubleValue()
//		);
		resistanceAndSupportLevels.put("S1-" + interval.toString(), s1.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put("R1-" + interval.toString(), r1.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put("S2-" + interval.toString(), s2.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put("R2-" + interval.toString(), r2.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put("S3-" + interval.toString(), s3.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		resistanceAndSupportLevels.put("R3-" + interval.toString(), r3.getValue(dailyCandleSticks.getEndIndex()).doubleValue());
		return resistanceAndSupportLevels;
	}

	protected Asset checkIfAlreadyNotified(
		Asset asset,
		Interval interval,
		Map<Interval, BarSeries> candlesticks,
		Map<String, Bar> alreadyNotified
	) throws ParseException {
		if (asset == null) {
			return null;
		}
		Bar latestNotified = alreadyNotified.get(asset.getAssetName());
		boolean alreadyNotifiedFlag = false;
		Bar latestBar = candlesticks.get(interval).getLastBar();
		if (latestNotified != null) {
			if (latestNotified.getBeginTime().compareTo(latestBar.getBeginTime()) != 0) {
				alreadyNotified.put(asset.getAssetName(), latestBar);
			} else {
				alreadyNotifiedFlag = true;
				org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
					getClass()::getName,
					"Variation already notified for coin {}",
					asset.getAssetName()
				);
			}
		} else {
			alreadyNotified.put(asset.getAssetName(), latestBar);
		}
		if (!alreadyNotifiedFlag) {
			return asset;
		}
		return null;
	}

	private static String format(double value) {
		return String.format("%1$,.6f", value);
	}

	public void sendMail(String to, String subject, String body, String... attachmentAbsolutePaths) throws MessagingException, IOException {
		JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
		mailSender.setHost("mail.burningwave.org");
		mailSender.setPort(587);

		mailSender.setUsername(environment.getProperty("emailAccount"));
		mailSender.setPassword(environment.getProperty("emailPassword"));

		Properties props = mailSender.getJavaMailProperties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.ssl.trust", "mail.burningwave.org");
		props.put("mail.debug", "true");

		MimeMessage email = mailSender.createMimeMessage();
		email.setFrom("crypto@burningwave.org");
		email.setRecipients(Message.RecipientType.TO, to);
		email.setSubject(subject);
		boolean attachmentsArePresent = attachmentAbsolutePaths != null && attachmentAbsolutePaths.length > 0;
		if (attachmentsArePresent) {
			Multipart multipart = new MimeMultipart();
			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setContent(body, "text/html; charset=utf-8");
			multipart.addBodyPart(messageBodyPart);
			for (String attachmentAbsolutePath : attachmentAbsolutePaths) {
				MimeBodyPart attachmentPart = new MimeBodyPart();
				attachmentPart.attachFile(new File(attachmentAbsolutePath));
				multipart.addBodyPart(attachmentPart);
			}
			email.setContent(multipart);
		} else {
			email.setText(body);
			email.setContent(body, "text/html; charset=utf-8");
		}
		mailSender.send(email);
	}


	private boolean todayIsFirstDayOfTheYear() {
		LocalDate now = LocalDate.now(ZoneId.of(environment.getProperty("timezone.default")));
		return now.compareTo(now.with(TemporalAdjusters.firstDayOfYear())) == 0;
	}

	private boolean isCurrentTimeMinorOrEqualsThan(int hour, int minute) {
		LocalDateTime now = LocalDateTime.now(ZoneId.of(environment.getProperty("timezone.default")));
		return now.getHour() < hour ?
			true :
			now.getHour() == hour && now.getMinute() <= minute;
	}

	private String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}


    private Long currentTimeMillis() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static class Asset {
    	private Map<String, Object> values;

		public Asset(
			String assetName,
			String collateral,
			Map<Interval, BarSeries> candleSticks,
			Double rSILevel,
			Double variationPercentage,
			Map<String, Double> supportAndResistance,
			Map<String, Double> variations
		) {
			values = new LinkedHashMap<>();
			values.put(Collection.LABELS.get(Collection.ASSET_NAME_LABEL_INDEX), assetName);
			values.put(Collection.LABELS.get(Collection.COLLATERAL_LABEL_INDEX), collateral);
			values.put(Collection.LABELS.get(Collection.LATEST_1D_BAR_LABEL_INDEX), candleSticks.get(Interval.ONE_DAYS).getBar(candleSticks.get(Interval.ONE_DAYS).getEndIndex()));
			values.put(Collection.LABELS.get(Collection.LATEST_4H_BAR_LABEL_INDEX), candleSticks.get(Interval.FOUR_HOURS).getBar(candleSticks.get(Interval.FOUR_HOURS).getEndIndex()));
			values.put(Collection.LABELS.get(Collection.RSI_LABEL_INDEX), rSILevel);
			values.put(Collection.LABELS.get(Collection.PRICE_VARIATION_PERCENTAGE_LABEL_INDEX), variationPercentage);
			values.put(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX), supportAndResistance);
			values.put(Collection.LABELS.get(Collection.VARIATION_PERCENTAGES_LABEL_INDEX), variations);
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
		public Double getPriceVariationPercentageOn4H() {
			return (Double)values.get(Collection.LABELS.get(Collection.PRICE_VARIATION_PERCENTAGE_LABEL_INDEX));
		}
		public Map<String, Double> getSupportAndResistance() {
			return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX));
		}
		public Map<String, Double> getVariationPercentages() {
			return (Map<String, Double>)values.get(Collection.LABELS.get(Collection.VARIATION_PERCENTAGES_LABEL_INDEX));
		}




    	private static class Collection {
    		private static List<String> LABELS = Arrays.asList("Asset name", "collateral", "Latest price from " + Interval.ONE_DAYS, "Latest price", "RSI on " + Interval.ONE_DAYS, "Spike size in % on " + Interval.FOUR_HOURS, "Price variation %", "Support and resistance levels");
    		private static int ASSET_NAME_LABEL_INDEX = 0;
    		private static int COLLATERAL_LABEL_INDEX = 1;
    		private static int LATEST_1D_BAR_LABEL_INDEX = 2;
    		private static int LATEST_4H_BAR_LABEL_INDEX = 3;
    		private static int RSI_LABEL_INDEX = 4;
    		private static int PRICE_VARIATION_PERCENTAGE_LABEL_INDEX = 5;
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

    		public void addSupportAndResistanceFor(
				Asset asset,
				List<Supplier<Map<String, Double>>> supportAndResistanceSuppliers
			) {
    			if (asset != null) {
    				for (Supplier<Map<String, Double>> supportAndResistanceSupplier : supportAndResistanceSuppliers) {
    					Map<String, Double> supportAndResistance = supportAndResistanceSupplier.get();
        				asset.addSupportAndResistance(supportAndResistance);
    				}
    				addOrMergeAndReplace(asset);
    			}
			}

    		public void clear() {
    			datas.clear();
			}

			public synchronized Collection addOrMergeAndReplace(Asset data) {
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
				if (oldDSupportAndResistance != newDSupportAndResistance) {
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
    			return "<table style=\"border-spacing: 20px;font-size:" + mailFontSizeInPixel + ";\">" +
					"<tr>" +
						String.join("", LABELS.stream().filter(hideColumnFilter()).map(label -> "<td><b>" + label + "</b></td>").collect(Collectors.toList())) +
						String.join("", dynamicLabelsGroupOne.stream().map(label -> "<td><b>" + label + "</b></td>").collect(Collectors.toList())) +
						String.join("", dynamicLabelsGroupTwo.stream().map(label -> "<td><b>" + label + "</b></td>").collect(Collectors.toList())) +
					"</tr>" +
					String.join("", datas.stream().map(this::toHTML).collect(Collectors.toList())) +
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

    		private String toHTML(Asset data) {
    			return "<tr>" +
    					String.join(
        					"",LABELS.stream().filter(hideColumnFilter()).map(label -> {
        						Object value = data.values.get(label);
        						String htmlCellValue;
        						if (value != null) {
	        						if (label.equals(LABELS.get(ASSET_NAME_LABEL_INDEX))) {
	        							htmlCellValue = "<a href=\"" + "https://www.binance.com/it/trade/" + value + "_" + data.values.get(LABELS.get(COLLATERAL_LABEL_INDEX)) + "?type=isolated" + "\">" + data.values.get(label) + "</a>";
	        						} else if (label.equals(LABELS.get(RSI_LABEL_INDEX))) {
	        							htmlCellValue = "<p style=\"color: " + ((Double)value <= 50 ? "green" : "red") +"\">" + format((Double)value) + "</p>";
	        						} else if (label.equals(LABELS.get(PRICE_VARIATION_PERCENTAGE_LABEL_INDEX))) {
	        							htmlCellValue = "<p style=\"color: " + ((Double)value <= 0 ? "green" : "red") +"\">" + format((Double) value) + "</p>";
	        						} else if (value instanceof Double) {
	        							htmlCellValue = format((Double)value);
	        						} else if (value instanceof Bar) {
	        							htmlCellValue = "" + format(((Bar)value).getClosePrice().doubleValue());
	        						} else {
	        							htmlCellValue = value.toString();
	        						}
        						} else {
        							htmlCellValue = "NA";
        						}
        						return "<td>" + htmlCellValue + "</td>";
        					}).collect(Collectors.toList())
        				) +
    					String.join(
        					"",dynamicLabelsGroupOne.stream().filter(hideColumnFilter()).map(label -> {
        						Double value = Optional.ofNullable(data.getVariationPercentages())
        						.map(supAndRes -> supAndRes.get(label)).orElseGet(() -> null);
        						String htmlCellValue;
        						if (value != null) {
        							htmlCellValue = "<p style=\"color: " + ((Double)value <= 0 ? "green" : "red") +"\">" + format((Double)value) + "</p>";
        						} else {
        							htmlCellValue = "NA";
        						}
        						return "<td>" + htmlCellValue + "</td>";
        					}).collect(Collectors.toList())
        				) +
    					String.join(
        					"",dynamicLabelsGroupTwo.stream().filter(hideColumnFilter()).map(label -> {
        						Double value = Optional.ofNullable(data.getSupportAndResistance())
        						.map(supAndRes -> supAndRes.get(label)).orElseGet(() -> null);
        						String htmlCellValue;
        						if (value != null) {
        							htmlCellValue = format((Double)value);
        						} else {
        							htmlCellValue = "NA";
        						}
        						return "<td>" + htmlCellValue + "</td>";
        					}).collect(Collectors.toList())
        				) +
    			"</tr>";
    		}
    	}

    }

}
