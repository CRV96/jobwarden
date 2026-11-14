package com.jobwarden;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.jobwarden.common.exception.ErrorCode;
import com.jobwarden.common.exception.JobWardenException;
import com.jobwarden.common.logging.JobWardenLogger;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conventions README.md sets for the code ("Project structure"), checked on every build, so they hold without
 * anyone having to remember them. {@link ModularityTests} checks the modules' boundaries; these, how each module is
 * written. A rule that no longer fits is changed here and in README.md together.
 */
@AnalyzeClasses(packages = "com.jobwarden", importOptions = ImportOption.DoNotIncludeTests.class)
class ConventionsTests {

	// Visibility: only a module's interfaces are called from Java code

	@ArchTest
	static final ArchRule serviceImplementationsArePackagePrivate = classes().that()
		.resideInAPackage("..service.impl..")
		.and()
		.areAnnotatedWith(Service.class)
		.should()
		.notBePublic()
		.andShould()
		.haveSimpleNameStartingWith("Default")
		.because("only the service interfaces are the module's API, and Default* names their implementation");

	@ArchTest
	static final ArchRule controllersArePackagePrivate = classes().that()
		.areAnnotatedWith(Controller.class)
		.or()
		.areAnnotatedWith(RestController.class)
		.should()
		.notBePublic()
		.because("Spring calls the controllers; no Java code should");

	@ArchTest
	static final ArchRule dtosAreRecordsOrEnums = classes().that()
		.resideInAPackage("..dto..")
		.and()
		.arePublic()
		.and()
		.areTopLevelClasses()
		.should()
		.beRecords()
		.orShould()
		.beEnums()
		.orShould()
		.beInterfaces()
		.because("records carry data without constructors, getters or equals to keep up");

	// Dependencies: constructor injection, one logger, no transactions in adapters

	@ArchTest
	static final ArchRule constructorInjection = noFields().should()
		.beAnnotatedWith(Autowired.class)
		.orShould()
		.beAnnotatedWith("jakarta.inject.Inject")
		.orShould()
		.beAnnotatedWith("jakarta.annotation.Resource")
		.because("a constructor makes the dependencies final and the class testable without Spring");

	@ArchTest
	static final ArchRule settingsThroughTheConstructor = noFields().that()
		.areNotFinal()
		.should()
		.beAnnotatedWith(Value.class)
		.because("@Value goes on a final field, which Lombok copies onto the constructor (lombok.config)");

	@ArchTest
	static final ArchRule noSetterInjection = noMethods().that()
		.areDeclaredInClassesThat()
		.doNotHaveSimpleName("BaseController")
		.should()
		.beAnnotatedWith(Autowired.class)
		.because("a setting goes into the constructor too: @Value on a final field, which Lombok copies there "
				+ "(lombok.config); BaseController's setters wait for its composition refactor (docs/quality-roadmap.md)");

	@ArchTest
	static final ArchRule logsThroughJobWardenLogger = noClasses().that()
		.doNotBelongToAnyOf(JobWardenLogger.class)
		.should()
		.dependOnClassesThat()
		.haveFullyQualifiedName("org.slf4j.LoggerFactory")
		.because("@CustomLog gives every class a JobWardenLogger, whose error() needs an error code");

	@ArchTest
	static final ArchRule noStandardStreams = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

	@ArchTest
	static final ArchRule noJavaUtilLogging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

	@ArchTest
	static final ArchRule noGenericExceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
		.because("the modules throw their own exceptions, with an error code");

	@ArchTest
	static final ArchRule springTransactionsOnly = noClasses().should()
		.dependOnClassesThat()
		.haveFullyQualifiedName("jakarta.transaction.Transactional")
		.because("Spring's @Transactional has readOnly, which the services use for their reads");

	@ArchTest
	static final ArchRule noTransactionsInControllers = noClasses().that()
		.resideInAPackage("..controller..")
		.should()
		.beAnnotatedWith(Transactional.class)
		.because("a transaction belongs to the domain module's service, never to an adapter");

