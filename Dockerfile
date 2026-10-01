# Stage 1: Build the WAR file using Maven and Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Deploy the WAR file to Apache Tomcat 11
FROM tomcat:11.0-jdk21-temurin
# Remove default Tomcat sample apps
RUN rm -rf /usr/local/tomcat/webapps/*
# Copy our built WAR file as ROOT.war so it loads directly at "/"
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]