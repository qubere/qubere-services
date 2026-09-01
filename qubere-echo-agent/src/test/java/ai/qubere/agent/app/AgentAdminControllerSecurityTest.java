package ai.qubere.agent.app;

import ai.qubere.agent.app.api.AgentAdminController;
import ai.qubere.agent.core.AgentErrorCode;
import ai.qubere.agent.core.AgentExecutionException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:agentadmin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.ai.model.chat=none",
        "agent-platform.ai.spring.enabled=false",
        "agent-platform.admin.enabled=true",
        "agent-platform.admin.token=test-admin-token"
})
class AgentAdminControllerSecurityTest {

    @Autowired
    private AgentAdminController adminController;

    @Test
    void rejectsAdminCallsWithoutConfiguredToken() {
        assertThatThrownBy(() -> adminController.observabilitySummary(null))
                .isInstanceOfSatisfying(AgentExecutionException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.AUTHORIZATION_DENIED));
    }

    @Test
    void allowsAdminCallsWithConfiguredToken() {
        assertThat(adminController.observabilitySummary("test-admin-token").observedEvents()).isZero();
    }
}
