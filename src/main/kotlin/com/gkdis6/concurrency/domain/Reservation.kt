package com.gkdis6.concurrency.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "reservations")
class Reservation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false, unique = true) // 각 좌석은 하나의 예약만 가질 수 있도록 unique 제약 추가
    val seat: Seat,

    @Column(nullable = false)
    val userId: String, // 사용자 ID

    @Column(nullable = false)
    val reservationTime: LocalDateTime = LocalDateTime.now()
)