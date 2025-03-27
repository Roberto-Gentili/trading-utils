package org.rg.service;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.burningwave.core.io.FileSystemItem;
import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
import org.rg.finance.Interval;
import org.rg.finance.Wallet;
import org.rg.service.detector.BigCandleDetector;
import org.rg.service.detector.BollingerBandDetector;
import org.rg.service.detector.CriticalIndicatorValueDetectorAbst;
import org.rg.service.detector.EMADetector;
import org.rg.service.detector.RSIDetector;
import org.rg.service.detector.ResistanceAndSupportDetector;
import org.rg.service.detector.SpikeDetector;
import org.rg.service.detector.StochasticRSIDetector;
import org.rg.util.RestTemplateSupplier;
import org.rg.util.ShellExecutor;
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

@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@SpringBootApplication
@SuppressWarnings({ "null" })
public class Application implements CommandLineRunner {
	private static ServerSocket alreadyRunningChecker = null;
	private static enum ShowConsistentDataOption {
		ONLY_THEM("Only them"),
		HIGHLIGHT_THEM("Highlight them"),
		IGNORE("ignore");

		private String value;

		private ShowConsistentDataOption(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		public boolean valueEquals(String value) {
			if (value == null) {
				return false;
			}
			return removeEmptySpaces(this.value).equalsIgnoreCase(removeEmptySpaces(value));
		}

		@Override
		public String toString() {
			return value;
		}

	}

	@Autowired
	private ApplicationContext appContext;

	@Autowired
	private Environment environment;

