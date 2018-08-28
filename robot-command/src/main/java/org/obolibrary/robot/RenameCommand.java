package org.obolibrary.robot;

import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * Handles inputs and outputs for the {@link RenameOperation}.
 *
 * @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a>
 */
public class RenameCommand {

  /** Namespace for error messages. */
  private static final String NS = "rename#";

  /** Error message when a mappings file does not exist. */
  private static final String missingFileError = NS + "file '%s' does not exist.";

  /** Error message when neither --mappings or --partial-mappings is provided. */
  private static final String missingMappingError =
      NS + "either '--mapping' or '--partial-mapping' is required.";

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialize the command. */
  public RenameCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "rename entities in an ontology from file");
    o.addOption("I", "input-iri", true, "rename entities in an ontology from an IRI");
    o.addOption("o", "output", true, "save updated ontology to a file");
    o.addOption("m", "mapping", true, "rename given a set of old -> new mapping as TSV/CSV");
    o.addOption("p", "partial-mapping", true, "rename prefixes given partial mapping as TSV/CSV");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "rename";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "rename entities";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot rename --input <file> " + "--mappings <file> " + "[options] " + "--output <file>";
  }

  /**
   * Command-line options for the command.
   *
   * @return options
   */
  public Options getOptions() {
    return options;
  }

  /**
   * Handle the command-line and file operations for the RenameOperation.
   *
   * @param args strings to use as arguments
   */
  public void main(String[] args) {
    try {
      execute(null, args);
    } catch (Exception e) {
      CommandLineHelper.handleException(e);
    }
  }

  /**
   * Given an input state and command line arguments
   *
   * @param state the state from the previous command, or null
   * @param args the command-line arguments
   * @return the state with the updated ontology
   * @throws Exception on any problem
   */
  public CommandState execute(CommandState state, String[] args) throws Exception {
    CommandLine line = CommandLineHelper.getCommandLine(getUsage(), getOptions(), args);
    if (line == null) {
      return null;
    }

    if (state == null) {
      state = new CommandState();
    }

    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);
    state = CommandLineHelper.updateInputOntology(ioHelper, state, line);
    OWLOntology ontology = state.getOntology();

    String mappingPath = CommandLineHelper.getOptionalValue(line, "mappings");
    String partialMappingPath = CommandLineHelper.getOptionalValue(line, "partial-mappings");

    if (mappingPath != null) {
      File mappingFile = new File(mappingPath);
      if (!mappingFile.exists()) {
        throw new IllegalArgumentException(String.format(missingFileError, mappingPath));
      }
      RenameOperation.fullRename(ontology, mappingFile);
    } else if (partialMappingPath != null) {
      File partialMappingsFile = new File(partialMappingPath);
      if (!partialMappingsFile.exists()) {
        throw new IllegalArgumentException(String.format(missingFileError, partialMappingPath));
      }
      RenameOperation.partialRename(ontology, partialMappingsFile);
    } else {
      throw new IllegalArgumentException(missingMappingError);
    }

    CommandLineHelper.maybeSaveOutput(line, ontology);
    state.setOntology(ontology);
    return state;
  }
}
