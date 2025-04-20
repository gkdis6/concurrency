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
    @DisplayName("[문제 재현] 동시성 제어 없이 예약 시도 시 DB 제약조건 위반 발생")
    fun `reserveSeat_concurrency_no_control_should_fail_on_db_constraint`() {
        val numberOfThreads = 100
        val threadPoolSize = 32
        val executorService = Executors.newFixedThreadPool(threadPoolSize)
        val latch = CountDownLatch(numberOfThreads)
        val successfulReservations = AtomicInteger(0)
        val failedDueToConflict = AtomicInteger(0)
        val failedDueToDbConstraint = AtomicInteger(0) // Counter for DataIntegrityViolationException
        val otherFailures = AtomicInteger(0)

        log.info("--- [TEST START - No Lock] Concurrency test for Seat ID: {} ---", testSeatId)

        for (i in 1..numberOfThreads) {
            executorService.submit {
                val userId = "user-$i"
                try {
                    log.info("[No Lock] Attempting reservation for {} on Seat {}", userId, testSeatId)
                    reservationService.reserveSeat(testSeatId, userId) // 기존 메소드 호출
                    successfulReservations.incrementAndGet()
                    log.info("[No Lock] SUCCESS for {}", userId)
                } catch (e: IllegalStateException) {
                    log.warn("[No Lock] FAILED for {} (Conflict - Already Reserved): {}", userId, e.message)
                    failedDueToConflict.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    log.error("[No Lock] FAILED for {} (DB Constraint Violation): {}", userId, e.message)
                    failedDueToDbConstraint.incrementAndGet()
                } catch (e: Exception) {
                    log.error("[No Lock] FAILED for {} (Other): {}", userId, e.javaClass.simpleName, e)
                    otherFailures.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        log.info("--- [TEST END - No Lock] Concurrency Test Finished ---")
        log.info("--- [No Lock] Results ---:")
        log.info("  Successes                     : {}", successfulReservations.get())
        log.info("  Failures (Conflict)           : {}", failedDueToConflict.get())
        log.info("  Failures (DB Constraint)      : {}", failedDueToDbConstraint.get())
        log.info("  Failures (Other)            : {}", otherFailures.get())

        val finalSeat = seatRepository.findById(testSeatId).orElseThrow()
        val reservationsInDb = reservationRepository.findAll().filter { it.seat.id == testSeatId }

        log.info("--- [No Lock] Final DB State ---:")
        log.info("  Seat ID {} Reserved Status : {}", testSeatId, finalSeat.reserved)
        log.info("  Reservations in DB for Seat {}: {}", testSeatId, reservationsInDb.size)

        // Assertions: Expect DB constraint violations
        assertThat(successfulReservations.get())
            .withFailMessage("[No Lock] 성공한 예약은 정확히 1개여야 하지만 DB 제약조건 덕분에 1개가 될 수 있음 (과정 확인 필요)")
            .isEqualTo(1) // DB 제약 때문에 결과는 1일 수 있음
        assertThat(reservationsInDb.size)
             .withFailMessage("[No Lock] DB에 저장된 예약은 정확히 1개여야 하지만 ${reservationsInDb.size}개 입니다.")
             .isEqualTo(1)
        assertThat(finalSeat.reserved).isTrue()
        assertThat(failedDueToDbConstraint.get())
             .withFailMessage("[No Lock] DB 제약조건 위반 실패가 발생해야 하지만, 발생하지 않았습니다.")
             .isGreaterThan(0) // << 핵심: DB 제약 조건 위반이 발생하는지 확인
    }


    @Test
    @DisplayName("[비관적 락] 동시 예약 시도 시 1건만 성공하고 DB 제약조건 위반 없음")
    fun `reserveSeat_concurrency_pessimistic_lock_should_succeed`() {
        val numberOfThreads = 100
        val threadPoolSize = 32
        val executorService = Executors.newFixedThreadPool(threadPoolSize)
        val latch = CountDownLatch(numberOfThreads)
        val successfulReservations = AtomicInteger(0)
        val failedDueToConflict = AtomicInteger(0)       // IllegalStateException counter
        val failedDueToDbConstraint = AtomicInteger(0)    // DataIntegrityViolationException counter
        val failedDueToLocking = AtomicInteger(0)       // PessimisticLockingFailureException counter
        val otherFailures = AtomicInteger(0)

        log.info("--- [TEST START - Pessimistic Lock] Concurrency test for Seat ID: {} ---", testSeatId)

        for (i in 1..numberOfThreads) {
            executorService.submit {
                val userId = "user-lock-$i" // Use different user ID prefix for clarity
                try {
                    log.info("[Pessimistic Lock] Attempting reservation for {} on Seat {}", userId, testSeatId)
                    reservationService.reserveSeatWithPessimisticLock(testSeatId, userId) // 비관적 락 메소드 호출
                    successfulReservations.incrementAndGet()
                    log.info("[Pessimistic Lock] SUCCESS for {}", userId)
                } catch (e: IllegalStateException) {
                    // This should now be the primary failure reason for concurrent attempts after the first success
                    log.warn("[Pessimistic Lock] FAILED for {} (Conflict - Already Reserved): {}", userId, e.message)
                    failedDueToConflict.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    // This should NOT happen with pessimistic locking
                    log.error("[Pessimistic Lock] FAILED for {} (DB Constraint Violation - UNEXPECTED): {}", userId, e.message)
                    failedDueToDbConstraint.incrementAndGet()
                } catch (e: PessimisticLockingFailureException) {
                    // Could happen if lock acquisition times out (less likely with H2 default settings)
                    log.error("[Pessimistic Lock] FAILED for {} (Lock Acquisition Failure): {}", userId, e.message)
                    failedDueToLocking.incrementAndGet()
                }
                catch (e: Exception) {
                    log.error("[Pessimistic Lock] FAILED for {} (Other): {}", userId, e.javaClass.simpleName, e)
                    otherFailures.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        log.info("--- [TEST END - Pessimistic Lock] Concurrency Test Finished ---")
        log.info("--- [Pessimistic Lock] Results ---:")
        log.info("  Successes                     : {}", successfulReservations.get())
        log.info("  Failures (Conflict)           : {}", failedDueToConflict.get())
        log.info("  Failures (DB Constraint)      : {}", failedDueToDbConstraint.get()) // Should be 0
        log.info("  Failures (Locking)          : {}", failedDueToLocking.get())     // Likely 0
        log.info("  Failures (Other)            : {}", otherFailures.get())

        val finalSeat = seatRepository.findById(testSeatId).orElseThrow()
        val reservationsInDb = reservationRepository.findAll().filter { it.seat.id == testSeatId }

        log.info("--- [Pessimistic Lock] Final DB State ---:")
        log.info("  Seat ID {} Reserved Status : {}", testSeatId, finalSeat.reserved)
        log.info("  Reservations in DB for Seat {}: {}", testSeatId, reservationsInDb.size)

        // Assertions: Expect exactly 1 success and NO DB constraint violations
        assertThat(successfulReservations.get())
            .withFailMessage("[Pessimistic Lock] 성공한 예약은 정확히 1개여야 합니다.")
            .isEqualTo(1)
        assertThat(reservationsInDb.size)
            .withFailMessage("[Pessimistic Lock] DB에 저장된 예약은 정확히 1개여야 합니다.")
            .isEqualTo(1)
        assertThat(finalSeat.reserved).isTrue()
        assertThat(failedDueToDbConstraint.get())
            .withFailMessage("[Pessimistic Lock] DB 제약조건 위반 실패는 발생하지 않아야 합니다.")
            .isEqualTo(0) // << 핵심: DB 제약 조건 위반이 없는지 확인
        assertThat(failedDueToLocking.get())
            .withFailMessage("[Pessimistic Lock] 락 획득 실패는 발생하지 않아야 합니다 (테스트 환경 확인 필요).")
            .isEqualTo(0) // PESSIMISTIC_WRITE는 보통 대기하므로 타임아웃이 없다면 0
    }
} 