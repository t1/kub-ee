package com.github.t1.kubee.entity;

import com.github.t1.kubee.tools.yaml.YamlDocument;
import com.github.t1.kubee.tools.yaml.YamlNode;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Data
public class Audits {
    String processState;
    List<Warning> warnings;
    List<Audit> audits;

    public Optional<Audit> findDeployment(String name) {
        return (audits == null)
                ? Optional.empty()
                : audits.stream().filter(audit -> audit.getName().equals(name)).findAny();
    }

    @Value
    public static class Warning {
        String text;

        @Override public String toString() { return "\"" + text + "\""; }

        private static Warning from(YamlNode node) {
            Map<String, String> map = node.asStringMap();
            return new Warning(map.get("text"));
        }
    }

    @Data
    @Builder(builderMethodName = "audit")
    public static class Audit {
        private final String type;
        private final String operation;
        private final String name;
        @Singular private final List<Change> changes;

        @Override public String toString() { return operation + "-" + type + ":" + name + changes; }

        public Optional<Change> findChange(String name) {
            return changes.stream().filter(change -> change.getName().equals(name)).findAny();
        }

        @Data
        @Builder(builderMethodName = "change")
        public static class Change {
            private final String name;
            private final String oldValue;
            private final String newValue;

            @Override public String toString() { return name + ":" + oldValue + "->" + newValue; }

            public static Change from(YamlNode node) {
                ChangeBuilder change = change();
                node.asMapping()
                    .mapString("name", change::name)
                    .mapString("old-value", change::oldValue)
                    .mapString("new-value", change::newValue);
                return change.build();
            }
        }

        public static Audit from(YamlNode node) {
            AuditBuilder audit = audit();
            audit.type(node.getTag().asString());
            node.asMapping()
                .mapString("operation", audit::operation)
                .mapString("name", audit::name)
                .mapString("category", audit::name)
                .mapSequence("changes", audit::changes, Change::from);
            return audit.build();
        }
    }


    /**
     * We want to be generic for the types of audits, but I didn't find a way to parse that out of the yaml tags with Jackson
     */
    public static Audits parseYaml(String yaml) {
        Audits audits = new Audits();
        YamlDocument document = YamlDocument.from(new StringReader(yaml));
        document.asMapping()
                .mapString("processState", audits::setProcessState)
                .mapSequence("warnings", audits::setWarnings, Warning::from)
                .mapSequence("audits", audits::setAudits, Audit::from);
        return audits;
    }
}
