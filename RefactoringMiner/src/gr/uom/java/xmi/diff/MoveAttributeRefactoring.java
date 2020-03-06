package gr.uom.java.xmi.diff;

import java.util.ArrayList;
import java.util.List;

import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import gr.uom.java.xmi.UMLAttribute;

public class MoveAttributeRefactoring implements Refactoring {
	protected UMLAttribute originalAttribute;
	protected UMLAttribute movedAttribute;
	private volatile int hashCode = 0;
	
	public MoveAttributeRefactoring(UMLAttribute originalAttribute, UMLAttribute movedAttribute) {
		this.originalAttribute = originalAttribute;
		this.movedAttribute = movedAttribute;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getName()).append("\t");
		sb.append(getOriginalAttribute().toQualifiedString());
		sb.append(" from class ");
		sb.append(getSourceClassName());
		sb.append(" to ");
		sb.append(getMovedAttribute().toQualifiedString());
		sb.append(" from class ");
		sb.append(getTargetClassName());
		return sb.toString();
	}

	public String getName() {
		return this.getRefactoringType().getDisplayName();
	}

	public RefactoringType getRefactoringType() {
		return RefactoringType.MOVE_ATTRIBUTE;
	}

	public UMLAttribute getOriginalAttribute() {
		return originalAttribute;
	}

	public UMLAttribute getMovedAttribute() {
		return movedAttribute;
	}

	public String getSourceClassName() {
		return originalAttribute.getClassName();
	}

	public String getTargetClassName() {
		return movedAttribute.getClassName();
	}

	/**
	 * @return the code range of the source attribute in the <b>parent</b> commit
	 */
	public CodeRange getSourceAttributeCodeRangeBeforeMove() {
		return originalAttribute.codeRange();
	}

	/**
	 * @return the code range of the target attribute in the <b>child</b> commit
	 */
	public CodeRange getTargetAttributeCodeRangeAfterMove() {
		return movedAttribute.codeRange();
	}

	public boolean equals(Object o) {
		if(this == o) {
			return true;
		}
		
		if(o instanceof MoveAttributeRefactoring) {
			MoveAttributeRefactoring other = (MoveAttributeRefactoring)o;
			return this.originalAttribute.equals(other.originalAttribute) &&
				this.movedAttribute.equals(other.movedAttribute) &&
				this.getSourceClassName().equals(other.getSourceClassName()) &&
				this.getTargetClassName().equals(other.getTargetClassName());
		}
		return false;
	}

	public int hashCode() {
		if(hashCode == 0) {
			int result = 17;
			result = 37*result + originalAttribute.hashCode();
			result = 37*result + movedAttribute.hashCode();
			result = 37*result + getSourceClassName().hashCode();
			result = 37*result + getTargetClassName().hashCode();
			hashCode = result;
		}
		return hashCode;
	}

	public List<String> getInvolvedClassesBeforeRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(getOriginalAttribute().getClassName());
		return classNames;
	}

	public List<String> getInvolvedClassesAfterRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(getMovedAttribute().getClassName());
		return classNames;
	}

	@Override
	public List<CodeRange> leftSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(originalAttribute.codeRange()
				.setDescription("original attribute declaration")
				.setCodeElement(originalAttribute.toString()));
		return ranges;
	}

	@Override
	public List<CodeRange> rightSide() {
		List<CodeRange> ranges = new ArrayList<CodeRange>();
		ranges.add(movedAttribute.codeRange()
				.setDescription("moved attribute declaration")
				.setCodeElement(movedAttribute.toString()));
		return ranges;
	}
}
