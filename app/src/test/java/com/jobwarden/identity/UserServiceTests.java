package com.jobwarden.identity;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import com.jobwarden.TestcontainersConfiguration;
import com.jobwarden.common.exception.BusinessRuleException;
import com.jobwarden.identity.dto.User;
import com.jobwarden.identity.enums.Assistant;
import com.jobwarden.identity.enums.IdentityErrorCode;
import com.jobwarden.identity.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class UserServiceTests {

	@Autowired
	private UserService users;

	@Autowired
	private JdbcClient jdbc;

	@BeforeEach
	void deleteAllProfiles() {
		this.jdbc.sql("TRUNCATE users CASCADE").update();
	}

	@Test
	void createsProfileWithTrimmedName() {
		User created = this.users.create("  Robert ");

		assertThat(created.name()).isEqualTo("Robert");
		assertThat(created.createdAt()).isNotNull();
		assertThat(this.users.findById(created.id())).get().extracting(User::name).isEqualTo("Robert");
	}

	@Test
	void rejectsNameAlreadyTakenIgnoringCase() {
		this.users.create("Robert");

		assertThatThrownBy(() -> this.users.create("robert")).isInstanceOfSatisfying(BusinessRuleException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.USER_NAME_TAKEN));
	}

	@Test
	void theDatabaseAlsoRejectsANameTakenIgnoringCase() {
		this.users.create("Robert");

		assertThatThrownBy(() -> this.jdbc.sql("INSERT INTO users (name) VALUES ('robert')").update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void listsProfilesByNameIgnoringCase() {
		this.users.create("bob");
		this.users.create("Carol");
		this.users.create("Alice");

		assertThat(this.users.findAll()).extracting(User::name).containsExactly("Alice", "bob", "Carol");
	}

	@Test
	void worksWithClaudeCodeUntilTheProfileChoosesAnotherAssistant() {
		User created = this.users.create("Robert");
		assertThat(created.assistant()).isEqualTo(Assistant.CLAUDE_CODE);

		assertThat(this.users.changeAssistant(created.id(), Assistant.CODEX).assistant()).isEqualTo(Assistant.CODEX);
		assertThat(this.users.findById(created.id())).get().extracting(User::assistant).isEqualTo(Assistant.CODEX);
		// The database knows only the assistants there are
		assertThatThrownBy(() -> this.jdbc.sql("UPDATE users SET assistant = 'CHATGPT'").update())
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void findsNothingForUnknownId() {
		assertThat(this.users.findById(Long.MAX_VALUE)).isEmpty();
	}

	@Test
	void tellsWhetherAProfileExists() {
		User robert = this.users.create("Robert");

		assertThat(this.users.exists(robert.id())).isTrue();
		assertThat(this.users.exists(Long.MAX_VALUE)).isFalse();
	}

	@Test
	void keepsAPhotoAsASmallSquarePngAndSaysWhenItChanged() throws IOException {
		User robert = this.users.create("Robert");
		byte[] photo = jpeg(new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB));

		User changed = this.users.changeImage(robert.id(), photo);

		assertThat(robert.hasImage()).isFalse();
		assertThat(changed.hasImage()).isTrue();
		assertThat(this.users.findById(robert.id())).get().extracting(User::imageChangedAt).isEqualTo(changed.imageChangedAt());
		BufferedImage kept = ImageIO.read(new ByteArrayInputStream(this.users.findImage(robert.id()).orElseThrow()));
		assertThat(kept.getWidth()).isEqualTo(160);
		assertThat(kept.getHeight()).isEqualTo(160);
		// Another replaces it
		this.users.changeImage(robert.id(), jpeg(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB)));
		assertThat(ImageIO.read(new ByteArrayInputStream(this.users.findImage(robert.id()).orElseThrow())).getWidth())
			.isEqualTo(50);
	}

	@Test
	void removesThePhoto() throws IOException {
		User robert = this.users.create("Robert");
		this.users.changeImage(robert.id(), jpeg(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)));

		User removed = this.users.removeImage(robert.id());

		assertThat(removed.hasImage()).isFalse();
		assertThat(this.users.findImage(robert.id())).isEmpty();
		assertThat(this.users.findAll()).singleElement().extracting(User::hasImage).isEqualTo(false);
	}

	@Test
	void refusesAFileThatIsntAPhotoOrIsTooLarge() {
		long robert = this.users.create("Robert").id();
		byte[] text = "Not a photo".getBytes(StandardCharsets.UTF_8);
		byte[] tooLarge = new byte[UserService.MAX_IMAGE_BYTES + 1];

		assertThatThrownBy(() -> this.users.changeImage(robert, text)).isInstanceOfSatisfying(BusinessRuleException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.IMAGE_UNREADABLE));
		assertThatThrownBy(() -> this.users.changeImage(robert, tooLarge)).isInstanceOfSatisfying(BusinessRuleException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(IdentityErrorCode.IMAGE_TOO_LARGE));
		assertThat(this.users.findImage(robert)).isEmpty();
	}

	private static byte[] jpeg(BufferedImage image) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(image, "jpeg", bytes);
		return bytes.toByteArray();
	}

}
