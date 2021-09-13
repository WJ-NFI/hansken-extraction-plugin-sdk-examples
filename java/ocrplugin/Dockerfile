FROM ubuntu:20.04

RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    default-jre \
 && rm -rf /var/lib/apt/lists/*

ADD target/*tar.gz .
LABEL hansken.extraction.plugin.name="OCRPlugin"

EXPOSE 8999
ENTRYPOINT ["java", "-jar", "plugin.jar"]
