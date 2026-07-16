package com.ember.backend.repository

import com.ember.backend.model.Photo
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PhotoRepository : JpaRepository<Photo, UUID>
