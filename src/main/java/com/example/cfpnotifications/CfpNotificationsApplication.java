package com.example.cfpnotifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.IteratorItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@SpringBootApplication
public class CfpNotificationsApplication {

	@Bean
	ConfsTechClient confsTechClient(ObjectMapper objectMapper, JsoupChromeClient jsoupChromeClient) {
		return new ConfsTechClient(objectMapper, jsoupChromeClient);
	}

	@Bean
	JsoupChromeClient jsoupChromeClient(@Value("${chromedriver.binaries}") File[] phantomJsPath) {
		var chromeDriverBinaries = Arrays.stream(phantomJsPath).filter(File::exists).collect(Collectors.toSet());
		Assert.isTrue(chromeDriverBinaries.size() > 0, () -> "you must have chromedriver installed somewhere!");
		chromeDriverBinaries.forEach(f -> log.info("the chromedriver binary is " + f.getAbsolutePath()));
		return new JsoupChromeClient(chromeDriverBinaries.iterator().next());
	}

	public static void main(String[] args) {
		SpringApplication.run(CfpNotificationsApplication.class, args);
	}

}


@Configuration
class EventsBatchJobConfiguration {

	@Bean
	@SneakyThrows
	ItemReader<Event> eventItemReader(ConfsTechClient client) {
		return new IteratorItemReader<Event>(client.read());
	}

	@Bean
	ItemWriter<Event> eventItemWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<Event>().dataSource(dataSource).assertUpdates(false).sql("""
				    INSERT INTO
				        events(name, start_date, end_date, url )
				    VALUES (?, ?, ?, ?)
				    ON CONFLICT ON CONSTRAINT
				        events_pkey
				    DO
				        UPDATE set end_date= ? , url = ?


				""").itemPreparedStatementSetter((event, ps) -> {
			var name = event.name();
			var start = new java.sql.Date(event.startDate().getTime());
			var stop = new java.sql.Date(event.endDate().getTime());
			var url = event.url().toString();
			ps.setString(1, name);
			ps.setDate(2, start);
			ps.setDate(3, stop);
			ps.setString(4, url);
			ps.setDate(5, stop);
			ps.setString(6, url);
		}).build();
	}

	@Bean
	Step stepOne(StepBuilderFactory builderFactory, ItemReader<Event> reader, ItemWriter<Event> writer) {
		return builderFactory.get("http-db").<Event, Event>chunk(500).reader(reader).writer(writer).build();
	}

	@Bean
	Job job(JobBuilderFactory jobs, Step stepOne) {
		return jobs.get("read-conferences").incrementer(new RunIdIncrementer()).start(stepOne).build();
	}

}

@Slf4j
record JsoupChromeClient(File chromeDriverBinary) {

	@SneakyThrows
	Document render(URL url, Predicate<WebDriver> pageReadyWebDriverPredicate) {
		System.setProperty("webdriver.chrome.driver", chromeDriverBinary.getAbsolutePath());
		var headless = true;
		var options = new ChromeOptions().setHeadless(headless).addArguments("--disable-gpu", "--window-size=1920,1200",
				"--ignore-certificate-errors");
		var driver = new ChromeDriver(options);
		try {
			driver.get(url.toString());
			var webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
			webDriverWait.until(pageReadyWebDriverPredicate::test);
			var html = driver.getPageSource();
			return Jsoup.parse(html);
		} //
		finally {
			driver.quit();
		}
	}
}

record ConfsTechClient(ObjectMapper objectMapper, JsoupChromeClient client) {

	List<Event> read() throws Exception {
		var cfpUrl = "https://confs.tech/?online=hybrid&topics=java";
		var resultsLoadedPredicate = (Predicate<WebDriver>) d -> {
			var sections = d.findElements(By.tagName("section"));
			var webElement = sections.iterator().next();
			var noResultsFound = webElement.getText().toLowerCase(Locale.ROOT).contains("no results found");
			return sections.size() > 0 && !noResultsFound;
		};
		var render = this.client.render(new URL(cfpUrl), resultsLoadedPredicate);
		var scripts = render.getElementsByAttributeValue("type", "application/ld+json");
		return scripts.stream().map(Element::html).map(this::parse).toList();
	}

	@SneakyThrows
	private Event parse(String jsonLd) {
		var tr = new TypeReference<Map<String, Object>>() {
		};
		var eventMap = objectMapper.readValue(jsonLd, tr);
		return new Event(parseLocation(eventMap.get("location")), //
				parseEventAttendanceMode((String) eventMap.get("eventAttendanceMode")), //
				(String) eventMap.get("name"), //
				parseDate((String) eventMap.get("startDate")), //
				parseDate((String) eventMap.get("endDate")), //
				new URL((String) eventMap.get("url")));
	}

	private Address parseAddress(Object address) {
		if (address instanceof Map map) {
			return new Address((String) map.get("addressLocality"), (String) map.get("addressCountry"));
		}
		return null;
	}

	private EventAttendanceMode parseEventAttendanceMode(Object eatm) {
		if (eatm != null) {
			if (eatm instanceof String url) {
				for (var eam : EventAttendanceMode.values())
					if (url.toLowerCase(Locale.ROOT).contains(eam.name().toLowerCase(Locale.ROOT)))
						return eam;
			}
		}
		return null;
	}

	private Location parseLocation(Object location) {
		if (location instanceof Map map) {
			var addy = parseAddress(map.get("address"));
			return new Location(addy, (String) map.get("name"));
		}
		return null;
	}

	private Date parseDate(String text) {
		var parts = text.split("-");
		var yr = Integer.parseInt(parts[0].trim());
		var mo = Integer.parseInt(parts[1].trim());
		var day = Integer.parseInt(parts[2].trim());
		var gregorianCalendar = new GregorianCalendar();
		gregorianCalendar.set(GregorianCalendar.MONTH, mo);
		gregorianCalendar.set(GregorianCalendar.YEAR, yr);
		gregorianCalendar.set(GregorianCalendar.DATE, day);
		return Date.from(gregorianCalendar.toInstant());
	}
}

record Address(String addressLocality, String addressCountry) {
}

record Location(Address address, String name) {
}

enum EventAttendanceMode {

	MixedEventAttendanceMode, OfflineEventAttendanceMode, OnlineEventAttendanceMode

}

record Event(Location location, EventAttendanceMode eventAttendanceMode, String name, Date startDate, Date endDate,
		URL url) {
}
