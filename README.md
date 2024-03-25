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

### Config flags (VM options)

| Flag                             | Example        | Description                                                                  |
|----------------------------------|----------------|------------------------------------------------------------------------------|
| diff.publisher.bootstrap-servers | localhost:9092 | Address of kafka bootstrap servers for integration with degressly-comparator |
| diff.publisher.topic-name        | diff_stream    | Kafka topic name for integration with degressly-comparator                   |

## Support

If you would like to reach out for support or feature requests, feel free to drop an email at [me@daniyaalkhan.com](mailto:me@daniyaalkhan.com)

