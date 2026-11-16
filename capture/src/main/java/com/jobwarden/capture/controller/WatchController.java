package com.jobwarden.capture.controller;

import com.jobwarden.capture.dto.WatchRequest;
import com.jobwarden.capture.dto.WatchStatus;
import com.jobwarden.identity.exception.UserNotFoundException;
import com.jobwarden.identity.service.UserService;
import com.jobwarden.watchlist.dto.BoardLink;
import com.jobwarden.watchlist.dto.WatchResult;
import com.jobwarden.watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The watchlist from the extension: on one of a company's jobs on Greenhouse, Lever or Ashby, the popup's eye shows
 * whether the profile watches the company's board, and watches it, or stops, when clicked. Like {@link CaptureController}, the extensions in Firefox and Chrome
 * may call it, and web pages may not.
 */
@RestController
@RequestMapping("${jobwarden.capture.path}/watchlist")
@RequiredArgsConstructor
class WatchController {

	private final WatchlistService watchlist;

	private final UserService users;

	/**
	 * Reads no board, so the popup can ask on every page it opens on.
	 *
	 * @return 200 with the board and whether the profile watches it, or 204 when the page isn't on a board
	 */
	@GetMapping
	ResponseEntity<WatchStatus> find(@RequestParam long userId, @RequestParam String link) {
		if (link.length() > WatchRequest.LINK_MAX_LENGTH) {
			// No board's link is that long
			return ResponseEntity.noContent().build();
		}
		requireProfile(userId);
		return this.watchlist.findBoard(userId, link)
			.map(WatchController::toStatus)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	/**
	 * Reads the board, and adds what matches the saved searches to the AI Assistant page.
	 *
	 * @return 201 when the company is new on the watchlist, 200 when it was there already
	 */
	@PostMapping
	ResponseEntity<WatchStatus> watch(@Valid @RequestBody WatchRequest request) {
		requireProfile(request.userId());
		WatchResult watched = this.watchlist.watch(request.userId(), request.link(), request.name());
		WatchStatus status = new WatchStatus(watched.company().name(), watched.company().ats().label(),
				watched.company().boardUrl(), true, watched.company().id(), watched.added(), watched.newJobs());
		return ResponseEntity.status(watched.added() ? HttpStatus.CREATED : HttpStatus.OK).body(status);
	}

	/**
	 * Stops watching the company: the extension's eye, clicked again. The jobs found on its board stay.
	 */
	@DeleteMapping("/{companyId}")
	ResponseEntity<Void> unwatch(@PathVariable long companyId, @RequestParam long userId) {
		requireProfile(userId);
		this.watchlist.unwatch(userId, companyId);
		return ResponseEntity.noContent().build();
	}

	private void requireProfile(long userId) {
		if (!this.users.exists(userId)) {
			throw new UserNotFoundException(userId);
		}
	}

	private static WatchStatus toStatus(BoardLink board) {
		return new WatchStatus(board.name(), board.ats().label(), board.boardUrl(), board.company() != null,
				(board.company() != null) ? board.company().id() : null, null, null);
	}

}
