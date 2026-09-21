package br.com.nhac.backend_nhac;

import org.testcontainers.containers.MariaDBContainer;

final class MariaDbTestContainer {

    private static final MariaDBContainer<?> INSTANCE = new MariaDBContainer<>("mariadb:11")
            .withDatabaseName("nhac_it")
            .withUsername("nhac")
            .withPassword("nhac_test");

    private static boolean started;

    private MariaDbTestContainer() {
    }

    static synchronized MariaDBContainer<?> instance() {
        if (!started) {
            INSTANCE.start();
            started = true;
        }
        return INSTANCE;
    }
}
