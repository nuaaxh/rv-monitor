/**
 * @author Feng Chen, Dongyun Jin
 * The class handling the mop specification tree
 */

package rvmonitor;

import rvmonitor.logicclient.LogicRepositoryConnector;
import rvmonitor.logicpluginshells.LogicPluginShellFactory;
import rvmonitor.logicpluginshells.LogicPluginShellResult;
import rvmonitor.output.AspectJCode;
import rvmonitor.output.JavaLibCode;
import rvmonitor.parser.ast.MOPSpecFile;
import rvmonitor.parser.ast.body.BodyDeclaration;
import rvmonitor.parser.ast.mopspec.EventDefinition;
import rvmonitor.parser.ast.mopspec.JavaMOPSpec;
import rvmonitor.parser.ast.mopspec.MOPParameter;
import rvmonitor.parser.ast.mopspec.PropertyAndHandlers;
import rvmonitor.parser.ast.visitor.CollectUserVarVisitor;
import rvmonitor.parser.logicrepositorysyntax.LogicRepositoryType;
import rvmonitor.util.Tool;

import java.util.List;

public class MOPProcessor {
	public static boolean verbose = false;

	public String name;

	public MOPProcessor(String name) {
		this.name = name;
	}

	private void registerUserVar(JavaMOPSpec mopSpec) throws MOPException {
		for (EventDefinition event : mopSpec.getEvents()) {
			MOPNameSpace.addUserVariable(event.getId());
			for(MOPParameter param : event.getMOPParameters()){
				MOPNameSpace.addUserVariable(param.getName());
			}
		}
		for (MOPParameter param : mopSpec.getParameters()) {
			MOPNameSpace.addUserVariable(param.getName());
		}
		MOPNameSpace.addUserVariable(mopSpec.getName());
		for (BodyDeclaration bd : mopSpec.getDeclarations()) {
			List<String> vars = bd.accept(new CollectUserVarVisitor(), null);

			if (vars != null)
				MOPNameSpace.addUserVariables(vars);
		}
	}

	public String process(MOPSpecFile mopSpecFile) throws MOPException {
		String result;

		// register all user variables to MOPNameSpace to avoid conflicts
		for(JavaMOPSpec mopSpec : mopSpecFile.getSpecs())
			registerUserVar(mopSpec);

		// Connect to Logic Repository
		for(JavaMOPSpec mopSpec : mopSpecFile.getSpecs()){
			for (PropertyAndHandlers prop : mopSpec.getPropertiesAndHandlers()) {
				// connect to the logic repository and get the logic output
				LogicRepositoryType logicOutput = LogicRepositoryConnector.process(mopSpec, prop);
				// get the monitor from the logic shell
				LogicPluginShellResult logicShellOutput = LogicPluginShellFactory.process(logicOutput, mopSpec.getEventStr());
				prop.setLogicShellOutput(logicShellOutput);
				
				if(logicOutput.getMessage().contains("versioned stack")){
					prop.setVersionedStack();
				}

				if (verbose) {
					System.out.println("== result from logic shell ==");
					System.out.print(logicShellOutput);
					System.out.println("");
				}
			}
		}

		// Error Checker
		for(JavaMOPSpec mopSpec : mopSpecFile.getSpecs()){
			MOPErrorChecker.verify(mopSpec);
		}

		// Generate output code
		if (Main.toJavaLib)
			result = (new JavaLibCode(name, mopSpecFile)).toString();
		else
			result = (new AspectJCode(name, mopSpecFile)).toString();


		// Do indentation
		result = Tool.changeIndentation(result, "", "\t");

		return result;
	}


}