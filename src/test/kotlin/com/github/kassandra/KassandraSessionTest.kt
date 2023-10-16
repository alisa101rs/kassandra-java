package com.github.kassandra

import com.datastax.oss.driver.api.core.CqlSession
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.result.shouldNotBeSuccess
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import java.util.*


@OptIn(ExperimentalSerializationApi::class)
class KassandraSessionTest : StringSpec({
    suspend fun KassandraSession.cqlSession(): CqlSession {
        start()
        delay(10)

        return CqlSession.builder()
            .withLocalDatacenter("datacenter1")
            .addContactPoint(address)
            .build()
    }

    fun CqlSession.initializeTables() {
        execute(
            """CREATE KEYSPACE cycling 
                  WITH REPLICATION = { 
                   'class' : 'NetworkTopologyStrategy', 
                   'datacenter1' : 1 
                  } ;
            """.trimIndent()
        )
        execute(
            """CREATE TABLE cycling.cyclist_name ( 
                   id UUID PRIMARY KEY, 
                   lastname text, 
                   firstname text )
            """.trimIndent()
        )
    }


    "initialize keyspace and table" {
        val session = Kassandra.create()
        val cql = session.cqlSession()

        runCatching { cql.initializeTables() }.shouldBeSuccess()
    }

    val uuid = UUID.randomUUID()

    "insert and select" {
        val session = Kassandra.create()
        val cql = session.cqlSession()
        cql.initializeTables()
        cql.execute(
            "insert into cycling.cyclist_name (id, lastname, firstname) values (?, ?, ?)",
            uuid,
            "Last",
            "First"
        )
        val row = cql.execute("select id, lastname, firstname from cycling.cyclist_name where id = ?", uuid).one()
            .shouldNotBeNull()

        row.get(1, String::class.java) shouldBe "Last"
        row.get(2, String::class.java) shouldBe "First"

        session.rawSnapshot() shouldBe """{"cycling":{"tables":{"cyclist_name":{"rows":[{"partition_key":"$uuid","clustering_key":null,"data":{"firstname":"First","id":"$uuid","lastname":"Last"}}]}}}}"""

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


    "state should be saved and load successfully" {
        val getState = suspend {
            val session = Kassandra.create()
            val cql = session.cqlSession()
            cql.initializeTables()
            cql.execute(
                "insert into cycling.cyclist_name (id, lastname, firstname) values (?, ?, ?)",
                uuid,
                "Last",
                "First"
            )
            val state = session.saveState()
            cql.close()
            session.close()
            state
        }

        val state = getState()
        val session = Kassandra.createWithState(state)
        val cql = session.cqlSession()
        val row = cql.execute("select id, lastname, firstname from cycling.cyclist_name where id = ?", uuid).one()
            .shouldNotBeNull()

        row.get(1, String::class.java) shouldBe "Last"
        row.get(2, String::class.java) shouldBe "First"
    }


    "incorrect state throw an error" {
        runCatching { Kassandra.createWithState("incorrect") }.shouldNotBeSuccess()
    }

    "close successfully" {
        val session = Kassandra.create()
        val cql = session.cqlSession()
        cql.close()
        runCatching { session.close() }.shouldBeSuccess()
    }
})