package com.github.t1.metadeployer.model;

import lombok.*;
import org.yaml.snakeyaml.nodes.NodeTuple;

import java.util.List;

import static com.github.t1.metadeployer.model.YamlTools.*;
import static java.lang.String.*;

@Value
@Builder
public class Stage {
    String name;
    String prefix;
    String suffix;
    int count;
    int indexLength;

    public String formattedIndex(int index) {
        return (indexLength == 0) ? Integer.toString(index) : format("%0" + indexLength + "d", index);
    }

    public static class StageBuilder {
        public static Stage from(NodeTuple tuple) {
            return builder()
                    .name(getScalarValue(tuple.getKeyNode()))
                    .read(getMappingValue(tuple.getValueNode()))
                    .build();
        }

        public StageBuilder read(List<NodeTuple> value) {
            suffix(getScalarValue(get(value, "suffix"), ""));
            prefix(getScalarValue(get(value, "prefix"), ""));
            count(toInt(get(value, "count"), 1));
            indexLength(toInt(get(value, "indexLength"), 0));
            return this;
        }
    }
}
