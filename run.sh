#!/usr/bin/env bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://mbp2021.local/bk
export SPRING_DATASOURCE_PASSWORD=bk
export SPRING_DATASOURCE_USERNAME=bk
./target/cfp-notifier
