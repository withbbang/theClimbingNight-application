package com.theclimbingnight.feed.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.theclimbingnight.feed.entity.Post;
import com.theclimbingnight.feed.repository.PostLikeRepository;
import com.theclimbingnight.feed.repository.PostRepository;
import com.theclimbingnight.user.entity.User;
import com.theclimbingnight.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/** 좋아요 수는 동시에 눌러도 실제 행 수와 같아야 한다. 공유 H2라 만든 행을 직접 지운다. */
@SpringBootTest
class PostLikeConcurrencyTest {

	@Autowired PostLikeService service;
	@Autowired PostRepository posts;
	@Autowired PostLikeRepository likes;
	@Autowired UserRepository users;

	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void clean() {
		likes.deleteAllInBatch();
		posts.deleteAllInBatch();
		users.deleteAllById(userIds);
	}

	private long user(String nickname) {
		long id = users.saveAndFlush(User.create(nickname)).getId();
		userIds.add(id);
		return id;
	}

	private long visiblePost(long author) {
		Post post = Post.draft(author);
		ReflectionTestUtils.setField(post, "status", com.theclimbingnight.feed.entity.PostStatus.VISIBLE);
		return posts.saveAndFlush(post).getId();
	}

	private void runAll(List<Runnable> tasks) throws Exception {
		CountDownLatch ready = new CountDownLatch(tasks.size());
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
		List<Future<?>> futures = new ArrayList<>();
		for (Runnable task : tasks) {
			futures.add(pool.submit(() -> {
				ready.countDown();
				go.await();
				task.run();
				return null;
			}));
		}
		ready.await();
		go.countDown();
		for (Future<?> f : futures) {
			f.get();
		}
		pool.shutdown();
	}

	@Test
	void 서로_다른_10명이_동시에_누르면_수가_10이다() throws Exception {
		// given
		long author = user("작성자");
		long postId = visiblePost(author);
		List<Runnable> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			long liker = user("누른이" + i);
			tasks.add(() -> service.like(liker, postId));
		}

		// when
		runAll(tasks);

		// then
		assertThat(posts.findById(postId).orElseThrow().getLikeCount()).isEqualTo(10);
		assertThat(likes.countByPostId(postId)).isEqualTo(10);
	}

	@Test
	void 같은_사람이_켜기와_끄기를_동시에_여러_번_보내도_수는_행_수와_같다() throws Exception {
		// given
		long author = user("작성자");
		long postId = visiblePost(author);
		long liker = user("연타");
		List<Runnable> tasks = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			tasks.add(i % 2 == 0 ? () -> service.like(liker, postId) : () -> service.unlike(liker, postId));
		}

		// when
		runAll(tasks);

		// then
		long rows = likes.countByPostId(postId);
		assertThat(rows).isBetween(0L, 1L);
		assertThat(posts.findById(postId).orElseThrow().getLikeCount()).isEqualTo((int) rows);
	}
}