	@ArchTest
	static final ArchRule noTransactionalControllerMethods = noMethods().that()
		.areDeclaredInClassesThat()
		.resideInAPackage("..controller..")
		.should()
		.beAnnotatedWith(Transactional.class)
		.because("a transaction belongs to the domain module's service, never to an adapter");

	// Entities

	@ArchTest
	static final ArchRule entitiesKeepObjectsMethods = methods().that()
		.areDeclaredInClassesThat()
		.areAnnotatedWith(Entity.class)
		.should()
		.notHaveName("toString")
		.andShould()
		.notHaveName("equals")
		.andShould()
		.notHaveName("hashCode")
		.allowEmptyShould(true)
		.because("they'd load lazy associations, and on a two-way link recurse forever (no Lombok @Data, @ToString or "
				+ "@EqualsAndHashCode)");

	@ArchTest
	static final ArchRule exceptionsCarryAnErrorCode = classes().that()
		.resideInAPackage("..exception..")
		.and()
		.areAssignableTo(Throwable.class)
		.should()
		.beAssignableTo(JobWardenException.class)
		.because("the exception handlers answer and log the modules' exceptions by their error code");

	// Time: java.time only. The mail reader is where jakarta.mail hands over a Date, turned into an Instant at once.

	@ArchTest
	static final ArchRule javaTimeOnly = noClasses().that()
		.doNotHaveSimpleName("ImapMailReader")
		.should()
		.dependOnClassesThat()
		.belongToAnyOf(Date.class, Calendar.class, SimpleDateFormat.class, java.sql.Date.class, Timestamp.class)
		.because("java.time says what a value is: a moment (Instant), a day (LocalDate) or a time of day");

	private static final JavaClasses APPLICATION = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("com.jobwarden");

	/**
	 * Nothing is null unless it says @Nullable, in every package: NullAway (-Pnullaway, and CI) checks the packages
	 * that say so, and would skip a new one that doesn't.
	 */
	@Test
	void everyPackageIsNullMarked() {
		Set<String> unmarked = APPLICATION.stream()
			.map(JavaClass::getPackage)
			.filter(javaPackage -> !javaPackage.isAnnotatedWith(NullMarked.class))
			.map(JavaPackage::getName)
			.collect(Collectors.toCollection(TreeSet::new));

		assertThat(unmarked).as("packages whose package-info.java lacks @NullMarked").isEmpty();
	}

	/**
	 * "Now" comes from the Clock bean, so a test can fix it. Entities stamp their own audit times (updatedAt...), as
	 * Hibernate's @UpdateTimestamp would, and keep the system's clock.
	 */
	@ArchTest
	static final ArchRule nowComesFromTheClock = noClasses().that()
		.resideOutsideOfPackage("..entity..")
		.should()
		.callMethod(Instant.class, "now")
		.orShould()
		.callMethod(LocalDate.class, "now")
		.orShould()
		.callMethod(LocalDateTime.class, "now")
		.orShould()
		.callMethod(LocalTime.class, "now")
		.orShould()
		.callMethod(ZoneId.class, "systemDefault")
		.because("the Clock bean says what now is, and in which time zone");

	/**
	 * Error codes are searched for in the logs, so each one stays unique, in its module's prefix, e.g. JOBS-001.
	 */
	@Test
	void everyErrorCodeIsUniqueAndNamedAfterItsModule() {
		Map<String, String> owners = new HashMap<>();
		List<JavaClass> codeEnums = APPLICATION.stream()
			.filter(type -> type.isEnum() && type.isAssignableTo(ErrorCode.class))
			.toList();

		assertThat(codeEnums).isNotEmpty();
		for (JavaClass codeEnum : codeEnums) {
			String module = codeEnum.getPackageName().split("\\.")[2].toUpperCase();
			for (Object constant : codeEnum.reflect().getEnumConstants()) {
				String code = ((ErrorCode) constant).getCode();
				String owner = codeEnum.getSimpleName() + "." + constant;
				assertThat(code).as(owner).matches("[A-Z]+-\\d{3}");
				if (!"common".equalsIgnoreCase(module)) {
					assertThat(code).as(owner).startsWith(module + "-");
				}
				assertThat(owners.putIfAbsent(code, owner)).as("%s is used by %s too", code, owner).isNull();
			}
		}
	}

}
