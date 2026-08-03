package ie.ul.egas.competency.domain.metamodel;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;

/**
 * The EGAS competency metamodel v1 (M2) and its typed access façade.
 *
 * <p><b>ADR-003</b>: while the metamodel churns (weeks 2–3) it is defined as a <em>dynamic</em>
 * Ecore package built programmatically here — no generated code, no Eclipse tooling at runtime.
 * At the freeze point this class is superseded by a one-off genmodel run; until then it is the
 * single source of truth, and {@code MetamodelEvidenceTests} emits the citable {@code .ecore}
 * artifact from it on every build.
 *
 * <p><b>Why a façade</b>: dynamic EMF's natural API is stringly typed
 * ({@code eGet(eClass.getEStructuralFeature("name"))}), which scatters magic strings and defeats
 * refactoring. Centralising every {@link EClass}/{@link EAttribute}/{@link EReference} behind
 * typed accessors confines that fragility to one construction site (GRASP: Information Expert)
 * and gives assembler, validator and mappers a compile-checked vocabulary.
 *
 * <p><b>Thread safety</b>: the instance is built once, never mutated afterwards (EMF offers no
 * freeze for dynamic packages — immutability is by discipline, documented here), and is safe for
 * concurrent read access. Per-operation {@code ResourceSet}s are the adapters' concern.
 *
 * <p><b>Metamodel shape (v1)</b>:
 * <pre>
 * CompetencyFramework(name[1], version[1], description[0..1], source[1]:FrameworkSourceKind)
 *   ⊃ levels : ProficiencyLevel[0..*]   (frameworks without proficiency scales stay representable — RQ1)
 *   ⊃ areas  : CompetencyArea[1..*]
 * ProficiencyLevel(code[1], name[0..1], ordinal[1])
 * CompetencyArea(code[1], name[1], description[0..1]) ⊃ competencies : Competency[1..*]
 * Competency(code[1], name[1], description[0..1])
 *   → prerequisites : Competency[0..*]          (cross-reference — the pathway graph)
 *   ⊃ levelDescriptors : LevelDescriptor[0..*]
 * LevelDescriptor(descriptor[0..1]) → level : ProficiencyLevel[1]
 * </pre>
 */
public final class CompetencyMetamodel {

    public static final String NS_URI = "http://egas.ul.ie/metamodel/competency/1.0";
    public static final String NS_PREFIX = "comp";

    private static final CompetencyMetamodel INSTANCE = new CompetencyMetamodel();

    private final EPackage ePackage;

    private final EEnum frameworkSourceKind;
    private final EClass framework;
    private final EAttribute frameworkName;
    private final EAttribute frameworkVersion;
    private final EAttribute frameworkDescription;
    private final EAttribute frameworkSource;
    private final EReference frameworkLevels;
    private final EReference frameworkAreas;

    private final EClass proficiencyLevel;
    private final EAttribute levelCode;
    private final EAttribute levelName;
    private final EAttribute levelOrdinal;

    private final EClass competencyArea;
    private final EAttribute areaCode;
    private final EAttribute areaName;
    private final EAttribute areaDescription;
    private final EReference areaCompetencies;

    private final EClass competency;
    private final EAttribute competencyCode;
    private final EAttribute competencyName;
    private final EAttribute competencyDescription;
    private final EReference competencyPrerequisites;
    private final EReference competencyLevelDescriptors;

    private final EClass levelDescriptor;
    private final EReference levelDescriptorLevel;
    private final EAttribute levelDescriptorText;

    private CompetencyMetamodel() {
        EcoreFactory f = EcoreFactory.eINSTANCE;

        ePackage = f.createEPackage();
        ePackage.setName("competency");
        ePackage.setNsPrefix(NS_PREFIX);
        ePackage.setNsURI(NS_URI);

        frameworkSourceKind = f.createEEnum();
        frameworkSourceKind.setName("FrameworkSourceKind");
        addLiteral(frameworkSourceKind, "BESPOKE", 0);
        addLiteral(frameworkSourceKind, "SFIA", 1);
        addLiteral(frameworkSourceKind, "ESCO", 2);
        addLiteral(frameworkSourceKind, "OTHER", 3);
        ePackage.getEClassifiers().add(frameworkSourceKind);

        framework = newClass("CompetencyFramework");
        proficiencyLevel = newClass("ProficiencyLevel");
        competencyArea = newClass("CompetencyArea");
        competency = newClass("Competency");
        levelDescriptor = newClass("LevelDescriptor");

        frameworkName = attr(framework, "name", EcorePackage.Literals.ESTRING, true);
        frameworkVersion = attr(framework, "version", EcorePackage.Literals.ESTRING, true);
        frameworkDescription = attr(framework, "description", EcorePackage.Literals.ESTRING, false);
        frameworkSource = attr(framework, "source", frameworkSourceKind, true);
        frameworkLevels = containment(framework, "levels", proficiencyLevel, 0);
        frameworkAreas = containment(framework, "areas", competencyArea, 1);

        levelCode = attr(proficiencyLevel, "code", EcorePackage.Literals.ESTRING, true);
        levelName = attr(proficiencyLevel, "name", EcorePackage.Literals.ESTRING, false);
        levelOrdinal = attr(proficiencyLevel, "ordinal", EcorePackage.Literals.EINT, true);

        areaCode = attr(competencyArea, "code", EcorePackage.Literals.ESTRING, true);
        areaName = attr(competencyArea, "name", EcorePackage.Literals.ESTRING, true);
        areaDescription = attr(competencyArea, "description", EcorePackage.Literals.ESTRING, false);
        areaCompetencies = containment(competencyArea, "competencies", competency, 1);

        competencyCode = attr(competency, "code", EcorePackage.Literals.ESTRING, true);
        competencyName = attr(competency, "name", EcorePackage.Literals.ESTRING, true);
        competencyDescription = attr(competency, "description", EcorePackage.Literals.ESTRING, false);
        competencyPrerequisites = reference(competency, "prerequisites", competency);
        competencyLevelDescriptors = containment(competency, "levelDescriptors", levelDescriptor, 0);

        levelDescriptorLevel = singleReference(levelDescriptor, "level", proficiencyLevel);
        levelDescriptorText = attr(levelDescriptor, "descriptor", EcorePackage.Literals.ESTRING, false);

        // A dynamic EPackage must be contained in a Resource whose URI is the nsURI:
        // otherwise EcoreUtil.getURI(eClass) yields a fragment-only URI and emfjson's
        // eClass discriminators cannot be resolved on load.
        new ResourceImpl(URI.createURI(NS_URI)).getContents().add(ePackage);
    }

