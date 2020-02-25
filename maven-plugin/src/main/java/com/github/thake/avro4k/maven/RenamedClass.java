package com.github.thake.avro4k.maven;

import java.util.Objects;

public class RenamedClass {
    private String regex;
    private String replacement;

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RenamedClass that = (RenamedClass) o;
        return Objects.equals(regex, that.regex) && Objects.equals(replacement, that.replacement);
    }

    @Override public int hashCode() {
        return Objects.hash(regex, replacement);
    }

}
