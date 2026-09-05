package com.seewik.api;

import java.time.Clock;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("!local-e2e")
public class SeewikClockConfiguration {
    @Bean
    Clock seewikClock() {
        return Clock.systemUTC();
    }
}

@Configuration
@Profile("local-e2e")
class LocalE2EClockConfiguration {
    @Bean
    AdjustableClock seewikClock(Environment environment) {
        LocalE2ESafety.requireSafe(environment::getProperty);
        return new AdjustableClock();
    }
}

final class LocalE2ESafety {
    private LocalE2ESafety() {}

    static void requireSafe(Function<String, String> property) {
        if (present(property.apply("K_SERVICE")) || present(property.apply("K_REVISION"))) {
            throw new IllegalStateException("The local-e2e profile is forbidden on Cloud Run");
        }
        if (!present(property.apply("FIRESTORE_EMULATOR_HOST"))
                || !present(property.apply("FIREBASE_AUTH_EMULATOR_HOST"))) {
            throw new IllegalStateException("The local-e2e profile requires Firestore and Auth emulators");
        }
        String projectId = property.apply("seewik.firebase.project-id");
        if (projectId == null || !projectId.startsWith("demo-")) {
            throw new IllegalStateException("The local-e2e profile requires a demo project ID");
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
