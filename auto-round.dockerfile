FROM nvidia/cuda:13.0.2-cudnn-devel-ubuntu24.04

ENV LANG C.UTF-8

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

RUN apt-get update && \
    apt-get install -y --no-install-recommends build-essential git jq libicu74 unzip curl zip ca-certificates wget numactl time && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /root

ENV PATH="/root/.venv/bin:$PATH" \
    VIRTUAL_ENV="/root/.venv" \
    UV_NO_PROGRESS=1 \
    UV_LINK_MODE=copy \
    UV_NO_CACHE=1 \
    TZ='Asia/Shanghai' \
    TQDM_MININTERVAL=120 \
    PYTHONUNBUFFERED=1

RUN uv venv --python=3.12 /root/.venv
RUN curl -k -LsS "https://download.agent.dev.azure.com/agent/4.268.0/vsts-agent-linux-x64-4.268.0.tar.gz" -o agent.tar.gz \
    && tar -xzf agent.tar.gz \
    && rm agent.tar.gz \
    && chmod +x config.sh run.sh

COPY start-agent.sh /start-agent.sh
RUN chmod +x /start-agent.sh

ENTRYPOINT ["/start-agent.sh"]
