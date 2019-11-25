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

    public String eatWord() {
        int i = text.indexOf(' ');
        if (i < 0) // last word
            return eatRest();
        String result = text.substring(0, i);
        text = text.substring(i + 1);
        return result;
    }

    public String eatRest() {
        String result = text;
        this.text = null;
        return result;
    }

    public boolean done() { return text == null || text.isEmpty(); }
}