    public static CompetencyMetamodel instance() {
        return INSTANCE;
    }

    /** Creates an instance of the given metamodel class via the package's dynamic factory. */
    public EObject create(EClass eClass) {
        return ePackage.getEFactoryInstance().create(eClass);
    }

    /** Resolves a {@code FrameworkSourceKind} literal by name; the caller guarantees validity. */
    public EEnumLiteral sourceLiteral(String name) {
        EEnumLiteral literal = frameworkSourceKind.getEEnumLiteral(name);
        if (literal == null) {
            throw new IllegalArgumentException("Unknown FrameworkSourceKind literal: " + name);
        }
        return literal;
    }

    public EPackage ePackage() { return ePackage; }
    public EEnum frameworkSourceKind() { return frameworkSourceKind; }

    public EClass framework() { return framework; }
    public EAttribute frameworkName() { return frameworkName; }
    public EAttribute frameworkVersion() { return frameworkVersion; }
    public EAttribute frameworkDescription() { return frameworkDescription; }
    public EAttribute frameworkSource() { return frameworkSource; }
    public EReference frameworkLevels() { return frameworkLevels; }
    public EReference frameworkAreas() { return frameworkAreas; }

    public EClass proficiencyLevel() { return proficiencyLevel; }
    public EAttribute levelCode() { return levelCode; }
    public EAttribute levelName() { return levelName; }
    public EAttribute levelOrdinal() { return levelOrdinal; }

    public EClass competencyArea() { return competencyArea; }
    public EAttribute areaCode() { return areaCode; }
    public EAttribute areaName() { return areaName; }
    public EAttribute areaDescription() { return areaDescription; }
    public EReference areaCompetencies() { return areaCompetencies; }

    public EClass competency() { return competency; }
    public EAttribute competencyCode() { return competencyCode; }
    public EAttribute competencyName() { return competencyName; }
    public EAttribute competencyDescription() { return competencyDescription; }
    public EReference competencyPrerequisites() { return competencyPrerequisites; }
    public EReference competencyLevelDescriptors() { return competencyLevelDescriptors; }

    public EClass levelDescriptor() { return levelDescriptor; }
    public EReference levelDescriptorLevel() { return levelDescriptorLevel; }
    public EAttribute levelDescriptorText() { return levelDescriptorText; }

    private EClass newClass(String name) {
        EClass c = EcoreFactory.eINSTANCE.createEClass();
        c.setName(name);
        ePackage.getEClassifiers().add(c);
        return c;
    }

    private static EAttribute attr(EClass owner, String name, EClassifier type, boolean required) {
        EAttribute a = EcoreFactory.eINSTANCE.createEAttribute();
        a.setName(name);
        a.setEType(type);
        a.setLowerBound(required ? 1 : 0);
        a.setUpperBound(1);
        owner.getEStructuralFeatures().add(a);
        return a;
    }

    private static EReference containment(EClass owner, String name, EClass type, int lowerBound) {
        EReference r = EcoreFactory.eINSTANCE.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setContainment(true);
        r.setLowerBound(lowerBound);
        r.setUpperBound(EReference.UNBOUNDED_MULTIPLICITY);
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    private static EReference reference(EClass owner, String name, EClass type) {
        EReference r = EcoreFactory.eINSTANCE.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setContainment(false);
        r.setLowerBound(0);
        r.setUpperBound(EReference.UNBOUNDED_MULTIPLICITY);
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    private static EReference singleReference(EClass owner, String name, EClass type) {
        EReference r = EcoreFactory.eINSTANCE.createEReference();
        r.setName(name);
        r.setEType(type);
        r.setContainment(false);
        r.setLowerBound(1);
        r.setUpperBound(1);
        owner.getEStructuralFeatures().add(r);
        return r;
    }

    private static void addLiteral(EEnum eEnum, String name, int value) {
        EEnumLiteral literal = EcoreFactory.eINSTANCE.createEEnumLiteral();
        literal.setName(name);
        literal.setValue(value);
        eEnum.getELiterals().add(literal);
    }
}
