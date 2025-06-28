package de.ur.operational

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class OperationalApplication

fun main(args: Array<String>) {
	runApplication<OperationalApplication>(*args)
}
