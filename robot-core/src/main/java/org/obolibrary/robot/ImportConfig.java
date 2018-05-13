package org.obolibrary.robot;

import java.util.Map;

/**
 * Java object to parse YAML import configuration options.
 *
 * @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a>
 */
public class ImportConfig {

  public String iri;
  public String input;
  public String output;
  public Map<String, String> extract;

  /** Default constructor for Jackson. */
  public ImportConfig() {}

  /**
   * Manual constructor.
   *
   * @param iri string IRI of the output import ontology
   * @param input string IRI of the ontology to import
   * @param output string path to the output
   * @param extract map of extraction options
   */
  public ImportConfig(String iri, String input, String output, Map<String, String> extract) {
    this.iri = iri;
    this.input = input;
    this.output = output;
    this.extract = extract;
  }
}
