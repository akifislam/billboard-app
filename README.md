# BillBoard Web

The local browser edition of BillBoard: one dashboard for NESCO, DESCO, and PGCL electricity and gas connections.

## Run locally

Requirements: Node.js 18 or later.

```bash
npm ci
npm start
```

BillBoard opens at `http://localhost:4173`. Add connections from the dashboard and the local server creates `server/meters.json` on your computer.

## Privacy

This public branch contains **no customer or saved meter data**. `server/meters.json` is runtime-only and ignored by Git. The included `server/meters.example.json` is an empty array.

The app runs locally and uses its Express server to request information from the utilities' public portals. Do not expose the local server directly to the public internet without adding authentication, access controls, and appropriate security hardening.

## Supported services

- NESCO prepaid and postpaid electricity
- DESCO prepaid electricity
- PGCL postpaid gas

## Tests

```bash
npm test
```

## Contact

For a customized version, contact [iamakifislam@gmail.com](mailto:iamakifislam@gmail.com).

BillBoard is independent and is not affiliated with or endorsed by NESCO, DESCO, or PGCL.
