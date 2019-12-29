FROM openjdk:8

ADD target/universal/stage /app

WORKDIR /app

VOLUME /data

EXPOSE 8080

ENTRYPOINT ["/app/bin/demo"]
