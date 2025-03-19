package org.rg.service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.burningwave.core.concurrent.QueuedTaskExecutor.ProducerTask;
import org.rg.finance.BinanceWallet;
import org.rg.finance.CryptoComWallet;
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
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@SpringBootApplication
@SuppressWarnings({ "null" })
public class Application implements CommandLineRunner {

	private static final String CRYPTO_REPORT_FILE_NAME = "crypto-report.xlsx";
	private static final String LABEL_INVESTMENT = "Investimento";
	private static final String LABEL_QUANTITY = "Quantità";
	private static final String LABEL_LAST_UPDATE_TIME = "Ultimo aggiornamento";
	private static final String LABEL_BALANCE_01 = "Bilancio";
	private static final String LABEL_BALANCE_02 = "Saldo";
	private static final String LABEL_WALLET_BALANCE = "Saldo portafoglio";
	private static final String LABEL_TOKEN = "Token";

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
		Wallet.Interval analysisInterval = Wallet.Interval.ONE_DAYS;
		int period = 200;
		List<String> uppers = new ArrayList<>();
		List<String> downers = new ArrayList<>();
		Map<String, Double> rSIForCoin = new TreeMap<>();
		for (Map.Entry<Wallet, ProducerTask<Collection<String>>> walletForAvailableCoins : walletsForAvailableCoins.entrySet()) {
			if (walletForAvailableCoins.getKey() instanceof BinanceWallet) {
//				Collection<String> allMarginAssets = ((BinanceWallet)walletForAvailableCoins.getKey())
//					.getAllMarginAssets().stream().map(asset -> asset.get("assetName"))
//					.map(String.class::cast).collect(Collectors.toList());
//				Collection<String> coins = walletForAvailableCoins.getKey().getAvailableCoins();
				Collection<String> marginUSDCCoins = ((BinanceWallet)walletForAvailableCoins.getKey()).getAllMarginAssetPairs()
					.stream().filter(asset -> asset.get("quote").equals("USDC")).map(asset -> asset.get("base")).
					map(String.class::cast).collect(Collectors.toList());
				String defaultCollateral = "USDC";
					//walletForAvailableCoins.getKey().getCollateralForCoin("DEFAULT");
				marginUSDCCoins.parallelStream().forEach(coin -> {
					try {
						BarSeries candlesticks = ((BinanceWallet)walletForAvailableCoins.getKey()).getCandlesticks(
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
						                    .timePeriod(Duration.ofDays(1))
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
						ClosePriceIndicator closePrice = new ClosePriceIndicator(candlesticks);
						RSIIndicator rSIIndicator = new RSIIndicator(closePrice, 14);
						List<Num> values = rSIIndicator.stream().collect(Collectors.toList());
						Double latestRSIValue = values.get(values.size() -1).doubleValue();
						if ((latestRSIValue > 70 || latestRSIValue < 30) && latestRSIValue != 0) {
							synchronized(rSIForCoin) {
								rSIForCoin.put(coin, values.get(values.size() -1).doubleValue());
							}
						}

					} catch (Throwable exc) {

					}
				});
			}
			if (!rSIForCoin.isEmpty()) {
				sendMail(
					"roberto.gentili.1980@gmail.com;fercoletti@gmail.com;",
					"Segnalazione RSI crypto",
					"<h1>Ciao! Ecco le crypto con RSI in ipervenduto/ipercomprato:</h1>" +
					String.join(
						"</br>",
						rSIForCoin.entrySet().stream().map(rec -> rec.getKey() + "&#9;" + rec.getValue())
						.collect(Collectors.toList())
					),
					null
				);
			}
		}
//		long initialTime = currentTimeMillis();
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Searching for '{}'", CRYPTO_REPORT_FILE_NAME);
//		FileSystemItem srcWallet = org.burningwave.core.assembler.ComponentContainer.getInstance().getPathHelper().findResources(path -> path.endsWith(CRYPTO_REPORT_FILE_NAME)).stream().findFirst().get();
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "'{}' found in '{}'", srcWallet.getName(), srcWallet.getAbsolutePath());
//		FileSystemItem mainProjectDir = srcWallet.findFirstInAllParents(FileSystemItem.Criteria.forAllFileThat(parent -> parent.getName().equals("target"))).getParent();
//		FileSystemItem destWallet = FileSystemItem.ofPath(mainProjectDir.getAbsolutePath() + "/src/main/resources/" + srcWallet.getName());
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Opening and reading '{}'", srcWallet.getName());
//		try (InputStream srcWalletInputStream = srcWallet.toInputStream(); Workbook workbook = new XSSFWorkbook(srcWalletInputStream);) {
//			Sheet sheet = workbook.getSheet(LABEL_BALANCE_01);
//			int tokenTypeColumnIndex = getCellIndex(sheet, LABEL_TOKEN);
//			Iterator<Row> rowIterator = sheet.rowIterator();
//			//Skipping header
//			rowIterator.next();
//			Collection<QueuedTaskExecutor.Task> tasks = new ArrayList<>();
//			org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Connecting to remote services");
//			while (rowIterator.hasNext()) {
//				Row row = rowIterator.next();
//				Cell cell = row.getCell(tokenTypeColumnIndex);
//				if (cell != null) {
//					tasks.add(
//						org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor.createTask(
//							() -> {
//								boolean updateExecuted = false;
//								for (Map.Entry<Wallet, ProducerTask<Collection<String>>> walletForAvailableCoins : walletsForAvailableCoins.entrySet()) {
//									if (walletForAvailableCoins.getValue().join().contains(cell.getStringCellValue())) {
//										updateDataForCoin(
//											sheet,
//											walletForAvailableCoins.getKey(),
//											cell.getStringCellValue()
//										);
//										updateExecuted = true;
//									}
//								}
//								if (updateExecuted == true) {
//									//updateInvestmentView(sheet.getWorkbook(), cell.getStringCellValue());
//									org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Data for coin {} successfully updated", cell.getStringCellValue());
//								} else {
//									getQuantityCell(sheet, cell.getStringCellValue()).setCellValue(0D);
//								}
//							}
//						).setExceptionHandler((task, exc) -> {
//							org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logError(
//								getClass()::getName,
//								"Could not update data for coin {}",
//								cell.getStringCellValue()
//							);
//							return false;
//						}).submit()
//					);
//				} else {
//					org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logWarn(
//						getClass()::getName,
//						"Row {} has invalid data",
//						row.getRowNum() + 1
//					);
//				}
//			}
//			tasks.stream().forEach(task-> task.join());
//			LocalDateTime currentTime = LocalDateTime.now(ZoneId.of(environment.getProperty("timezone.default")));
//			int updateDateCellIndex = getCellIndex(sheet, LABEL_LAST_UPDATE_TIME);
//			LocalDateTime lastUpdateTime = sheet.getRow(1).getCell(updateDateCellIndex).getLocalDateTimeCellValue();
//			sheet.getRow(1).getCell(updateDateCellIndex).setCellValue(currentTime);
//			try (FileOutputStream outputStream = new FileOutputStream(destWallet.getAbsolutePath())) {
//				Cell latestWalletBalanceCell = getLatestNotNullCell(sheet, LABEL_WALLET_BALANCE);
//				Cell walletBalanceCell = sheet.getRow(1).getCell(getCellIndex(sheet, LABEL_WALLET_BALANCE));
//				walletBalanceCell.setCellFormula(latestWalletBalanceCell.getAddress().formatAsString());
//				XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);
//				workbook.write(outputStream);
//				Double balanceValue = evaluateCell(sheet, LABEL_BALANCE_02, 1).getNumericCellValue();
//				if (balanceValue > 1000) {
//					sendMail(
//						"roberto.gentili.1980@gmail.com",
//						"Saldo criptovalute in positivo",
//						"<h1>Il saldo delle criptovalute è in positivo!</h1><br/><h2>Scarica da <a href=\"https://github.com/Roberto-Gentili/services/blob/main/src/main/resources/crypto-report.xlsx?raw=true\">qui</a> il report.</h2>"
//					);
//				}
//			}
//			if (currentTime.getDayOfMonth() == 1 && (lastUpdateTime == null || lastUpdateTime.getMonth() != currentTime.getMonth())) {
//
//			}
//		}
//		org.burningwave.core.assembler.StaticComponentContainer.ManagedLoggerRepository.logInfo(getClass()::getName, "Report succesfully updated - Elapsed time: " + getFormattedDifferenceOfMillis(currentTimeMillis(), initialTime));
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

