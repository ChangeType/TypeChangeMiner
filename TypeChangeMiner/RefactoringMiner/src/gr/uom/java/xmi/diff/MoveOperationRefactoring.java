package gr.uom.java.xmi.diff;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import gr.uom.java.xmi.UMLOperation;
import gr.uom.java.xmi.decomposition.UMLOperationBodyMapper;
import gr.uom.java.xmi.decomposition.replacement.Replacement;

public class MoveOperationRefactoring implements Refactoring {
	protected UMLOperation originalOperation;
	protected UMLOperation movedOperation;
	private Set<Replacement> replacements;
	private UMLOperationBodyMapper bodyMapper;

	public MoveOperationRefactoring(UMLOperationBodyMapper bodyMapper) {
		this.bodyMapper = bodyMapper;
		this.originalOperation = bodyMapper.getOperation1();
		this.movedOperation = bodyMapper.getOperation2();
		this.replacements = bodyMapper.getReplacements();
	}

	public MoveOperationRefactoring(UMLOperation originalOperation, UMLOperation movedOperation) {
		this.originalOperation = originalOperation;
		this.movedOperation = movedOperation;
		this.replacements = new LinkedHashSet<Replacement>();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getName()).append("\t");
		sb.append(originalOperation);
		sb.append(" from class ");
		sb.append(originalOperation.getClassName());
		sb.append(" to ");
		sb.append(movedOperation);
		sb.append(" from class ");
		sb.append(movedOperation.getClassName());
		return sb.toString();
	}

	public String getName() {
		return this.getRefactoringType().getDisplayName();
	}

	public RefactoringType getRefactoringType() {
		return RefactoringType.MOVE_OPERATION;
	}

	public UMLOperationBodyMapper getBodyMapper() {
		return bodyMapper;
	}

	public UMLOperation getOriginalOperation() {
		return originalOperation;
	}

	public UMLOperation getMovedOperation() {
		return movedOperation;
	}

	public Set<Replacement> getReplacements() {
		return replacements;
	}

	/**
	 * @return the code range of the source method in the <b>parent</b> commit
	 */
	public CodeRange getSourceOperationCodeRangeBeforeMove() {
		return originalOperation.codeRange();
	}

	/**
	 * @return the code range of the target method in the <b>child</b> commit
	 */
	public CodeRange getTargetOperationCodeRangeAfterMove() {
		return movedOperation.codeRange();
	}

	public boolean compatibleWith(MoveAttributeRefactoring ref) {
		if(ref.getMovedAttribute().getClassName().equals(this.movedOperation.getClassName()) &&
				ref.getOriginalAttribute().getClassName().equals(this.originalOperation.getClassName())) {
			List<String> originalOperationVariables = this.originalOperation.getAllVariables();
			List<String> movedOperationVariables = this.movedOperation.getAllVariables();
			return originalOperationVariables.contains(ref.getOriginalAttribute().getName()) &&
					movedOperationVariables.contains(ref.getMovedAttribute().getName());
		}
		return false;
	}

	public List<String> getInvolvedClassesBeforeRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(getOriginalOperation().getClassName());
		return classNames;
	}

	public List<String> getInvolvedClassesAfterRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(getMovedOperation().getClassName());
		return classNames;
	}

	@Override
	public List<CodeRange> leftSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(originalOperation.codeRange()
				.setDescription("original method declaration")
				.setCodeElement(originalOperation.toString()));
		return ranges;
	}

	@Override
	public List<CodeRange> rightSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(movedOperation.codeRange()
				.setDescription("moved method declaration")
				.setCodeElement(movedOperation.toString()));
		return ranges;
	}
}
