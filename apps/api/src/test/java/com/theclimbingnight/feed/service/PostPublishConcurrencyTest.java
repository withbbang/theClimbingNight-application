package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.theclimbingnight.common.BusinessException;
import com.theclimbingnight.common.ErrorCode;
import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.entity.PostMedia;
import com.theclimbingnight.feed.repository.PostMediaRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.media.job.MediaJobKind;
import com.theclimbingnight.media.job.MediaJobRepository;
import com.theclimbingnight.media.storage.MediaStorage;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 같은 게시물에 게시 요청이 동시에 와도 하나만 성공하고 작업은 사진 수만큼만 생긴다. 공유 H2라 만든 행을 직접 지운다. */
@SpringBootTest
class PostPublishConcurrencyTest {

	@Autowired PostCommandService service;
	@Autowired PostRepository posts;
	@Autowired PostMediaRepository media;
	@Autowired MediaJobRepository jobs;
	@Autowired UserRepository users;

	@MockitoBean MediaStorage storage;

	private final List<Long> userIds = new ArrayList<>();
	private final List<Long> postIds = new ArrayList<>();

	@AfterEach
	void clean() {
		jobs.deleteAllInBatch();
		media.deleteAllInBatch();
		posts.deleteAllInBatch();
		users.deleteAllById(userIds);
	}

	@Test
	void 같은_게시물_게시가_동시에_와도_작업은_사진_수만큼이다() throws Exception {
		// given
		long author = users.saveAndFlush(User.create("동시게시")).getId();
		userIds.add(author);
		Post draft = posts.saveAndFlush(Post.draft(author));
		media.saveAndFlush(PostMedia.image(draft.getId(), 0, "uploads/c1.jpg", "p/c1"));
		media.saveAndFlush(PostMedia.image(draft.getId(), 1, "uploads/c2.jpg", "p/c2"));
		for (PostMedia m : media.findAllByPostIdOrderBySortOrder(draft.getId())) {
			org.springframework.test.util.ReflectionTestUtils.setField(m, "byteSize", 1000L);
			media.saveAndFlush(m);
		}
		given(storage.uploadedSize(anyString())).willReturn(OptionalLong.of(1000L));

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		// when
		List<Future<ErrorCode>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				try {
					service.publish(author, draft.getId(), "본문");
					return null;
				} catch (BusinessException e) {
					return e.getErrorCode();
				}
			}));
		}
		ready.await();
		go.countDown();
		List<ErrorCode> outcomes = new ArrayList<>();
		for (Future<ErrorCode> f : results) {
			outcomes.add(f.get());
		}
		pool.shutdown();

		// then
		assertThat(outcomes).containsExactlyInAnyOrder(null, ErrorCode.POST_NOT_DRAFT);
		assertThat(jobs.findAll()).hasSize(2).allMatch(j -> j.getKind() == MediaJobKind.PROCESS);
	}
}
