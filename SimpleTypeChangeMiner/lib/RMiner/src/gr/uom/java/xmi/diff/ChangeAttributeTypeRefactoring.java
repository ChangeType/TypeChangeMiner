package gr.uom.java.xmi.diff;

import org.refactoringminer.LocationFor;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import gr.uom.java.xmi.decomposition.AbstractCodeMapping;
import gr.uom.java.xmi.decomposition.VariableDeclaration;
import io.vavr.Tuple2;

public class ChangeAttributeTypeRefactoring implements Refactoring, LocationFor {
	private VariableDeclaration originalAttribute;
	private VariableDeclaration changedTypeAttribute;
	private String classNameBefore;
	private String classNameAfter;
	private Set<AbstractCodeMapping> attributeReferences;
	private Set<Refactoring> relatedRefactorings;
	
	public ChangeAttributeTypeRefactoring(VariableDeclaration originalAttribute,
			VariableDeclaration changedTypeAttribute, String classNameBefore, String classNameAfter, Set<AbstractCodeMapping> attributeReferences) {
		this.originalAttribute = originalAttribute;
		this.changedTypeAttribute = changedTypeAttribute;
		this.classNameBefore = classNameBefore;
		this.classNameAfter = classNameAfter;
		this.attributeReferences = attributeReferences;
		this.relatedRefactorings = new LinkedHashSet<Refactoring>();
	}

	public void addRelatedRefactoring(Refactoring refactoring) {
		this.relatedRefactorings.add(refactoring);
	}

	public Set<Refactoring> getRelatedRefactorings() {
		return relatedRefactorings;
	}

	public VariableDeclaration getOriginalAttribute() {
		return originalAttribute;
	}

	public VariableDeclaration getChangedTypeAttribute() {
		return changedTypeAttribute;
	}

	public String getClassNameBefore() {
		return classNameBefore;
	}

	public String getClassNameAfter() {
		return classNameAfter;
	}

	public Set<AbstractCodeMapping> getAttributeReferences() {
		return attributeReferences;
	}

	@Override
	public RefactoringType getRefactoringType() {
		return RefactoringType.CHANGE_ATTRIBUTE_TYPE;
	}

	@Override
	public String getName() {
		return this.getRefactoringType().getDisplayName();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		boolean qualified = originalAttribute.getType().equals(changedTypeAttribute.getType()) && !originalAttribute.getType().equalsQualified(changedTypeAttribute.getType());
		sb.append(getName()).append("\t");
		sb.append(qualified ? originalAttribute.toQualifiedString() : originalAttribute.toString());
		sb.append(" to ");
		sb.append(qualified ? changedTypeAttribute.toQualifiedString() : changedTypeAttribute.toString());
		sb.append(" in class ").append(classNameAfter);
		return sb.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((changedTypeAttribute == null) ? 0 : changedTypeAttribute.hashCode());
		result = prime * result + ((classNameAfter == null) ? 0 : classNameAfter.hashCode());
		result = prime * result + ((classNameBefore == null) ? 0 : classNameBefore.hashCode());
		result = prime * result + ((originalAttribute == null) ? 0 : originalAttribute.hashCode());
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
		ChangeAttributeTypeRefactoring other = (ChangeAttributeTypeRefactoring) obj;
		if (changedTypeAttribute == null) {
			if (other.changedTypeAttribute != null)
				return false;
		} else if (!changedTypeAttribute.equals(other.changedTypeAttribute))
			return false;
		if (classNameAfter == null) {
			if (other.classNameAfter != null)
				return false;
		} else if (!classNameAfter.equals(other.classNameAfter))
			return false;
		if (classNameBefore == null) {
			if (other.classNameBefore != null)
				return false;
		} else if (!classNameBefore.equals(other.classNameBefore))
			return false;
		if (originalAttribute == null) {
			if (other.originalAttribute != null)
				return false;
		} else if (!originalAttribute.equals(other.originalAttribute))
			return false;
		return true;
	}

	@Override
	public List<String> getInvolvedClassesBeforeRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(classNameBefore);
		return classNames;
	}

	@Override
	public List<String> getInvolvedClassesAfterRefactoring() {
		List<String> classNames = new ArrayList<String>();
		classNames.add(classNameAfter);
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
		ranges.add(changedTypeAttribute.codeRange()
				.setDescription("changed-type attribute declaration")
				.setCodeElement(changedTypeAttribute.toString()));
		return ranges;
	}

	@Override
	public boolean isTypeChange() {return true;}

	@Override
	public Tuple2<String, String> getUrlsToElement(Tuple2<String, String> commitUtrl) {
		return commitUtrl.map(b4 -> generateUrl(getOriginalAttribute().getLocationInfo(), b4, "L")
				, after -> generateUrl(getChangedTypeAttribute().getLocationInfo(), after, "R"));
	}
}
