# law-archive-data

This repository contains Singapore legal data obtained various public sources and converted into a machine-readable format, including relating to the following:

- Court hearings: `data/hearings.json`
- Senior Counsels: `data/sc.json`
- PDPC undertakings: `data/pdpc-undertakings.json`

You can view and query the data using [this Datasette instance](https://law-archive-data.fly.dev/data).

The code and configuration files in this repository are licensed under the EUPL-1.2.

The data remains owned by its respective owners. This repository is not affiliated with the Singapore Academy of Law, Singapore Courts, or any government agency, and is provided for convenience only.

## How the data is obtained

The data is obtained periodically via scheduled GitHub action workflows and committed to this repository.

## Development

The historical data across the many commits can be analysed using the [`git-history`](https://github.com/simonw/git-history) tool.

```bash
bb ./scripts/build_db.bb
```

To run Datasette locally:

```bash
cd law-archive-data
bb ./scripts/dev_docker.bb
```
