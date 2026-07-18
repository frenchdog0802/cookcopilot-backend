# Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src ./src
RUN ./mvnw -B -DskipTests package \
  && cp target/*.jar /app/app.jar

# Runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"
ENV PORT=8080

COPY --from=build /app/app.jar ./app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
