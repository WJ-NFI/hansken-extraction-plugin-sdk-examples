FROM openjdk:11.0.9.1-jre-buster

LABEL hansken.extraction.plugin.name="JavaDataDigestPlugin"

ADD target/*.tar.gz .

EXPOSE 8999
ENTRYPOINT ["java", "-jar", "plugin.jar"]
