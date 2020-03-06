package gr.uom.java.xmi.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.VariableDeclaration;

public class RenameVariableRefactoring implements Refactoring {
	private VariableDeclaration originalVariable;
	private VariableDeclaration renamedVariable;
	private UMLOperation operationBefore;
	private UMLOperation operationAfter;
	private Set<AbstractCodeMapping> variableReferences;

	public RenameVariableRefactoring(
			VariableDeclaration originalVariable,
			VariableDeclaration renamedVariable,
			UMLOperation operationBefore,
			UMLOperation operationAfter,
			Set<AbstractCodeMapping> variableReferences) {
		this.originalVariable = originalVariable;
		this.renamedVariable = renamedVariable;
		this.operationBefore = operationBefore;
		this.operationAfter = operationAfter;
		this.variableReferences = variableReferences;
	}

	public RefactoringType getRefactoringType() {
		if(originalVariable.isParameter() && renamedVariable.isParameter())
			return RefactoringType.RENAME_PARAMETER;
		if(!originalVariable.isParameter() && renamedVariable.isParameter())
			return RefactoringType.PARAMETERIZE_VARIABLE;
		if(!originalVariable.isAttribute() && renamedVariable.isAttribute())
			return RefactoringType.REPLACE_VARIABLE_WITH_ATTRIBUTE;
		return RefactoringType.RENAME_VARIABLE;
	}

	public String getName() {
		return this.getRefactoringType().getDisplayName();
	}

	public VariableDeclaration getOriginalVariable() {
		return originalVariable;
	}

	public VariableDeclaration getRenamedVariable() {
		return renamedVariable;
	}

	public UMLOperation getOperationBefore() {
		return operationBefore;
	}

	public UMLOperation getOperationAfter() {
		return operationAfter;
	}

	public Set<AbstractCodeMapping> getVariableReferences() {
		return variableReferences;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getName()).append("\t");
		sb.append(originalVariable);
		sb.append(" to ");
		sb.append(renamedVariable);
		sb.append(" in method ");
		sb.append(operationAfter);
		sb.append(" in class ").append(operationAfter.getClassName());
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((operationAfter == null) ? 0 : operationAfter.hashCode());
		result = prime * result + ((operationBefore == null) ? 0 : operationBefore.hashCode());
		result = prime * result + ((originalVariable == null) ? 0 : originalVariable.hashCode());
		result = prime * result + ((renamedVariable == null) ? 0 : renamedVariable.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RenameVariableRefactoring other = (RenameVariableRefactoring) obj;
		if (operationAfter == null) {
			if (other.operationAfter != null)
				return false;
		} else if (!operationAfter.equals(other.operationAfter))
			return false;
		if (operationBefore == null) {
			if (other.operationBefore != null)
				return false;
		} else if (!operationBefore.equals(other.operationBefore))
			return false;
		if (originalVariable == null) {
			if (other.originalVariable != null)
				return false;
		} else if (!originalVariable.equals(other.originalVariable))
			return false;
		if (renamedVariable == null) {
			if (other.renamedVariable != null)
				return false;
		} else if (!renamedVariable.equals(other.renamedVariable))
			return false;
		return true;
	}

	public List<String> getInvolvedClassesBeforeRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(operationBefore.getClassName());
		return classNames;
	}

	public List<String> getInvolvedClassesAfterRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(operationAfter.getClassName());
		return classNames;
	}

	@Override
	public List<CodeRange> leftSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(originalVariable.codeRange()
				.setDescription("original variable declaration")
				.setCodeElement(originalVariable.toString()));
		return ranges;
	}

	@Override
	public List<CodeRange> rightSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(renamedVariable.codeRange()
				.setDescription("renamed variable declaration")
				.setCodeElement(renamedVariable.toString()));
		return ranges;
	}
}
