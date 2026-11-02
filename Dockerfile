FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
ARG MODULE
RUN mvn -B -pl ${MODULE} -am package -DskipTests
RUN cp ${MODULE}/target/${MODULE}-1.0.0.jar /app/application.jar
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/application.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
