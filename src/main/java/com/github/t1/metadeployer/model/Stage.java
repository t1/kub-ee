package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.tools.yaml.*;
import lombok.*;

import static java.lang.String.*;

@Value
@Builder
public class Stage {
    public static final Stage NULL_STAGE =
            Stage.builder().name("").prefix("").suffix("").count(1).indexLength(0).build();

    String name;
    String prefix;
    String suffix;
    int count;
    int indexLength;

    public String formattedIndex(int index) {
        if (indexLength == 0)
            if (count == 1)
                return "";
            else
                return Integer.toString(index);
        else
            return format("%0" + indexLength + "d", index);
    }

    public static class StageBuilder {
        public static Stage from(YamlEntry entry) {
            return builder()
                    .name(entry.key().asString())
                    .read(entry.value().asMapping())
                    .build();
        }

        public StageBuilder read(YamlMapping value) {
            suffix(value.get("suffix").asStringOr(""));
            prefix(value.get("prefix").asStringOr(""));
            count(value.get("count").asIntOr(1));
            indexLength(value.get("indexLength").asIntOr(0));
            return this;
        }
    }
}
