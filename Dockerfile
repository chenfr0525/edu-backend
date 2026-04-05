FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY edu-common/pom.xml edu-common/
COPY edu-domain/pom.xml edu-domain/
COPY edu-repository/pom.xml edu-repository/
COPY edu-service/pom.xml edu-service/
COPY edu-web/pom.xml edu-web/
RUN mvn dependency:go-offline

COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/edu-web/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
