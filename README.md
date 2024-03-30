# Degressly

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/daniyaalk)

---

Degressly is a portmanteau of "Deterministic Regression". Inspired by [opendiffy/diffy](https://github.com/opendiffy/diffy), degressly aims to create a scalable framework for performing regression testing.

Degressly works by running three parallel instances of your code side by side and multicasting all inbound requests to all three instances. The primary and secondary instances run your last known good code, while the candidate instances runs code that is to be tested.
Differences between primary and candidate instances involve noise from non-deterministic sources like random number generation and timestamps, these differences are ignored based on the differences obtained from responses of primary and secondary instances.

The degressly ecosystem depends on the following repositories:

| Repository               | Description                                                                                                                                                                                                                                                      |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [degressly-core](https://github.com/degressly/degressly-core)       | Core frontend HTTP Proxy. Logs responses from each downstream separately, can be configured to push observations for analysis by degressly-comparator.                                                                                                           |
| [degressly-comparator](https://github.com/degressly/degressly-comparator) | Analyzes observations received from Primary, Secondary and Candidate deployments                                                                                                                                                                                 |
| [degressly-downstream](https://github.com/degressly/degressly-downstream) | Downstream proxy for services that make S2S calls to downstream services, with the ability to accomodate non-idempotent downstreams. Logs requests from each downstream separately, can be configured to push observations for analysis by degressly-comparator. |


![Degressly architecture](images/Degressly.png)

## Quick start

Run degressly-core with:
```mvn spring-boot:run```

### Docker

```
docker build -f Dockerfile.quickstart -t degressly-core-quickstart:latest
docker run -p8000:8000 degressly-core-quickstart:latest
```

## Docker setup for development
Due to the structure of maven, the quick start Dockerfile downloads dependencies on each build. For development setup, follow these steps:
1. Compile application:
   * `./mvnw clean package`
2. Build Docker image:
   * `docker build -t degressly-core:latest .  `
3. Run container:
    * `docker run -p8000:8000 degressly-core:latest `

### Config flags

| VM Options                       | Docker Args                      | Example                                                      | Description                                                                  |
|----------------------------------|----------------------------------|--------------------------------------------------------------|------------------------------------------------------------------------------|
| diff.publisher.bootstrap-servers | diff_publisher_bootstrap_servers | false(default)                             \| localhost:9092 | Address of kafka bootstrap servers for integration with degressly-comparator |
| diff.publisher.topic-name        | diff_publisher_topic_name        | diff_stream                                                  | Kafka topic name for integration with degressly-comparator                   |
| primary.host                     | primary_host                     | http://localhost:9000                                        | Forwarding address of primary instance                                       |
| secondary.host                   | secondary_host                   | http://localhost:9001                                        | Forwarding address of secondary instance                                     |
| candidate.host                   | candidate_host                   | http://localhost:9002                                        | Forwarding address of candidate instance                                     |
| return.response.from             | return_response_from             | PRIMARY(default) \| SECONDARY \| CANDIDATE                   | Which instance's response is to be returned to the user.                     |

## Limitations / TODO
_In no particular order:_
* DB layer observation recon (comparator can consume from debezium or similar CDC pipeline).
* DB Proxy...?
* Performance regression tracking.
* Dockerization.

## Support

If you would like to reach out for support or feature requests, feel free to drop an email at [me@daniyaalkhan.com](mailto:me@daniyaalkhan.com)

