# README #

A Spring Boot app that take data from sources into HBase.

### Requires ###
HBase running on port 8080.

http://hbase.apache.org/0.94/book/standalone_dist.html

### Components ###

* common
* data-ingest

### How to build ###

1 ./gradlew build (needed to generated Avro classes)

2 Open the project in your IDE

### How to run ###

* ./gradlew bootRun
