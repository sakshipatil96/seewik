package com.seewik.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LocalE2EClockConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SeewikClockConfiguration.class,
                    LocalE2EClockConfiguration.class,
                    LocalE2EClockController.class);

    @Test
    void productionContextDoesNotExposeClockControl() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(LocalE2EClockController.class);
            assertThat(context).doesNotHaveBean(AdjustableClock.class);
        });
    }

    @Test
    void localProfileRequiresBothEmulators() {
        runner.withPropertyValues(
                "spring.profiles.active=local-e2e",
                "seewik.firebase.project-id=demo-seewik-local")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void cloudRunCannotActivateLocalProfileEvenWithEmulatorVariables() {
        runner.withPropertyValues(
                "spring.profiles.active=local-e2e",
                "seewik.firebase.project-id=demo-seewik-local",
                "FIRESTORE_EMULATOR_HOST=127.0.0.1:8081",
                "FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099",
                "K_SERVICE=seewik-api")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void isolatedLocalProfileExposesAdjustableClock() {
        runner.withPropertyValues(
                "spring.profiles.active=local-e2e",
                "seewik.firebase.project-id=demo-seewik-local",
                "FIRESTORE_EMULATOR_HOST=127.0.0.1:8081",
                "FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AdjustableClock.class);
                    assertThat(context).hasSingleBean(LocalE2EClockController.class);
                });
    }
}
