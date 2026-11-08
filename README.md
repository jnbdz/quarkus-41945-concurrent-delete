# Reproducer for quarkusio/quarkus#41945

Concurrent `deleteById` calls on a Hibernate Reactive Panache entity fail with
`HR000069: Detected use of the reactive Session from a different Thread`, or never answer.

The application is the Hibernate Reactive Panache quickstart attached to the issue.
`ConcurrentDeleteTest` creates 200 fruits, deletes them all at the same time with `DELETE /fruits/{id}`,
and repeats this five times. It counts every response that is not 204 and every request without an answer.

## Run

    ./run.sh                      # 3.2.12.Final, 3.40.1.1 and 4.0.0.Beta3
    ./run.sh 3.40.1.1             # one version

The test needs Docker or Podman for the PostgreSQL Dev Service. Quarkus 3.2 needs JDK 17 or newer, the others JDK 21.

## Results on 2026-11-08

| Quarkus | Hibernate Reactive | HR000069 errors | No response after 30 s |
|---|---|---|---|
| 3.2.12.Final | 2.0.8.Final | 6 | 19 |
| 3.40.1.1 | 3.4.2.Final | 7 | 91 |
| 4.0.0.Beta3 | 5.1.0.Beta1 | 0 | 0 |

The fix is hibernate/hibernate-reactive#4174.
