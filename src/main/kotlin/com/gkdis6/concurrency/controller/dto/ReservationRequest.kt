package com.gkdis6.concurrency.controller.dto

// Use data class for simple value holding
data class ReservationRequest(
    val seatId: Long,
    val userId: String
) 