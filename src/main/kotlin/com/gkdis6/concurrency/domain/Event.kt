package com.gkdis6.concurrency.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "events") // 테이블 이름 명시 (선택사항)
class Event(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false) // Not null 제약 조건
    val name: String,

    @Column(nullable = false)
    val eventDateTime: LocalDateTime,

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY) // Lazy loading 추천
    val seats: MutableList<Seat> = mutableListOf()
) {
    fun addSeat(seat: Seat) {
        seats.add(seat)
        seat.event = this
    }
}
