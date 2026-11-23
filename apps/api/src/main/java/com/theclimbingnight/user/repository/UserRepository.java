package com.theclimbingnight.user.repository;

import com.theclimbingnight.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByNicknameIgnoreCase(String nickname);
}
