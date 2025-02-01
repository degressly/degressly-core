# Degressly

[!["Buy Me A Coffee"](https://www.buymeacoffee.com/assets/img/custom_images/orange_img.png)](https://www.buymeacoffee.com/daniyaalk)

---

degressly-core is the multicast fronted component of degressly. It handles replaying the request to primary, secondary and candidate instances and records the differences from each of the replicas.

## Quick start

Run degressly-core with: `mvn spring-boot:run`

### Config flags

| VM Options (When running jar)    | Environment Variables (When using Docker) | Example                                                  | Description                                                                                                                                                                                                                        |
|----------------------------------|-------------------------------------------|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| diff.publisher.bootstrap-servers | diff_publisher_bootstrap_servers          | false(default)                             \| kafka:9092 | Address of kafka bootstrap servers for integration with degressly-comparator                                                                                                                                                       |
| diff.publisher.topic-name        | diff_publisher_topic_name                 | diff_stream                                              | Kafka topic name for integration with degressly-comparator                                                                                                                                                                         |
| primary.host                     | primary_host                              | http://localhost:9000 / http://host.docker.internal:9000 | Forwarding address of primary instance                                                                                                                                                                                             |
| secondary.host                   | secondary_host                            | http://localhost:9001 / http://host.docker.internal:9001 | Forwarding address of secondary instance                                                                                                                                                                                           |
| candidate.host                   | candidate_host                            | http://localhost:9002 / http://host.docker.internal:9002 | Forwarding address of candidate instance                                                                                                                                                                                           |
| return.response.from             | return_response_from                      | PRIMARY(default) \| SECONDARY \| CANDIDATE               | Which instance's response is to be returned to the user.                                                                                                                                                                           |
|wait.after.forwarding.to.primary| wait_after_forwarding_to_primary          | 100                                                      | Time to wait(in ms) after sending the request to primary and before sending it to secondary and candidate replicas. May be negative in which case request will be sent to primary and candidate instances before primary instance. |

## Support

If you would like to reach out for support or feature requests, feel free to drop an email at [me@daniyaalkhan.com](mailto:me@daniyaalkhan.com)

