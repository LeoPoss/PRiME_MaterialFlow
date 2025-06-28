package de.ur.operational.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SankeyData(
    val nodes: List<SankeyNode>, val links: List<SankeyLink>
)

data class SankeyNode(
    val name: String,
    val id: String,
    val color: String = "#737373" // Default gray color
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SankeyLink(
    val material: String,
    val source: String,
    val target: String,
    val value: Number,
    val unit: String? = null
)