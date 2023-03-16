package com.syrobin.cloud.webflux.webclient;

import org.springframework.cloud.context.named.NamedContextFactory;

import java.util.Arrays;

/**
 * @author syrobin
 * @version v1.0
 * @description:
 * @date 2022-08-25 16:22
 */
public class WebClientSpecification implements NamedContextFactory.Specification {

    private final String name;

    private final Class<?>[] configuration;

    public WebClientSpecification(String name, Class<?>[] configuration) {
        this.name = name;
        this.configuration = configuration;
    }

    @Override
    public String toString() {
        return "WebClientSpecification{" +
                "name='" + name + '\'' +
                ", configuration=" + Arrays.toString(configuration) +
                '}';
    }



    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<?>[] getConfiguration() {
        return configuration;
    }
}
