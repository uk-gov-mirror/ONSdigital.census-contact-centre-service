FROM openjdk:11-jre-slim

ARG JAR_FILE=contactcentresvc*.jar
RUN apt-get update
RUN apt-get -yq install curl
RUN apt-get -yq clean
COPY target/$JAR_FILE /opt/contactcentresvc.jar

ENTRYPOINT [ "sh", "-c", "java", "$JAVA_OPTS", "-jar", "/opt/contactcentresvc.jar" ]

