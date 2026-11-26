package com.theclimbingnight.moderation.repository;

import com.theclimbingnight.moderation.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {
}
