#!/usr/bin/env bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://mbp2019.local/bk
#export SPRING_DATASOURCE_URL=jdbc:postgresql://mbp2021.local/bk
export SPRING_DATASOURCE_PASSWORD=bk
#export SPRING_BATCH_JDBC_INITIALIZE_SCHEMA=always
export SPRING_DATASOURCE_USERNAME=bk
./target/cfp-notifier
