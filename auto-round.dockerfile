FROM ubuntu:24.04
ENV LANG C.UTF-8

RUN apt-get update && apt-get install -y git jq libicu74 unzip curl zip ca-certificates wget numactl time

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

RUN mkdir -p /workspace
WORKDIR /workspace

ENV PATH="/workspace/.venv/bin:$PATH"
ENV VIRTUAL_ENV="/workspace/.venv"
ENV UV_NO_PROGRESS=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1

RUN uv venv --python=3.13 /workspace/.venv
RUN which python && python --version

RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash

COPY start-agent.sh /start-agent.sh
RUN chmod +x /start-agent.sh

ENTRYPOINT ["/start-agent.sh"]
