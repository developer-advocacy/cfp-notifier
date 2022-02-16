package com.example.cfpnotifications;

/*
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
*/

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.nativex.hint.InitializationHint;
import org.springframework.nativex.hint.InitializationTime;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@NativeHint(
        resources = {@ResourceHint(patterns = {
                "org/asynchttpclient/request/body/multipart/ahc-mime.types",
                "org/asynchttpclient/config/ahc-default.properties",
                "org/asynchttpclient/config/ahc-version.properties",
        })},
        initialization = {@InitializationHint(types = {io.netty.channel.DefaultFileRegion.class, io.netty.util.AbstractReferenceCounted.class}, initTime = InitializationTime.RUN)}
)
//@EnableBatchProcessing
@SpringBootApplication
public class CfpNotificationsApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(CfpNotificationsApplication.class, args);
        Thread.sleep(10000);
    }
}


/*

@Slf4j
@RequiredArgsConstructor
class ChromeClient {

    private final File chromeDriverBinary;

    @SneakyThrows
    Document render(URL url, Predicate<WebDriver> pageReadyWebDriverPredicate) {
        System.setProperty("webdriver.chrome.driver", chromeDriverBinary.getAbsolutePath());
        var headless = true;
        var options = new ChromeOptions()
                .setHeadless(headless)
                .addArguments("--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors");
        var driver = new ChromeDriver(options);
        try {
            log.info("before get");
            driver.get(url.toString());
            log.info("before wait");
            var webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
            webDriverWait.until(pageReadyWebDriverPredicate::test);
            log.info("after wait");
            var html = driver.getPageSource();
            log.info("html: " + html);
            return Jsoup.parse(html);
        }//
        finally {
            driver.quit();
        }
    }
}

*/
@Slf4j
@Configuration
class RunnerConfiguration {

    @Bean
    ApplicationRunner runner(ConfsTechClient client) {
        return args -> {
            log.info("running...");
            client.read().forEach(System.out::println);
        };
    }

    @Bean
    ChromeClient chrome(@Value("${chromedriver.binaries}") File[] phantomJsPath) {
        var chromeDriverBinaries =
                Arrays.stream(phantomJsPath).filter(File::exists).collect(Collectors.toSet());
        Assert.isTrue(chromeDriverBinaries.size() > 0, () -> "you must have chromedriver installed somewhere!");
        chromeDriverBinaries.forEach(f -> log.info("the chromedriver binary is " + f.getAbsolutePath()));
        return new ChromeClient(chromeDriverBinaries.iterator().next());
    }
}


@Slf4j
record ChromeClient(File chromeDriverBinary) {

    @SneakyThrows
    Document render(URL url, Predicate<WebDriver> pageReadyWebDriverPredicate) {
        var chromeDriverBinaryAbsolutePath = chromeDriverBinary.getAbsolutePath();
        log.info("the chrome driver is going to be {}", chromeDriverBinaryAbsolutePath);
        System.setProperty("webdriver.chrome.driver", chromeDriverBinaryAbsolutePath);
        var headless = true;
        var options = new ChromeOptions()
                .setHeadless(headless)
                .addArguments("--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors");
        var driver = new ChromeDriver(options);
        try {
            log.info("before get");
            driver.get(url.toString());
            log.info("before wait");
            var webDriverWait = new WebDriverWait(driver, Duration.ofSeconds(30));
            webDriverWait.until(pageReadyWebDriverPredicate::test);
            log.info("after wait");
            var html = driver.getPageSource();
            log.info("html: " + html);
            return Jsoup.parse(html);
        }//
        finally {
            driver.quit();
        }
    }
}


@Component
record ConfsTechClient(ObjectMapper objectMapper, ChromeClient client) {

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
        return new Event(
                parseLocation(eventMap.get("location")),//
                parseEventAttendanceMode((String) eventMap.get("eventAttendanceMode")),//
                (String) eventMap.get("name"),//
                parseDate((String) eventMap.get("startDate")),//
                parseDate((String) eventMap.get("endDate")),//
                new URL((String) eventMap.get("url"))
        );
    }

    private Address parseAddress(Object address) {
        if (address instanceof Map map) {
            return new Address(
                    (String) map.get("addressLocality"),
                    (String) map.get("addressCountry")
            );
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
    MixedEventAttendanceMode,
    OfflineEventAttendanceMode,
    OnlineEventAttendanceMode
}

record Event(Location location, EventAttendanceMode eventAttendanceMode, String name, Date startDate, Date endDate,
             URL url) {
}


/*
@Configuration
class MyApp {

    @Bean
    ChromeClient chrome(@Value("${chromedriver.binary}") File phantomJsPath) {
        return new ChromeClient(phantomJsPath);
    }

    @Bean
    @SneakyThrows
    ItemReader<Event> eventItemReader(ConfsTechClient client) {
        return new IteratorItemReader<Event>(client.read());
    }

    @Bean
    ItemWriter<Event> eventItemWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Event>()
                .dataSource(dataSource)
                .assertUpdates(false)
                .sql("""
                            INSERT INTO
                                events(name, start_date, end_date, url )
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT ON CONSTRAINT
                                events_pkey
                            DO
                                UPDATE set end_date= ? , url = ?


                        """)
                .itemPreparedStatementSetter((event, ps) -> {
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
                })
                .build();
    }

    @Bean
    Step stepOne(StepBuilderFactory builderFactory, ItemReader<Event> reader, ItemWriter<Event> writer) {
        return builderFactory
                .get("http-db")
                .<Event, Event>chunk(500)
                .reader(reader)
                .writer(writer)
                .build();
    }

    @Bean
    Job job(JobBuilderFactory jobs, Step stepOne) {
        return jobs
                .get("read-conferences")
                .incrementer(new RunIdIncrementer())
                .start(stepOne)
                .build();
    }
}

*/