FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY target/external-payment-mock-*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8081

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
