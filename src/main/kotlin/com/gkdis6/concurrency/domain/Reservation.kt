package com.gkdis6.concurrency.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reservations")
class Reservation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    val seat: BaseSeat,

    @Column(nullable = false)
    val userId: String, // 사용자 ID

    @Column(nullable = false)
    val reservationTime: LocalDateTime = LocalDateTime.now()
)