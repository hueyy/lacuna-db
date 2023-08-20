# law-archive-data

This repository contains legal data obtained various public sources and converted into a machine-readable format, including:

- Data about court hearings: `hearings.json`
- Data about Senior Counsel: `sc.json`

The code and configuration files in this repository are licensed under the EUPL-1.2.

The data remains owned by its respective owners. This repository is not affiliated with the Singapore Academy of Law, Singapore Courts, or any government agency, and is provided for convenience only.

## How the data is obtained

The data is obtained periodically via scheduled GitHub action workflows and committed to this repository.

## Development

The historical data across the many commits can be analysed using the [`git-history`](https://github.com/simonw/git-history) tool.

```bash
bb ./scripts/build-db.bb
bb ./scripts/publish-dockerfile.bb
```