	public static void main(String[] args) {
	    try {
	    	alreadyRunningChecker = new ServerSocket(Byte.MAX_VALUE);
	    	new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE).run(args);
	    } catch (IOException e) {
	        System.err.println("Application already running!");
	        System.exit(-1);
	    }
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
	public Wallet cryptoComWallet(
		RestTemplate restTemplate, Environment environment,
		@Qualifier("cryptoComAliasForCoinName")Map<String, String> cryptoComAliasForCoinName,
		@Qualifier("cryptoComCoinCollaterals")Map<String, String> coinCollaterals
	) {
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

	@Bean("indicatorMailServiceNotifierConfig")
	@ConfigurationProperties("service.mail.indicator.notifier")
	public Map<String, String> indicatorMailServiceNotifierConfig(){
	    return new LinkedHashMap<>();
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
		FileSystemItem targetFolder =
			org.burningwave.core.assembler.ComponentContainer.getInstance().getPathHelper().findResources(path ->
				path.contains("target") && path.contains(Application.class.getSimpleName())
			).stream().findFirst().map(fIS -> {
				while (!(fIS = fIS.getParent()).getName().equals("target")) {}
				return fIS;
			}).get();
		FileSystemItem projectFolder = targetFolder.getParent();
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
		List<Interval> intervals = Arrays.asList(Interval.WEEK, Interval.ONE_DAYS, Interval.FOUR_HOURS, Interval.ONE_HOURS);
		Map<Class<? extends CriticalIndicatorValueDetector>, Map<Interval, Map<String, Bar>>> alreadyNotifiedMap =
			buildAlreadyNotifiedHolder(intervals, new ConcurrentHashMap<>());

		Map<Interval, Integer> candlestickQuantityForInterval = new LinkedHashMap<>();
		candlestickQuantityForInterval.put(intervals.get(0), 215);
		candlestickQuantityForInterval.put(intervals.get(1), 215);
		candlestickQuantityForInterval.put(intervals.get(2), 215);
		candlestickQuantityForInterval.put(intervals.get(3), 215);
		Map<String, Map<Interval, BarSeries>> candlesticksForCoin = new ConcurrentHashMap<>();
		List<String> notifiedAssetInPreviousEmail = new CopyOnWriteArrayList<>();
		int minNumberOfIndicatorsDetectedOption =
			Integer.valueOf((String)((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig")).get("min-number-of-indicators-detected"));
		boolean  resendAlreadyNotifiedOption =
			Boolean.valueOf((String)((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig")).get("resend-already-notified"));
		String showConsistentDataOption =
			removeEmptySpaces
			((String)((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig")).getOrDefault("show-consistent-data", "ignore"))
			;
		long viewAutorefreshOption =
			Long.valueOf(
				((String)((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig")).getOrDefault("view.autorefresh-every", "300000"))
			);
		String recipients = ((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig"))
			.entrySet().stream()
			.filter(keyAndVal -> keyAndVal.getKey().startsWith("recipient"))
			.map(Map.Entry::getValue)
			.map(String.class::cast)
			.collect(Collectors.joining(","));
		AtomicInteger cycles = new AtomicInteger(1);
		Supplier<Integer> cyclesSupplier = () ->
			environment.getProperty("EXTERNAL_MANAGED_LOOP") != null ? cycles.getAndDecrement(): cycles.get();
		while (cyclesSupplier.get() > 0) {
			Asset.Collection dataCollection = new Asset.Collection().setOnTopFixedHeader(
				Boolean.valueOf((String)((Map<String, Object>)appContext.getBean("indicatorMailServiceNotifierConfig")).get("text.table.on-top-fixed-header"))
			);
			for (Map.Entry<Wallet, ProducerTask<Collection<String>>> walletForAvailableCoins : walletsForAvailableCoins.entrySet()) {
				if (walletForAvailableCoins.getKey() instanceof BinanceWallet) {
					String defaultCollateral = walletForAvailableCoins.getKey().getCollateralForCoin("DEFAULT");
					Collection<String> marginUSDCCoins = ((BinanceWallet)walletForAvailableCoins.getKey()).getAllMarginAssetPairs()
						.stream().filter(asset -> asset.get("quote").equals(defaultCollateral)).map(asset -> asset.get("base")).
						map(String.class::cast).collect(Collectors.toList());
//					marginUSDCCoins = new ArrayList<>(marginUSDCCoins).subList(0, 25);
					marginUSDCCoins.parallelStream().forEach(coin -> {
						try {
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"Loading data from remote for asset {}...",
								coin
							);
							AssetDataLoader assetDataLoader = new AssetDataLoader(
								walletForAvailableCoins.getKey(),
								coin,
								defaultCollateral
							);
							for (Map.Entry<Interval, Integer> cFI : candlestickQuantityForInterval.entrySet()) {
								assetDataLoader = assetDataLoader.loadInParallel(cFI.getKey(), cFI.getValue());
							}
							Map<Interval, BarSeries> candlesticks = assetDataLoader.retrieve();
							candlesticksForCoin.put(coin + defaultCollateral, candlesticks);
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"... All data loaded from remote for asset {}",
								coin
							);
							Asset detected = null;
							for (Interval interval : intervals) {
								detected =
									process(
										new EMADetector(coin,defaultCollateral,candlesticks, false, 25,50,100,200),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new RSIDetector(coin,defaultCollateral,candlesticks,14),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new StochasticRSIDetector(coin,defaultCollateral,candlesticks,14),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new BollingerBandDetector(coin,defaultCollateral,candlesticks, 20, 2d),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new BollingerBandDetector(coin,defaultCollateral,candlesticks, 20, 2d),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new SpikeDetector(coin,defaultCollateral,candlesticks, 40, 3),
										interval,
										dataCollection,
										detected
									);
							}
							for (Interval interval : intervals) {
								detected =
									process(
										new BigCandleDetector(coin,defaultCollateral,candlesticks, 10d),
										interval,
										dataCollection,
										detected
									);
							}
							if (detected != null) {
								CriticalIndicatorValueDetector resistanceAndSupportDetector =
									new ResistanceAndSupportDetector(coin, defaultCollateral, candlesticks, false);
								for (Map.Entry<Interval, Integer> cFI : candlestickQuantityForInterval.entrySet()) {
									if (CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(coin) || candlesticks.get(cFI.getKey()).getBarCount() >= cFI.getValue()) {
										detected = process(
											resistanceAndSupportDetector,
											cFI.getKey(),
											dataCollection,
											detected
										);
									}
								}
							}
						} catch (Throwable exc) {
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logError(
								getClass()::getName,
								"Exception occurred while processing asset {}",
								exc,
								coin
							);
						}
					});
					if (minNumberOfIndicatorsDetectedOption > -1) {
						dataCollection.filter(asset -> {
							Collection<Runnable> alreadyNotifiedUpdaters = new ArrayList<>();
							int[] counters = {0,0,0};
							List<int[]> counterList = Arrays.asList(
								computeIfMustBeNotified(intervals, showConsistentDataOption, alreadyNotifiedMap, candlesticksForCoin, asset,
										RSIDetector.class, asset.getRSI(), alreadyNotifiedUpdaters),
								computeIfMustBeNotified(intervals, showConsistentDataOption, alreadyNotifiedMap, candlesticksForCoin, asset,
										StochasticRSIDetector.class, asset.getStochasticRSI(), alreadyNotifiedUpdaters),
								computeIfMustBeNotified(intervals, showConsistentDataOption, alreadyNotifiedMap, candlesticksForCoin, asset,
										BollingerBandDetector.class, asset.getBollingerBands(), alreadyNotifiedUpdaters),
								computeIfMustBeNotified(intervals, showConsistentDataOption, alreadyNotifiedMap, candlesticksForCoin, asset,
										SpikeDetector.class, asset.getSpikeSizePercentage(), alreadyNotifiedUpdaters),
								computeIfMustBeNotified(intervals, showConsistentDataOption, alreadyNotifiedMap, candlesticksForCoin, asset,
										BigCandleDetector.class, asset.getVariationPercentages(), alreadyNotifiedUpdaters)
							);
							for (int i = 0; i < counterList.size(); i++) {
								int[] countersFromCounterList = counterList.get(i);
								for (int j = 0; j < counters.length; j++) {
									counters[j] += countersFromCounterList[j];
								}
							}
							if (ShowConsistentDataOption.HIGHLIGHT_THEM.valueEquals(showConsistentDataOption)) {
								if (asset.getName().equals("CRV")) {
									System.out.println();
								}
								if ((counters[1] == 0 && counters[2] > 0)) {
									asset.highligtName(Color.GREEN.getCode());
								} else if ((counters[2] == 0 && counters[1] > 0)) {
									asset.highligtName(Color.RED.getCode());
								}
							}
							if (!ShowConsistentDataOption.ONLY_THEM.valueEquals(showConsistentDataOption)) {
								if (counters[0] >= minNumberOfIndicatorsDetectedOption) {
									alreadyNotifiedUpdaters.stream().forEach(Runnable::run);
								}
								return
									CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(asset.getName()) ||
									counters[0] >= minNumberOfIndicatorsDetectedOption;
							} else {
								boolean	show = (counters[1] == 0 && counters[2] >= minNumberOfIndicatorsDetectedOption) ||
									counters[2] == 0 && counters[1] >= minNumberOfIndicatorsDetectedOption;
								if (show) {
									alreadyNotifiedUpdaters.stream().forEach(Runnable::run);
								}
								return
									show ||
									CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(asset.getName());
							}
						});
					}
					StringBuffer presentation = new StringBuffer(
						"<p style=\"" + Asset.DEFAULT_FONT_SIZE + "\">" +
						"Ciao!<br/>In data <b>" + new SimpleDateFormat("yyyy\\MM\\dd-HH:mm:ss").format(new Date())+ "</b> " +
						"sono stati rilevati i seguenti " + (dataCollection.size() -1) + " asset (BTC escluso) con variazioni rilevanti {0}</p>"
					);
					List<String> notifiedAssetInThisEmail = null;
					boolean sameAssetsSentInPreviousEmail = false;
					if (resendAlreadyNotifiedOption) {
						notifiedAssetInThisEmail = dataCollection.getAssetList().stream()
							.map(Asset::getName).collect(Collectors.toList());
						sameAssetsSentInPreviousEmail =
							notifiedAssetInThisEmail.containsAll(notifiedAssetInPreviousEmail) &&
							notifiedAssetInPreviousEmail.containsAll(notifiedAssetInThisEmail);
					}
					if (!sameAssetsSentInPreviousEmail && dataCollection.size() > 1) {
						if (!recipients.isEmpty()) {
							sendMail(
								recipients,
								"Segnalazione asset",
								presentation.toString().replace(
									"{0}",
									"(la lista è visualizzabile anche da <a href=\"" +
									//"https://html-preview.github.io/?url=https://github.com/Roberto-Gentili/trading-utils/blob/main/src/main/resources/assets.html" +
									"https://rg-shared.neocities.org/assets" +
									"\">qui</a>)"
								) +
								dataCollection.toHTML(),
								(String[])null
							);
						}
						org.burningwave.core.assembler.StaticComponentContainer.Streams.store(
							projectFolder.getAbsolutePath() + "/src/main/resources/assets.html",
							("<html>" +
								"<script>" +
									"window.setTimeout( function() {" +
										"window.location.reload();" +
									"}, " + viewAutorefreshOption + ");" +
								"</script>" +
								"<body>" +
									presentation.toString().replace("{0}", "") + dataCollection.setOnTopFixedHeader(true).toHTML() +
								"</body>" +
							"</html>")
							.getBytes(StandardCharsets.UTF_8)
						);
						ShellExecutor.execute(
							projectFolder.getAbsolutePath() + "/upload-assets.cmd "+
							environment.getProperty("NEOCITIES_ACCOUNT_NAME")+":"+
							environment.getProperty("NEOCITIES_ACCOUNT_PASSWORD"),
							true
						);
						if (notifiedAssetInThisEmail != null) {
							notifiedAssetInPreviousEmail.clear();
							notifiedAssetInPreviousEmail.addAll(notifiedAssetInThisEmail);
						}
					}
					dataCollection.clear();
					candlesticksForCoin.clear();
					if (resendAlreadyNotifiedOption) {
						buildAlreadyNotifiedHolder(intervals, alreadyNotifiedMap);
					}
//					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
//						getClass()::getName,
//						"Waiting 10 seconds"
//					);
//					Thread.sleep(10000);
				}
			}
		}
		System.exit(0);
	}

	private Map<Class<? extends CriticalIndicatorValueDetector>, Map<Interval, Map<String, Bar>>> buildAlreadyNotifiedHolder(
			List<Interval> intervals,
			Map<Class<? extends CriticalIndicatorValueDetector>, Map<Interval, Map<String, Bar>>> alreadyNotifiedMap
	) {
		alreadyNotifiedMap.clear();
		for (Class<? extends CriticalIndicatorValueDetector> indicatorType : Arrays.asList(
			RSIDetector.class,
			StochasticRSIDetector.class,
			BollingerBandDetector.class,
			SpikeDetector.class,
			BigCandleDetector.class
		)) {
			for (Interval interval : intervals) {
				Map<Interval, Map<String, Bar>> temp = new ConcurrentHashMap<Interval, Map<String,Bar>>();
				temp.put(interval, new ConcurrentHashMap<String, Bar>());
				alreadyNotifiedMap.put(indicatorType, temp);
			}
		}
		return alreadyNotifiedMap;
	}

	protected int[] computeIfMustBeNotified(
		List<Interval> intervals,
		String showConsistentDataOption,
		Map<Class<? extends CriticalIndicatorValueDetector>,
		Map<Interval, Map<String, Bar>>> alreadyNotified,
		Map<String, Map<Interval, BarSeries>> candlesticksForCoin,
		Asset asset,
		Class<? extends CriticalIndicatorValueDetector> indicatorType,
		Map<String, Number> indicatorValues,
		Collection<Runnable> alreadyNotifiedUpdaters
	) {
		int counter = 0;
		int redCounter = 0;
		int greenCounter = 0;
		if (indicatorValues != null && !indicatorValues.isEmpty()) {
			Collection<Runnable> alreadyNotifiedGreenUpdaters = new ArrayList<>();
			Collection<Runnable> alreadyNotifiedRedUpdaters = new ArrayList<>();
			Collection<Runnable> allUpdaters = new ArrayList<>();
			for (Map.Entry<String, Number> indicator : indicatorValues.entrySet()) {
				for (Interval interval : intervals) {
					if (indicator.getKey().contains(interval.toString())) {
						Map<Interval, BarSeries> candlesticks =
							candlesticksForCoin.get(asset.getColoredName()+ asset.getCollateral());
						candlesticks.get(interval);
						Runnable updater = checkIfAlreadyNotified(
							asset,
							interval,
							candlesticksForCoin.get(asset.getColoredName() + asset.getCollateral()),
							getAlreadyNotified(alreadyNotified.get(indicatorType), interval)
						);
						if (updater != null) {
							if (Color.GREEN.getCode().equals(((ColoredNumber)indicator.getValue()).getColor())) {
								alreadyNotifiedGreenUpdaters.add(updater);
								greenCounter++;
							} else if (Color.RED.getCode().equals(((ColoredNumber)indicator.getValue()).getColor())) {
								alreadyNotifiedRedUpdaters.add(updater);
								redCounter++;
							}
							allUpdaters.add(updater);
							counter++;
						}
					}
				}
			}
			if (ShowConsistentDataOption.ONLY_THEM.valueEquals(showConsistentDataOption)) {
				if (alreadyNotifiedGreenUpdaters.isEmpty()) {
					alreadyNotifiedUpdaters.addAll(alreadyNotifiedRedUpdaters);
				} else if (alreadyNotifiedRedUpdaters.isEmpty()) {
					alreadyNotifiedUpdaters.addAll(alreadyNotifiedGreenUpdaters);
				}
			} else {
				alreadyNotifiedUpdaters.addAll(allUpdaters);
			}
		}
		return new int[] {counter, redCounter, greenCounter};
	}

	private Map<String, Bar> getAlreadyNotified(Map<Interval, Map<String, Bar>> map, Interval interval) {
		Map<String, Bar> output = map.get(interval);
		if (output == null) {
			synchronized(map) {
				output = map.get(interval);
				if (output == null) {
					map.put(interval, output = new ConcurrentHashMap<>());
				}
			}
		}
		return output;
	}

	private Asset process(
		CriticalIndicatorValueDetector dataSupplier,
		Interval interval,
		Asset.Collection dataCollection,
		Asset... previousProcessedData
	) throws Throwable {
		Asset newData = dataSupplier.compute(interval);
		dataCollection.addOrMergeAndReplace(newData);
		return newData != null?
			newData :
			previousProcessedData.length > 0 ?
				previousProcessedData[previousProcessedData.length -1] :
				null;
	}

	protected Runnable checkIfAlreadyNotified(
		Asset asset,
		Interval interval,
		Map<Interval, BarSeries> candlesticks,
		Map<String, Bar> alreadyNotified
	) {
		if (asset == null) {
			return null;
		}
		if (alreadyNotified == null) {
			return new Runnable() {
				@Override
				public void run() {

				}
			};
		}
		Bar latestNotified = alreadyNotified.get(asset.getColoredName());
		boolean alreadyNotifiedFlag = false;
		Bar latestBar = candlesticks.get(interval).getLastBar();
		Runnable updater = null;
		if (latestNotified != null) {
			if (latestNotified.getBeginTime().compareTo(latestBar.getBeginTime()) != 0) {
				updater = () ->
					alreadyNotified.put(asset.getName(), latestBar);
			} else {
				alreadyNotifiedFlag = true;
				org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
					getClass()::getName,
					"Variation already notified for coin {}",
					asset.getColoredName()
				);
			}
		} else {
			updater = () ->
				alreadyNotified.put(asset.getName(), latestBar);
		}
		if (!alreadyNotifiedFlag) {
			return updater;
		}
		return null;
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
		email.setFrom("financial.info@burningwave.org");
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


	public static String removeEmptySpaces(String value) {
		return value.replaceAll("\\s+", "");
	}

}
