FROM mcr.microsoft.com/playwright:v1.62.1-noble@sha256:dcc5531e97840b9b5e794f2814476b21571c5124a3fca2267d73041f56e7580e

ENV NODE_ENV=production \
    PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /opt/pdf-tools-html

COPY package.json package-lock.json ./
RUN apt-get update \
    && apt-get install -y --no-install-recommends dumb-init \
    && rm -rf /var/lib/apt/lists/* \
    && npm ci --omit=dev --ignore-scripts \
    && groupadd --system --gid 10001 htmlworker \
    && useradd --system --uid 10001 --gid htmlworker \
        --home-dir /home/htmlworker htmlworker \
    && mkdir -p \
        /home/htmlworker \
        /tmp/html-work \
        /var/lib/pdf-tools-html/requests \
        /var/lib/pdf-tools-html/responses \
        /var/lib/pdf-tools-html/signals \
    && chown -R htmlworker:htmlworker \
        /home/htmlworker \
        /tmp/html-work \
        /var/lib/pdf-tools-html

COPY --chown=htmlworker:htmlworker src ./src

USER 10001:10001

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["node", "src/daemon.mjs"]
