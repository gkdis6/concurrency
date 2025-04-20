package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.Seat
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SeatRepository : JpaRepository<Seat, Long> {
    // 좌석 번호로 좌석을 찾는 메소드 (예시)
    fun findBySeatNumber(seatNumber: String): Seat?

    // 이벤트 ID로 해당 이벤트의 모든 좌석을 찾는 메소드 (예시)
    fun findByEventId(eventId: Long): List<Seat>

    // 비관적 쓰기 락(PESSIMISTIC_WRITE)을 사용하여 좌석 조회
    // 트랜잭션이 끝날 때까지 다른 트랜잭션은 이 행에 쓰기 작업을 할 수 없음 (대기)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id") // Lock을 사용하기 위해 @Query 필요할 수 있음
    fun findByIdWithPessimisticLock(id: Long): Optional<Seat> // Return Optional
} 