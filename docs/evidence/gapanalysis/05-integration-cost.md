# Evidence 05 — additive integration cost across three steps

Captured: 2026-08-04T03:46:43Z · Repository: 1938198

The RQ2 measurement. Steps 3 and 4 demonstrated **consumer isolation**: a complete bounded
context added with an empty diff under the module it references. Step 5 demonstrates
something harder — **additive integration cost**: what it takes for an existing producer to
serve a genuinely new consumer. This is not a regression from zero-touch; it is the
question zero-touch could not answer (ADR-022).

## Steps 3 and 4: any change under competency/src/main?
```
$ git diff --stat 6c847ca..905b3b7 -- egas/src/main/java/ie/ul/egas/competency
(no output above = no changes across two consecutive steps)
```

## Step 5: what it cost Competency Modelling
```
$ git diff --numstat 905b3b7..HEAD --diff-filter=M -- egas/src/main/java/ie/ul/egas/competency
25	0	egas/src/main/java/ie/ul/egas/competency/api/CompetencyId.java
17	1	egas/src/main/java/ie/ul/egas/competency/application/CompetencyFrameworkService.java
13	4	egas/src/main/java/ie/ul/egas/competency/infrastructure/web/FrameworkWebMapper.java
7	1	egas/src/main/java/ie/ul/egas/competency/infrastructure/web/dto/FrameworkDetailResponse.java
```

## Step 5: what it cost Learner Profiling
```
$ git diff --numstat 905b3b7..HEAD --diff-filter=M -- egas/src/main/java/ie/ul/egas/learner
10	0	egas/src/main/java/ie/ul/egas/learner/infrastructure/persistence/LearnerProfileSpringDataRepository.java
```

## Deletions or renames in either producer?
```
$ git diff --numstat 905b3b7..HEAD --diff-filter=DR -- <competency> <learner>
(no output above = none)
```

## Module dependency declaration, unchanged by Step 5
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Gap Analysis",
        allowedDependencies = {"competency :: api", "learner :: api"})
```

The DAG gained **no edge**: both dependencies were declared before Step 5 and were
exercised for the first time rather than newly permitted. Full analysis — including which
deleted line was a signature change, and why a REST DTO component is not a
published-contract break — is in `docs/reviews/step5-completion-review.md`.
