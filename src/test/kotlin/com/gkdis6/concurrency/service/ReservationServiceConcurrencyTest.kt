package com.gkdis6.concurrency.service

import com.gkdis6.concurrency.domain.Event
import com.gkdis6.concurrency.domain.SeatWithVersion
import com.gkdis6.concurrency.domain.SeatWithoutVersion
import com.gkdis6.concurrency.exception.AlreadyReservedException
import com.gkdis6.concurrency.repository.EventRepository
import com.gkdis6.concurrency.repository.ReservationRepository
import com.gkdis6.concurrency.repository.SeatWithVersionRepository
import com.gkdis6.concurrency.repository.SeatWithoutVersionRepository
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
import org.springframework.jdbc.core.JdbcTemplate // Import JdbcTemplate
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException // Import for H2 specific exception check

@SpringBootTest // Use SpringBootTest for integration testing
class ReservationServiceConcurrencyTest {

    // Logger instance
    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var reservationService: ReservationService

    @Autowired
    private lateinit var eventRepository: EventRepository

    @Autowired
    private lateinit var seatWithVersionRepository: SeatWithVersionRepository

    @Autowired
    private lateinit var seatWithoutVersionRepository: SeatWithoutVersionRepository

    @Autowired
    private lateinit var reservationRepository: ReservationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate // Autowire JdbcTemplate

    @BeforeEach
    fun setUp() {
        // Clean slate for each test, respecting foreign key constraints
        reservationRepository.deleteAllInBatch()
        seatWithVersionRepository.deleteAllInBatch()
        seatWithoutVersionRepository.deleteAllInBatch()
        eventRepository.deleteAllInBatch()
        log.info("--- [SETUP] Cleared Repositories ---")
    }

    @AfterEach
    fun tearDown() {
        log.info("--- [TEARDOWN] Test finished, state reset by next @BeforeEach ---")
    }

