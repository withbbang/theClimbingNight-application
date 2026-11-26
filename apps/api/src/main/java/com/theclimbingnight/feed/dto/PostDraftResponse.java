package com.theclimbingnight.feed.dto;

import java.util.List;

public record PostDraftResponse(long postId, List<PostUploadResponse> uploads) {
}
