# Extract

## Contents

1. [Overview](#overview)
2. [Extraction method: SLME](#syntactic-locality-module-extractor-slme)
3. [Extraction method: MIREOT](#mireot)
4. [Handling Imports (`--imports`)](#handling-imports)
5. [Extracting Ontology Annotations (`--copy-ontology-annotations`)](#extracting-ontology-annotations)
6. [Adding Source Annotations (`--annotate-with-source`)](#adding-source-annotations)
7. [Import Configuration File (`--config`)](#import-configuration-file)

## Overview

The reuse of ontology terms creates links between data, making the ontology and the data more valuable. But often you want to reuse just a subset of terms from a target ontology, not the whole thing. Here we take the filtered ontology from the previous step and extract a "STAR" module for the term 'adrenal cortex' and its supporting terms:

    robot extract --method STAR \
        --input filtered.owl \
        --term-file uberon_module.txt \
        --output results/uberon_module.owl

See <a href="/examples/uberon_module.txt" target="_blank">`uberon_module.txt`</a> for an example of a term file. Terms should be listed line by line, and comments can be included with `#`. Individual terms can be specified with `--term` followed by the CURIE.

The `--method` options fall into two groups: Syntactic Locality Module Extractor (SLME) and Minimum Information to Reference an External Ontology Term (MIREOT).

- STAR: use the SLME to extract a fixpoint-nested module
- TOP: use the SLME to extract a top module
- BOT: use the SLME to extract a bottom module
- MIREOT: extract a simple hierarchy of terms

## Syntactic Locality Module Extractor (SLME)

Each SLME module type takes a "seed" that you specify with `--term` and `--term-file` options. From the seed it builds a module with a "signature" that includes the seed plus any other terms required so that any logical entailments are preserved between entities (classes, properties and individuals) in the signature. For example, if an ontology implies that A is a subclass of B, and the seed contains A and B, then the module will also imply that A is a subclass of B. In other words, the module will contain all the axioms needed to provide the same entailments for the seed terms (and resulting signature) as the full ontology would.

- BOT: The BOT, or BOTTOM, -module contains mainly the terms in the seed, plus all their super-classes and the inter-relations between them. The module is called BOT (or BOTTOM) because it takes a view from the BOTTOM of the class-hierarchy upwards. Modules of this type are typically of a medium size and should be used if there is a need to include all super-classes in the module. This is the most widely used module type - when in doubt, use this one.

- TOP: The TOP-module contains mainly the terms in the seed, plus all their sub-classes and the inter-relations between them. The module is called TOP because it takes a view from the TOP of the class-hierarchy downwards. Modules of this type are typically large and should only be used if there is a need to include all sub-classes in the module.

- STAR: The STAR-module contains mainly the terms in the seed and the inter-relations between them (not necessarily sub- and super-classes). Modules of this type are typically very small and should be used if the module needs to be of minimal size containing only (or mostly) the classes in the seed file.

For more details see:

- [OWL Modularity](http://owl.cs.manchester.ac.uk/research/modularity/)
- [SLME source code](http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/SyntacticLocalityModuleExtractor.html)
- [ModuleType source code](http://owlcs.github.io/owlapi/apidocs_4/uk/ac/manchester/cs/owlapi/modularity/ModuleType.html)

ROBOT expects any `--term` or IRI in the `--term-file` to exist in the input ontology. If none of the input terms exist, the command will fail with an [empty terms error](errors#empty-terms-error). This can be overridden by including `--force true`. 

### Instances

__Important note for ontologies that include individuals:__ When using the SLME method of extraction, all individuals (ABox axioms) and their class types (the TBox axioms they depend on) are included by default. The `extract` command provides an `--individuals` option to specify what (if any) individuals are included in the output ontology:
* `--individuals include`: all individuals in the input ontology and their class types (default)
* `--individuals minimal`: only the individuals that are a type of a class in the extracted module
* `--individuals definitions`: only the individuals that are used in logical definitions of classes
* `--individuals exclude`: no individuals

## MIREOT

The MIREOT method preserves the hierarchy of the input ontology (subclass and subproperty relationships), but does not try to preserve the full set of logical entailments. Both "upper" (ancestor) and "lower" (descendant) limits can be specified, like this:

    robot extract --method MIREOT \
        --input uberon_fragment.owl \
        --upper-term "obo:UBERON_0000465" \
        --lower-term "obo:UBERON_0001017" \
        --lower-term "obo:UBERON_0002369" \
        --output results/uberon_mireot.owl

To specify upper and lower term files, use `--upper-terms` and `--lower-terms`. The upper terms are the upper boundaries of what will be extracted. If no upper term is specified, all terms up to the root (`owl:Thing`) will be returned. The lower term (or terms) is required; this is the limit to what will be extracted, e.g. no descendants of the lower term will be included in the result.

To only include all descendants of a term or set of terms, use `--branch-from-term` or `--branch-from-terms`, respectively. `--lower-term` or `--lower-terms` are not required when using this option.

For more details see the [MIREOT paper](http://dx.doi.org/10.3233/AO-2011-0087).

### Intermediates

When extracting (especially with MIREOT), sometimes the hierarchy can have too many intermediate classes, making it difficult to identify relevant relationships. For example, you may end up with this after extracting `adrenal gland`:
```
- material anatomical entity
  - anatomical structure
    - multicellular anatomical structure
      - organ
        - abdomen element
          - adrenal/interrenal gland
            - adrenal gland (*)
    - lateral structure
      - adrenal gland (*)
```
By specifying how to handle these intermediates, you can reduce unnecessary intermediate classes:
* `--intermediates all`: default behavior, do not prune the ontology
* `--intermediates minimal`: only include intermediate intermediates with more than one *sibling* (i.e. the parent class has another child)
* `--intermediates none`: do not include any intermediates
  * For MIREOT, this will only include top and bottom level classes.
  * For any SLME method, this will only include the classes directly used in the logic of the input terms

Running this command to extract, inclusively, between 'material anatomical entity' and 'adrenal gland':

    robot extract --method MIREOT \
        --input uberon_fragment.owl \
        --upper-term UBERON:0000465 \
        --lower-term UBERON:0002369 \
        --intermediates minimal \
        --output results/uberon_minimal.owl

Would result in the following structure:
```
- material anatomical entity
  - anatomical structure
    - adrenal gland (*)
    - organ
      - adrenal gland (*)
```

You can chain this output into reduce to further clean up the structure, as some redundant axioms may appear.

Running the same command, but with `--intermediates none`:

    robot extract --method MIREOT \
        --input uberon_fragment.owl \
        --upper-term UBERON:0000465 \
        --lower-term UBERON:0002369 \
        --intermediates none \
        --output results/uberon_none.owl

Would result in:
```
- material anatomical entity
  - adrenal gland
```

Any term specified as an input term will not be pruned.

## Handling Imports

By default, `extract` will include imported ontologies. To exclude imported ontologies, just add `--imports exclude` for any non-MIREOT extraction method:

    robot extract --method BOT \
      --catalog catalog.xml \
      --input imports-nucleus.owl \
      --term GO:0005739 \
      --imports exclude \
      --output results/mitochondrion.owl

This only includes what is asserted in `imports-nucleus.owl`, which imports `nucleus.owl`. `imports-nucleus.owl` only includes the term 'mitochondrion' (`GO:0005739`) and links it to its parent class, 'intracellular membrane-bounded organelle' (`GO:0043231`). `nucleus.owl` contains the full hierarchy down to 'intracellular membrane-bounded organelle'. The output module, `mitochondrion.owl`, only includes the term 'mitochondrion' and this subClassOf statement.

By contrast, including imports returns the full hierarchy down to 'mitochondrion', which is asserted in `nucleus.owl`:

    robot extract --method BOT \
      --catalog catalog.xml \
      --input imports-nucleus.owl \
      --term GO:0005739 \
      --imports include \
      --output results/mitochondrion-full.owl

## Extracting Ontology Annotations

You can also include ontology annotations from the input ontology with `--copy-ontology-annotations true`. By default, this is false.

    robot extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --copy-ontology-annotations true \
      --output results/annotated_module.owl
      
## Adding Source Annotations

`extract` provides an option to annotate extracted terms with `rdfs:isDefinedBy`. If the term already has an annotation using this property, the existing annotation will be copied and no new annotation will be added.

    robot extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --annotate-with-source true \
      --output results/annotated_source.owl 

The object of the property is, by default, the base name of the term's IRI. For example, the IRI for `GO:0000001` (`http://purl.obolibrary.org/obo/GO_0000001`) would receive the source `http://purl.obolibrary.org/obo/go.owl`. 

Sometimes classes are adopted by other ontologies, but retain their original IRI. In this case, you can provide the path to a [term-to-source mapping file](/examples/source-map.tsv) as CSV or TSV.

    robot --prefix 'GO: http://purl.obolibrary.org/obo/GO_' \
      extract --method BOT \
      --input annotated.owl \
      --term UBERON:0000916 \
      --annotate-with-source true \
      --sources source-map.tsv \
      --output results/changed_source.owl

The mapping file can either use full IRIs:

```
http://purl.obolibrary.org/obo/BFO_0000001,http://purl.obolibrary.org/obo/ro.owl
```

Or prefixes, as long as the [prefix is valid](/global#prefixes):

```
BFO:0000001,RO
```

## Import Configuration File

Instead of specifying each option on the command line, ROBOT offers a `--config <file>` option in which you can specify a text configuration file. The basic format is:
```
! option 1
arg

! option 2
arg 1
arg 2
...
```

The following command-line options are supported:
* `! input` - expects one line (path to the input ontology)
* `! input-iri` - expects one line (IRI to the input ontology)
* `! output-iri` - expects one line (IRI to save the output ontology with)
* `! method` - expects one line (a valid [extraction method](#overview))
* `! intermediates` - expects one line (a valid [intermediates option](#intermediates))
* `! annotate-with-source` - expects one line (`true` or `false`, defaults to `true`)
* `! imports` - expects one line (how to [handle input imports](#handling-imports), defaults to `include`)
* `! lower-terms` - expects one or more lines (lower terms for [MIREOT](#mireot))
* `! upper-terms` - expects one or more lines (upper terms for [MIREOT](#mireot))
* `! terms` - expects one or more lines (terms for [SLME](#syntactic-locality-module-extractor-slme))

Either an `! input` or `! input-iri` is required. The `! method` is required. If using MIREOT, `! lower-terms` is a required option. For SLME, `! terms` is a required option.

When using the `--config` option, an `--output` must be specified:
```
robot extract --config my-config.txt --output my-new-ontology.owl
```

#### Using Labels

All labels are loaded from the `! input` or `! input-iri` ontology and can be used in the terms instead of CURIEs or IRIs. If you prefer, you can always reference a term by CURIE or IRI instead.

The config file expects that no term label will have a tab character in it. This is recommended practice, but if for some reason you have a label with a tab character, be sure to surround it with single quotes.

We have included a `! target` option to specify the target ontology. No terms are extracted from the target ontology, but it is loaded so that you can use labels when specifying replacement parents and annotation properties.

```
! target
path/to/my-ontology.owl
```

#### Annotations

The config file also supports an `! annotations` option to specify how to handle annotations.

The format for each argument is (with `\t` representing a tab):
```
<original label or ID> \t <keyword> \t <copy/replace label or ID>
```

The first `<label or ID>` is required. This is the annotation property to include when extracting. The `<keyword>` and second `<label or ID>` are optional. These are special operations to determine what to do with the annotation after extraction.

The keywords are:
* `copyTo` - the original annotation is kept in this case and the annotation value is copied to the given annotation property on the same subject.
* `mapTo` - the original annotation property is replaced with the new annotation property.

#### Annotating With Source Ontology

If `! annotate-with-source` is not included, this defaults to `true`.

If `annotate-with-source` is `true`, each extracted term will be annotated with `rdfs:isDefinedBy <source-ontology-iri>`. If the source ontology does not have an IRI, this annotation is not added.

#### Terms and Parents

The default parents can be replaced by specifying new parents for any of the arguments in `terms`, `lower-terms`, or `upper-terms`. The format is:

```
<label or ID> \t <new parent label or ID>
```

For all term options, the first `<label or ID>` is required. This specifies which term to extract from the input ontology. The `<new parent label or ID>` is optional. You can specify as many new parents as you'd like, separating each with a tab on the same line.


#### Example

Here is an example config file to extract a set of terms from UO for OBI. These terms are specified to be children of the term 'measurement unit label', which is loaded from the `! target` ontology.

The annotations included from the input ontology are 'label' and 'definition'. The labels are copied to the 'editor preferred term'.

```
! input-iri
http://purl.obolibrary.org/obo/uo.owl

! target
src/ontology/obi.owl

! output-iri
http://purl.obolibrary.org/obo/obi/uo_import.owl

! method
MIREOT

! annotations
label       copyTo	editor preferred term
definition

! intermediates
none

! lower-terms
concentration unit	measurement unit label
frequency unit		measurement unit label
length unit		measurement unit label
```

---

## Error Messages

### Missing MIREOT Term(s) Error

MIREOT requires either `--lower-term` or `--branch-from-term` to proceed. `--upper-term` is optional.

### Missing Lower Term(s) Error

If an `--upper-term` is specified for MIREOT, `--lower-term` (or terms) must also be specified.

### Invalid Method Error

The `--method` option only accepts: MIREOT, STAR, TOP, and BOT.

### Invalid Option Error

The following flags *should not* be used with STAR, TOP, or BOT methods:
* `--upper-term` & `--upper-terms`
* `--lower-term` & `--lower-terms`
* `--branch-from-term` & `--branch-from-terms`

### Invalid Source Map Error

The input for `--sources` must be either CSV or TSV format.

### Unknown Individuals Error

`--individuals` must be one of: `include`, `minimal`, `definitions`, or `exclude`.

### Unknown Intermediates Error

 `--intermediates` must be one of: `all`, `minimal`, or `none`.
