package github.kaloyanov5.merkantil.ratelimit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimited;
import github.kaloyanov5.merkantil.common.ratelimit.annotation.RateLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitedKeyTest {

    private static final Pattern SPEL_VARIABLE = Pattern.compile("#([A-Za-z_$][A-Za-z0-9_$]*)");

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("github.kaloyanov5.merkantil");

    @Test
    @DisplayName("the scan finds the annotated methods it is meant to guard")
    void scanIsNotVacuous() {
        assertThat(annotatedMethods())
                .describedAs("methods annotated with @RateLimited in src/main")
                .isNotEmpty();
    }

    @Test
    @DisplayName("parameter names survive compilation, without which SpEL cannot resolve a #name key")
    void parameterNamesAreCompiledIn() {
        List<String> problems = new ArrayList<>();

        for (Method method : annotatedMethods()) {
            for (Parameter parameter : method.getParameters()) {
                if (!parameter.isNamePresent()) {
                    problems.add("%s.%s is compiled without -parameters"
                            .formatted(method.getDeclaringClass().getSimpleName(), method.getName()));
                    break;
                }
            }
        }

        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("every @RateLimited key references a real parameter of the method it annotates")
    void keysReferenceRealParameters() {
        List<String> problems = new ArrayList<>();

        for (Method method : annotatedMethods()) {
            Set<String> parameterNames = new LinkedHashSet<>();
            for (Parameter parameter : method.getParameters()) {
                parameterNames.add(parameter.getName());
            }

            for (RateLimited rateLimited : method.getAnnotationsByType(RateLimited.class)) {
                Matcher matcher = SPEL_VARIABLE.matcher(rateLimited.key());
                while (matcher.find()) {
                    String variable = matcher.group(1);
                    if (!parameterNames.contains(variable)) {
                        problems.add("%s.%s: key \"%s\" references #%s, but the parameters are %s"
                                .formatted(
                                        method.getDeclaringClass().getSimpleName(),
                                        method.getName(),
                                        rateLimited.key(),
                                        variable,
                                        parameterNames));
                    }
                }
            }
        }

        assertThat(problems)
                .describedAs("a key naming a parameter that does not exist resolves to null and silently disables the limit")
                .isEmpty();
    }

    @Test
    @DisplayName("bucket names are unique, so two limits cannot share a counter by accident")
    void bucketsAreUnique() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (Method method : annotatedMethods()) {
            for (RateLimited rateLimited : method.getAnnotationsByType(RateLimited.class)) {
                if (!seen.add(rateLimited.bucket())) {
                    duplicates.add(rateLimited.bucket());
                }
            }
        }

        assertThat(duplicates).isEmpty();
    }

    private static List<Method> annotatedMethods() {
        List<Method> methods = new ArrayList<>();
        for (JavaClass javaClass : PRODUCTION_CLASSES) {
            for (JavaMethod javaMethod : javaClass.getMethods()) {
                if (javaMethod.isAnnotatedWith(RateLimited.class) || javaMethod.isAnnotatedWith(RateLimits.class)) {
                    methods.add(javaMethod.reflect());
                }
            }
        }
        return methods;
    }
}
