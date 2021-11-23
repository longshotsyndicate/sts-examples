# Sample STS Applications

Sample project to help get started with the STS API. This contains code to demonstrate some basic use cases.

API specification is found here: `https://app.swaggerhub.com/apis/longshot/STS/2.0.0`

For more information contact Longshot Systems.

## Java

* Run `make` in `/java`

This will:
- download the Swagger spec
- build the Java model
- download the appropriate version of Gradle
- download any Java dependencies
- build the project

There is a class containing a sample application: `uk.co.longshotsystems.sts.SampleApp`. It takes 3 command line arguments:

1. API username
2. API password
3. API root url (eg: `api.example.com/longshot/STS/2.0.0/`)

This application will:

* authenticate with the API
* manage heartbeating
* connect to the websocket
* store any data received
* pick a random event (preferring an inplay game) and bet on it
* handle the lifecycle of this bet
