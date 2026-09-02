package github.kaloyanov5.merkantil.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

import java.util.Map;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
        packages = "github.kaloyanov5.merkantil",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureTest {

    private static final String ROOT = "github.kaloyanov5.merkantil";

    private static final Map<String, String> GROUP_BY_MODULE = Map.of(
            "ledger", "LEDGER",
            "account", "LEDGER",
            "trading", "LEDGER",
            "portfolio", "LEDGER",
            "identity", "IDENTITY",
            "marketdata", "MARKETDATA",
            "analytics", "ANALYTICS",
            "notification", "NOTIFICATION"
    );

    private static final DescribedPredicate<JavaClass> OUTSIDE_COMMON =
            resideInAPackage(ROOT + "..").and(not(resideInAPackage("..merkantil.common..")));

    private static final SliceAssignment SERVICE_GROUPS = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (!packageName.startsWith(ROOT + ".")) {
                return SliceIdentifier.ignore();
            }
            String remainder = packageName.substring(ROOT.length() + 1);
            int dot = remainder.indexOf('.');
            String module = dot < 0 ? remainder : remainder.substring(0, dot);
            String group = GROUP_BY_MODULE.get(module);
            return group == null ? SliceIdentifier.ignore() : SliceIdentifier.of(group);
        }

        @Override
        public String getDescription() {
            return "service groups";
        }
    };

    @ArchTest
    static final ArchRule ledger_internals_stay_inside_ledger = noClasses()
            .that().resideOutsideOfPackage("..merkantil.ledger..")
            .should().dependOnClassesThat().resideInAPackage("..merkantil.ledger.internal..")
            .because("the ledger is reachable only through its public API");

    @ArchTest
    static final ArchRule common_does_not_depend_on_modules = noClasses()
            .that().resideInAPackage("..merkantil.common..")
            .should().dependOnClassesThat(OUTSIDE_COMMON)
            .because("a shared library that depends on modules makes every module redeploy together");

    @ArchTest
    static final ArchRule service_groups_are_free_of_cycles = FreezingArchRule.freeze(
            SlicesRuleDefinition.slices().assignedFrom(SERVICE_GROUPS)
                    .should().beFreeOfCycles());

    @ArchTest
    static final ArchRule cross_group_dependencies_are_declared = FreezingArchRule.freeze(
            SlicesRuleDefinition.slices().assignedFrom(SERVICE_GROUPS)
                    .should().notDependOnEachOther());
}
