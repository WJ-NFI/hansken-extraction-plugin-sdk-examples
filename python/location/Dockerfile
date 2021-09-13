FROM python:3.8-slim AS base

FROM base AS builder


RUN mkdir -p /install
COPY dist distribution

RUN pip install --prefix='/install' --no-warn-script-location distribution/*.whl


FROM base

LABEL maintainer="tester@holmes.nl"
# plugin discovery aid
LABEL hansken.extraction.plugin.name="PythonLocationPlugin"

COPY --from=builder /install /usr/local
COPY /plugin /

EXPOSE 8999

ENTRYPOINT ["/usr/local/bin/serve_plugin"]
CMD ["location_plugin.py", "8999"]
