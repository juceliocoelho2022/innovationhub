package com.innovationhub;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:sqlserver:2022-CU20-ubuntu-22.04:///innovationhub?TC_DAEMON=true",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.username=sa",
        "spring.datasource.password=InnovationHub@2026!",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
public abstract class AbstractSqlServerIntegrationTest {
}
