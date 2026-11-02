FROM iboates/osm2pgsql:2.3.1
USER root
RUN apk add --no-cache bash curl postgresql-client util-linux lua5.4
WORKDIR /workspace
ENTRYPOINT ["bash", "/workspace/importer/run.sh"]