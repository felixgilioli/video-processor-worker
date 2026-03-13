FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

RUN ./gradlew --no-daemon -Dorg.gradle.dependency.verification=off dependencies

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon -Dorg.gradle.dependency.verification=off

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/video-processor-worker-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]