# Use a debian image with a configured python 3.8 installation as base image
FROM python:3.8-slim AS base

FROM base AS builder


# Copy the wheel containing the plugin to the image
RUN mkdir -p /install
COPY dist distribution

# Install wheel
RUN pip install --prefix='/install' --no-warn-script-location distribution/*.whl


FROM base

LABEL maintainer="tester@holmes.nl"
# plugin discovery aid
LABEL hansken.extraction.plugin.name="PythonSecretsPlugin"

COPY --from=builder /install /usr/local
COPY /plugin /

EXPOSE 8999

ENTRYPOINT ["/usr/local/bin/serve_plugin"]
CMD ["secrets_plugin.py", "8999"]
