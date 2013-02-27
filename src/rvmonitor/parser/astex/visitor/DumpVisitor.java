/*
 * Copyright (C) 2008 Feng Chen.
 * 
 * This file is part of JavaMOP parser.
 *
 * JavaMOP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JavaMOP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JavaMOP.  If not, see <http://www.gnu.org/licenses/>.
 */

package rvmonitor.parser.astex.visitor;

import rvmonitor.parser.ast.ImportDeclaration;
import rvmonitor.parser.ast.stmt.BlockStmt;
import rvmonitor.parser.astex.MOPSpecFileExt;
import rvmonitor.parser.astex.aspectj.EventPointCut;
import rvmonitor.parser.astex.aspectj.HandlerPointCut;
import rvmonitor.parser.astex.mopspec.EventDefinitionExt;
import rvmonitor.parser.astex.mopspec.ExtendedSpec;
import rvmonitor.parser.astex.mopspec.FormulaExt;
import rvmonitor.parser.astex.mopspec.HandlerExt;
import rvmonitor.parser.astex.mopspec.JavaMOPSpecExt;
import rvmonitor.parser.astex.mopspec.PropertyAndHandlersExt;
import rvmonitor.parser.astex.mopspec.ReferenceSpec;

/**
 * @author Julio Vilmar Gesser
 */

public final class DumpVisitor extends rvmonitor.parser.ast.visitor.DumpVisitor implements VoidVisitor<Object> {

	// All extended componenets

	// - JavaMOP components

	public void visit(MOPSpecFileExt f, Object arg) {
		if (f.getPakage() != null)
			f.getPakage().accept(this, arg);
		if (f.getImports() != null) {
			for (ImportDeclaration i : f.getImports()) {
				i.accept(this, arg);
			}
			printer.printLn();
		}
		if (f.getSpecs() != null) {
			for (JavaMOPSpecExt i : f.getSpecs()) {
				i.accept(this, arg);
				printer.printLn();
			}
		}
	}

	// soha: printing out references to other spec
	public void visit(ReferenceSpec r, Object arg) {
		if (r.getSpecName() != null)
			if (r.getReferenceElement() != null)
				printer.print(r.getSpecName() + "." + r.getReferenceElement());
			else
				printer.print(r.getSpecName());
		else if (r.getReferenceElement() != null)
			printer.print(r.getReferenceElement());

	}

	public void visit(JavaMOPSpecExt s, Object arg) {
		if (s.isPublic())
			printer.print("public ");
		printSpecModifiers(s.getModifiers());
		printer.print(s.getName());
		printSpecParameters(s.getParameters(), arg);
		if (s.getInMethod() != null) {
			printer.print(" within ");
			printer.print(s.getInMethod());
			// s.getInMethod().accept(this, arg);
		}
		if (s.hasExtend()) {
			printer.print(" includes ");
			int size = 1;
			for (ExtendedSpec e : s.getExtendedSpec()) {
				e.accept(this, arg);
				if (size != s.getExtendedSpec().size())
					printer.print(",");
				size++;
			}
		}

		printer.printLn(" {");
		printer.indent();

		if (s.getDeclarations() != null) {
			printMembers(s.getDeclarations(), arg);
		}

		if (s.getEvents() != null) {
			for (EventDefinitionExt e : s.getEvents()) {
				e.accept(this, arg);
			}
		}

		if (s.getPropertiesAndHandlers() != null) {
			for (PropertyAndHandlersExt p : s.getPropertiesAndHandlers()) {
				p.accept(this, arg);
			}
		}

		printer.unindent();
		printer.printLn("}");
	}

	public void visit(EventDefinitionExt e, Object arg) {
		printer.print("event " + e.getId() + " ");
		printSpecParameters(e.getParameters(), arg);
		if (e.getAction() != null) {
			e.getAction().accept(this, arg);
		}

		printer.printLn();
	}

	public void visit(PropertyAndHandlersExt p, Object arg) {
		if (p.getProperty() != null)
			p.getProperty().accept(this, arg);
		printer.printLn();
		for (String event : p.getHandlers().keySet()) {
			for (HandlerExt h : p.getHandlerList()) { // i need to remove that
														// later, i'm using it
														// now to just make sure
														// that things are
														// parsed correctly.
														// Soha.
				if (h.getState() == event) { // Soha: printing out the new
												// syntax of the handler and
												// property
					if ((h.getReferenceSpec().getSpecName() != null) || (h.getReferenceSpec().getReferenceElement() != null))
						printer.print("@");
					h.accept(this, arg);
				}
			}
			BlockStmt stmt = p.getHandlers().get(event);
			printer.printLn("@" + event);
			printer.indent();
			stmt.accept(this, arg);
			printer.unindent();
			printer.printLn();
		}
	}
	
	public void visit(FormulaExt f, Object arg) {
		printer.print(f.getType()+ " "+ f.getName() + ": " + f.getFormula());
	}

	public void visit(HandlerExt h, Object arg) {
		h.getReferenceSpec().accept(this, arg);
	}

	public void visit(ExtendedSpec extendedSpec, Object arg) {
		printer.print(" " + extendedSpec.getName());
		if (extendedSpec.isParametric()) {
			printer.print("(");
			int size = 1;
			for (String extendedParameters : extendedSpec.getParameters()) {
				printer.print(extendedParameters);
				if (size != extendedSpec.getParameters().size())
					printer.print(",");
				size++;
			}

			printer.print(")");
		}
	}

	// - AspectJ components --------------------

	public void visit(EventPointCut p, Object arg) {
		printer.print("event" + "(");
		p.getReferenceSpec().accept(this, arg);
		printer.print(")");
	}

	public void visit(HandlerPointCut p, Object arg) {
		printer.print("handler" + "(");
		p.getReferenceSpec().accept(this, arg);
		printer.print("@" + p.getState());
		printer.print(")");
	}
}