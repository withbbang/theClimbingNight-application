package com.theclimbingnight.roster.service;

/** CSV 데이터 한 행. line은 파일의 줄 번호(머리글이 1), note는 빈 값이면 null. */
public record RosterCsvRow(long line, String somoimNickname, String note) {
}
