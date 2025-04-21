# 좌석 예약 시스템 동시성 제어 예제

이 프로젝트는 좌석 예약 시스템에서 발생할 수 있는 동시성 문제를 해결하기 위한 다양한 전략을 구현하고 테스트하는 예제입니다.

## 구현된 동시성 제어 시나리오

총 4가지 동시성 제어 시나리오를 구현하고 `ReservationServiceConcurrencyTest.kt` 에서 테스트합니다.

### 1. 제어 없음 (No Control)

*   **설명:** 별도의 동시성 제어 로직 없이, 애플리케이션 레벨에서 좌석의 예약 상태 (`isReserved`)만 확인하여 예약을 시도합니다. `@Version` 어노테이션이 없는 `SeatWithoutVersion` 엔티티를 사용합니다.
*   **예상 동작:** 여러 스레드가 동시에 예약 가능 상태를 확인하고 예약 로직을 실행하여 경쟁 상태(Race Condition)가 발생합니다. 초기에는 여러 스레드가 성공하는 것처럼 보일 수 있으나, 최종적으로 DB에 저장되는 예약은 하나여야 합니다. (애플리케이션 레벨 충돌 또는 DB 제약조건으로 인해 실패)
*   **관련 파일:**
    *   `domain/SeatWithoutVersion.kt`
    *   `service/ReservationService.kt` (`reserveSeatNoControl` 메서드)
    *   `test/.../ReservationServiceConcurrencyTest.kt` (`reserveSeat_concurrency_no_control_should_mostly_fail_on_conflict` 테스트)

### 2. 비관적 락 (Pessimistic Locking)

*   **설명:** 데이터베이스의 비관적 락 기능을 사용하여 특정 좌석 데이터에 대한 동시 접근을 제어합니다. 좌석 조회 시 `SELECT ... FOR UPDATE` 구문과 유사하게 동작하여 트랜잭션이 완료될 때까지 다른 트랜잭션의 쓰기 접근을 차단합니다. 이 예제에서는 `@Version`이 없는 `SeatWithoutVersion` 엔티티와 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 어노테이션을 리포지토리에 추가하여 구현했습니다. (`@Version` 필드는 비관적 락의 필수 요건이 아닙니다.)
*   **예상 동작:** 가장 먼저 좌석에 락을 획득한 스레드만 예약에 성공하고, 나머지 스레드는 락이 해제된 후 좌석이 이미 예약되었음을 확인하고 실패합니다. DB 충돌 없이 단 1건의 예약만 성공합니다.
*   **관련 파일:**
    *   `domain/SeatWithoutVersion.kt`
    *   `repository/SeatWithoutVersionRepository.kt` (`findByIdWithPessimisticLock` 메서드)
    *   `service/ReservationService.kt` (`reserveSeatWithPessimisticLock` 메서드)
    *   `test/.../ReservationServiceConcurrencyTest.kt` (`reserveSeat_concurrency_pessimistic_lock_should_succeed` 테스트)

### 3. 낙관적 락 (Optimistic Locking)

*   **설명:** 데이터베이스 레코드에 버전(`@Version`) 컬럼을 두고, 업데이트 시 버전을 비교하여 데이터 변경 충돌을 감지합니다. 여러 스레드가 동시에 데이터를 읽는 것은 허용하지만, 업데이트 시점에 버전이 일치하지 않으면 `ObjectOptimisticLockingFailureException`을 발생시킵니다. (`@Version` 필드가 반드시 필요합니다.)
*   **예상 동작:** 여러 스레드가 동시에 예약 로직을 진행할 수 있지만, 최종적으로 커밋(Commit)하는 시점에 버전 충돌이 발생하여 `ObjectOptimisticLockingFailureException` 예외와 함께 실패하는 스레드가 다수 발생합니다. 단 1건의 예약만 성공합니다.
*   **관련 파일:**
    *   `domain/SeatWithVersion.kt` (`@Version` 필드)
    *   `service/ReservationService.kt` (`reserveSeat` 메서드)
    *   `test/.../ReservationServiceConcurrencyTest.kt` (`reserveSeat_concurrency_optimistic_lock_should_fail_with_exception` 테스트)

### 4. 데이터베이스 제약 조건 활용 (Database Unique Constraint)

*   **설명:** 애플리케이션 레벨에서는 명시적인 락을 사용하지 않고, `Reservation` 테이블의 `seat_id` 컬럼에 데이터베이스 레벨의 Unique Constraint를 설정하여 동일 좌석에 대한 중복 예약을 방지합니다. 제약 조건 위반 시 `DataIntegrityViolationException`이 발생합니다.
*   **예상 동작:** 여러 스레드가 예약을 시도하더라도 데이터베이스 레벨에서 중복을 허용하지 않으므로, 가장 먼저 `INSERT`에 성공한 스레드를 제외한 나머지 스레드는 `DataIntegrityViolationException` 예외와 함께 실패합니다. 단 1건의 예약만 성공합니다.
*   **관련 파일:**
    *   `domain/Reservation.kt` (테스트 코드 내에서 DDL로 제약 조건 추가/삭제)
    *   `service/ReservationService.kt` (`reserveSeatNoControl` 메서드 사용)
    *   `test/.../ReservationServiceConcurrencyTest.kt` (`reserveSeat_concurrency_rely_on_db_constraint_should_fail` 테스트 - `JdbcTemplate` 사용)

## 테스트 실행 방법

프로젝트 루트 디렉토리에서 다음 Gradle 명령어를 사용하여 동시성 테스트를 실행할 수 있습니다.

```bash
./gradlew test --tests com.gkdis6.concurrency.service.ReservationServiceConcurrencyTest
```

(Windows에서는 `./gradlew` 대신 `gradlew` 사용)

각 테스트 메서드는 `@DisplayName`에 명시된 시나리오를 검증하며, 콘솔 로그를 통해 각 스레드의 예약 시도 결과(성공, 실패 유형) 및 최종 DB 상태를 확인할 수 있습니다.

## 향후 개선 방안 / 다음 단계

현재 구현된 시나리오 외에 고려해볼 수 있는 개선 방안은 다음과 같습니다.

*   **낙관적 락 + 재시도:** `ObjectOptimisticLockingFailureException` 발생 시, 자동으로 재시도하는 로직을 추가하여 사용자 경험을 개선하고 성공률을 높일 수 있습니다. (예: Spring Retry 활용)
*   **분산 락 (Distributed Lock):** 여러 애플리케이션 인스턴스가 동작하는 분산 환경에서는 데이터베이스 락만으로는 부족할 수 있습니다. Redis나 ZooKeeper 등을 이용한 분산 락 도입을 고려할 수 있습니다.
*   **성능 비교:** 각 동시성 제어 전략의 처리량, 응답 시간 등 성능 지표를 측정하고 비교하여 시스템 요구사항에 가장 적합한 방식을 선택할 수 있습니다.
*   **멱등성 보장:** 동일한 예약 요청이 여러 번 발생하더라도 단 한 번만 처리되도록 요청 ID 등을 활용하여 멱등성을 확보할 수 있습니다. 