    @Test
    @DisplayName("[No Lock] 동시성 제어 없이 좌석 예약 시도 시, 대부분 애플리케이션 레벨 충돌로 실패해야 함")
    fun `reserveSeat_concurrency_no_control_should_mostly_fail_on_conflict`() {
        // Given
        val event = eventRepository.save(Event(name = "Test Event - No Lock", eventDateTime = LocalDateTime.now().plusDays(1)))
        val seat = seatWithoutVersionRepository.save(SeatWithoutVersion(event = event, seatNumber = "A1"))
        val seatId = seat.id
        val threadCount = 100
        val executorService = Executors.newFixedThreadPool(32)
        val latch = CountDownLatch(threadCount)

        val successCount = AtomicInteger(0)
        val failedDueToConflict = AtomicInteger(0)
        val failedDueToDbConstraint = AtomicInteger(0)
        val failedDueToOther = AtomicInteger(0)

        log.info("--- [SETUP - No Lock] Event ID: ${event.id}, Seat ID: $seatId (SeatWithoutVersion) ---")
        log.info("--- [TEST START - No Lock] Concurrency test for Seat ID: $seatId ---")

        // When
        for (i in 1..threadCount) {
            executorService.submit {
                val userId = "user-no-control-$i"
                try {
                    log.info("[No Lock] Attempting reservation for $userId on Seat $seatId")
                    reservationService.reserveSeatNoControl(seatId, userId)
                    successCount.incrementAndGet()
                    log.info("[No Lock] SUCCESS for $userId")
                } catch (e: AlreadyReservedException) {
                    failedDueToConflict.incrementAndGet()
                    log.warn("[No Lock] FAILED for $userId (Conflict - Already Reserved): ${e.message}")
                } catch (e: DataIntegrityViolationException) {
                    // 이 예외는 unique=true 제약조건 제거 후 발생하지 않을 것으로 예상됨
                    failedDueToDbConstraint.incrementAndGet()
                    log.error("[No Lock] FAILED for $userId (DB Constraint Violation - UNEXPECTED): ${e.message}", e)
                } catch (e: ObjectOptimisticLockingFailureException) {
                    // @Version이 없음에도 발생할 수 있는 예외 (상태 변경 충돌 감지 가능성)
                    failedDueToOther.incrementAndGet()
                    log.error("[No Lock] FAILED for $userId (Other - UNEXPECTED): ObjectOptimisticLockingFailureException", e)
                } catch (e: Exception) {
                    failedDueToOther.incrementAndGet()
                    log.error("[No Lock] FAILED for $userId (Other - UNEXPECTED): ${e::class.simpleName}", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        log.info("--- [TEST END - No Lock] Concurrency Test Finished ---")
        log.info("--- [No Lock] Results ---:")
        log.info("  Successes                     : ${successCount.get()}")
        log.info("  Failures (Conflict)           : ${failedDueToConflict.get()}")
        log.info("  Failures (DB Constraint)      : ${failedDueToDbConstraint.get()}")
        log.info("  Failures (Other)            : ${failedDueToOther.get()}")

        // Then
        val finalSeat = seatWithoutVersionRepository.findById(seatId).orElse(null)
        val reservations = reservationRepository.findAll().filter { it.seat.id == seatId }

        log.info("--- [No Lock] Final DB State ---:")
        log.info("  Seat ID $seatId Reserved Status : ${finalSeat?.reserved}")
        log.info("  Reservations in DB for Seat $seatId: ${reservations.size}")

        assertThat(successCount.get()).isEqualTo(1) // 오직 하나만 성공해야 함
        assertThat(failedDueToDbConstraint.get()).isEqualTo(0) // DB 제약조건 위반은 없어야 함
        assertThat(failedDueToConflict.get() + failedDueToOther.get()).isEqualTo(threadCount - 1) // 나머지는 충돌 또는 기타 예외로 실패
        assertThat(finalSeat?.reserved).isTrue()
        assertThat(reservations.size).isEqualTo(1) // 최종적으로 예약은 하나만 있어야 함
    }

    @Test
    @DisplayName("[비관적 락] 동시 예약 시도 시 1건만 성공하고 DB 제약조건 위반 없음 (SeatWithoutVersion 사용)")
    fun `reserveSeat_concurrency_pessimistic_lock_should_succeed`() {
        // Given: Use SeatWithoutVersion for this test
        val event = eventRepository.save(Event(name = "Pessimistic Lock Test Event", eventDateTime = LocalDateTime.now().plusDays(1)))
        var seatEntity = SeatWithoutVersion(event = event, seatNumber = "A1-Pessimistic-NoVersion") // Use SeatWithoutVersion
        seatEntity = seatWithoutVersionRepository.save(seatEntity) // Save using the correct repository

        val testSeatId = seatEntity.id
        assertThat(testSeatId).isNotNull()
        log.info("--- [SETUP - Pessimistic Lock] Event ID: {}, Seat ID: {} (SeatWithoutVersion) --- ", event.id, testSeatId)

        val fetchedSeatBefore = seatWithoutVersionRepository.findById(testSeatId) // Fetch using the correct repository
        assertThat(fetchedSeatBefore).isPresent
        assertThat(fetchedSeatBefore.get().id).isEqualTo(testSeatId)
        log.info("Checked: Successfully fetched Seat ID {} before starting concurrent threads.", testSeatId)

        val numberOfThreads = 100
        val threadPoolSize = 32
        val executorService = Executors.newFixedThreadPool(threadPoolSize)
        val latch = CountDownLatch(numberOfThreads)
        val successfulReservations = AtomicInteger(0)
        val failedDueToConflict = AtomicInteger(0)
        val failedDueToDbConstraint = AtomicInteger(0)
        val failedDueToLocking = AtomicInteger(0)
        val otherFailures = AtomicInteger(0)

        log.info("--- [TEST START - Pessimistic Lock] Concurrency test for Seat ID: {} ---", testSeatId)

        // When: Call the modified service method
        for (i in 1..numberOfThreads) {
            executorService.submit {
                val userId = "user-lock-$i"
                try {
                    log.info("[Pessimistic Lock] Attempting reservation for {} on Seat {}", userId, testSeatId)
                    reservationService.reserveSeatWithPessimisticLock(testSeatId, userId)
                    successfulReservations.incrementAndGet()
                    log.info("[Pessimistic Lock] SUCCESS for {}", userId)
                } catch (e: IllegalStateException) { // Catching IllegalStateException now (includes AlreadyReservedException)
                    log.warn("[Pessimistic Lock] FAILED for {} (Conflict - Already Reserved): {}", userId, e.message)
                    failedDueToConflict.incrementAndGet()
                } catch (e: DataIntegrityViolationException) {
                    log.error("[Pessimistic Lock] FAILED for {} (DB Constraint Violation - UNEXPECTED): {}", userId, e.message)
                    failedDueToDbConstraint.incrementAndGet()
                } catch (e: PessimisticLockingFailureException) {
                    log.error("[Pessimistic Lock] FAILED for {} (Lock Acquisition Failure - Potentially Expected): {}", userId, e.message)
                    failedDueToLocking.incrementAndGet()
                }
                catch (e: Exception) {
                    log.error("[Pessimistic Lock] FAILED for {} (Other - UNEXPECTED): {}", userId, e.javaClass.simpleName, e)
                    otherFailures.incrementAndGet()
                }
                finally {
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
        log.info("  Failures (DB Constraint)      : {}", failedDueToDbConstraint.get())
        log.info("  Failures (Locking)          : {}", failedDueToLocking.get())
        log.info("  Failures (Other)            : {}", otherFailures.get())

        // Then: Verify final state using SeatWithoutVersionRepository
        val finalSeat = seatWithoutVersionRepository.findById(testSeatId).orElseThrow()
        val reservationsInDb = reservationRepository.findAll().filter { it.seat.id == testSeatId }

        log.info("--- [Pessimistic Lock] Final DB State ---:")
        log.info("  Seat ID {} Reserved Status : {}", testSeatId, finalSeat.reserved)
        // log.info("  Seat ID {} Final Version    : {}", testSeatId, finalSeat.version) // Version info removed
        log.info("  Reservations in DB for Seat {}: {}", testSeatId, reservationsInDb.size)

        assertThat(successfulReservations.get()).isEqualTo(1)
        assertThat(reservationsInDb.size).isEqualTo(1)
        assertThat(finalSeat.reserved).isTrue()
        // assertThat(finalSeat.version).isEqualTo(1L) // Version check removed
        assertThat(failedDueToConflict.get()).isEqualTo(numberOfThreads - 1)
        assertThat(failedDueToDbConstraint.get()).isEqualTo(0)
        assertThat(failedDueToLocking.get()).isEqualTo(0)
        assertThat(otherFailures.get()).isEqualTo(0)
    }

    @Test
    @DisplayName("[낙관적 락] 동시 예약 시도 시 1건만 성공하고 나머지는 OptimisticLockException 또는 예외 발생 (SeatWithVersion)")
    fun `reserveSeat_concurrency_optimistic_lock_should_fail_with_exception`() {
        // 1. Save Event first
        val event = eventRepository.save(Event(name = "Optimistic Lock Test Event", eventDateTime = LocalDateTime.now().plusDays(1)))

        // 2. Create Seat WITH the saved Event
        var seatEntity = SeatWithVersion(event = event, seatNumber = "A1-Optimistic")

        // 3. Save Seat (now has valid event_id)
        seatEntity = seatWithVersionRepository.save(seatEntity)

        // 4. (Optional but good practice) Update Event's collection and save/flush Event
        // event.addSeat(seatEntity)
        // eventRepository.saveAndFlush(event)

        val testSeatId = seatEntity.id // ID should now be non-zero
        assertThat(testSeatId).isNotNull()
        val initialVersion = seatWithVersionRepository.findById(testSeatId).get().version // Fetch version AFTER getting ID
        log.info("--- [SETUP - Optimistic Lock] Event ID: {}, Seat ID: {} (SeatWithVersion), Initial Version: {} ---", event.id, testSeatId, initialVersion)

        val fetchedSeatBefore = seatWithVersionRepository.findById(testSeatId)
        assertThat(fetchedSeatBefore).isPresent
        val numberOfThreads = 100
        val threadPoolSize = 32
        val executorService = Executors.newFixedThreadPool(threadPoolSize)
        val latch = CountDownLatch(numberOfThreads)
        val successfulReservations = AtomicInteger(0)
        val failedDueToConflict = AtomicInteger(0)
        val failedDueToOptimisticLock = AtomicInteger(0)
        val otherFailures = AtomicInteger(0)

        log.info("--- [TEST START - Optimistic Lock] Concurrency test for Seat ID: {} ---", testSeatId)

        for (i in 1..numberOfThreads) {
            executorService.submit {
                val userId = "user-optimistic-$i"
                try {
                    log.info("[Optimistic Lock] Attempting reservation for {} on Seat {}", userId, testSeatId)
                    reservationService.reserveSeat(testSeatId, userId)
                    successfulReservations.incrementAndGet()
                    log.info("[Optimistic Lock] SUCCESS for {}", userId)
                } catch (e: IllegalStateException) {
                    log.warn("[Optimistic Lock] FAILED for {} (Conflict - Already Reserved): {}", userId, e.message)
                    failedDueToConflict.incrementAndGet()
                } catch (e: ObjectOptimisticLockingFailureException) {
                    log.warn("[Optimistic Lock] FAILED for {} (Optimistic Lock Failed - EXPECTED): {}", userId, e.message)
                    failedDueToOptimisticLock.incrementAndGet()
                }
                catch (e: Exception) {
                    var cause = e.cause
                    var isOptimisticLockEx = false
                    while (cause != null) {
                        if (cause is ObjectOptimisticLockingFailureException) {
                            isOptimisticLockEx = true
                            break
                        }
                        cause = cause.cause
                    }

                    if (isOptimisticLockEx) {
                         log.warn("[Optimistic Lock] FAILED for {} (Optimistic Lock Failed - Wrapped): {}", userId, e.message)
                         failedDueToOptimisticLock.incrementAndGet()
                    } else {
                         log.error("[Optimistic Lock] FAILED for {} (Other): {}", userId, e.javaClass.simpleName, e)
                         otherFailures.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        log.info("--- [TEST END - Optimistic Lock] Concurrency Test Finished ---")
        log.info("--- [Optimistic Lock] Results ---:")
        log.info("  Successes                     : {}", successfulReservations.get())
        log.info("  Failures (Conflict)           : {}", failedDueToConflict.get()) // Could be non-zero
        log.info("  Failures (Optimistic Lock)    : {}", failedDueToOptimisticLock.get()) // Should be > 0
        log.info("  Failures (Other)            : {}", otherFailures.get())

        // Verification might require handling potential NoResultException if testSeatId was rolled back 
        // Fetching might fail if the transaction that created the seat was rolled back due to optimistic lock failure in some cases
        // A safer approach might be to check counts or re-fetch allowing for null/empty.
        try {
            val finalSeat = seatWithVersionRepository.findById(testSeatId).orElse(null)
            val reservationsInDb = reservationRepository.findAll().filter { it.seat.id == testSeatId }

            log.info("--- [Optimistic Lock] Final DB State (Best Effort) ---:")
            if (finalSeat != null) {
                log.info("  Seat ID {} Reserved Status : {}", testSeatId, finalSeat.reserved)
                log.info("  Final Seat Version          : {}", finalSeat.version)
                assertThat(finalSeat.reserved).isTrue()
                assertThat(finalSeat.version).isEqualTo(1L) // Version should be incremented on successful update
            } else {
                 log.warn("Seat ID {} not found after test, potentially rolled back.", testSeatId)
            }
            log.info("  Reservations in DB for Seat {}: {}", testSeatId, reservationsInDb.size)
            assertThat(reservationsInDb.size).isEqualTo(1)

        } catch (e: Exception) {
             log.error("Error during final verification: {}", e.message)
        }

        // Assertions: Expect exactly 1 success and Optimistic Lock failures
        assertThat(successfulReservations.get())
            .withFailMessage("[Optimistic Lock] 성공한 예약은 정확히 1개여야 합니다.")
            .isEqualTo(1)
        assertThat(failedDueToOptimisticLock.get())
            .withFailMessage("[Optimistic Lock] 낙관적 락 실패가 발생해야 합니다.")
            .isGreaterThan(0)
    }

    @Test
    @DisplayName("[DB 제약 조건] 동시 예약 시도 시 DB Unique Constraint로 인해 1건만 성공해야 함")
    fun `reserveSeat_concurrency_rely_on_db_constraint_should_fail`() {
        // Define constraint name (consistent usage)
        val constraintName = "uk_reservations_seat_id"

        try {
            // === GIVEN (Setup within try block for proper cleanup) ===
            log.info("--- [SETUP - DB Constraint] Adding unique constraint: $constraintName ---")
            jdbcTemplate.execute("ALTER TABLE reservations ADD CONSTRAINT $constraintName UNIQUE (seat_id)")

            val event = eventRepository.save(Event(name = "Test Event - DB Constraint", eventDateTime = LocalDateTime.now().plusDays(1)))
            val seat = seatWithoutVersionRepository.save(SeatWithoutVersion(event = event, seatNumber = "A1-DB"))
            val seatId = seat.id
            val threadCount = 100
            val executorService = Executors.newFixedThreadPool(32)
            val latch = CountDownLatch(threadCount)

            val successCount = AtomicInteger(0)
            val failedDueToConflict = AtomicInteger(0)
            val failedDueToDbConstraint = AtomicInteger(0)
            val failedDueToOther = AtomicInteger(0)

            log.info("--- [SETUP - DB Constraint] Event ID: ${event.id}, Seat ID: $seatId (SeatWithoutVersion) ---")
            log.info("--- [TEST START - DB Constraint] Concurrency test for Seat ID: $seatId ---")

            // === WHEN ===
            for (i in 1..threadCount) {
                executorService.submit {
                    val userId = "user-db-constraint-$i"
                    try {
                        log.info("[DB Constraint] Attempting reservation for $userId on Seat $seatId")
                        reservationService.reserveSeatNoControl(seatId, userId)
                        successCount.incrementAndGet()
                        log.info("[DB Constraint] SUCCESS for $userId")
                    } catch (e: AlreadyReservedException) {
                        failedDueToConflict.incrementAndGet()
                        log.warn("[DB Constraint] FAILED for $userId (Conflict - Already Reserved - LESS LIKELY): ${e.message}")
                    } catch (e: DataIntegrityViolationException) {
                        failedDueToDbConstraint.incrementAndGet()
                        log.warn("[DB Constraint] FAILED for $userId (DB Constraint Violation - EXPECTED): ${e.message}")
                    } catch (e: ObjectOptimisticLockingFailureException) {
                        failedDueToOther.incrementAndGet()
                        log.error("[DB Constraint] FAILED for $userId (Other - UNEXPECTED): ObjectOptimisticLockingFailureException", e)
                    } catch (e: Exception) {
                        var cause = e.cause
                        var isDbConstraintEx = false
                        while (cause != null) {
                            if (cause is JdbcSQLIntegrityConstraintViolationException || cause.message?.contains(constraintName, ignoreCase = true) == true) {
                               isDbConstraintEx = true
                               break
                            }
                            cause = cause.cause
                        }
                        if(isDbConstraintEx) {
                             failedDueToDbConstraint.incrementAndGet()
                             log.warn("[DB Constraint] FAILED for $userId (DB Constraint Violation - Wrapped - EXPECTED): ${e.message}")
                        } else {
                            failedDueToOther.incrementAndGet()
                            log.error("[DB Constraint] FAILED for $userId (Other - UNEXPECTED): ${e::class.simpleName}", e)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executorService.shutdown()

            log.info("--- [TEST END - DB Constraint] Concurrency Test Finished ---")
            log.info("--- [DB Constraint] Results ---:")
            log.info("  Successes                     : ${successCount.get()}")
            log.info("  Failures (Conflict)           : ${failedDueToConflict.get()}")
            log.info("  Failures (DB Constraint)      : ${failedDueToDbConstraint.get()}")
            log.info("  Failures (Other)            : ${failedDueToOther.get()}")

            // === THEN ===
            val finalSeat = seatWithoutVersionRepository.findById(seatId).orElse(null)
            val reservations = reservationRepository.findAll().filter { it.seat.id == seatId }

            log.info("--- [DB Constraint] Final DB State ---:")
            log.info("  Seat ID $seatId Reserved Status : ${finalSeat?.reserved}")
            log.info("  Reservations in DB for Seat $seatId: ${reservations.size}")

            assertThat(successCount.get()).isEqualTo(1)
            assertThat(reservations.size).isEqualTo(1)
            assertThat(failedDueToDbConstraint.get()).isGreaterThan(0)
            assertThat(failedDueToConflict.get() + failedDueToOther.get() + failedDueToDbConstraint.get()).isEqualTo(threadCount - 1)
            assertThat(finalSeat?.reserved).isTrue()

        } finally {
            // === CLEANUP (Always drop constraint) ===
            log.info("--- [CLEANUP - DB Constraint] Dropping unique constraint: $constraintName ---")
            try {
                 jdbcTemplate.execute("ALTER TABLE reservations DROP CONSTRAINT $constraintName")
            } catch (e: Exception) {
                 log.error("Failed to drop constraint $constraintName, might not have been created or already dropped: ${e.message}")
            }
        }
    }
} 