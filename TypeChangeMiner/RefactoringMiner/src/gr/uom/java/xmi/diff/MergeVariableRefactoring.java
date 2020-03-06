package gr.uom.java.xmi.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.VariableDeclaration;

public class MergeVariableRefactoring implements Refactoring {
	private Set<VariableDeclaration> mergedVariables;
	private VariableDeclaration newVariable;
	private UMLOperation operationBefore;
	private UMLOperation operationAfter;
	private Set<AbstractCodeMapping> variableReferences;
	
	public MergeVariableRefactoring(Set<VariableDeclaration> mergedVariables, VariableDeclaration newVariable,
			UMLOperation operationBefore, UMLOperation operationAfter, Set<AbstractCodeMapping> variableReferences) {
		this.mergedVariables = mergedVariables;
		this.newVariable = newVariable;
		this.operationBefore = operationBefore;
		this.operationAfter = operationAfter;
		this.variableReferences = variableReferences;
	}

	public Set<VariableDeclaration> getMergedVariables() {
		return mergedVariables;
	}

	public VariableDeclaration getNewVariable() {
		return newVariable;
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

	private boolean allVariablesAreParameters() {
		for(VariableDeclaration declaration : mergedVariables) {
			if(!declaration.isParameter()) {
				return false;
			}
		}
		return newVariable.isParameter();
	}

	public RefactoringType getRefactoringType() {
		if(allVariablesAreParameters())
			return RefactoringType.MERGE_PARAMETER;
		return RefactoringType.MERGE_VARIABLE;
	}

	public String getName() {
		return this.getRefactoringType().getDisplayName();
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

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getName()).append("\t");
		sb.append(mergedVariables);
		sb.append(" to ");
		sb.append(newVariable);
		sb.append(" in method ");
		sb.append(operationAfter);
		sb.append(" in class ").append(operationAfter.getClassName());
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mergedVariables == null) ? 0 : mergedVariables.hashCode());
		result = prime * result + ((newVariable == null) ? 0 : newVariable.hashCode());
		result = prime * result + ((operationAfter == null) ? 0 : operationAfter.hashCode());
		result = prime * result + ((operationBefore == null) ? 0 : operationBefore.hashCode());
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
		MergeVariableRefactoring other = (MergeVariableRefactoring) obj;
		if (mergedVariables == null) {
			if (other.mergedVariables != null)
				return false;
		} else if (!mergedVariables.equals(other.mergedVariables))
			return false;
		if (newVariable == null) {
			if (other.newVariable != null)
				return false;
		} else if (!newVariable.equals(other.newVariable))
			return false;
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
		return true;
	}

	@Override
	public List<CodeRange> leftSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		for(VariableDeclaration mergedVariable : mergedVariables) {
			ranges.add(mergedVariable.codeRange()
					.setDescription("merged variable declaration")
					.setCodeElement(mergedVariable.toString()));
		}
		return ranges;
	}

	@Override
	public List<CodeRange> rightSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(newVariable.codeRange()
				.setDescription("new variable declaration")
				.setCodeElement(newVariable.toString()));
		return ranges;
	}
}
