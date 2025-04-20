package com.gkdis6.concurrency.domain

import jakarta.persistence.*

@Entity
@Table(name = "seats")
class Seat(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY) // 관계의 주인 설정 및 Lazy loading
    @JoinColumn(name = "event_id", nullable = false) // Foreign key, Not null
    var event: Event? = null, // Nullable로 두되, addSeat에서 설정 보장

    @Column(nullable = false)
    val seatNumber: String,

    @Column(nullable = false)
    var reserved: Boolean = false
) {
    fun reserve() {
        if (reserved) {
            throw IllegalStateException("이미 예약된 좌석입니다. (Seat ID: ${this.id})") // 예외 메시지에 정보 추가
        }
        reserved = true
    }

    fun cancelReservation() {
        if (!reserved) {
            // 이미 취소된 상태라면 알림 (선택적)
             println("이미 취소된 좌석입니다. (Seat ID: ${this.id})")
             return
        }
        reserved = false
    }
}
