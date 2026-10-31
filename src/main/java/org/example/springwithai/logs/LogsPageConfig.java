package org.example.springwithai.logs;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the live log page at {@code /logs}: a static page that tails {@code /actuator/logfile}. */
@Configuration(proxyBeanMethods = false)
class LogsPageConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController(LogPaths.PAGE).setViewName("forward:" + LogPaths.PAGE_FILE);
    }
}
