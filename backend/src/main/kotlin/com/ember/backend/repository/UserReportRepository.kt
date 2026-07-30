package com.ember.backend.repository

import com.ember.backend.model.UserReport
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserReportRepository : JpaRepository<UserReport, UUID>