	public Cell evaluateCell(Sheet sheet, String headerName, int rowIndex) {
		FormulaEvaluator evaluator = sheet.getWorkbook().getCreationHelper().createFormulaEvaluator();
		CellReference cellReference = new CellReference(rowIndex, getCellIndex(sheet, headerName));
		Row row = sheet.getRow(cellReference.getRow());
		Cell cell = row.getCell(cellReference.getCol());
		evaluator.evaluateFormulaCell(cell);
		return cell;
	}

	private int getCellIndex(Sheet sheet, String name) {
		return getCellIndex(sheet, 0, name);
	}

	private int getCellIndex(Sheet sheet, int headerIndex, String name) {
		Row header = sheet.getRow(headerIndex);
		Iterator<Cell> cellIterator = header.cellIterator();
		int cellIndex = -1;
		int currentIndex = -1;
		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			currentIndex++;
			if (CellType.STRING.equals(cell.getCellType()) && name.equalsIgnoreCase(cell.getStringCellValue()) ) {
				cellIndex = currentIndex;
				break;
			}
		}
		return cellIndex;
	}

	private Cell getLatestNotNullCell(Sheet sheet, String name) {
		return getLatestNotNullCell(sheet, 0, name);
	}

	private Cell getLatestNotNullCell(Sheet sheet, int headerIndex, String name) {
		int cellIndex = getCellIndex(sheet, name);
		Iterator<Row> rowIterator = sheet.rowIterator();
		Cell cell = null;
		while (rowIterator.hasNext()) {
			Cell temp = rowIterator.next().getCell(cellIndex);
			if (!(temp == null || temp.getCellType() == CellType.BLANK)) {
				cell = temp;
			}
		}
		return cell;
	}

	private void updateDataForCoin(Sheet sheet, Wallet wallet, String coinName) {
		Cell quantityCell = getQuantityCell(sheet, coinName);
		if (quantityCell != null) {
			Double quantity = wallet.getQuantityForCoin(coinName);
			if (quantity != null) {
				quantityCell.setCellValue(quantity);
			}
			Cell valueCell = getValueCell(sheet, coinName);
			if (valueCell != null) {
				if (!coinName.equals(wallet.getCollateralForCoin(coinName))) {
					Double value = wallet.getValueForCoin(coinName);
					if (value != null) {
						valueCell.setCellValue(value);
					}
				} else {
					valueCell.setCellValue(1.0D);
				}
			}
		}
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

	private void updateInvestmentView(Workbook workbook, String coinName) {
		Sheet investmentSheet = workbook.getSheet(LABEL_INVESTMENT + " " + coinName);
		if (investmentSheet != null) {
			Cell quantityCell = getQuantityCell(workbook.getSheet(LABEL_BALANCE_01), coinName);
			if (quantityCell == null) {
				return;
			}
			Double newValue = quantityCell.getNumericCellValue();
			int cellIdx = getCellIndex(investmentSheet, LABEL_QUANTITY);
			Cell firstCell = investmentSheet.getRow(1).getCell(cellIdx);
			Double oldValue = firstCell.getNumericCellValue();
			if (todayIsFirstDayOfTheYear() && isCurrentTimeMinorOrEqualsThan(1, 59)) {
				firstCell.setCellValue(newValue);
				newValue = evaluateCell(investmentSheet, LABEL_QUANTITY, 2).getNumericCellValue();
				firstCell.setCellValue(newValue);
			} else if (oldValue != null && oldValue < newValue) {
				firstCell.setCellValue(newValue);
			}
		}
	}

	private Cell getQuantityCell(Sheet sheet, String coinName) {
		return getCellForCoin(sheet, coinName, 1);
	}

	private Cell getValueCell(Sheet sheet, String coinName) {
		return getCellForCoin(sheet, coinName, 2);
	}

	private Cell getCellForCoin(Sheet sheet, String coinName, int offset) {
		Iterator<Row> rowIterator = sheet.rowIterator();
		while (rowIterator.hasNext()) {
			Row row = rowIterator.next();
			Iterator<Cell> cellIterator = row.cellIterator();
			while (cellIterator.hasNext()) {
				Cell cell = cellIterator.next();
				if (CellType.STRING.equals(cell.getCellType()) && coinName.equals(cell.getStringCellValue())) {
					while (offset-- > 0) {
						cell = cellIterator.next();
					}
					return cell;
				}
			}
		}
		return null;
	}

	private String getFormattedDifferenceOfMillis(long value1, long value2) {
		String valueFormatted = String.format("%04d", (value1 - value2));
		return valueFormatted.substring(0, valueFormatted.length() - 3) + "," + valueFormatted.substring(valueFormatted.length() -3);
	}


    private Long currentTimeMillis() {
        return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
