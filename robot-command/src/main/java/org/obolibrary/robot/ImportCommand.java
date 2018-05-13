package org.obolibrary.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;

/**
 * Add and remove annotations from an ontology.
 *
 * @author <a href="mailto:james@overton.ca">James A. Overton</a>
 */
public class ImportCommand implements Command {
  /** Logger. */
  private static final Logger logger = LoggerFactory.getLogger(ImportCommand.class);

  /** Namespace for error messages. */
  private static final String NS = "import#";
  
  /** Error message when a lower-terms file is not provided for a MIREOT extract. */
  private static final String missingLowerError = NS + "MIREOT extract requires a 'lower-terms' file path for %s";
  
  /** Error message when a method isn't provided in 'extract'. */
  private static final String missingMethodError = NS + "'extract' configuration for %s requires a 'method'";

  /** Store the command-line options for the command. */
  private Options options;

  /** Initialize the command. */
  public ImportCommand() {
    Options o = CommandLineHelper.getCommonOptions();
    o.addOption("i", "input", true, "create imports based on config file");
    options = o;
  }

  /**
   * Name of the command.
   *
   * @return name
   */
  public String getName() {
    return "import";
  }

  /**
   * Brief description of the command.
   *
   * @return description
   */
  public String getDescription() {
    return "create a set of imports";
  }

  /**
   * Command-line usage for the command.
   *
   * @return usage
   */
  public String getUsage() {
    return "robot import --file <file>";
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
   * Handle the command-line and file operations for the command.
   *
   * @param args strings to use as arguments
   */
  public void main(String[] args) {
    try {
      execute(null, args);
    } catch (Exception e) {
      CommandLineHelper.handleException(getUsage(), getOptions(), e);
    }
  }

  /**
   * Given an input state and command line arguments, create a set of import ontologies based on a
   * YAML configuration file.
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
    IOHelper ioHelper = CommandLineHelper.getIOHelper(line);

    String filePath =
        CommandLineHelper.getRequiredValue(line, "input", "A configuration file is required.");
    // Read YAML file
    YAMLFactory yaml = new YAMLFactory();
    ObjectMapper mapper = new ObjectMapper(yaml);
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    YAMLParser yamlParser = yaml.createParser(new File(filePath));
    MappingIterator<ImportConfig> importIterator =
        mapper.readValues(yamlParser, new TypeReference<ImportConfig>() {});
    List<ImportConfig> imports = importIterator.readAll();

    // Extract each based on config
    for (ImportConfig config : imports) {
      // TODO: validate these args
      IRI outputIRI = IRI.create(config.iri);
      // TODO: accept files and IRIs
      IRI inputIRI = IRI.create(config.input);
      String outputPath = config.output;
      logger.info(
          "Importing terms from {} to {} at {}",
          inputIRI.toString(),
          outputIRI.toString(),
          outputPath);

      OWLOntology inputOntology = ioHelper.loadOntology(inputIRI);

      Map<String, String> extractOpts = config.extract;
      String method = extractOpts.get("method");
      if (method == null) {
        throw new Exception(String.format(missingMethodError, outputIRI.toString()));
      }
      if (method.equalsIgnoreCase("mireot")) {
        String lowerTermsFile = extractOpts.get("lower-terms");
        String upperTermsFile = extractOpts.get("upper-terms");
        // TODO: String intermediates = extractOpts.get("intermediates");
        if (lowerTermsFile == null) {
          throw new Exception(String.format(missingLowerError, outputIRI.toString()));
        }
        String lowerTermsString = FileUtils.readFileToString(new File(lowerTermsFile));
        Set<IRI> lowerTerms = ioHelper.parseTerms(lowerTermsString);
        Set<IRI> upperTerms = new HashSet<>();
        if (upperTermsFile != null) {
          String upperTermsString = FileUtils.readFileToString(new File(upperTermsFile));
          upperTerms = ioHelper.parseTerms(upperTermsString);
        }
        OWLOntology outputOntology =
            MireotOperation.getAncestors(inputOntology, upperTerms, lowerTerms, null);
        // Set output IRI
        OWLOntologyManager manager = outputOntology.getOWLOntologyManager();
        manager.setOntologyDocumentIRI(outputOntology, outputIRI);
        logger.info(
            "Extracted {} axioms into {}",
            outputOntology.getAxiomCount(),
            outputOntology.getOntologyID().getOntologyIRI().orNull());
        try {
          ioHelper.saveOntology(outputOntology, outputPath);
        } catch (IllegalArgumentException e) {
          // TODO: illegal formatting/extension
        }
      } else {
        String termsFile = extractOpts.get("terms");
        if (termsFile == null) {
          // TODO: throw exception, required
        }
        ModuleType moduleType = null;
        if (method.equalsIgnoreCase("top")) {
          moduleType = ModuleType.TOP;
        } else if (method.equalsIgnoreCase("bot")) {
          moduleType = ModuleType.BOT;
        } else if (method.equalsIgnoreCase("star")) {
          moduleType = ModuleType.STAR;
        }
        String termString = FileUtils.readFileToString(new File(termsFile));
        Set<IRI> terms = ioHelper.parseTerms(termString);
        OWLOntology outputOntology =
            ExtractOperation.extract(inputOntology, terms, outputIRI, moduleType);
        logger.info(
            "Extracted {} axioms into {}",
            outputOntology.getAxiomCount(),
            outputOntology.getOntologyID().getOntologyIRI().orNull());
        try {
          ioHelper.saveOntology(outputOntology, outputPath);
        } catch (IllegalArgumentException e) {
          // TODO: illegal formatting/extension
        }
      }
    }
    // State does not change - do we want to do something different?
    return state;
  }
}
