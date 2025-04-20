package com.gkdis6.concurrency.controller.dto

import com.gkdis6.concurrency.domain.Reservation
import java.time.LocalDateTime

data class ReservationResponse(
    val reservationId: Long,
    val seatId: Long,
    val eventName: String, // Include event name for more context
    val seatNumber: String,
    val userId: String,
    val reservationTime: LocalDateTime
) {
    companion object {
        // Factory method to create Response from Domain object
        fun from(reservation: Reservation): ReservationResponse {
            // Check for null event, though it should be set by logic
            val eventName = reservation.seat.event?.name ?: "N/A"
            return ReservationResponse(
                reservationId = reservation.id,
                seatId = reservation.seat.id,
                eventName = eventName,
                seatNumber = reservation.seat.seatNumber,
                userId = reservation.userId,
                reservationTime = reservation.reservationTime
            )
        }
    }
} 