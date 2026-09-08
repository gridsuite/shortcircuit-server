# Shortcircuit Server

[![Actions Status](https://github.com/gridsuite/shortcircuit-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/shortcircuit-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Ashortcircuit-analysis-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Ashortcircuit-analysis-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **shortcircuit-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **power network short-circuit computation**.

A short-circuit analysis simulates electrical faults (three-phase, single-phase, etc.) on a network — either on all buses or on a specific bus — and computes the resulting short-circuit currents (Icc), feeder contributions, and limit violations, so that switchgear and protection devices can be validated against the network's real fault levels.

It provides the following capabilities:

- **Run short-circuit analysis computations** on a whole network, or targeted on a single bus, using configurable providers.
- **Fault results**: for each simulated fault, the short-circuit current, fault type, limits (min/max Icc) and, optionally, the limit violations and feeder (branch) contributions. Results can be fetched in `BASIC`, `FULL`, `WITH_LIMIT_VIOLATIONS` or `NONE` mode, with filtering, pagination, sorting and CSV export.
- **Feeder results**: per-feeder (branch/injection) contribution to a fault current, with filtering, pagination and sorting.
- **ICC map**: quick access to the map of bus ID → short-circuit current (Icc) for a given voltage level.
- **Manage parameter sets** (create, read, update, duplicate, delete) including model-specific (provider-specific) parameters and predefined configurations (`ICC_MAX_WITH_CEI909`, `ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP`, `ICC_MIN_WITH_NOMINAL_VOLTAGE_MAP`).
- **Debug files**: optionally generate and download a debug export of a computation for troubleshooting.
- **Save external results**: persist a short-circuit result computed outside the service (e.g. by the Monitor project) via a dedicated endpoint. The result is stored in the same PostgreSQL database and JPA entities as a normal run, but only status and fault/feeder data are saved (short-circuit limits are not computed/stored for this path).
- Run computations either **synchronously** (direct response) or **asynchronously** (via a RabbitMQ message queue).

---

## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL
- Liquibase
- RabbitMQ via Spring Cloud Stream
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus
- [gridsuite-computation](https://github.com/gridsuite/computation)
- [powsybl-shortcircuit-api](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/shortcircuit/index.html): powsybl API/SPI for short-circuit computation, used to run the analysis and model its inputs/outputs (faults, parameters, results). The concrete computation engine is a pluggable provider, resolved at runtime via `ServiceLoader`.

---

## Development Scripts

Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `src/main/resources/db/changelog/db.changelog-master.yaml`.

---

## Interactions with Other Microservices

```text
┌───────────────────────────────┐
│  shortcircuit-server          │──► network-store-server  (read network topology)
│                               │──► filter-server          (resolve equipment filters)
│                               │──► report-server          (post computation functional logs)
└───────────────────────────────┘
          ▲  ▼
       RabbitMQ (shortcircuitanalysis.run / .cancel / .result / .debug / .stopped / .cancelfailed)
```

---

## Asynchronous Execution Flow

1. The controller publishes a message on the `shortcircuitanalysis.run` queue.
2. Parallel consumers (`consumeRun1`, `consumeRun2`) process messages concurrently for load balancing.
3. The computation result is published on `shortcircuitanalysis.result` (and, if requested, a debug file on `shortcircuitanalysis.debug`).
4. Cancellation of a running computation goes through the `shortcircuitanalysis.cancel` queue.
5. Dead-letter queues (`shortcircuitanalysis.run.dlx`) and quorum queues ensure reliability.

---

## Result Data

A short-circuit analysis result is composed of several complementary datasets exposed through the REST API:

| Dataset | Description |
|---|---|
| **Fault results** | For each simulated fault: fault type, computed short-circuit current (Icc), limits, and optionally the associated limit violations and feeder results. Supports `mode` selection (`BASIC`, `FULL`, `WITH_LIMIT_VIOLATIONS`, `NONE`), column filters, global filters, pagination, sorting and CSV export. |
| **Feeder results** | Per-feeder (branch/injection) contribution to a fault, listing the connectable and its computed current contribution. Supports column filters, pagination and sorting. |
| **ICC map** | Map of bus ID → short-circuit current (Icc), scoped to a given voltage level, for one-bus targeted analyses. |
| **Debug file** | Optional export of the computation inputs/outputs for a given result, downloadable for troubleshooting a provider run. |

---

## Parameters and Predefined Configurations

Short-circuit analysis parameters include:

- **Provider** selection (model-specific parameters are exposed per provider).
- **Fault targeting**: whole network, or a single bus (`busId`).
- **Predefined configurations**: `ICC_MAX_WITH_CEI909`, `ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP`, `ICC_MIN_WITH_NOMINAL_VOLTAGE_MAP`, used to derive default parameter values (e.g. min/max Icc computation, nominal voltage-based limits).
- **Debug mode**: enables generation of a debug file alongside the result.

---

## Built on gridsuite-computation

The following capabilities are provided by the gridsuite-computation shared library:

- asynchronous run/cancel pipeline,
- transactional result notifications,
- network equipment filtering,
- report integration,
- Micrometer observability.

The shortcircuit-server itself focuses on short-circuit-specific logic (parameters, fault/feeder result model, ICC computation, debug export) and delegates the common computation infrastructure to this lib.

---

