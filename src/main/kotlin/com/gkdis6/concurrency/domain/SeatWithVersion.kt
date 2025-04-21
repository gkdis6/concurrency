package com.gkdis6.concurrency.domain

import jakarta.persistence.*

@Entity
@DiscriminatorValue("WITH_VERSION")
class SeatWithVersion(
    // Inherit id, event, seatNumber, reserved from BaseSeat
    event: Event?, // Pass event to super constructor or set later
    seatNumber: String,

    // Remove @Version field definition - it will be inherited from BaseSeat
    // @Version
    // val version: Long = 0L

) : BaseSeat(
    seatNumber = seatNumber
    // id, reserved are handled by BaseSeat defaults
) {

    // Override reserve to include inherited version in the message
    override fun reserve() {
        if (reserved) {
            // Access inherited version property
            throw IllegalStateException("이미 예약된 좌석입니다. (Seat ID: ${this.id}, Version: ${this.version})")
        }
        reserved = true
    }

    // Override cancelReservation similarly if needed
    override fun cancelReservation() {
        if (!reserved) {
            // Access inherited version property
            println("이미 취소된 좌석입니다. (Seat ID: ${this.id}, Version: ${this.version})")
             return
        }
        reserved = false
    }

    // Constructor or init block to set the event relationship
    init {
        this.event = event
    }
} 