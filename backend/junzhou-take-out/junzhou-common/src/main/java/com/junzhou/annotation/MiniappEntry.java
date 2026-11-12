package com.junzhou.annotation;

import java.lang.annotation.*;

/** Explicitly marks a resolved miniapp handler that does not require a selected store. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MiniappEntry {
    Scope value();
    enum Scope { PUBLIC, IDENTITY }
}
