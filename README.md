# README #

A Spring Boot app that take data from sources into HBase.

### Components ###

* common
* data-ingest

### How to build ###

1 ./gradlew build (needed to generated Avro classes)

2 Open the project in your IDE

### How to run ###

* ./gradlew bootRun

### How to authenticate ###

Right now we support OAuth 2 password credential grant flow.

You need to provide the following fields in your request body:

* username
* password
* client_id (depends on which Keycloak server you use)
* grant_type (at this moment, must be "password")
* client_secret (depends on which Keycloak server you use)

The above call will return a JSON result that contains an "access_token" field.

Any subsequent calls must have an "Authorization" header looks like this:

Authorization : Bearer the_token_string_you_get_from_above_api_call