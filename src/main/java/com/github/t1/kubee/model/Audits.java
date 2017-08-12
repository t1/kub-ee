package com.github.t1.kubee.model;

import com.github.t1.kubee.tools.yaml.YamlParser.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.nodes.Node;

import java.util.*;

import static com.github.t1.kubee.tools.yaml.YamlParser.*;

@Slf4j
@Data
public class Audits {
    String processState;
    List<Warning> warnings;
    List<Audit> audits;

    public Optional<Audit> findDeployment(String name) {
        return audits.stream().filter(audit -> audit.getName().equals(name)).findAny();
    }

    @Value
    public static class Warning {
        String text;

        @Override public String toString() { return "\"" + text + "\""; }

        private static Warning from(Node node) {
            Map<String, String> map = asStringMap(node);
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

            public static Change from(Node node) {
                ChangeBuilder change = change();
                Mapping.mapString("name", change::name)
                       .mapString("old-value", change::oldValue)
                       .mapString("new-value", change::newValue)
                       .from(node);
                return change.build();
            }
        }

        public static Audit from(Node node) {
            AuditBuilder audit = audit();
            audit.type(node.getTag().getValue());
            Mapping.mapString("operation", audit::operation)
                   .mapString("name", audit::name)
                   .mapString("category", audit::name)
                   .mapSequence("changes", audit::changes, Change::from)
                   .from(node);
            return audit.build();
        }
    }


    /**
     * We want to be generic for the types of audits, but I didn't find a way to parse that out of the yaml tags with Jackson
     */
    public static Audits parseYaml(String yaml) {
        Audits audits = new Audits();
        Mapping.mapString("processState", audits::setProcessState)
               .mapSequence("warnings", audits::setWarnings, Warning::from)
               .mapSequence("audits", audits::setAudits, Audit::from)
               .from(parse(yaml));
        return audits;
    }
}
