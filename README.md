# hmpps-prisoner-finance-general-ledger-api

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-prisoner-finance-general-ledger-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-prisoner-finance-general-ledger-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-finance-general-ledger-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-finance-general-ledger-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)


## Pre-requisites

To be able to run this repo locally you will need the following software installed

- Docker + Docker Compose
  - The easiest way to do this is to install Docker Desktop which comes bundled with both
- Java Development Kit (JDK) 21 
  - The JDK version must match the gradle expectations exactly
- Gradle
  - Running the application will usually download and install this for you
- IntelliJ Idea
  - The ultimate version requires a license from MoJ but licenses are limited but you can use the Community Edition (CE) without issue



## Instructions

### Project set up

Enable pre-commit hooks for formatting and linting code with the following command;

```bash
./gradlew addKtlintFormatGitPreCommitHook addKtlintCheckGitPreCommitHook
```


### Running unit tests

To run the unit tests use the command:

```bash
make unit-test
```

### Running integration tests

To run the integration tests, use the command:

```bash
make integration-test
```

## Coverage
### Show coverage in intellij
- ```bash
  make check
  ```
- Intellij -> Run -> Manage Coverage Reports
- Add the files in build/jacoco/
- Click OK. The coverage report will now appear in the Coverage Tool Window and the code will be highlighted in the editor.

### Open Coverage report in the browser
To visualize the reports in the browser:
- Build the project
- Open the `index.html` files in the folders under `build/reports/jacoco`



## Running the application locally

There a `docker-compose.yml` that can be used to run a local instance in docker and also an
instance of HMPPS Auth.

```bash
make serve
```

will build the application and run it and HMPPS Auth within a local docker instance.

To verify the app has started,
1. ensure the containers are visible (and running) in Docker, and
2. visit http://localhost:8080/health ensuring the result contains "status: UP"


### Running the application in Intellij

```bash
make serve-environment
```

will just start a docker instance of HMPPS Auth. The application should then be started with
a `dev` active profile in Intellij.

### API Documentation
Is available on a running local server at http://localhost:8080/swagger-ui/index.html#/


## Using local API endpoints

### Generating an auth token
- Use this command to request a local auth token:
  ```bash
  curl -X POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n hmpps-prisoner-finance-general-ledger-api:clientsecret | base64)"
  ```

- The response body will contain an access token something like this:

  ```json
  {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5...BAtWD653XpCzn8A",
    "token_type": "bearer",
    "expires_in": 3599,
    "scope": "read write",
    "sub": "hmpps-prisoner-finance-general-ledger"        
  }
  ```
- Use the value of `access_token` as a Bearer Token to authenticate when calling the local API endpoints.

### Health Checks
- `/health`: provides information about the application health and its dependencies.
- `/info`: provides information about the version of deployed application.


## Further resources

This project is community managed by the mojdt `#kotlin-dev` slack channel.
Please raise any questions or queries there. Contributions welcome!

Our security policy is located [here](https://github.com/ministryofjustice/hmpps-prisoner-finance-general-ledger-api/security/policy).

Documentation to create new service is located [here](https://tech-docs.hmpps.service.justice.gov.uk/creating-new-services/).

### Common Kotlin patterns

Many patterns have evolved for HMPPS Kotlin applications. Using these patterns provides consistency across our suite of
Kotlin microservices and allows you to concentrate on building your business needs rather than reinventing the
technical approach.

Documentation for these patterns can be found in the [HMPPS tech docs](https://tech-docs.hmpps.service.justice.gov.uk/common-kotlin-patterns/).
If this documentation is incorrect or needs improving please report to [#ask-prisons-digital-sre](https://moj.enterprise.slack.com/archives/C06MWP0UKDE)
or [raise a PR](https://github.com/ministryofjustice/hmpps-tech-docs).

## Running the application locally

The application comes with a `dev` spring profile that includes default settings for running locally. This is not
necessary when deploying to kubernetes as these values are included in the helm configuration templates -
e.g. `values-dev.yaml`.

There is also a `docker-compose.yml` that can be used to run a local instance of the template in docker and also an
instance of HMPPS Auth (required if your service calls out to other services using a token).

```bash
docker compose pull && docker compose up
```

will run the application and HMPPS Auth within a local docker instance.

### Running the application in Intellij

```bash
docker compose pull && docker compose up --scale hmpps-prisoner-finance-general-ledger-api=0
```

will just start a docker instance of HMPPS Auth. The application should then be started with a `dev` active profile
in Intellij.

### Building and running the docker image locally

The `Dockerfile` relies on the application being built first. Steps to build the docker image:
1. Build the jar files
```
./gradlew clean assemble
```
2. Copy the jar files to the base directory so that the docker build can find them
```
cp build/libs/*.jar .
```
3. Build the docker image with required arguments
```
docker build --build-arg GIT_REF=21345 --build-arg GIT_BRANCH=bob --build-arg BUILD_NUMBER=$(date '+%Y-%m-%d') .
```
4. Run the docker image, setting the auth url so that it starts up
```
docker run -e HMPPS_AUTH_URL="https://sign-in-dev.hmpps.service.justice.gov.uk/auth" <sha from step 3>
```


### LocalStack for local SQS queue

LocalStack is a local implementation of AWS services so that we can run integration tests locally and be sure that they will work with live AWS services.
Primarily we are only using SQS for the calculated balance queue though this may be added to in the future.

Integration tests will run an instance of LocalStack automatically, these commands are intended to debug any future or failing tests to see what is currently still in the queue or the dead letter queue.

Install awslocal for debuggging LocalStack SQS 
```bash
brew install awscli-local
```
Get the queue url
```bash
awslocal sqs get-queue-url \
  --queue-name calculated_balance_queue \
  --region eu-west-2
```
Peek at the queue
```bash
awslocal sqs receive-message \
  --queue-url http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/calculated_balance_queue \
  --region eu-west-2 \
  --attribute-names All \
  --message-attribute-names All \
  --max-number-of-messages 10 \
  --visibility-timeout 0
```

Purge/clear the queue
```bash
awslocal sqs purge-queue \
  --queue-url http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/calculated_balance_queue \
  --region eu-west-2
```

Peek at the dead letter queue
```bash
awslocal sqs receive-message \
  --queue-url http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/calculated_balance_queue-dlq \
  --region eu-west-2 \
  --attribute-names All \
  --message-attribute-names All \
  --max-number-of-messages 10 \
  --visibility-timeout 0
```

Purge/clear the dead letter queue
```bash
awslocal sqs purge-queue \
  --queue-url http://sqs.eu-west-2.localhost.localstack.cloud:4566/000000000000/calculated_balance_queue-dlq \
  --region eu-west-2
```
