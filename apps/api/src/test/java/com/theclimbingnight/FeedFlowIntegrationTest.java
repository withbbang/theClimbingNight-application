package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.entity.PostMediaStatus;
import com.theclimbingnight.feed.entity.PostStatus;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJob;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.media.storage.PresignedUpload;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 사진 피드의 한 흐름(초안 → 게시 → 사진 처리 완료 → 노출 → 초상권 신고로 격리 → 운영진 복원)을 실제 DB로 확인한다.
 * 사진 처리 워커는 따로 있으므로 처리 결과는 저장소로 직접 만든다. H2를 다른 통합 테스트와 같이 쓰므로 쓴 행은 전후에 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedFlowIntegrationTest {

	private static final MediaType JSON = MediaType.APPLICATION_JSON;

	@MockitoBean MediaStorage storage;
	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired JwtTokenService jwtTokenService;
	@Autowired UserRepository userRepository;
	@Autowired PostRepository postRepository;
	@Autowired PostMediaRepository postMediaRepository;
	@Autowired MediaJobRepository mediaJobRepository;
	@Autowired ReportRepository reportRepository;

	private final List<Long> createdUserIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		clean();
		given(storage.presignUpload(anyString(), anyString(), anyLong(), any(Duration.class)))
				.willReturn(new PresignedUpload("https://upload.test/x", Map.of(), Instant.now().plusSeconds(900)));
		given(storage.uploadedSize(anyString())).willReturn(OptionalLong.of(500_000L));
	}

	@AfterEach
	void tearDown() {
		clean();
		userRepository.deleteAllById(createdUserIds);
		createdUserIds.clear();
	}

	private void clean() {
		reportRepository.deleteAllInBatch();
		mediaJobRepository.deleteAllInBatch();
		postMediaRepository.deleteAllInBatch();
		postRepository.deleteAllInBatch();
	}

	private User saveUser(String nickname, Role role) {
		User user = User.create(nickname);
		user.changeRole(role);
		User saved = userRepository.saveAndFlush(user);
		createdUserIds.add(saved.getId());
		return saved;
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user.getId()).token();
	}

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private boolean feedContains(long postId) throws Exception {
		JsonNode items = json(mockMvc.perform(get("/api/v1/posts")).andExpect(status().isOk())).at("/data/items");
		for (JsonNode item : items) {
			if (item.get("id").asLong() == postId) {
				return true;
			}
		}
		return false;
	}

	private List<MediaJob> jobs(MediaJobKind kind) {
		return mediaJobRepository.findAll().stream().filter(j -> j.getKind() == kind).toList();
	}

	@Test
	void 게시한_사진이_노출되고_초상권_신고로_격리됐다가_운영진이_복원한다() throws Exception {
		// given
		User author = saveUser("피드플로우작성", Role.USER);
		User reporter = saveUser("피드플로우신고", Role.USER);
		User admin = saveUser("피드플로우운영", Role.ADMIN);

		// when : 초안 → 게시
		JsonNode draft = json(mockMvc.perform(post("/api/v1/posts").header("Authorization", bearer(author))
				.contentType(JSON)
				.content("{\"media\":[{\"contentType\":\"image/jpeg\",\"byteSize\":500000,\"width\":1600,\"height\":1200}]}"))
				.andExpect(status().isCreated()));
		long postId = draft.at("/data/postId").asLong();
		long mediaId = draft.at("/data/uploads/0/mediaId").asLong();
		mockMvc.perform(post("/api/v1/posts/" + postId + "/publish").header("Authorization", bearer(author))
				.contentType(JSON).content("{\"body\":\"오늘 완등\"}")).andExpect(status().isOk());

		// then : 처리 전에는 피드에 없고 PROCESS 작업이 하나 생긴다
		assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(feedContains(postId)).isFalse();
		assertThat(jobs(MediaJobKind.PROCESS)).hasSize(1);

		// when : 워커 대신 직접 처리 완료
		MediaJob process = jobs(MediaJobKind.PROCESS).get(0);
		process.markDone();
		mediaJobRepository.save(process);
		PostMedia media = postMediaRepository.findById(mediaId).orElseThrow();
		media.markReady("2030/03/" + mediaId + ".jpg", 1080, 810, 120_000L);
		postMediaRepository.save(media);
		Post published = postRepository.findById(postId).orElseThrow();
		published.markVisible(Instant.now());
		postRepository.save(published);

		// then : 비로그인 피드에 보인다
		assertThat(feedContains(postId)).isTrue();

		// when : 다른 회원이 초상권으로 신고
		mockMvc.perform(post("/api/v1/reports").header("Authorization", bearer(reporter)).contentType(JSON)
				.content("{\"targetType\":\"POST\",\"targetId\":" + postId + ",\"reason\":\"PORTRAIT_PRIVACY\"}"))
				.andExpect(status().isNoContent());

		// then : 피드에서 빠지고 격리 작업이 생긴다
		assertThat(feedContains(postId)).isFalse();
		assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.HIDDEN_PENDING);
		assertThat(jobs(MediaJobKind.QUARANTINE)).extracting(MediaJob::getTargetId).containsExactly(mediaId);

		// when : 운영진이 복원
		mockMvc.perform(post("/api/v1/report-targets/POST/" + postId + "/actions").header("Authorization", bearer(admin))
				.contentType(JSON).content("{\"action\":\"RESTORE\"}")).andExpect(status().isNoContent());

		// then : 사진을 다시 올릴 때까지 PENDING이고 RELEASE 작업이 생긴다
		assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PENDING);
		assertThat(jobs(MediaJobKind.RELEASE)).extracting(MediaJob::getTargetId).containsExactly(mediaId);
		assertThat(postMediaRepository.findById(mediaId).orElseThrow().getStatus())
				.isEqualTo(PostMediaStatus.PROCESSING);
		assertThat(feedContains(postId)).isFalse();
	}
}
