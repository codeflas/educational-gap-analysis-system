# ADR-012: EMF as the domain formalism of Competency Modelling — and nowhere else

Status: Accepted
Date: 2026-08-02

## Problem
ADR-002/003 make Ecore the metamodelling technology. The hexagonal purity rule says the domain
ring is framework-free. Is org.eclipse.emf a "framework" (belongs in adapters) or the domain's
own formalism (belongs in the core)? Mapping every EObject into a parallel POJO graph at the
domain boundary would answer "framework" — at the cost of duplicating the entire metamodel in
code and forfeiting models-at-runtime, the point of RQ2.

## Alternatives
1. EMF everywhere it is convenient — erodes boundaries; every module grows an EMF dependency.
2. EMF only in infrastructure, POJO domain — metamodel duplicated as hand-written classes;
   dynamic extensibility lost; the MDE claim becomes decorative.
3. EMF admitted into the Competency Modelling context as its domain formalism; banned elsewhere;
   serialisation machinery (XMI, emfjson) confined to infrastructure even within the module.

## Decision
Option 3. Within ie.ul.egas.competency, org.eclipse.emf.ecore/common are legitimate domain
vocabulary: the M1 EObject graph IS domain state, conformance validation IS domain logic.
Outside the module, any EMF dependency is an architecture violation. EObjects never cross the
module boundary — other contexts consume identifiers, DTOs and events. Enforced by two ArchUnit
fitness functions (emfConfinedToCompetencyModule, emfSerializationStaysOutOfDomain), not by
convention.

## Consequences
Models-at-runtime is real: metamodel evolution does not ripple as parallel POJO maintenance.
The rest of the system is EMF-illiterate by construction, so the W10 second-framework case study
exercises modelling, not plumbing.

## Trade-offs
The competency domain is testable without Spring but not without EMF (accepted: EMF is plain
JARs, no container). Dynamic EMF's stringly API is mitigated by the typed metamodel facade.
Deep immutability of exposed EObject graphs is by documented contract, not type system.

## Quality attributes affected
Modifiability/extensibility (+, RQ2), conceptual integrity (+), testability (neutral),
encapsulation (managed via facade + ArchUnit).

## Future evolution
Post-freeze genmodel (ADR-003) replaces the dynamic facade with generated typed APIs; the
confinement rules are unaffected. Read-only EMF adapters are a hardening option.
