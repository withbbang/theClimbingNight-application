package com.theclimbingnight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.theclimbingnight.auth.service.JwtTokenService;
import com.theclimbingnight.comment.repository.CommentRepository;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.moderation.repository.UserBlockRepository;
import com.theclimbingnight.moderation.repository.ReportRepository;
import com.theclimbingnight.user.entity.Role;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

/** 댓글·답글 작성, 삭제 자리, 수 다시 세기, 차단을 실제 DB와 HTTP로 확인한다. 쓴 행은 전후에 지운다. */
@SpringBootTest
@AutoConfigureMockMvc
class CommentFlowIntegrationTest {

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
	@Autowired CommentRepository commentRepository;
	@Autowired UserBlockRepository blockRepository;

	private final List<Long> createdUserIds = new ArrayList<>();

	@BeforeEach
	void setUp() {
		clean();
	}

	@AfterEach
	void tearDown() {
		clean();
		userRepository.deleteAllById(createdUserIds);
		createdUserIds.clear();
	}

	private void clean() {
		reportRepository.deleteAllInBatch();
		commentRepository.deleteAllInBatch();
		blockRepository.deleteAllInBatch();
		mediaJobRepository.deleteAllInBatch();
		postMediaRepository.deleteAllInBatch();
		postRepository.deleteAllInBatch();
	}

	private User saveUser(String nickname) {
		User user = User.create(nickname);
		user.changeRole(Role.USER);
		User saved = userRepository.saveAndFlush(user);
		createdUserIds.add(saved.getId());
		return saved;
	}

	private long visiblePost(User author) {
		Post post = Post.draft(author.getId());
		post.publish("오늘 완등");
		post.markVisible(Instant.now());
		return postRepository.saveAndFlush(post).getId();
	}

	private String bearer(User user) {
		return "Bearer " + jwtTokenService.issueAccessToken(user.getId()).token();
	}

	private JsonNode json(ResultActions actions) throws Exception {
		return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
	}

	private JsonNode write(User user, long postId, String body, Long replyTo) throws Exception {
		String content = replyTo == null ? "{\"body\":\"" + body + "\"}"
				: "{\"body\":\"" + body + "\",\"replyToCommentId\":" + replyTo + "}";
		return json(mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
				.header("Authorization", bearer(user)).contentType(JSON).content(content))
				.andExpect(status().isCreated()));
	}

	private JsonNode list(User viewer, long postId, String query) throws Exception {
		return json(mockMvc.perform(get("/api/v1/posts/" + postId + "/comments" + query)
				.header("Authorization", bearer(viewer))).andExpect(status().isOk()));
	}

	@Test
	void 댓글과_답글을_달고_부모를_지우면_자리가_남고_수는_답글만_센다() throws Exception {
		// given
		User author = saveUser("댓글작성자");
		User replier = saveUser("답글작성자");
		long postId = visiblePost(author);

		// when : 댓글 → 답글
		long parentId = write(author, postId, "첫 댓글", null).at("/data/id").asLong();
		long replyId = write(replier, postId, "답글이에요", parentId).at("/data/id").asLong();

		// then
		assertThat(postRepository.findById(postId).orElseThrow().getCommentCount()).isEqualTo(2);

		// when : 부모 삭제
		mockMvc.perform(delete("/api/v1/comments/" + parentId).header("Authorization", bearer(author)))
				.andExpect(status().isNoContent());

		// then : 부모 자리가 남고 replyCount는 1이다
		JsonNode items = list(replier, postId, "").at("/data/items");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("id").asLong()).isEqualTo(parentId);
		assertThat(items.get(0).get("display").asString()).isEqualTo("DELETED");
		assertThat(items.get(0).get("replyCount").asInt()).isEqualTo(1);
		JsonNode replies = list(replier, postId, "?parentId=" + parentId).at("/data/items");
		assertThat(replies).hasSize(1);
		assertThat(replies.get(0).get("id").asLong()).isEqualTo(replyId);

		// then : 상세의 commentCount는 답글 하나뿐이다
		JsonNode detail = json(mockMvc.perform(get("/api/v1/posts/" + postId).header("Authorization", bearer(replier)))
				.andExpect(status().isOk()));
		assertThat(detail.at("/data/commentCount").asInt()).isEqualTo(1);
	}

	@Test
	void 차단하면_그_사람의_댓글과_그_아래_답글이_빠진다() throws Exception {
		// given
		User author = saveUser("글쓴이");
		User blocked = saveUser("차단당함");
		User other = saveUser("제3자");
		User viewer = saveUser("보는사람");
		long postId = visiblePost(author);
		long blockedId = write(blocked, postId, "차단될 댓글", null).at("/data/id").asLong();
		write(other, postId, "남의 답글", blockedId);
		long keptId = write(other, postId, "남는 댓글", null).at("/data/id").asLong();

		// when
		mockMvc.perform(post("/api/v1/blocks").header("Authorization", bearer(viewer)).contentType(JSON)
				.content("{\"userId\":" + blocked.getId() + "}")).andExpect(status().isCreated());

		// then : 목록에는 남는 댓글만, 직접 요청한 답글 목록은 404
		JsonNode items = list(viewer, postId, "").at("/data/items");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("id").asLong()).isEqualTo(keptId);
		mockMvc.perform(get("/api/v1/posts/" + postId + "/comments?parentId=" + blockedId)
				.header("Authorization", bearer(viewer))).andExpect(status().isNotFound());
	}
}
