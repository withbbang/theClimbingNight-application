package com.theclimbingnight.feed.dto;

/** READY가 아니거나 글이 숨김 상태면 url·thumbnailUrl은 null이다. */
public record PostMediaResponse(long id, String type, String url, String thumbnailUrl, int width, int height) {
}
