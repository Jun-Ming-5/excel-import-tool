package com.jun.ming.excel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Excel {

	/**
	 * the name of the column
	 */
    String name() default "";

    /**
     * a json value
     * key: the cell value
     * value: the mapped value
     */
    String kv() default "";

    String descrip() default "";

    /**
     * Whether the header column is nullable.
     * false: able to be null
     * true: disable to be null
     */
    boolean required() default false;
    
    /**
     * Whether the header column is a unique key
     */
    boolean unique() default false;

    int precision() default 0;

    int scale() default 0;
}
