package uk.gov.pay.connector.app;

import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;

/**
 * Keeps the tests' own configuration classes (annotated {@link TestConfiguration}), which sit in the service's
 * packages on the test class path, out of the service's component scanning: a test uses one only by naming it.
 */
class TestConfigurationExcludeFilter extends TypeExcludeFilter {

    @Override
    public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
        return metadataReader.getAnnotationMetadata().hasAnnotation(TestConfiguration.class.getName());
    }

    @Override
    public boolean equals(Object other) {
        return other != null && getClass() == other.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
