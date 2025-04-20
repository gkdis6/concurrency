package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.Reservation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ReservationRepository : JpaRepository<Reservation, Long> {
    // 특정 좌석 ID에 대한 예약을 찾는 메소드 (예시)
    // Reservation 엔티티의 unique 제약 때문에 결과는 하나거나 없음
    fun findBySeatId(seatId: Long): Reservation?

    // 특정 사용자의 모든 예약을 찾는 메소드 (예시)
    fun findByUserId(userId: String): List<Reservation>
} 