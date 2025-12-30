FROM eclipse-temurin:17-jre

WORKDIR /app

COPY build/libs/demo-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx2048m", "-Xms1024m", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]