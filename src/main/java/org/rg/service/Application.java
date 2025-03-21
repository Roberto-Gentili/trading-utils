package org.rg.service;

import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.RESISTANCE_3;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_1;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_2;
import static org.ta4j.core.indicators.pivotpoints.PivotLevel.SUPPORT_3;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.burningwave.core.function.ThrowingSupplier;
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
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.pivotpoints.PivotPointIndicator;
import org.ta4j.core.indicators.pivotpoints.StandardReversalIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@SpringBootApplication
@SuppressWarnings({ "null" })
public class Application implements CommandLineRunner {

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
			int oneDayCandleStickSize = 370;
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
							BarSeries dailyCandleSticks = retrieveCandlestick(
								Interval.ONE_DAYS,
								oneDayCandleStickSize,
								walletForAvailableCoins,
								defaultCollateral,
								coin
							);
							BarSeries fourHCandleSticks = retrieveCandlestick(
								Interval.FOUR_HOURS,
								fourHoursCandleStickSize,
								walletForAvailableCoins,
								defaultCollateral,
								coin
							);
							BarSeries oneHCandleSticks = retrieveCandlestick(
								Interval.ONE_HOURS,
								oneHoursCandleStickSize,
								walletForAvailableCoins,
								defaultCollateral,
								coin
							);
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"... All data loaded from remote for asset {}",
								coin
							);
							Asset detected =
								process(() ->
									checkLowAndHighRSIValue(
										coin, defaultCollateral, dailyCandleSticks,
										fourHCandleSticks, rSIForCoinAlreadyNotified, 14
									),
									dataCollection
								);
							detected =
								process(() ->
									checkSpike(
										coin, defaultCollateral, dailyCandleSticks, fourHCandleSticks,
										spikeForCoinAlreadyNotified
									),
									dataCollection,
									detected
								);
							List<Supplier<Map<String, Double>>> supportAndResistanceSuppliers =
								new ArrayList<>();
							if (dailyCandleSticks.getBarCount() >= oneDayCandleStickSize) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(dailyCandleSticks, TimeLevel.DAY, Interval.ONE_DAYS)
								);
							} else 	if (fourHCandleSticks.getBarCount() >= fourHoursCandleStickSize) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(fourHCandleSticks, TimeLevel.BARBASED, Interval.FOUR_HOURS)
								);
							} else if (oneHCandleSticks.getBarCount() >= oneHoursCandleStickSize) {
								supportAndResistanceSuppliers.add(
									() -> checkSupportAndResistanceCrossing(oneHCandleSticks, TimeLevel.BARBASED, Interval.ONE_HOURS)
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
					StringBuffer presentation = new StringBuffer("<h1>Ciao!</h1>Sono stati rilevati i seguenti asset con variazioni rilevanti");
					if (!dataCollection.isEmpty()) {
						sendMail(
							"roberto.gentili.1980@gmail.com"
							+ ",fercoletti@gmail.com"
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

	private Asset process(ThrowingSupplier<Asset, Throwable> dataSupplier, Asset.Collection dataCollection, Asset... previousProcessedData) throws Throwable {
		Asset newData = dataSupplier.get();
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


	protected Asset checkLowAndHighRSIValue(
		String coin,
		String collateral,
		BarSeries dailyCandleSticks,
		BarSeries fourHoursCandleSticks,
		Map<String, Bar> alreadyNotified,
		int period
	) {
		ClosePriceIndicator closePrice = new ClosePriceIndicator(dailyCandleSticks);
		RSIIndicator rSIIndicator = new RSIIndicator(closePrice, period);
		List<Num> values = rSIIndicator.stream().collect(Collectors.toList());
		Double latestRSIValue = values.get(dailyCandleSticks.getEndIndex()).doubleValue();
		Bar latestDailyCandleStick = dailyCandleSticks.getBar(dailyCandleSticks.getEndIndex());
		Asset data = null;
		if ((latestRSIValue > 70 || latestRSIValue < 30) && latestRSIValue != 0) {
			Bar latestNotified = alreadyNotified.get(coin);
			boolean alreadyNotifiedFlag = false;
			if (latestNotified != null) {
				if (latestNotified.getBeginTime().compareTo(latestDailyCandleStick.getBeginTime()) != 0) {
					alreadyNotified.put(coin, latestDailyCandleStick);
				} else {
					alreadyNotifiedFlag = true;
					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
						getClass()::getName,
						"Coin {} with value {} already notified for RSI value",
						coin, latestRSIValue
					);
				}
			} else {
				alreadyNotified.put(coin, latestDailyCandleStick);
			}
			if (!alreadyNotifiedFlag) {
				data = new Asset(
					coin,
					collateral,
					latestDailyCandleStick,
					fourHoursCandleSticks.getBar(fourHoursCandleSticks.getEndIndex()),
					latestRSIValue,
					null,
					null
				);
			}
		}
		return data;
	}

	protected Asset checkSpike(
		String coin,
		String collateral,
		BarSeries dailyCandleSticks,
		BarSeries fourHoursCandleSticks,
		Map<String, Bar> alreadyNotified
	) throws ParseException {
		int lastCandleIndex = fourHoursCandleSticks.getEndIndex();
		Bar latest4HBar = fourHoursCandleSticks.getBar(lastCandleIndex);

		boolean considerOnlyBBContacts = true;
		BigDecimal spikePercentage = toBigDecimal(40d);
		BigDecimal comparingValue = toBigDecimal(3d);
		int bBMaPeriod = 20;
		DecimalNum bBDev = DecimalNum.valueOf(2);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(fourHoursCandleSticks);
		SMAIndicator ma = new SMAIndicator(closePrice, bBMaPeriod);
        StandardDeviationIndicator deviation = new StandardDeviationIndicator(closePrice, bBMaPeriod);
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(ma);
        BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, deviation, bBDev);
        BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, deviation, bBDev);
		//BollingerBandFacade bBFacade = new BollingerBandFacade(candlesticks, 20, 2);
		BigDecimal bBLower =
			//toBigDecimal(bBFacade.lower().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(lowBBand.getValue(lastCandleIndex).doubleValue());
		BigDecimal bBUpper =
			//toBigDecimal(bBFacade.upper().getValue(lastCandleIndex).doubleValue());
			toBigDecimal(upBBand.getValue(lastCandleIndex).doubleValue());
		BigDecimal high = toBigDecimal(latest4HBar.getHighPrice().doubleValue());
		BigDecimal low = toBigDecimal(latest4HBar.getLowPrice().doubleValue());
		BigDecimal priceVariation = high.subtract(low);
		BigDecimal open = toBigDecimal(latest4HBar.getOpenPrice().doubleValue());
		BigDecimal close = toBigDecimal(latest4HBar.getClosePrice().doubleValue());
		BigDecimal lowSpikeValue = close.compareTo(open) < 0 ? close.subtract(low) : open.subtract(low);
		BigDecimal highSpikeValue = close.compareTo(open) > 0 ? high.subtract(close) : high.subtract(open);
		BigDecimal lowSpikePercentage = divide(lowSpikeValue.multiply(toBigDecimal(100d)), priceVariation);
		BigDecimal highSpikePercentage = divide(highSpikeValue.multiply(toBigDecimal(100d)), priceVariation);
		BigDecimal totalCandleVariation = divide(high.subtract(low),high).multiply(toBigDecimal(100d));
		//log.info('variation: {0}', totalCandleVariation)
		boolean buyCondition =
			lowSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && lowSpikeValue.compareTo(highSpikeValue) >= 0 && (considerOnlyBBContacts ? (low.compareTo(bBLower) <= 0) : true);
		boolean sellCondition =
			highSpikePercentage.compareTo(spikePercentage) >= 0 && totalCandleVariation.compareTo(comparingValue) >= 0 && highSpikeValue.compareTo(lowSpikeValue) >= 0 && (considerOnlyBBContacts ? (high.compareTo(bBUpper) >= 0) : true);
		Asset data = null;
		if (buyCondition || sellCondition) {
			Bar latestNotified = alreadyNotified.get(coin);
			boolean alreadyNotifiedFlag = false;
			if (latestNotified != null) {
				if (latestNotified.getBeginTime().compareTo(latest4HBar.getBeginTime()) != 0) {
					alreadyNotified.put(coin, latest4HBar);
				} else {
					alreadyNotifiedFlag = true;
					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
						getClass()::getName,
						"Spike already notified for coin {}",
						coin
					);
				}
			} else {
				alreadyNotified.put(coin, latest4HBar);
			}
			if (!alreadyNotifiedFlag) {
				data = new Asset(
					coin,
					collateral,
					dailyCandleSticks.getBar(dailyCandleSticks.getEndIndex()),
					latest4HBar,
					null,
					buyCondition? (lowSpikePercentage.negate().doubleValue()) : highSpikePercentage.doubleValue(),
					null
				);
			}
		}
		return data;
	}

	private BigDecimal toBigDecimal(double value) {
		return new BigDecimal(value).setScale(50, RoundingMode.HALF_DOWN);
	}

	private BigDecimal divide(BigDecimal a, BigDecimal b) {
		if (b.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.ZERO;
		}
		return a.divide(b, 50, RoundingMode.HALF_DOWN);
	}

	protected BarSeries retrieveCandlestick(Interval analysisInterval, int period,
			Map.Entry<Wallet, ProducerTask<Collection<String>>> walletForAvailableCoins, String defaultCollateral,
			String coin) throws ParseException {
		return ((BinanceWallet)walletForAvailableCoins.getKey()).getCandlesticks(
			coin + defaultCollateral,
			analysisInterval,
			null,
			period,
			new BinanceWallet.CandleStick.Converter<BarSeries>() {
				@Override
				public BarSeries convert(Collection<List<?>> input) {
					BarSeries series = new BaseBarSeriesBuilder().withName(coin + "-" + analysisInterval + "-" + period).build();
			        for (List<?> candlestickData : input) {
			        	series.addBar(
		    	        	BaseBar.builder(DecimalNum::valueOf, Number.class)
		                    .timePeriod(analysisInterval.toDuration())
		                    .endTime(
		                		ZonedDateTime.ofInstant(
		                			Instant.ofEpochMilli(
		            					(long)candlestickData.get(6)
		            				),ZoneId.systemDefault()
		            			)
		                	)
		                    .openPrice(Double.parseDouble((String)candlestickData.get(1)))
		                    .highPrice(Double.parseDouble((String)candlestickData.get(2)))
		                    .lowPrice(Double.parseDouble((String)candlestickData.get(3)))
		                    .closePrice(Double.parseDouble((String)candlestickData.get(4)))
		                    .volume(Double.parseDouble((String)candlestickData.get(5)))
		                    .build()
		                );
			        }
			        return series;
				}
			}
		);
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

    private static class Asset {

		public Asset(
			String assetName,
			String collateral,
			Bar oneDHBar,
			Bar fourHBar,
			Double rSILevel,
			Double variationPercentage,
			Map<String, Double> supportAndResistance
		) {
			values = new LinkedHashMap<>();
			values.put(Collection.LABELS.get(Collection.ASSET_NAME_LABEL_INDEX), assetName);
			values.put(Collection.LABELS.get(Collection.COLLATERAL_LABEL_INDEX), collateral);
			values.put(Collection.LABELS.get(Collection.LATEST_1D_BAR_LABEL_INDEX), oneDHBar);
			values.put(Collection.LABELS.get(Collection.LATEST_4H_BAR_LABEL_INDEX), fourHBar);
			values.put(Collection.LABELS.get(Collection.RSI_LABEL_INDEX), rSILevel);
			values.put(Collection.LABELS.get(Collection.PRICE_VARIATION_PERCENTAGE_LABEL_INDEX), variationPercentage);
			values.put(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX), supportAndResistance);
		}
		public void addSupportAndResistance(Map<String, Double> values) {
			Map<String, Double> supportAndResistance =
				(Map<String, Double>)this.values.get(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX));
			if (supportAndResistance != null) {
				supportAndResistance.putAll(values);
			} else {
				this.values.put(Collection.LABELS.get(Collection.SUPPORT_AND_RESISTANCE_LABEL_INDEX), values);
			}

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


    	private static class Collection {
    		private static List<String> LABELS = Arrays.asList("Asset name", "collateral", "Latest price from " + Interval.ONE_DAYS, "Latest price", "RSI on " + Interval.ONE_DAYS , "Price variation % on " + Interval.FOUR_HOURS, "Support and resistance levels");
    		private static int ASSET_NAME_LABEL_INDEX = 0;
    		private static int COLLATERAL_LABEL_INDEX = 1;
    		private static int LATEST_1D_BAR_LABEL_INDEX = 2;
    		private static int LATEST_4H_BAR_LABEL_INDEX = 3;
    		private static int RSI_LABEL_INDEX = 4;
    		private static int PRICE_VARIATION_PERCENTAGE_LABEL_INDEX = 5;
    		private static int SUPPORT_AND_RESISTANCE_LABEL_INDEX = 6;
    		private List<Asset> datas;
    		private Set<String> dynamicLabels;

    		public Collection() {
    			datas = new ArrayList<>();
    			dynamicLabels = new LinkedHashSet<>();
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
    			Map<String, Double> supportAndResistance = data.getSupportAndResistance();
    			if (supportAndResistance != null) {
    				dynamicLabels.addAll(supportAndResistance.keySet());
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
    			return "<table style=\"border-spacing: 20px;\">" +
					"<tr>" +
						String.join("", LABELS.stream().filter(hideColumnFilter()).map(label -> "<td><b>" + label + "</b></td>").collect(Collectors.toList())) +
						String.join("", dynamicLabels.stream().map(label -> "<td><b>" + label + "</b></td>").collect(Collectors.toList())) +
					"</tr>" +
					String.join("", datas.stream().map(this::toHTML).collect(Collectors.toList())) +
				"</table>";
    		}

    		private Predicate<String> hideColumnFilter() {
    			return label -> {
    				return !label.equals(LABELS.get(SUPPORT_AND_RESISTANCE_LABEL_INDEX)) &&
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
        					"",dynamicLabels.stream().filter(hideColumnFilter()).map(label -> {
        						Double value = Optional.ofNullable(data.getSupportAndResistance()).map(supAndRes -> supAndRes.get(label)).orElseGet(() -> null);
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

    	private Map<String, Object> values;

    }

}
