FROM datasetteproject/datasette

RUN datasette install datasette-vega datasette-pretty-json

WORKDIR /app

EXPOSE 8001
ENTRYPOINT ["datasette", "serve", "-p", "8001", "-h", "0.0.0.0", "--metadata", "/data/metadata.json", "/data/data.db"]
