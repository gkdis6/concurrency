package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.SeatWithoutVersion
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.jpa.repository.Query
import java.util.Optional

// @Repository // Removed unnecessary annotation
interface SeatWithoutVersionRepository : JpaRepository<SeatWithoutVersion, Long> {
    // Basic find methods are inherited from JpaRepository
    // Add custom queries if needed specifically for SeatWithoutVersion

    // Example (if needed later):
    // fun findBySeatNumber(seatNumber: String): SeatWithoutVersion?

    // 비관적 쓰기 락을 사용하여 좌석 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(value = [QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")]) // 락 타임아웃 3초
    @Query("SELECT s FROM SeatWithoutVersion s WHERE s.id = :id") // Explicit JPQL query added
    fun findByIdWithPessimisticLock(id: Long): Optional<SeatWithoutVersion>
} 