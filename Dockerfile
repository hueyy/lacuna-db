FROM datasetteproject/datasette

RUN datasette install datasette-vega datasette-pretty-json

COPY ./data.db /data/data.db
COPY ./metadata.yml /data/metadata.yml

WORKDIR /app

EXPOSE 8001
ENTRYPOINT ["datasette", "serve", "-i", "/data/data.db", "-p", "8001", "-h", "0.0.0.0", "--metadata", "/data/metadata.yml"]
