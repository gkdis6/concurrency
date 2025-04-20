package com.gkdis6.concurrency.repository

import com.gkdis6.concurrency.domain.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository // Spring Bean으로 등록됨을 명시 (선택사항이지만 권장)
interface EventRepository : JpaRepository<Event, Long> {
    // 기본 CRUD 메소드 (save, findById, findAll, delete 등)는 JpaRepository가 제공
    // 필요에 따라 커스텀 쿼리 메소드를 추가할 수 있음 (예: findByName)
}
