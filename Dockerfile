FROM maven:3.9.6-amazoncorretto-21

WORKDIR /app

ADD pom.xml .

RUN mvn clean verify --fail-never

ARG diff_publisher_bootstrap_servers=false
ARG diff_publisher_topic_name=diff_stream
ARG primary_host=http://host.docker.internal:9000
ARG secondary_host=http://host.docker.internal:9001
ARG candidate_host=http://host.docker.internal:9002
ARG return_response_from=PRIMARY

ENV diff_publisher_bootstrap_servers=${diff_publisher_bootstrap_servers}
ENV diff_publisher_topic_name=${diff_publisher_topic_name}
ENV primary_host=$primary_host
ENV secondary_host=$secondary_host
ENV candidate_host=$candidate_host
ENV return_response_from=$return_response_from

COPY . .
RUN mvn clean package

EXPOSE 8000

ENTRYPOINT ["java", "-jar", \
        "-Ddiff.publisher.bootstrap-servers=${diff_publisher_bootstrap_servers}", \
        "-Ddiff.publisher.topic-name=${diff_publisher_topic_name}", \
        "-Dprimary.host=${primary_host}", \
        "-Dsecondary.host=${secondary_host}", \
        "-Dcandidate.host=${candidate_host}", \
        "-Dreturn.response.from=${return_response_from}", \
        "target/core-0.0.1-SNAPSHOT.jar" \
    ]