package com.github.t1.kubee.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.Tag;

import static lombok.AccessLevel.*;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlTag {
    private final Tag tag;

    public String asString() { return tag.getValue(); }
}
