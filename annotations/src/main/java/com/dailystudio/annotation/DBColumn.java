package com.dailystudio.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface DBColumn {
    public String name() default "";
    public String allowNull() default "true";
    public String primary() default "false";
    public int version() default 1;

}
