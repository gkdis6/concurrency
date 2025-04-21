package com.gkdis6.concurrency.service

import com.gkdis6.concurrency.domain.Reservation
import com.gkdis6.concurrency.domain.SeatWithVersion
import com.gkdis6.concurrency.domain.SeatWithoutVersion
import com.gkdis6.concurrency.repository.ReservationRepository
import com.gkdis6.concurrency.repository.SeatWithVersionRepository
import com.gkdis6.concurrency.repository.SeatWithoutVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.NoSuchElementException

@Service
class ReservationService(
    // Inject new repositories
    private val seatWithVersionRepository: SeatWithVersionRepository,
    private val seatWithoutVersionRepository: SeatWithoutVersionRepository,
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
    @Transactional
    fun reserveSeat(seatId: Long, userId: String): Reservation {
        // This method now handles the Optimistic Lock case using SeatWithVersion
        val seat = seatWithVersionRepository.findById(seatId)
            .orElseThrow { NoSuchElementException("Seat not found with ID: $seatId") }

        // reserve() method in SeatWithVersion includes version in exception message
        seat.reserve()
        // seatWithVersionRepository.save(seat) // Save is cascaded from Reservation or handled by dirty checking

        val reservation = Reservation(
            seat = seat, // seat is SeatWithVersion, which extends BaseSeat
            userId = userId,
            reservationTime = LocalDateTime.now()
        )
        return reservationRepository.save(reservation)
    }

    /**
     * [비관적 락] 비관적 락을 사용하여 좌석 예약 시도
     */
    @Transactional
    fun reserveSeatWithPessimisticLock(seatId: Long, userId: String): Reservation {
        // Uses pessimistic lock query from SeatWithVersionRepository
        val seat = seatWithVersionRepository.findByIdWithPessimisticLock(seatId)
            .orElseThrow { NoSuchElementException("Seat not found with ID: $seatId") }

        // reserve() method in SeatWithVersion includes version in exception message
        seat.reserve()
        // seatWithVersionRepository.save(seat) // Save is cascaded or handled by dirty checking

        val reservation = Reservation(
            seat = seat, // seat is SeatWithVersion, which extends BaseSeat
            userId = userId,
            reservationTime = LocalDateTime.now()
        )
        return reservationRepository.save(reservation)
    }

    // New method for the No Control scenario using SeatWithoutVersion
    @Transactional
    fun reserveSeatNoControl(seatId: Long, userId: String): Reservation {
        val seat = seatWithoutVersionRepository.findById(seatId)
             .orElseThrow { NoSuchElementException("SeatWithoutVersion not found with ID: $seatId") }

        // reserve() method in SeatWithoutVersion (inherited from BaseSeat) does NOT include version
        seat.reserve()
        // seatWithoutVersionRepository.save(seat) // Save is cascaded or handled by dirty checking

        val reservation = Reservation(
            seat = seat, // seat is SeatWithoutVersion, which extends BaseSeat
            userId = userId,
            reservationTime = LocalDateTime.now()
        )
        // This save might cause DataIntegrityViolationException due to unique constraint on reservations.seat_id
        return reservationRepository.save(reservation)
    }

    // Consider adding cancelReservation methods if needed, handling both entity types
} 