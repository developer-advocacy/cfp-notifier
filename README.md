# CFP Notifier

`cfp-notifier` uses Selenium and JSoup to data-scrape information about upcoming 
industry conferences [from the glorious confs.tech portal](https://confs.tech).

You're going to need `chromedriver` on the OS. Make sure that you've got the correct 
`chromedriver` version for the version [of Google Chrome](https://chromedriver.chromium.org/downloads) 
you've installed.

## Building JRE Application

The usual: `mvn clean package`

## Building a Native Image 

You'll need to use the `native` Maven profile: `mvn -DskipTests=true -Pnative clean package` 

## Building a Docker Image 

The usual: `mvn -DskipTests=true spring-boot:build-image `

