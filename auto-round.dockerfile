FROM pytorch/pytorch:2.10.0-cuda13.0-cudnn9-runtime

RUN apt-get update && apt-get install -y git jq libicu74 unzip curl zip
RUN curl -sL https://aka.ms/InstallAzureCLIDeb | bash

COPY start-agent.sh /start-agent.sh
RUN chmod +x /start-agent.sh

ENTRYPOINT ["/start-agent.sh"]
