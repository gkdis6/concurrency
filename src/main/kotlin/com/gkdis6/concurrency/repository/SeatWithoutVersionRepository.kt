package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.SeatWithoutVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SeatWithoutVersionRepository : JpaRepository<SeatWithoutVersion, Long> {
    // Basic find methods are inherited from JpaRepository
    // Add custom queries if needed specifically for SeatWithoutVersion

    // Example (if needed later):
    // fun findBySeatNumber(seatNumber: String): SeatWithoutVersion?
} 