package org.obolibrary.robot;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.semanticweb.owlapi.model.OWLOntology;

/**
 * Replace entity usage based on old -> new mappings. This is generally used to update data in
 * response to `replaced by` annotations, or to replace ontologies used in logical definitions.
 *
 * @author <a href="mailto:rctauber@gmail.com">Becky Tauber</a>
 */
public class ReplaceCommand implements Command {

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialize the command. */
  public ReplaceCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "replace entities in an ontology from file");
    o.addOption("I", "input-iri", true, "replace entities in an ontology from an IRI");
    o.addOption("o", "output", true, "save updated ontology to a file");
    o.addOption("m", "mappings", true, "specify replacements for old entities from file");
    o.addOption(
        "p", "property", true, "property used to specify OWL mappings (default IAO:0100001)");
    o.addOption("r", "reverse-mappings", true, "if true, reverse the mappings (default false)");
    o.addOption(
        "d", "allow-dangling", true, "if true, allow dangling entities in output (default false)");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "replace";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "replace entity usage";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot replace --input <file> "
        + "--mappings <file> "
        + "[options] "
        + "--output <file>";
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

    // Get inputs
    String mappingPath =
        CommandLineHelper.getRequiredValue(line, "mappings", "A mapping file is required.");

    // Get options
    Map<String, String> replaceOptions = new HashMap<>();
    replaceOptions.put(
        "property", CommandLineHelper.getDefaultValue(line, "property", "IAO:0100001"));
    replaceOptions.put(
        "reverse-mappings", CommandLineHelper.getDefaultValue(line, "reverse-mappings", "false"));
    replaceOptions.put(
        "allow-dangling", CommandLineHelper.getDefaultValue(line, "allow-dangling", "false"));

    ReplaceOperation.replace(ioHelper, ontology, new File(mappingPath), replaceOptions);

    CommandLineHelper.maybeSaveOutput(line, ontology);
    state.setOntology(ontology);
    return state;
  }
}
