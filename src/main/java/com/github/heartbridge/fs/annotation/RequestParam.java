package com.github.heartbridge.fs.annotation;

import java.lang.annotation.*;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestParam {
    String DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n";

    String value();

    String defaultValue() default DEFAULT_NONE;
}
