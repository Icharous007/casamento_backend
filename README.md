# casamento-backend

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Gallery feed

`GET /api/v1/media/feed?feedId=<uuid>&sort=random&cursor=0&pageSize=10`
requires a guest token. Each request returns `items`, `nextCursor`, `hasMore`,
and `galleryHidden`. Follow `nextCursor` with the same `feedId` and `sort`;
the initial and subsequent batches contain at most 10 media items.

The client generates a new UUID when opening or explicitly refreshing a feed.
Migration `V016__gallery_feed_sessions.sql` stores an ordered UUID snapshot per
guest/event for two hours. Re-fetching a page refreshes its URLs, counters and
like state without changing the order. New uploads appear in a new feed;
hidden/deleted media are excluded on each request. An expired session returns
HTTP 410 (`FEED_EXPIRED`); the client must explicitly start a new feed.
Expired snapshots are removed hourly after a one-day grace period.

Sorts: `recent` (newest first), `popular` (most likes, then newest), and
`random` (weighted random sampling without replacement). Random weights are
`1 + ln(1 + likes) + 2 * ln(1 + comments)`: comments carry more weight, the
logarithm limits popularity dominance, and unengaged media remain eligible.
Only lightweight candidate metadata is scanned when creating the snapshot;
media files are never downloaded by the feed endpoint. Snapshot creation is
intended for event-sized galleries; larger deployments should measure this
scan and snapshot storage before introducing precomputed ranking.
The existing page-based `/api/v1/media` and admin APIs remain compatible.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/casamento-backend-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Hibernate ORM with Panache ([guide](https://quarkus.io/guides/hibernate-orm-panache)): Simplified JPA/Hibernate data access layer with active record and repository patterns
- SmallRye Health ([guide](https://quarkus.io/guides/smallrye-health)): Monitor service health
- Hibernate Validator ([guide](https://quarkus.io/guides/validation)): Bean validation using Hibernate Validator and Jakarta Validation annotations
- SmallRye JWT ([guide](https://quarkus.io/guides/security-jwt)): Secure your applications with JSON Web Token
- Flyway ([guide](https://quarkus.io/guides/flyway)): Handle your database schema migrations
- JDBC Driver - PostgreSQL ([guide](https://quarkus.io/guides/datasource)): Connect to the PostgreSQL database via JDBC
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it

## Provided Code

### Hibernate ORM

Create your first JPA entity

[Related guide section...](https://quarkus.io/guides/hibernate-orm)


[Related Hibernate with Panache section...](https://quarkus.io/guides/hibernate-orm-panache)


### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)

### SmallRye Health

Monitor your application's health using SmallRye Health

[Related guide section...](https://quarkus.io/guides/smallrye-health)
