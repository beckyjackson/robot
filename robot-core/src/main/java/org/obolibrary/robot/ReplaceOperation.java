package org.obolibrary.robot;

import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.obolibrary.robot.checks.InvalidReferenceChecker;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.OWLEntityRenamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplaceOperation {

  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(RenameOperation.class);

  /** Namespace for error messages. */
  private static final String NS = "replace#";

  /** Error message when table row does not contain split character. */
  private static final String cellSplitError =
      NS + "CELL SPLIT ERROR line %d does not contain character to split on ('%s')";

  /** Error message when an IRI cannot be created from a string. */
  private static final String invalidIRIError =
      NS + "INVALID IRI ERROR could not create IRI for '%s' on line %d";

  /** Error message when a table row has more than 2 columns. */
  private static final String lineLengthError =
      NS + "LINE LENGTH ERROR line %d must contain exactly two cells (old IRI, new IRI)";

  /** Error message when mapping file is not CSV, TSV, or TXT. */
  private static final String mappingFileError =
      NS
          + "MAPPING FILE ERROR file '%s' type must be one of: "
          + "OWL ontology, comma-separated (CSV), tab-separated (TSV, TXT).";

  /** Error message when mappings file does not return any mappings. */
  private static final String missingMappingsError =
      NS + "MISSING MAPPINGS ERROR file '%s' does not contain any mappings";

  /** Set of accepted OWL ontology formats. */
  private static final Set<String> owlOntologyFormats =
      Stream.of("owl", "ofn", "owx", "json", "ttl", "omn", "obo").collect(Collectors.toSet());

  /**
   * Get the default replace options. "property": "IAO:0100001" "reverse-mappings": "false"
   * "allow-dangling": "false"
   *
   * @return map of default replace options
   */
  private static Map<String, String> getDefaultOptions() {
    Map<String, String> options = new HashMap<>();
    options.put("property", "IAO:0100001");
    options.put("reverse-mappings", "false");
    options.put("allow-dangling", "false");
    return options;
  }

  /**
   * Given an IOHelper, an ontology, and a mappings file, replace IRIs in the ontology based on the
   * mappings file using the default options.
   *
   * @param ioHelper IOHelper to parse CURIEs and IRIs
   * @param ontology OWLOntology to replace in
   * @param mappingsFile File containing mappings (tab/comma separated table or OWLOntology)
   * @throws Exception on any problem
   */
  public static void replace(IOHelper ioHelper, OWLOntology ontology, File mappingsFile)
      throws Exception {
    replace(ioHelper, ontology, mappingsFile, getDefaultOptions());
  }

  /**
   * Given an IOHelper, an ontology, a mappings file, and a set of options, replace IRIs in the
   * ontology based on the mappings file. Options: If "reverse-mappings" is true, replace all
   * occurrences of the IRI. Otherwise, only replace references. If "allow-dangling" is false, throw
   * exception on any dangling entities. Finally, "property" specifies the annotation property to
   * use in case of an OWLOntology mapping file.
   *
   * @param ioHelper IOHelper to parse CURIEs and IRIs
   * @param ontology OWLOntology to replace in
   * @param mappingsFile File containing mappings (tab/comma separated table or OWLOntology)
   * @param options map of replace options
   * @throws Exception on any problem
   */
  public static void replace(
      IOHelper ioHelper, OWLOntology ontology, File mappingsFile, Map<String, String> options)
      throws Exception {
    // Process options
    if (options == null) {
      options = getDefaultOptions();
    }
    String property = OptionsHelper.getOption(options, "property", "IAO:0100001");
    boolean reverseMappings = OptionsHelper.optionIsTrue(options, "reverse-mappings");
    boolean allowDangling = OptionsHelper.optionIsTrue(options, "allow-dangling");

    OWLOntologyManager manager = ontology.getOWLOntologyManager();

    // Get the mappings
    Map<IRI, IRI> mappings = parseMappings(ioHelper, mappingsFile, property);
    if (mappings.isEmpty()) {
      throw new IOException(String.format(missingMappingsError, mappingsFile.getName()));
    }

    // Replace based on mappings
    if (reverseMappings) {
      replaceAll(ontology, mappings);
    } else {
      replaceReferences(ontology, mappings);
    }

    // Maybe check for dangling entities
    if (!allowDangling) {
      Set<OWLObject> objects = new HashSet<>();
      for (OWLAxiom ax : ontology.getAxioms()) {
        objects.addAll(OntologyHelper.getObjects(ax));
      }
      Set<OWLEntity> dangling = new HashSet<>();
      for (OWLObject object : objects) {
        if (object instanceof OWLEntity) {
          OWLEntity e = (OWLEntity) object;
          if (InvalidReferenceChecker.isDangling(ontology, e)) {
            dangling.add(e);
          }
        }
      }
      if (!dangling.isEmpty()) {
        logger.error("Ontology contains %d dangling entities:", dangling.size());
        for (OWLEntity d : dangling) {
          logger.error(" - %s", d.getIRI().toString());
        }
        // Kill process
        System.exit(1);
      }
    }
  }

  /**
   * Given an ontology and a map of left IRI (old) -> right IRI (new), replace all occurrences of
   * left IRI with right IRI.
   *
   * @param ontology OWLOntology to replace in
   * @param mappings map of left IRI -> right IRI
   */
  public static void replaceAll(OWLOntology ontology, Map<IRI, IRI> mappings) {
    OWLEntityRenamer entityRenamer =
        new OWLEntityRenamer(ontology.getOWLOntologyManager(), Sets.newHashSet(ontology));
    for (Map.Entry<IRI, IRI> mapping : mappings.entrySet()) {
      IRI leftIRI = mapping.getKey();
      IRI rightIRI = mapping.getValue();
      entityRenamer.changeIRI(leftIRI, rightIRI);
    }
  }

  /**
   * Given an ontology and a map of left IRI (old) -> right IRI (new), replace references to left
   * IRI with right IRI, but maintain axioms about the left IRI.
   *
   * @param ontology OWLOntology to replace in
   * @param mappings map of left IRI -> right IRI
   * @throws OWLOntologyCreationException on issue creating subset for replacement
   */
  public static void replaceReferences(OWLOntology ontology, Map<IRI, IRI> mappings)
      throws Exception {
    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    List<OWLOntology> subsets = new ArrayList<>();
    for (Map.Entry<IRI, IRI> mapping : mappings.entrySet()) {
      IRI leftIRI = mapping.getKey();
      IRI rightIRI = mapping.getValue();
      // Find the entity in the ontology - will break if it does not exist
      OWLEntity leftEntity = OntologyHelper.getEntity(ontology, leftIRI);
      // Get all referencing axioms for left IRI
      Set<OWLAxiom> axioms =
          new HashSet<>(EntitySearcher.getReferencingAxioms(leftEntity, ontology));
      // Create a new subset with these axioms to use entityRenamer
      // This may be slow, but prevents overlaps where another IRI to replace may be a subject
      OWLOntology subset = manager.createOntology(axioms);
      OWLEntityRenamer entityRenamer = new OWLEntityRenamer(manager, Sets.newHashSet(subset));
      entityRenamer.changeIRI(leftIRI, rightIRI);
      // Remove the referencing axioms from original ontology
      manager.removeAxioms(ontology, axioms);
      // Add the new subset to the list to merge later
      subsets.add(subset);
    }
    // Merge subsets into the original ontology
    MergeOperation.mergeInto(subsets, ontology);
  }

  /**
   * Given an IOHelper, a mappings file, and an annotation property as string, parse the mapping
   * file to a Map of left IRI (old) -> right IRI (new). If the file is an OWLOntology, use the
   * annotation property to find mappings.
   *
   * @param ioHelper IOHelper to parse CURIEs and IRIs
   * @param mappingsFile File containing mappings
   * @param property annotation property for OWLOntology mapping file
   * @return Map of left IRI (old) -> right IRI (new)
   * @throws IOException on issue parsing file
   */
  private static Map<IRI, IRI> parseMappings(IOHelper ioHelper, File mappingsFile, String property)
      throws IOException {
    String fileName = mappingsFile.getPath();
    String ext = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
    if (owlOntologyFormats.contains(ext)) {
      OWLOntology mappingsOntology = ioHelper.loadOntology(mappingsFile);
      return parseOWLMappings(ioHelper, mappingsOntology, property);
    } else if (ext.equals("csv")) {
      return parseTableMappings(ioHelper, mappingsFile, ",");
    } else if (ext.equals("tsv") || ext.equals("txt")) {
      return parseTableMappings(ioHelper, mappingsFile, "\t");
    } else {
      // TODO: YAML format
      throw new IllegalArgumentException(String.format(mappingFileError, fileName));
    }
  }

  /**
   * Given an IOHelper, a mappings file, and an annotation property as string, parse the mapping
   * ontology file to a Map of left IRI (old) -> right IRI (new).
   *
   * @param ioHelper IOHelper to parse CURIEs and IRIs
   * @param mappingsOntology OWLOntology containing mappings
   * @param property mapping annotation property as string
   * @return Map of left IRI (old) -> right IRI (new)
   */
  private static Map<IRI, IRI> parseOWLMappings(
      IOHelper ioHelper, OWLOntology mappingsOntology, String property) {
    Map<IRI, IRI> mappings = new HashMap<>();
    PrefixManager pm = ioHelper.getPrefixManager();

    OWLDataFactory dataFactory = mappingsOntology.getOWLOntologyManager().getOWLDataFactory();
    OWLAnnotationProperty ap = dataFactory.getOWLAnnotationProperty(property, pm);

    for (OWLAxiom ax : mappingsOntology.getAxioms()) {
      if (ax.containsEntityInSignature(ap) && ax instanceof OWLAnnotationAssertionAxiom) {
        IRI leftIRI;
        IRI rightIRI;
        OWLAnnotationAssertionAxiom annotationAx = (OWLAnnotationAssertionAxiom) ax;
        // The property should be the pre-defined ap (could also be the subject - ignore that)
        if (annotationAx.getProperty() == ap) {
          OWLAnnotationSubject subject = annotationAx.getSubject();
          if (subject instanceof IRI) {
            leftIRI = (IRI) subject;
          } else {
            // TODO
            logger.warn("todo - subject is not IRI");
            continue;
          }

          OWLAnnotationValue value = annotationAx.getValue();
          OWLLiteral literal = value.asLiteral().orNull();
          String iriString;
          if (literal != null) {
            // First, try to parse the value as a literal
            String curieOrIRI = literal.getLiteral();
            IRI maybeIRI = ioHelper.createIRI(curieOrIRI);
            if (maybeIRI != null) {
              rightIRI = maybeIRI;
            } else {
              // TODO
              logger.warn("todo - could not create IRI from literal");
              continue;
            }
          } else {
            // Then try the value as an IRI
            IRI valueIRI = value.asIRI().orNull();
            if (valueIRI != null) {
              rightIRI = valueIRI;
            } else {
              // TODO
              logger.warn("todo - literal and IRI are both null");
              continue;
            }
          }
          mappings.put(leftIRI, rightIRI);
        }
      }
    }
    return mappings;
  }

  /**
   * Given an IOHelper, a mappings file (table format), and a string to separate cells, parse the
   * file to a Map of left IRI (old) -> right IRI (new).
   *
   * @param ioHelper IOHelper to parse CURIEs and IRIs
   * @param mappingsTable File containing mappings in table format
   * @param separator String to separate cells on
   * @return Map of left IRI (old) -> right IRI (new)
   * @throws IOException on issue parsing file or creating IRIs
   */
  private static Map<IRI, IRI> parseTableMappings(
      IOHelper ioHelper, File mappingsTable, String separator) throws IOException {
    Map<IRI, IRI> mappings = new HashMap<>();
    IRI leftIRI;
    IRI rightIRI;

    List<String> lines = FileUtils.readLines(mappingsTable);
    // Line number for error messages
    int lineNumber = 0;
    for (String line : lines) {
      lineNumber++;
      if (!line.contains(separator)) {
        // Format tab for error message
        if (separator.equals("\t")) {
          separator = "\\t";
        }
        throw new IOException(String.format(cellSplitError, lineNumber, separator));
      }
      String[] cells = line.split(separator);
      if (cells.length != 2) {
        // We only want oldIRI & newIRI, anything else is an error
        throw new IOException(String.format(lineLengthError, lineNumber));
      }
      leftIRI = ioHelper.createIRI(cells[0]);
      rightIRI = ioHelper.createIRI(cells[1]);
      // If either IRI cannot be created, replacements cannot be made
      if (leftIRI == null) {
        throw new IOException(String.format(invalidIRIError, cells[0], lineNumber));
      } else if (rightIRI == null) {
        throw new IOException(String.format(invalidIRIError, cells[1], lineNumber));
      }
      mappings.put(leftIRI, rightIRI);
    }
    return mappings;
  }
}
