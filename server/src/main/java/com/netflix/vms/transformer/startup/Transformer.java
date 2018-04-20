package com.netflix.vms.transformer.startup;

import com.google.inject.Injector;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.config.MapConfig;
import com.netflix.archaius.guice.ApplicationOverride;
import com.netflix.archaius.guice.ArchaiusModule;
import com.netflix.governator.InjectorBuilder;
import com.netflix.governator.guice.jetty.JettyModule;
import com.netflix.governator.guice.servlet.WebApplicationInitializer;
import java.io.IOException;
import java.util.Properties;

/**
 * The "main" class that boots up the service. When it's deployed within a servlet container such
 * as Tomcat, only the createInjector() is called. For local testing one simply invokes the
 * main() method as if running a normal Java app.
 *
 * @author This file is auto-generated by runtime@netflix.com. Feel free to modify.
 */
public class Transformer implements WebApplicationInitializer {

    public static void main(String[] args) throws Exception {
        InjectorBuilder.fromModules(
                new TransformerModule(),
                new JettyModule(),
                new ArchaiusModule() {
                    @Override
                    protected void configureArchaius() {
                        Properties props = new Properties();
                        try {
                            props.load(this.getClass()
                                           .getClassLoader()
                                           .getResourceAsStream("laptop.properties"));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        bind(Config.class).annotatedWith(ApplicationOverride.class)
                                          .toInstance(new MapConfig(props));
                    }
                }).createInjector().awaitTermination();
    }

    @Override
    public Injector createInjector() {
        return InjectorBuilder.fromModules(new TransformerModule()).createInjector();
    }
}
