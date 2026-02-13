FROM ubuntu:24.04
ENV LANG C.UTF-8

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential git jq libicu74 unzip curl zip ca-certificates wget numactl time && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p /workspace
WORKDIR /workspace

ENV PATH="/workspace/.venv/bin:$PATH" \
    VIRTUAL_ENV="/workspace/.venv" \
    UV_NO_PROGRESS=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1

RUN uv venv --python=3.13 /workspace/.venv
RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* && \
    find /opt/az -type d -name "__pycache__" -exec rm -rf {} + && \
    find /opt/az -type d -name "tests" -exec rm -rf {} +

RUN which python && python --version && az --version

COPY start-agent.sh /start-agent.sh
RUN chmod +x /start-agent.sh

ENTRYPOINT ["/start-agent.sh"]
