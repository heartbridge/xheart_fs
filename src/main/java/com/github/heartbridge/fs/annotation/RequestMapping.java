package com.github.heartbridge.fs.annotation;

import java.lang.annotation.*;

/**
 * @author GavinCook
 * @since 1.0.0
 **/
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequestMapping {

    String name() default "";

    String[] value() default {};

    RequestMethod[] method() default {};

}
