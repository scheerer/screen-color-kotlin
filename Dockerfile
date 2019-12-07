FROM alpine:3.9

RUN apk --no-cache add openjdk8

WORKDIR /app

RUN adduser -D appuser
RUN chown -R appuser:appuser /app

USER appuser

COPY target/screen-color.jar .

CMD java -jar screen-color.jar
