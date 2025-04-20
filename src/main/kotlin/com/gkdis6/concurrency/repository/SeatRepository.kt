package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.Seat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SeatRepository : JpaRepository<Seat, Long> {
    // 좌석 번호로 좌석을 찾는 메소드 (예시)
    fun findBySeatNumber(seatNumber: String): Seat?

    // 이벤트 ID로 해당 이벤트의 모든 좌석을 찾는 메소드 (예시)
    fun findByEventId(eventId: Long): List<Seat>
} 