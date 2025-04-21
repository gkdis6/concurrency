package com.gkdis6.concurrency.domain

import jakarta.persistence.*

@Entity
@DiscriminatorValue("NO_VERSION")
class SeatWithoutVersion(
    // Inherit id, event, seatNumber, reserved from BaseSeat
    event: Event?, // Pass event to super constructor if needed, or set later
    seatNumber: String
) : BaseSeat(
    seatNumber = seatNumber
    // id, reserved are handled by BaseSeat defaults
    // event needs to be set, JPA relationships handle this but constructor might need adjustment
) {
    // No @Version field here

    // Override reserve if specific behavior needed (e.g., different message)
    // override fun reserve() { ... }

    // Ensure event is set correctly, maybe via constructor or a setter
    // If BaseSeat handles event setting, this might be sufficient
    init {
        this.event = event // Ensure event is set if passed via constructor
    }
} 