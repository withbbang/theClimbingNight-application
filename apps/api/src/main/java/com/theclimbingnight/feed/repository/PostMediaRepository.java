package com.theclimbingnight.feed.repository;

import com.theclimbingnight.feed.entity.PostMedia;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {

	List<PostMedia> findAllByPostIdOrderBySortOrder(long postId);

	List<PostMedia> findAllByPostIdInOrderBySortOrder(Collection<Long> postIds);
}
