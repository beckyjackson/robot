package org.obolibrary.robot;

import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLEntityRenamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rename entity IRIs based on old -> new mappings.
 *
 * @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a>
 */
public class RenameOperation {

  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(RenameOperation.class);

  /** Namespace for error messages. */
  private static final String NS = "rename#";

  /** Error message when a line cannot be parsed into old -> new values. */
  private static final String lineReadError =
      NS + "LINE READ ERROR unable to parse line '%s' with '%s' separator.";

  /** Error message when mapping file is not CSV, TSV, or TXT. */
  private static final String mappingFileError =
      NS
          + "MAPPING FILE ERROR file '%s' must be: comma-separated (CSV) or tab-separated (TSV, TXT).";

  /**
   * Given an ontology and a mapping file (tab- or comma-separated values), rename the IRIs in
   * column 1 with the IRIs in column 2.
   *
   * @param ontology OWLOntology to rename entities in
   * @param mappingFile File to map old to new IRIs
   * @throws IOException on issue parsing mapping file
   */
  public static void fullRename(OWLOntology ontology, File mappingFile) throws IOException {
    Map<String, String> mappings = parseMappings(mappingFile);

    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLEntityRenamer entityRenamer = new OWLEntityRenamer(manager, Sets.newHashSet(ontology));

    List<OWLOntologyChange> changes = new ArrayList<>();
    for (Map.Entry<String, String> mapping : mappings.entrySet()) {
      IRI oldIRI = IRI.create(mapping.getKey());
      IRI newIRI = IRI.create(mapping.getValue());
      if (!ontology.containsEntityInSignature(oldIRI)) {
        // TODO exception or warning?
        logger.warn("ontology does not contain entity: %s", oldIRI);
      }
      changes.addAll(entityRenamer.changeIRI(oldIRI, newIRI));
    }
    // Apply the changes
    manager.applyChanges(changes);
  }

  /**
   * Given an ontology and a mapping file (tab- or comma-separated values), renamed the namespaces
   * in column 1 with the namespaces in column 2.
   *
   * @param ontology OWLOntology to rename namespaces in
   * @param mappingFile File to map old to new namespaces
   * @throws IOException on issue parsing mapping file
   */
  public static void partialRename(OWLOntology ontology, File mappingFile) throws IOException {
    Map<String, String> partialMappings = parseMappings(mappingFile);
    Set<String> replace = partialMappings.keySet();

    OWLOntologyManager manager = ontology.getOWLOntologyManager();
    OWLEntityRenamer entityRenamer = new OWLEntityRenamer(manager, Sets.newHashSet(ontology));

    List<OWLOntologyChange> changes = new ArrayList<>();
    for (OWLEntity e : OntologyHelper.getEntities(ontology)) {
      IRI iri = e.getIRI();
      for (String ns : replace) {
        if (iri.toString().startsWith(ns)) {
          String newIRI = iri.toString().replace(ns, partialMappings.get(ns));
          entityRenamer.changeIRI(iri, IRI.create(newIRI));
        }
      }
    }
    // Apply the changes
    manager.applyChanges(changes);
  }

  /**
   * Given a mapping file, parse the tab- or comma- separated values into a map.
   *
   * @param mappingFile File to get mappings from
   * @return map of old -> new values
   * @throws IOException on issue reading file
   */
  private static Map<String, String> parseMappings(File mappingFile) throws IOException {
    List<String> lines = FileUtils.readLines(mappingFile);
    String fileName = mappingFile.getName();
    if (fileName.endsWith(".csv")) {
      logger.debug("parsing mappings as comma-separated values");
      return parseMappings(lines, ",");
    } else if (fileName.endsWith(".tsv") || fileName.endsWith(".txt")) {
      logger.debug("parsing mappings as tab-separated values");
      return parseMappings(lines, "\t");
    } else {
      // TODO
      throw new IllegalArgumentException(String.format(mappingFileError, fileName));
    }
  }

  /**
   * Given a mapping file and a separator string, parse the values into a map separated by the given
   * string.
   *
   * @param lines list of lines of the file to get mappings from
   * @param separator string character to split the lines on
   * @return map of old -> new values
   * @throws IOException on issue splitting lines
   */
  private static Map<String, String> parseMappings(List<String> lines, String separator)
      throws IOException {
    Map<String, String> mappings = new HashMap<>();
    // Strip header
    lines.remove(0);
    for (String line : lines) {
      String[] pairs = line.split(separator);
      try {
        mappings.put(pairs[0], pairs[1]);
      } catch (IndexOutOfBoundsException e) {
        // Format tab separator for exception message
        if (separator.equals("\t")) {
          separator = "\\t";
        }
        throw new IOException(String.format(lineReadError, line, separator));
      }
    }
    return mappings;
  }
}
