package com.gkdis6.concurrency.domain

import jakarta.persistence.*

@Entity
@Table(name = "seats")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "seat_type")
abstract class BaseSeat( // Make abstract as it shouldn't be instantiated directly
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    open val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    open var event: Event? = null, // 'var' to allow setting from Event.addSeat

    @Column(nullable = false)
    open val seatNumber: String,

    @Column(nullable = false)
    open var reserved: Boolean = false,

    // Add the @Version field here (must be in root of hierarchy)
    @Version
    open val version: Long = 0L // Make it open, provide default

    // Need to add version property here for the reserve method's message,
    // but it should only exist conceptually in SeatWithVersion.
    // Let's make reserve abstract or handle the message differently.
    // Option 1: Abstract method (requires subclasses to implement message)
    // abstract fun reserve()
    // Option 2: Pass version info into reserve() (less clean)
    // Option 3: Remove version from the message in BaseSeat (simplest)

    // Option 3: Reserve method without version in the message
) {
    open fun reserve() {
        if (reserved) {
            // Generic message, subclasses can override if they need version info
            throw IllegalStateException("이미 예약된 좌석입니다. (Seat ID: ${this.id})")
        }
        reserved = true
    }

    open fun cancelReservation() {
         if (!reserved) {
             println("이미 취소된 좌석입니다. (Seat ID: ${this.id})")
             return
        }
        reserved = false
    }

    // Make properties open if they need to be overridden or accessed polymorphically
    // Make class abstract
} 