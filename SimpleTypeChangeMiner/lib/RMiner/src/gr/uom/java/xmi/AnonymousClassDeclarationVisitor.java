package gr.uom.java.xmi;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;

public class AnonymousClassDeclarationVisitor extends ASTVisitor {

	private Set<AnonymousClassDeclaration> anonymousClassDeclarations = new LinkedHashSet<AnonymousClassDeclaration>();
	public boolean visit(AnonymousClassDeclaration node) {
		anonymousClassDeclarations.add(node);
		return super.visit(node);
	}

	public Set<AnonymousClassDeclaration> getAnonymousClassDeclarations() {
		return anonymousClassDeclarations;
	}
}
