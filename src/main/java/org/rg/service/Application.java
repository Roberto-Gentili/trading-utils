package org.rg.service;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
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
import org.rg.service.detector.BigCandleDetector;
import org.rg.service.detector.BollingerBandDetector;
import org.rg.service.detector.CriticalIndicatorValueDetectorAbst;
import org.rg.service.detector.RSIDetector;
import org.rg.service.detector.ResistanceAndSupportDetector;
import org.rg.service.detector.SpikeDetector;
import org.rg.service.detector.StochasticRSIDetector;
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

@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@SpringBootApplication
@SuppressWarnings({ "null" })
public class Application implements CommandLineRunner {
	private static final String RECIPIENTS =
		"roberto.gentili.1980@gmail.com"
		+ ",fercoletti@gmail.com"
	;

	static final String mailFontSizeInPixel = "15px";
	static final boolean filterEnabled = true;

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
		List<Map<String, Bar>> alreadyNotified = new ArrayList<>();
		Map<String, Bar> rSIOn1DForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> rSIOn4HForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> bBOn1DForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> bBOn4HForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> stochRSIOn1DForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> stochRSIOn4HForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> spikeForCoinOn4HAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> suddenMovementOn1HForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<String, Bar> suddenMovementOn4HForCoinAlreadyNotified = new ConcurrentHashMap<>();
		Map<Interval,Integer> candlestickQuantityForInterval = new LinkedHashMap<>();
		candlestickQuantityForInterval.put(Interval.ONE_DAYS, 370);
		candlestickQuantityForInterval.put(Interval.FOUR_HOURS, 200);
		candlestickQuantityForInterval.put(Interval.ONE_HOURS, 370);
		while (true) {
			Asset.Collection dataCollection = new Asset.Collection();
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
							AssetDataLoader assetDataLoader = new AssetDataLoader(
								walletForAvailableCoins.getKey(),
								coin,
								defaultCollateral
							);
							for (Entry<Interval, Integer> cFI : candlestickQuantityForInterval.entrySet()) {
								assetDataLoader = assetDataLoader.loadInParallel(cFI.getKey(), cFI.getValue());
							}
							Map<Interval, BarSeries> candlesticks =  assetDataLoader.retrieve();
							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
								getClass()::getName,
								"... All data loaded from remote for asset {}",
								coin
							);
							Asset detected =
								process(
									new RSIDetector(coin,defaultCollateral,candlesticks,14),
									Interval.ONE_DAYS,
									rSIOn1DForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new RSIDetector(coin,defaultCollateral,candlesticks,14),
									Interval.FOUR_HOURS,
									rSIOn4HForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new StochasticRSIDetector(coin,defaultCollateral,candlesticks,14),
									Interval.ONE_DAYS,
									stochRSIOn1DForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new StochasticRSIDetector(coin,defaultCollateral,candlesticks,14),
									Interval.FOUR_HOURS,
									stochRSIOn4HForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new BollingerBandDetector(coin,defaultCollateral,candlesticks, 20, 2d),
									Interval.ONE_DAYS,
									bBOn1DForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new BollingerBandDetector(coin,defaultCollateral,candlesticks, 20, 2d),
									Interval.FOUR_HOURS,
									bBOn4HForCoinAlreadyNotified,
									dataCollection
								);
							detected =
								process(
									new SpikeDetector(coin,defaultCollateral,candlesticks),
									Interval.FOUR_HOURS,
									spikeForCoinOn4HAlreadyNotified,
									dataCollection,
									detected
								);
							detected =
								process(
									new BigCandleDetector(coin,defaultCollateral,candlesticks, 10d),
									Interval.FOUR_HOURS,
									suddenMovementOn4HForCoinAlreadyNotified,
									dataCollection,
									detected
								);
							detected =
								process(
									new BigCandleDetector(coin,defaultCollateral,candlesticks, 3d),
									Interval.ONE_HOURS,
									suddenMovementOn1HForCoinAlreadyNotified,
									dataCollection,
									detected
								);
							if (detected != null) {
								CriticalIndicatorValueDetector resistanceAndSupportDetector =
									new ResistanceAndSupportDetector(coin, defaultCollateral, candlesticks);
								for (Entry<Interval, Integer> cFI : candlestickQuantityForInterval.entrySet()) {
									if (CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(coin) || candlesticks.get(cFI.getKey()).getBarCount() >= cFI.getValue()) {
										detected = process(
											resistanceAndSupportDetector,
											cFI.getKey(),
											null,
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
					if (filterEnabled) {
						dataCollection.filter(asset -> {
							int counter = 0;
							if (asset.getRSI() != null && !asset.getRSI().isEmpty()) {
								counter += asset.getRSI().size();
							} else {
								rSIOn1DForCoinAlreadyNotified.remove(asset.getName());
								rSIOn4HForCoinAlreadyNotified.remove(asset.getName());
							}
							if (asset.getStochasticRSI() != null && !asset.getStochasticRSI().isEmpty()) {
								counter += asset.getStochasticRSI().size();
							} else {
								stochRSIOn1DForCoinAlreadyNotified.remove(asset.getName());
								stochRSIOn4HForCoinAlreadyNotified.remove(asset.getName());
							}
							if (asset.getBollingerBands() != null && !asset.getBollingerBands().isEmpty()) {
								counter += asset.getBollingerBands().size();
							} else {
								bBOn1DForCoinAlreadyNotified.remove(asset.getName());
								bBOn4HForCoinAlreadyNotified.remove(asset.getName());
							}
							if (asset.getSpikeSizePercentage() != null && !asset.getSpikeSizePercentage().isEmpty()) {
								counter += asset.getSpikeSizePercentage().size();
							} else {
								spikeForCoinOn4HAlreadyNotified.remove(asset.getName());
							}
							if (asset.getVariationPercentages() != null && !asset.getVariationPercentages().isEmpty()) {
								counter += asset.getVariationPercentages().size();
							} else {
								suddenMovementOn1HForCoinAlreadyNotified.remove(asset.getName());
								suddenMovementOn4HForCoinAlreadyNotified.remove(asset.getName());
							}
							return CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(asset.getName()) || counter >=2;
						});
					}
					StringBuffer presentation = new StringBuffer("<p style=\"font-size:" + mailFontSizeInPixel + ";\">Ciao!</br>Sono stati rilevati i seguenti " + (dataCollection.size() -1) + " asset (BTC escluso) con variazioni rilevanti</p>");
					if (dataCollection.size() > 1) {
						sendMail(
							RECIPIENTS,
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

	protected Asset checkIfAlreadyNotified(
		Asset asset,
		Interval interval,
		Map<Interval, BarSeries> candlesticks,
		Map<String, Bar> alreadyNotified
	) throws ParseException {
		if (asset == null) {
			return null;
		}
		if (alreadyNotified == null) {
			return asset;
		}
		if (CriticalIndicatorValueDetectorAbst.checkIfIsBitcoin(asset.getName())) {
			return asset;
		}
		Bar latestNotified = alreadyNotified.get(asset.getName());
		boolean alreadyNotifiedFlag = false;
		Bar latestBar = candlesticks.get(interval).getLastBar();
		if (latestNotified != null) {
			if (latestNotified.getBeginTime().compareTo(latestBar.getBeginTime()) != 0) {
				alreadyNotified.put(asset.getName(), latestBar);
			} else {
				alreadyNotifiedFlag = true;
				org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
					getClass()::getName,
					"Variation already notified for coin {}",
					asset.getName()
				);
			}
		} else {
			alreadyNotified.put(asset.getName(), latestBar);
		}
		if (!alreadyNotifiedFlag) {
			return asset;
		}
		return null;
	}

	static String format(double value) {
		return String.format("%1$,.8f", value);
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

}
