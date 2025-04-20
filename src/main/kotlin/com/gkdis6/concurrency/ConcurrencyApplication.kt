package com.gkdis6.concurrency

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ConcurrencyApplication

fun main(args: Array<String>) {
	runApplication<ConcurrencyApplication>(*args)
}
