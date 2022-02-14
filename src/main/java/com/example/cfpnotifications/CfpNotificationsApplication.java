package com.example.cfpnotifications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

@SpringBootApplication
public class CfpNotificationsApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(CfpNotificationsApplication.class, args);
        Thread.sleep(10_000);
    }

    @Bean
    ChromeClient chrome(@Value("${chromedriver.binary}") File phantomJsPath) {
        return new ChromeClient(phantomJsPath);
    }

}

@Slf4j
@RequiredArgsConstructor
class ChromeClient {

    private final File chromeDriverBinary;

    @SneakyThrows
    Document render(URL url, Predicate<WebDriver> pageReadyWebDriverPredicate) {
        System.setProperty("webdriver.chrome.driver", chromeDriverBinary.getAbsolutePath());

        var headless = false;
        var options = new ChromeOptions()
                .setHeadless(headless)
                .addArguments("--disable-gpu", "--window-size=1920,1200", "--ignore-certificate-errors");
        var driver = new ChromeDriver(options);
        try {
            driver.get(url.toString());
            var webDriverWait = new WebDriverWait(driver, 30);
            log.info("bef for WebDriver");
            webDriverWait.until(pageReadyWebDriverPredicate::test);
            log.info("aft for WebDriver");
            return Jsoup.parse(driver.getPageSource());
        }//
        finally {
            driver.quit();
        }
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

@Slf4j
@Component
record ConfsTechRunner(ObjectMapper objectMapper, ChromeClient client) {

    List<Event> read() throws Exception {
        var cfpUrl = "https://confs.tech/?online=hybrid&topics=java";
        var render = this.client.render(new URL(cfpUrl), d -> d.findElements(By.tagName("section")).size() > 0);
        var scripts = render.getElementsByAttributeValue("type", "application/ld+json");
        var events = scripts.stream().map(Element::html).map(this::parse).toList();
        if (log.isDebugEnabled())
            for (var e : events)
                log.debug("event: " + e);
        return events;
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
        log.info("the date is " + text);
        var parts = text.split("-");
        var yr = Integer.parseInt(parts[0]);
        var mo = Integer.parseInt(parts[1]);
        var day = Integer.parseInt(parts[2]);
        var gregorianCalendar = new GregorianCalendar();
        gregorianCalendar.set(GregorianCalendar.MONTH, mo);
        gregorianCalendar.set(GregorianCalendar.YEAR, yr);
        gregorianCalendar.set(GregorianCalendar.DATE, day);
        return Date.from(gregorianCalendar.toInstant());
    }
}