# Kassandra: In-Memory CQL Compliant Cassandra for Testing

## Overview

Kassandra is a lightweight, in-memory database engine designed to make it easier to run unit tests against a
CQL-compliant Cassandra database. With Kassandra, you don't need a running instance of Cassandra on your local machine;
the library provides all the necessary features to interact with your Cassandra codebase for testing purposes.

Forget about the hassle of setting up and tearing down real Cassandra instances just for your tests.
Kassandra aims to make your development workflow smoother and faster.

## Features

- **In-Memory Database**: Quick to start and low on resource usage.
- **CQL Compliant**: Easily integrate with your existing Cassandra queries.
- **Fast Execution**: Optimized for running multiple tests in parallel.
- **Ease of Use**: Designed to be simple to integrate into existing unit tests.

Note: this is java bindings to the [rust](https://github.com/alisa101rs/kassandra) version of this library

## Installation

### Kotlin DSL

Add the JitPack repository to your `build.gradle.kts`

```kotlin
allprojects {
    repositories {
        ...
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.kassandra:kassandra:$KASSANDRA_VERSION")
}
```

## Quick Start

### Basic

```kotlin
val kassandra = Kassandra.create()

// Start listening for incoming cql request on a random port
kassandra.start()

// Create cql session implementation("com.datastax.oss:java-driver-core:$DRIVER_VERSION")
val cqlSession = CqlSession.builder()
    .withLocalDatacenter("datacenter1")
    .addContactPoint(kassandra.address)
    .build()

// Execute arbitrary queries
cqlSession.execute(
    """CREATE KEYSPACE cycling 
          WITH REPLICATION = { 
           'class' : 'NetworkTopologyStrategy', 
           'datacenter1' : 1 
          } ;
    """.trimIndent()
)
cqlSession.execute(
    """CREATE TABLE cycling.cyclist_name ( 
           id UUID PRIMARY KEY, 
           lastname text, 
           firstname text )
    """.trimIndent()
)

cqlSession.execute(
    "insert into cycling.cyclist_name (id, lastname, firstname) values (?, ?, ?)",
    UUID.random(),
    "Last",
    "First"
)

val rows = cqlSession.execute("select * from cycling.cyclist_name")


```

### Unit tests with snapshots

```kotlin 

class Test : StringSpec({
    fun KassandraSession.cqlSession(): CqlSession {
        start()

        return CqlSession.builder()
            .withLocalDatacenter("datacenter1")
            .addContactPoint(address)
            .build()
    }

    "insert should produce expected output" {
        val session = Kassandra.create()
        val cql = session.cqlSession()

        // Init keyspace and table
        cql.initTables()
        // Do some changes to DB
        val uuid = UUID.randomUUID()
        cqlSession.execute(
            "insert into cycling.cyclist_name (id, lastname, firstname) values (?, ?, ?)",
            uuid,
            "Last",
            "First"
        )

        // Assert on json snapshot
        session.rawSnapshot() shouldBe """{"cycling":{"tables":{"cyclist_name":{"rows":[{"partition_key":"$uuid","clustering_key":null,"data":{"firstname":"First","id":"$uuid","lastname":"Last"}}]}}}}"""

        // Or assert on deserialized snapshot
        session.snapshot() shouldBe mapOf(
            "cycling" to Keyspace(
                "cyclist_name" to Table(
                    Row(
                        JsonPrimitive("$uuid"),
                        JsonPrimitive(null),
                        mapOf(
                            "id" to JsonPrimitive("$uuid"),
                            "lastname" to JsonPrimitive("Last"),
                            "firstname" to JsonPrimitive("First"),
                        )
                    )
                )
            )
        )
    }
})
```

`KassandraSession.snapshot()` and `KassandraSession.rawSnapshot()` returns json representation of non-empty non-system
keyspaces and tables.
See [DataSnapshot](src/main/kotlin/com/github/kassandra/DataSnapshot.kt) for a schema.

You can also deserialize `rawSnapshot` into your own typed representation of tables with your json library of choice.

### States

In order to skip keyspace, table, data initialization step in your test,
you can save the entire DB state to a string constant:

```kotlin
val session = Kassandra.create()
val cql = session.cqlSession()

// Make some changes to newly created table:
cql.initializeTables()
cql.execute(
    "insert into cycling.cyclist_name (id, lastname, firstname) values (?, ?, ?)",
    uuid,
    "Last",
    "First"
)

// Save the state
val state = session.saveState() // "H4sIANwpDWUAA+1cUW/jNhJ+v18R+CkB6..."
```

And load it later to without any initialization

```kotlin
"some unit test" {
    val session = Kassandra.createWithState("H4sIANwpDWUAA+1cUW/jNhJ+v18R+CkB6...")
    val cql = session.cqlSession()

    val row = cql.execute("select id, lastname, firstname from cycling.cyclist_name where id = ?", uuid).one()
        .shouldNotBeNull()

    row.get(1, String::class.java) shouldBe "Last"
    row.get(2, String::class.java) shouldBe "First"
}
```


---

## Contribution

Contributions are always welcome! Feel free to open an issue or submit a pull request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

