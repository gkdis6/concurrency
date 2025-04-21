package com.gkdis6.concurrency.repository

// Change import from Seat to SeatWithVersion
import com.gkdis6.concurrency.domain.SeatWithVersion
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
// Change interface name and generic type from Seat to SeatWithVersion
interface SeatWithVersionRepository : JpaRepository<SeatWithVersion, Long> {
    // Change return type from Seat to SeatWithVersion
    fun findBySeatNumber(seatNumber: String): SeatWithVersion?

    // Change return type from List<Seat> to List<SeatWithVersion>
    fun findByEventId(eventId: Long): List<SeatWithVersion>

    // 비관적 쓰기 락(PESSIMISTIC_WRITE)을 사용하여 좌석 조회
    // 트랜잭션이 끝날 때까지 다른 트랜잭션은 이 행에 쓰기 작업을 할 수 없음 (대기)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Update query to select from SeatWithVersion
    @Query("select s from SeatWithVersion s where s.id = :id")
    // Change return type from Optional<Seat> to Optional<SeatWithVersion>
    fun findByIdWithPessimisticLock(id: Long): Optional<SeatWithVersion>
} 