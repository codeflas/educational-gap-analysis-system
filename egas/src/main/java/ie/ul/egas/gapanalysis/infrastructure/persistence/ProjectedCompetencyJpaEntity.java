package ie.ul.egas.gapanalysis.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.BatchSize;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence mapping for a projected competency — adapter-private, never a domain object.
 *
 * <p>{@code frameworkId} is a plain column rather than a {@code @ManyToOne}: the projection is read
 * as flat rows, and an association would buy object navigation this adapter never uses while
 * costing a fetch strategy to get wrong. The foreign key still exists in the schema, which is what
 * makes replacing a framework's projection a single cascading delete.
 *
 * <p>{@code definedLevelCodes} carries {@code @BatchSize} so that loading many competencies issues
 * one additional query for their level codes rather than one per competency. Fetch-joining it
 * alongside the framework's own level collection would be the other option and a worse one — two
 * collection joins produce a cartesian product, and Hibernate refuses it outright when both are
 * bags. The query count is asserted rather than assumed.
 */
@Entity
@Table(name = "projected_competency", schema = "gap_analysis",
        uniqueConstraints = @UniqueConstraint(name = "uq_projected_competency_framework_code",
                columnNames = {"framework_id", "code"}))
class ProjectedCompetencyJpaEntity {

    @Id
    @Column(name = "competency_id")
    private UUID competencyId;

    @Column(name = "framework_id", nullable = false)
    private UUID frameworkId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "area_code", nullable = false, length = 50)
    private String areaCode;

    @ElementCollection
    @CollectionTable(name = "projected_competency_level", schema = "gap_analysis",
            joinColumns = @JoinColumn(name = "competency_id"))
    @Column(name = "level_code", nullable = false, length = 50)
    @BatchSize(size = 200)
    private Set<String> definedLevelCodes = new LinkedHashSet<>();

    protected ProjectedCompetencyJpaEntity() {
        // JPA
    }

    ProjectedCompetencyJpaEntity(UUID competencyId, UUID frameworkId, String code, String name,
                                 String areaCode) {
        this.competencyId = competencyId;
        this.frameworkId = frameworkId;
        this.code = code;
        this.name = name;
        this.areaCode = areaCode;
    }

    void addDefinedLevelCode(String levelCode) {
        definedLevelCodes.add(levelCode);
    }

    UUID getCompetencyId() { return competencyId; }
    UUID getFrameworkId() { return frameworkId; }
    String getCode() { return code; }
    String getName() { return name; }
    String getAreaCode() { return areaCode; }
    Set<String> getDefinedLevelCodes() { return definedLevelCodes; }
}
