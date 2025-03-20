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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
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
		Map<String, Double> rSIForCoinAlreadyNotified = new TreeMap<>();
		Map<String, Spike> spikeForCoinAlreadyNotified = new TreeMap<>();
		while (true) {
			Map<String, Double> rSIForCoin = new TreeMap<>();
			Map<String, Spike> spikeForCoin = new TreeMap<>();
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
								"Checking asset {}",
								coin
							);
							BarSeries dailyCandleSticks = retrieveCandlestick(
								Interval.ONE_DAYS,
								370,
								walletForAvailableCoins,
								defaultCollateral,
								coin
							);

							checkLowAndHighRSIValue(coin, dailyCandleSticks, rSIForCoinAlreadyNotified, rSIForCoin, 14);
							BarSeries fourHCandleSticks = retrieveCandlestick(
								Interval.FOUR_HOURS,
								125,
								walletForAvailableCoins,
								defaultCollateral,
								coin
							);
							checkSpike(dailyCandleSticks, fourHCandleSticks, coin, spikeForCoinAlreadyNotified, spikeForCoin);
						} catch (Throwable exc) {
							exc.printStackTrace();
						}
					});
					StringBuffer presentation = new StringBuffer("<h1>Ciao!</h1>");
					StringBuffer mailText = new StringBuffer("");
					if (!rSIForCoin.isEmpty()) {
						mailText.append(
							"<h1>Sono stati rilevati i seguenti asset con RSI critico su " + Interval.ONE_DAYS + ":</h1>" +
							rSIToHTMLTable(rSIForCoin, defaultCollateral)
						);
						rSIForCoin.clear();
					}
					if (!spikeForCoin.isEmpty()) {
						mailText.append(
							"<h1>Sono stati rilevati i seguenti asset con spike sulle bande di Bollinger su " + Interval.FOUR_HOURS + ":</h1>" +
							spikesToHTMLTable(spikeForCoin, defaultCollateral)
						);
						spikeForCoin.clear();
					}
					if (!mailText.toString().isEmpty()) {
						sendMail(
							"roberto.gentili.1980@gmail.com,fercoletti@gmail.com",
							"Segnalazioni asset",
							presentation.append(mailText).toString(),
							(String[])null
						);
					}
					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
						getClass()::getName,
						"Waiting 10 seconds"
					);
					Thread.sleep(10000);
					if (LocalDate.now().getDayOfYear() != today.getDayOfYear()) {
						today = LocalDate.now();
						rSIForCoinAlreadyNotified.clear();
					}
				}
			}
		}
	}

	private Map<String, Double> checkSupportAndResistanceCrossing(
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

	protected void checkSpike(
		BarSeries dailyCandleSticks,
		BarSeries fourHoursCandleSticks,
		String coin,
		Map<String, Spike> spikeForCoinAlreadyNotified,
		Map<String, Spike> spikeForCoin
	) throws ParseException {
		int lastCandleIndex = fourHoursCandleSticks.getEndIndex();
		Bar toBeChecked = fourHoursCandleSticks.getBar(lastCandleIndex);

		boolean considerOnlyBBContacts = true;
		Double spikePercentage = 40d;
		Double comparingValue = 3d;
		int bBMaPeriod = 20;
		DecimalNum bBDev = DecimalNum.valueOf(2);
		ClosePriceIndicator closePrice = new ClosePriceIndicator(fourHoursCandleSticks);
		SMAIndicator ma = new SMAIndicator(closePrice, bBMaPeriod);
        StandardDeviationIndicator deviation = new StandardDeviationIndicator(closePrice, bBMaPeriod);
        BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(ma);
        BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, deviation, bBDev);
        BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, deviation, bBDev);
		//BollingerBandFacade bBFacade = new BollingerBandFacade(candlesticks, 20, 2);
		Double bBLower =
			//bBFacade.lower().getValue(lastCandleIndex).doubleValue();
			lowBBand.getValue(lastCandleIndex).doubleValue();
		Double bBUpper =
			//bBFacade.upper().getValue(lastCandleIndex).doubleValue();
			upBBand.getValue(lastCandleIndex).doubleValue();
		Double high = toBeChecked.getHighPrice().doubleValue();
		Double low = toBeChecked.getLowPrice().doubleValue();
		Double priceVariation = high - low;
		Double open = toBeChecked.getOpenPrice().doubleValue();
		Double close = toBeChecked.getClosePrice().doubleValue();
		Double lowSpikeValue = close < open ? close - low : open - low;
		Double highSpikeValue = close > open ? high - close : high - open;
		Double lowSpikePercentage = (lowSpikeValue * 100d)/priceVariation;
		Double highSpikePercentage = (highSpikeValue * 100d)/priceVariation;
		Double totalCandleVariation = ((high - low) / high) * 100d;
		//log.info('variation: {0}', totalCandleVariation)
		boolean buyCondition =
			lowSpikePercentage >= spikePercentage && totalCandleVariation >= comparingValue && lowSpikeValue >= highSpikeValue && (considerOnlyBBContacts ? (low <= bBLower) : true);
		boolean sellCondition =
			highSpikePercentage >= spikePercentage && totalCandleVariation >= comparingValue && highSpikeValue >= lowSpikeValue && (considerOnlyBBContacts ? (high >= bBUpper) : true);
		Map<String, Double> supportAndResistance = checkSupportAndResistanceCrossing(fourHoursCandleSticks, TimeLevel.DAY, Interval.ONE_DAYS);
		//supportAndResistance.putAll(checkSupportAndResistanceCrossing(dailyCandleSticks, TimeLevel.BARBASED, Interval.FOUR_HOURS));
		if (buyCondition || sellCondition) {
			Spike spike = new Spike(toBeChecked, buyCondition? (lowSpikePercentage * -1) : highSpikePercentage, supportAndResistance);
			synchronized(spikeForCoin) {
				Bar latestNotified = Optional.ofNullable(spikeForCoinAlreadyNotified.get(coin)).map(Spike::getBar).orElseGet(() -> null);
				if (latestNotified != null) {
					if (latestNotified.getBeginTime().compareTo(toBeChecked.getBeginTime()) != 0) {
						spikeForCoin.put(coin, spike);
						spikeForCoinAlreadyNotified.put(coin, spike);
					} else {
						org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
							getClass()::getName,
							"Spike already notified for coin {}",
							coin
						);
					}
				} else {
					spikeForCoin.put(coin, spike);
					spikeForCoinAlreadyNotified.put(coin, spike);
				}
			}
		}
	}

	protected void checkLowAndHighRSIValue(
		String coin,
		BarSeries candlesticks,
		Map<String, Double> alreadyNotified,
		Map<String, Double> rSIForCoin,
		int period
	) {
		ClosePriceIndicator closePrice = new ClosePriceIndicator(candlesticks);
		RSIIndicator rSIIndicator = new RSIIndicator(closePrice, period);
		List<Num> values = rSIIndicator.stream().collect(Collectors.toList());
		Double latestRSIValue = values.get(candlesticks.getEndIndex()).doubleValue();
		if ((latestRSIValue > 70 || latestRSIValue < 30) && latestRSIValue != 0) {
			synchronized(rSIForCoin) {
				if (!alreadyNotified.containsKey(coin)) {
					rSIForCoin.put(coin, latestRSIValue);
					alreadyNotified.put(coin, latestRSIValue);
				} else {
					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(
						getClass()::getName,
						"Coin {} with value {} already notified for RSI value",
						coin, latestRSIValue
					);
				}
			}
		}
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

	private String rSIToHTMLTable(Map<String, Double> rSIForCoinEntrySet, String collateral) {
		return "<table>" +
			String.join(
				"",
				rSIForCoinEntrySet.entrySet().stream().map(
					rec ->
						"<tr>" +
							"<td><a href=\"" + "https://www.binance.com/it/trade/" + rec.getKey() + "_" + collateral + "?type=isolated" + "\">" + rec.getKey() + "</a></td>"+
							"<td width=\"25px\"></td><td><p style=\"color: " + (rec.getValue() <= 50 ? "green" : "red") +"\">" + rec.getValue() + "</p></td>" +
						"</tr>"
				).collect(Collectors.toList())
			) +
		"</table>";
	}

	private String spikesToHTMLTable(Map<String, Spike> spikeForCoin, String collateral) {
		String header =
		"<tr>" +
			"<td><b>" +
				"Asset name" +
			"</b></td>" +
			"<td><b>" +
				"Spike variation" +
			"</b></td>" +
			"<td><b>" +
				"Current price" +
			"</b></td>" +
			String.join("", spikeForCoin.entrySet().stream().findFirst().get().getValue().getSupportAndResistance().keySet().stream().map(supResLabel -> "<td><b>" + supResLabel + "</b></td>").collect(Collectors.toList())) +
		"</tr>";
		;
		return "<table style=\"border-spacing: 20px;\">" +
				header +
				String.join(
					"",
					spikeForCoin.entrySet().stream().map(
						rec ->
							"<tr>" +
								"<td><a href=\"" + "https://www.binance.com/it/trade/" + rec.getKey() + "_" + collateral + "?type=isolated" + "\"><p>" + rec.getKey() + "</p></a></td>"+
								"<td><p style=\"color: " + (rec.getValue().getVariationPercentage() <= 0 ? "green" : "red") +"\">" + format(rec.getValue().getVariationPercentage()) + "&#37;</p></td>" +
								"<td>" + format(rec.getValue().getBar().getClosePrice().doubleValue()) +"</td>" +
								String.join("", rec.getValue().getSupportAndResistance().values().stream().map(supResVal -> "<td>" + format(supResVal) + "</td>").collect(Collectors.toList())) +
							"</tr>"
					).collect(Collectors.toList())
				) +
			"</table>";
	}



	private String format(double value) {
		// TODO Auto-generated method stub
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

    private static class Spike {
    	private Bar bar;
    	private Double variationPercentage;
    	private Map<String, Double> supportAndResistance;

		public Spike(Bar bar, Double variationPercentage, Map<String, Double> supportAndResistance) {
			this.bar = bar;
			this.variationPercentage = variationPercentage;
			this.supportAndResistance = supportAndResistance;
		}
		public Bar getBar() {
			return bar;
		}
		public Double getVariationPercentage() {
			return variationPercentage;
		}
		public Map<String, Double> getSupportAndResistance() {
			return supportAndResistance;
		}


    }

}
