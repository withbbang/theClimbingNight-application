package com.theclimbingnight.roster.service;

import com.theclimbingnight.roster.dto.RosterImportResponse;
import com.theclimbingnight.roster.dto.RosterImportResponse.SkippedRow;
import com.theclimbingnight.roster.entity.RosterMember;
import com.theclimbingnight.roster.entity.RosterSource;
import com.theclimbingnight.roster.repository.RosterMemberRepository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RosterImportService {

	private final RosterCsvParser parser;
	private final RosterMemberRepository rosterMemberRepository;

	public RosterImportService(RosterCsvParser parser, RosterMemberRepository rosterMemberRepository) {
		this.parser = parser;
		this.rosterMemberRepository = rosterMemberRepository;
	}

	/** 오류 행이 있어도 나머지는 등록한다. 같은 파일을 고쳐 다시 올리면 추가분만 들어간다. */
	@Transactional
	public RosterImportResponse importCsv(InputStream csv) {
		RosterCsvResult parsed = parser.parse(csv);

		Set<String> seen = new HashSet<>();
		List<RosterCsvRow> unique = new ArrayList<>();
		List<SkippedRow> skipped = new ArrayList<>();
		for (RosterCsvRow row : parsed.rows()) {
			if (seen.add(lower(row.somoimNickname()))) {
				unique.add(row);
			} else {
				skipped.add(new SkippedRow(row.line(), row.somoimNickname(), "파일 안에서 중복입니다."));
			}
		}

		Set<String> existing = unique.isEmpty() ? Set.of()
				: new HashSet<>(rosterMemberRepository.findExistingLowerNicknames(seen));
		List<RosterMember> toSave = new ArrayList<>();
		for (RosterCsvRow row : unique) {
			if (existing.contains(lower(row.somoimNickname()))) {
				skipped.add(new SkippedRow(row.line(), row.somoimNickname(), "이미 명부에 있습니다."));
			} else {
				toSave.add(RosterMember.create(row.somoimNickname(), row.note(), RosterSource.IMPORT));
			}
		}
		skipped.sort(java.util.Comparator.comparingLong(SkippedRow::line));
		if (!toSave.isEmpty()) {
			rosterMemberRepository.saveAll(toSave);
		}
		return new RosterImportResponse(toSave.size(), skipped, parsed.errors());
	}

	private static String lower(String nickname) {
		return nickname.toLowerCase(Locale.ROOT);
	}

}
