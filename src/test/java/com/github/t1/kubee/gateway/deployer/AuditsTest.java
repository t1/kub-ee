package com.github.t1.kubee.gateway.deployer;

import com.github.t1.kubee.model.Audits;
import com.github.t1.kubee.model.Audits.Warning;
import org.junit.jupiter.api.Test;

import static com.github.t1.kubee.model.Audits.Audit.Change.change;
import static com.github.t1.kubee.model.Audits.Audit.audit;
import static org.assertj.core.api.Assertions.assertThat;

class AuditsTest {
    @Test void shouldDeserialize() {
        Audits audits = Audits.parseYaml(""
                + "audits:\n"
                + "- !<deployable>\n"
                + "  operation: change\n"
                + "  changes:\n"
                + "  - name: checksum\n"
                + "    old-value: 1234\n"
                + "    new-value: 5678\n"
                + "  - name: version\n"
                + "    old-value: 0.0.3\n"
                + "    new-value: 0.0.2\n"
                + "  name: ping\n"
                + "- !<logger>\n"
                + "  operation: change\n"
                + "  changes:\n"
                + "  - name: handlers\n"
                + "    old-value: '[JOLOKIA]'\n"
                + "    new-value: '[JOLOKIA-JSON]'\n"
                + "  category: org.jolokia.jolokia\n"
                + "- !<logger>\n"
                + "  operation: add\n"
                + "  changes:\n"
                + "  - name: level\n"
                + "    new-value: DEBUG\n"
                + "  - name: use-parent-handlers\n"
                + "    new-value: \"false\"\n"
                + "  - name: handlers\n"
                + "    new-value: '[PING-JSON]'\n"
                + "  category: com.airhacks.ping\n"
                + "warnings:\n"
                + "- text: some warning\n"
                + "- text: another warning\n"
                + "processState: running\n");

        assertThat(audits.getProcessState()).isEqualTo("running");
        assertThat(audits.getWarnings()).containsExactly(
                new Warning("some warning"),
                new Warning("another warning"));
        assertThat(audits.getAudits()).containsExactly(
                audit().type("deployable").operation("change").name("ping")
                       .change(change().name("checksum")
                                       .oldValue("1234")
                                       .newValue("5678")
                                       .build())
                       .change(change().name("version").oldValue("0.0.3").newValue("0.0.2").build())
                       .build(),
                audit().type("logger").operation("change").name("org.jolokia.jolokia")
                       .change(change().name("handlers").oldValue("[JOLOKIA]").newValue("[JOLOKIA-JSON]").build())
                       .build(),
                audit().type("logger").operation("add").name("com.airhacks.ping")
                       .change(change().name("level").newValue("DEBUG").build())
                       .change(change().name("use-parent-handlers").newValue("false").build())
                       .change(change().name("handlers").newValue("[PING-JSON]").build())
                       .build()
        );
    }
}
