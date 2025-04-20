package com.gkdis6.concurrency.service

import com.gkdis6.concurrency.domain.Event
import com.gkdis6.concurrency.domain.Seat
import com.gkdis6.concurrency.repository.EventRepository
import com.gkdis6.concurrency.repository.ReservationRepository
import com.gkdis6.concurrency.repository.SeatRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory // SLF4J Logger Import
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.orm.ObjectOptimisticLockingFailureException // For later Optimistic Lock test
import org.springframework.dao.PessimisticLockingFailureException // For later Pessimistic Lock test
import org.springframework.dao.DataIntegrityViolationException // Import for specific exception handling
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest // Use SpringBootTest for integration testing
class ReservationServiceConcurrencyTest {

    // Logger instance
    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var reservationService: ReservationService

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var seatRepository: SeatRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    private var testEventId: Long = 0
    private var testSeatId: Long = 0

    @BeforeEach
    fun setUp() {
        // Create a test event and a single seat for it
        val event = Event(name = "Concurrent Test Event", eventDateTime = LocalDateTime.now().plusDays(1))
        val seat = Seat(seatNumber = "A1")
        event.addSeat(seat) // Link seat to event
        val savedEvent = eventRepository.saveAndFlush(event) // Save event and cascade save seat, flush to DB

        testEventId = savedEvent.id
        testSeatId = savedEvent.seats.first().id // Get the ID of the saved seat

        log.info("--- [SETUP] Test Event ID: {}, Test Seat ID: {} ---", testEventId, testSeatId)
    }

    @AfterEach
    fun tearDown() {
        // Clean up database after each test
        // Reservations must be deleted first due to foreign key constraints if any
        reservationRepository.deleteAll()
        // Deleting the event will cascade delete the associated seats
        eventRepository.deleteAll()
        log.info("--- [TEARDOWN] Database Cleaned Up ---")
    }

    @Test
    @DisplayName("동시에 100개의 요청으로 같은 좌석 예약을 시도하면, 동시성 제어 없이는 여러 건 예약 성공(문제 발생)")
    fun `reserveSeat_concurrency_no_control_should_fail`() {
        val numberOfThreads = 100 // Number of concurrent requests simulation
        val threadPoolSize = 32 // Size of the thread pool managing execution
        val executorService = Executors.newFixedThreadPool(threadPoolSize)
        val latch = CountDownLatch(numberOfThreads) // Coordinate thread completion
        val successfulReservations = AtomicInteger(0) // Thread-safe counter for successes
        val failedDueToConflict = AtomicInteger(0) // Thread-safe counter for known conflicts (e.g., already reserved)
        val failedDueToDbConstraint = AtomicInteger(0) // Counter for DataIntegrityViolationException
        val otherFailures = AtomicInteger(0) // Counter for unexpected errors

        log.info("--- [TEST START] Concurrency test for Seat ID: {} ---", testSeatId)

        // Submit tasks for each simulated user request
        for (i in 1..numberOfThreads) {
            executorService.submit {
                val userId = "user-$i"
                try {
                    log.info("Attempting reservation for {} on Seat {}", userId, testSeatId)
                    // Attempt to reserve the seat
                    reservationService.reserveSeat(testSeatId, userId)
                    // If reserveSeat completes without exception, increment success counter
                    successfulReservations.incrementAndGet()
                    log.info("SUCCESS for {}", userId)
                } catch (e: IllegalStateException) {
                    // Catch the specific exception thrown when the seat is already reserved
                    log.warn("FAILED for {} (Conflict - Already Reserved): {}", userId, e.message)
                    failedDueToConflict.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    // Database unique constraint violation - indicates race condition occurred
                    log.error("FAILED for {} (DB Constraint Violation): {}", userId, e.message) // Log as ERROR as it's a key issue
                    failedDueToDbConstraint.incrementAndGet()
                } catch (e: Exception) {
                    // Catch any other unexpected exceptions during the process
                    log.error("FAILED for {} (Other): {}", userId, e.javaClass.simpleName, e)
                    otherFailures.incrementAndGet()
                } finally {
                    latch.countDown() // Signal that this thread has finished its attempt
                }
            }
        }

        latch.await() // Wait for all threads to complete their execution
        executorService.shutdown() // Shut down the executor service

        log.info("--- [TEST END] Concurrency Test Finished ---")
        log.info("--- Results ---:")
        log.info("  Successes                     : {}", successfulReservations.get())
        log.info("  Failures (Conflict)           : {}", failedDueToConflict.get())
        log.info("  Failures (DB Constraint)      : {}", failedDueToDbConstraint.get()) // Log the new counter
        log.info("  Failures (Other)            : {}", otherFailures.get())

        // Verification: Check the final state in the database
        val finalSeat = seatRepository.findById(testSeatId).orElseThrow()
        val reservationsInDb = reservationRepository.findAll().filter { it.seat.id == testSeatId }

        log.info("--- Final DB State ---:")
        log.info("  Seat ID {} Reserved Status : {}", testSeatId, finalSeat.reserved)
        log.info("  Reservations in DB for Seat {}: {}", testSeatId, reservationsInDb.size)

        // Assertions:
        // Without concurrency control, multiple threads might read 'reserved = false'
        // before any thread can commit the change 'reserved = true'.
        // This leads to a race condition.

        // Ideally, only ONE reservation should succeed.
        // THIS ASSERTION IS EXPECTED TO FAIL in this version without locks.
        assertThat(successfulReservations.get())
            .withFailMessage("성공한 예약은 정확히 1개여야 하지만 ${successfulReservations.get()}개 입니다.")
            .isEqualTo(1)

        // Consequently, only ONE reservation record should exist in the database for this seat.
        // THIS ASSERTION IS ALSO EXPECTED TO FAIL.
        assertThat(reservationsInDb.size)
            .withFailMessage("DB에 저장된 예약은 정확히 1개여야 하지만 ${reservationsInDb.size}개 입니다.")
            .isEqualTo(1)

        // The final state of the seat should be 'reserved = true'.
        // This might pass by chance if at least one reservation succeeded, but doesn't guarantee correctness.
        assertThat(finalSeat.reserved).isTrue()

        // Crucially, assert that the race condition DID occur (as expected in this test)
        assertThat(failedDueToDbConstraint.get())
             .withFailMessage("DB 제약조건 위반 실패가 발생해야 하지만, 발생하지 않았습니다. (테스트 환경 확인 필요)")
             .isGreaterThan(0)
    }
} 