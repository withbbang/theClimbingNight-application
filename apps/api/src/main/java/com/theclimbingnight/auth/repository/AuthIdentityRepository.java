package com.theclimbingnight.auth.repository;

import com.theclimbingnight.auth.entity.AuthIdentity;
import com.theclimbingnight.auth.social.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

	Optional<AuthIdentity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	void deleteByUserId(Long userId);
}
