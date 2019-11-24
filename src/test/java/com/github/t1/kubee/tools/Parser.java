package com.github.t1.kubee.tools;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class Parser {
    private String text;

    public boolean eats(String pattern) {
        if (!text.startsWith(pattern))
            return false;
        text = text.substring(pattern.length());
        return true;
    }

    public String eatRest() {
        String result = text;
        this.text = null;
        return result;
    }

    public boolean done() { return text == null || text.isEmpty(); }
}
