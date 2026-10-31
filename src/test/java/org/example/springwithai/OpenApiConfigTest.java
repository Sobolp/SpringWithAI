package org.example.springwithai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void infoUsesVersionFromBuildProperties() {
        Properties entries = new Properties();
        entries.setProperty("version", "1.2.3");

        Info info = config.springWithAiOpenApi(providerOf(new BuildProperties(entries))).getInfo();

        assertThat(info.getTitle()).isEqualTo("SpringWithAI API");
        assertThat(info.getVersion()).isEqualTo("1.2.3");
    }

    @Test
    void infoFallsBackToUnknownVersionWithoutBuildProperties() {
        Info info = config.springWithAiOpenApi(emptyProvider()).getInfo();

        assertThat(info.getTitle()).isEqualTo("SpringWithAI API");
        assertThat(info.getVersion()).isEqualTo(OpenApiConfig.UNKNOWN_VERSION);
    }

    @Test
    void infoFallsBackToUnknownVersionWhenBuildInfoHasNoVersion() {
        Info info = config.springWithAiOpenApi(providerOf(new BuildProperties(new Properties()))).getInfo();

        assertThat(info.getVersion()).isEqualTo(OpenApiConfig.UNKNOWN_VERSION);
    }

    @Test
    void infoFallsBackToUnknownVersionWhenBuildInfoVersionIsBlank() {
        Properties entries = new Properties();
        entries.setProperty("version", " ");

        Info info = config.springWithAiOpenApi(providerOf(new BuildProperties(entries))).getInfo();

        assertThat(info.getVersion()).isEqualTo(OpenApiConfig.UNKNOWN_VERSION);
    }

    private static ObjectProvider<BuildProperties> providerOf(BuildProperties buildProperties) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("buildProperties", buildProperties);
        return beanFactory.getBeanProvider(BuildProperties.class);
    }

    private static ObjectProvider<BuildProperties> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(BuildProperties.class);
    }
}
