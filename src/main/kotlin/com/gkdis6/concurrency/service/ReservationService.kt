package com.gkdis6.concurrency.service

import com.gkdis6.concurrency.domain.Reservation
import com.gkdis6.concurrency.repository.ReservationRepository
import com.gkdis6.concurrency.repository.SeatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.NoSuchElementException

@Service
class ReservationService(
    // Constructor injection for repositories
    private val seatRepository: SeatRepository,
    private val reservationRepository: ReservationRepository
) {

    /**
     * Attempts to reserve a seat for a given user.
     * This initial version does not implement explicit concurrency control.
     *
     * @param seatId The ID of the seat to reserve.
     * @param userId The ID of the user making the reservation.
     * @return The created Reservation object if successful.
     * @throws NoSuchElementException if the seat with the given ID is not found.
     * @throws IllegalStateException if the seat is already reserved.
     */
    @Transactional // Ensures atomicity: either all operations succeed, or none do.
    fun reserveSeat(seatId: Long, userId: String): Reservation {
        // 1. Find the seat by ID. Throw an exception if not found.
        val seat = seatRepository.findById(seatId)
            .orElseThrow { NoSuchElementException("해당 좌석을 찾을 수 없습니다. ID: $seatId") }

        // 2. Check availability and mark as reserved within the Seat entity.
        // This throws IllegalStateException if seat.reserved is already true.
        seat.reserve()

        // Note: JPA's dirty checking mechanism within a @Transactional context
        // will automatically detect the change in the 'seat.reserved' state
        // and generate an UPDATE SQL statement upon transaction commit.
        // Explicitly calling seatRepository.save(seat) is usually not needed here.

        // 3. Create the reservation record.
        val reservation = Reservation(
            seat = seat,
            userId = userId
            // reservationTime is set automatically by the entity's default value
        )

        // 4. Save the reservation record to the database.
        return reservationRepository.save(reservation)
    }
} 