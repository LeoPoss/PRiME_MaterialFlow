package de.ur.operational.process.util

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object JsonUtil {
    val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    
    inline fun <reified T> fromJson(json: String?): T? =
        json?.let { mapper.readValue<T>(it) }
    
    fun toJson(obj: Any?): String =
        mapper.writeValueAsString(obj)
}
