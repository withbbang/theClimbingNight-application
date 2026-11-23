package com.theclimbingnight.auth.entity;

import com.theclimbingnight.auth.social.SocialProvider;
import com.theclimbingnight.common.entity.BaseTimeEntity;
import com.theclimbingnight.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "auth_identities", uniqueConstraints = @UniqueConstraint(
		name = "uk_auth_identities_provider", columnNames = {"provider", "provider_user_id"}))
public class AuthIdentity extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private SocialProvider provider;

	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	public static AuthIdentity link(User user, SocialProvider provider, String providerUserId) {
		AuthIdentity identity = new AuthIdentity();
		identity.user = user;
		identity.provider = provider;
		identity.providerUserId = providerUserId;
		return identity;
	}
}
