package au.org.ala.collectory.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Provides static access to the Spring ApplicationContext.
 * <p>
 * This is used by non-Spring-managed objects (such as {@code DataSourceAdapter}
 * instances created via reflection) to look up Spring beans.
 */
@Component
public class ApplicationContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static ApplicationContext getContext() {
        return context;
    }

    public static <T> T getBean(Class<T> clazz) {
        return context != null ? context.getBean(clazz) : null;
    }
}
