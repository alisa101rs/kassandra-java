package com.github.kassandra

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

public typealias DataSnapshot = Map<String, Keyspace>

@Serializable
public data class Keyspace(
    val tables: Map<String, Table>
) {
    public constructor(vararg tables: Pair<String, Table>) : this(mapOf(*tables))
}

@Serializable
public data class Table(
    val rows: List<Row>
) {
    public constructor(vararg rows: Row): this(listOf(*rows))
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class Row(
    @JsonNames("partition_key")
    val partitionKey: JsonElement,

    @JsonNames("clustering_key")
    val clusteringKey: JsonElement,

    val data: Map<String, JsonElement>,
)
