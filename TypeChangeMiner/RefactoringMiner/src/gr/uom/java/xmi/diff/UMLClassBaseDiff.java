package gr.uom.java.xmi.diff;

import com.t2r.common.models.refactorings.NameSpaceOuterClass.NameSpace;
import gr.uom.java.xmi.*;
import gr.uom.java.xmi.decomposition.*;
import gr.uom.java.xmi.decomposition.replacement.*;
import gr.uom.java.xmi.decomposition.replacement.Replacement.ReplacementType;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringMinerTimedOutException;
import org.refactoringminer.util.PrefixSuffixUtils;

import java.util.*;

public abstract class UMLClassBaseDiff implements Comparable<UMLClassBaseDiff> {

	public static final double MAX_OPERATION_NAME_DISTANCE = 0.4;
	protected UMLClass originalClass;
	protected UMLClass nextClass;
	protected List<UMLOperation> addedOperations;
	protected List<UMLOperation> removedOperations;
	protected List<UMLAttribute> addedAttributes;
	protected List<UMLAttribute> removedAttributes;
	private List<UMLOperationBodyMapper> operationBodyMapperList;
	private boolean visibilityChanged;
	private String oldVisibility;
	private String newVisibility;
	private boolean abstractionChanged;
	private boolean oldAbstraction;
	private boolean newAbstraction;
	private boolean superclassChanged;
	private UMLType oldSuperclass;
	private UMLType newSuperclass;
	private List<UMLType> addedImplementedInterfaces;
	private List<UMLType> removedImplementedInterfaces;
	private List<UMLAnonymousClass> addedAnonymousClasses;
	private List<UMLAnonymousClass> removedAnonymousClasses;
	private List<UMLOperationDiff> operationDiffList;
	protected List<UMLAttributeDiff> attributeDiffList;
	protected List<Refactoring> refactorings;
	private Set<MethodInvocationReplacement> consistentMethodInvocationRenames;
	private Set<CandidateAttributeRefactoring> candidateAttributeRenames = new LinkedHashSet<CandidateAttributeRefactoring>();
	private Set<CandidateMergeVariableRefactoring> candidateAttributeMerges = new LinkedHashSet<CandidateMergeVariableRefactoring>();
	private Set<CandidateSplitVariableRefactoring> candidateAttributeSplits = new LinkedHashSet<CandidateSplitVariableRefactoring>();
	private Map<Replacement, Set<CandidateAttributeRefactoring>> renameMap = new LinkedHashMap<Replacement, Set<CandidateAttributeRefactoring>>();
	private Map<MergeVariableReplacement, Set<CandidateMergeVariableRefactoring>> mergeMap = new LinkedHashMap<MergeVariableReplacement, Set<CandidateMergeVariableRefactoring>>();
	private Map<SplitVariableReplacement, Set<CandidateSplitVariableRefactoring>> splitMap = new LinkedHashMap<SplitVariableReplacement, Set<CandidateSplitVariableRefactoring>>();
	private UMLModelDiff modelDiff;








	public UMLClassBaseDiff(UMLClass originalClass, UMLClass nextClass, UMLModelDiff modelDiff) {
		this.originalClass = originalClass;
		this.nextClass = nextClass;
		this.visibilityChanged = false;
		this.abstractionChanged = false;
		this.superclassChanged = false;
		this.addedOperations = new ArrayList<>();
		this.removedOperations = new ArrayList<UMLOperation>();
		this.addedAttributes = new ArrayList<UMLAttribute>();
		this.removedAttributes = new ArrayList<UMLAttribute>();
		this.operationBodyMapperList = new ArrayList<UMLOperationBodyMapper>();
		this.addedImplementedInterfaces = new ArrayList<UMLType>();
		this.removedImplementedInterfaces = new ArrayList<UMLType>();
		this.addedAnonymousClasses = new ArrayList<UMLAnonymousClass>();
		this.removedAnonymousClasses = new ArrayList<UMLAnonymousClass>();
		this.operationDiffList = new ArrayList<UMLOperationDiff>();
		this.attributeDiffList = new ArrayList<UMLAttributeDiff>();
		this.refactorings = new ArrayList<Refactoring>();
		this.modelDiff = modelDiff;
	}

	public void process() throws RefactoringMinerTimedOutException {
		processInheritance();
		processOperations();
		createBodyMappers();
		processAttributes();
		checkForAttributeChanges();
		processAnonymousClasses();
		checkForOperationSignatureChanges();
		checkForInlinedOperations();
		checkForExtractedOperations();
	}

	public UMLOperationDiff getOperationDiff(UMLOperation operation1, UMLOperation operation2) {
		for(UMLOperationDiff diff : operationDiffList) {
			if(diff.getRemovedOperation().equals(operation1) && diff.getAddedOperation().equals(operation2)) {
				return diff;
			}
		}
		return null;
	}

	public UMLOperationBodyMapper findMapperWithMatchingSignatures(UMLOperation operation1, UMLOperation operation2) {
		for(UMLOperationBodyMapper mapper : operationBodyMapperList) {
			if(mapper.getOperation1().equalSignature(operation1) && mapper.getOperation2().equalSignature(operation2)) {
				return mapper;
			}
		}
		return null;
	}

	public UMLOperationBodyMapper findMapperWithMatchingSignature2(UMLOperation operation2) {
		for(UMLOperationBodyMapper mapper : operationBodyMapperList) {
			if(mapper.getOperation2().equalSignature(operation2)) {
				return mapper;
			}
		}
		return null;
	}

	public Set<UMLType> nextClassCommonInterfaces(UMLClassBaseDiff other) {
		Set<UMLType> common = new LinkedHashSet<UMLType>(nextClass.getImplementedInterfaces());
		common.retainAll(other.nextClass.getImplementedInterfaces());
		return common;
	}

	protected void checkForAttributeChanges() {
		//optional step
	}

	protected void createBodyMappers() throws RefactoringMinerTimedOutException {
		//optional step
	}

	protected void processAnonymousClasses() {
		for(UMLAnonymousClass umlAnonymousClass : originalClass.getAnonymousClassList()) {
    		if(!nextClass.containsAnonymousWithSameAttributesAndOperations(umlAnonymousClass))
    			this.removedAnonymousClasses.add(umlAnonymousClass);
    	}
    	for(UMLAnonymousClass umlAnonymousClass : nextClass.getAnonymousClassList()) {
    		if(!originalClass.containsAnonymousWithSameAttributesAndOperations(umlAnonymousClass))
    			this.addedAnonymousClasses.add(umlAnonymousClass);
    	}
	}

	protected void processAttributes() {
		for(UMLAttribute attribute : originalClass.getAttributes()) {
    		UMLAttribute attributeWithTheSameName = nextClass.attributeWithTheSameNameIgnoringChangedType(attribute);
			if(attributeWithTheSameName == null) {
    			this.removedAttributes.add(attribute);
    		}
			else if((!attribute.equals(attributeWithTheSameName) || !attribute.equalsQualified(attributeWithTheSameName)) && !attributeDiffListContainsAttribute(attribute, attributeWithTheSameName)) {
				UMLAttributeDiff attributeDiff = new UMLAttributeDiff(attribute, attributeWithTheSameName);
				if(attributeDiff.isTypeChanged() || attributeDiff.isQualifiedTypeChanged()) {
					ChangeAttributeTypeRefactoring ref = new ChangeAttributeTypeRefactoring(attribute.getVariableDeclaration(), attributeWithTheSameName.getVariableDeclaration(), originalClass.getName(), nextClass.getName(),
							VariableReferenceExtractor.findReferences(attribute.getVariableDeclaration(), attributeWithTheSameName.getVariableDeclaration(), operationBodyMapperList), originalClass.getFieldTypeMap(), nextClass.getFieldTypeMap(),attribute.getVisibility() );
					refactorings.add(ref);
				}
				this.attributeDiffList.add(attributeDiff);
			}
    	}
    	for(UMLAttribute attribute : nextClass.getAttributes()) {
    		UMLAttribute attributeWithTheSameName = originalClass.attributeWithTheSameNameIgnoringChangedType(attribute);
			if(attributeWithTheSameName == null) {
    			this.addedAttributes.add(attribute);
    		}
			else if((!attribute.equals(attributeWithTheSameName) || !attribute.equalsQualified(attributeWithTheSameName)) && !attributeDiffListContainsAttribute(attributeWithTheSameName, attribute)) {
				UMLAttributeDiff attributeDiff = new UMLAttributeDiff(attributeWithTheSameName, attribute);
				if(attributeDiff.isTypeChanged() || attributeDiff.isQualifiedTypeChanged()) {
					ChangeAttributeTypeRefactoring ref = new ChangeAttributeTypeRefactoring(attributeWithTheSameName.getVariableDeclaration(), attribute.getVariableDeclaration(), originalClass.getName(), nextClass.getName(),
							VariableReferenceExtractor.findReferences(attributeWithTheSameName.getVariableDeclaration(), attribute.getVariableDeclaration(), operationBodyMapperList), originalClass.getFieldTypeMap(), nextClass.getFieldTypeMap(), attributeWithTheSameName.getVisibility());
					refactorings.add(ref);
				}
				this.attributeDiffList.add(attributeDiff);
			}
    	}
	}

	protected void processOperations() throws RefactoringMinerTimedOutException {
		for(UMLOperation operation : originalClass.getOperations()) {
    		UMLOperation operationWithTheSameSignature = nextClass.operationWithTheSameSignatureIgnoringChangedTypes(operation);
			if(operationWithTheSameSignature == null) {
				this.removedOperations.add(operation);
    		}
			else if(!mapperListContainsOperation(operation, operationWithTheSameSignature)) {
				UMLOperationBodyMapper mapper = new UMLOperationBodyMapper(operation, operationWithTheSameSignature, this);
				this.operationBodyMapperList.add(mapper);
			}
    	}
    	for(UMLOperation operation : nextClass.getOperations()) {
    		UMLOperation operationWithTheSameSignature = originalClass.operationWithTheSameSignatureIgnoringChangedTypes(operation);
			if(operationWithTheSameSignature == null) {
				this.addedOperations.add(operation);
    		}
			else if(!mapperListContainsOperation(operationWithTheSameSignature, operation)) {
				UMLOperationBodyMapper mapper = new UMLOperationBodyMapper(operationWithTheSameSignature, operation, this);
				this.operationBodyMapperList.add(mapper);
			}
    	}
	}

	private boolean attributeDiffListContainsAttribute(UMLAttribute attribute1, UMLAttribute attribute2) {
		for(UMLAttributeDiff diff : attributeDiffList) {
			if(diff.getRemovedAttribute().equals(attribute1) || diff.getAddedAttribute().equals(attribute2))
				return true;
		}
		return false;
	}

	private boolean mapperListContainsOperation(UMLOperation operation1, UMLOperation operation2) {
		for(UMLOperationBodyMapper mapper : operationBodyMapperList) {
			if(mapper.getOperation1().equals(operation1) || mapper.getOperation2().equals(operation2))
				return true;
		}
		return false;
	}

	public boolean matches(String className) {
		return this.originalClass.getName().equals(className) ||
				this.nextClass.getName().equals(className);
	}

	public boolean matches(UMLType type) {
		return this.originalClass.getName().endsWith("." + type.getClassType()) ||
				this.nextClass.getName().endsWith("." + type.getClassType());
	}

	public String getOriginalClassName() {
		return originalClass.getName();
	}

	public String getNextClassName() {
		return nextClass.getName();
	}

	public UMLClass getOriginalClass() {
		return originalClass;
	}

	public UMLClass getNextClass() {
		return nextClass;
	}

	public List<UMLOperationBodyMapper> getOperationBodyMapperList() {
		return operationBodyMapperList;
	}

	public List<UMLOperation> getAddedOperations() {
		return addedOperations;
	}

	public List<UMLOperation> getRemovedOperations() {
		return removedOperations;
	}

	public List<UMLAttribute> getAddedAttributes() {
		return addedAttributes;
	}

	public List<UMLAttribute> getRemovedAttributes() {
		return removedAttributes;
	}

	//return true if "classMoveDiff" represents the move of a class that is inner to this.originalClass
	public boolean isInnerClassMove(UMLClassBaseDiff classDiff) {
		if(this.originalClass.isInnerClass(classDiff.originalClass) && this.nextClass.isInnerClass(classDiff.nextClass))
			return true;
		return false;
	}

	public boolean nextClassImportsType(String targetClass) {
		return nextClass.importsType(targetClass);
	}

	public boolean originalClassImportsType(String targetClass) {
		return originalClass.importsType(targetClass);
	}

	public List<UMLAttribute> nextClassAttributesOfType(String targetClass) {
		return nextClass.attributesOfType(targetClass);
	}

	public List<UMLAttribute> originalClassAttributesOfType(String targetClass) {
		return originalClass.attributesOfType(targetClass);
	}

	private void reportAddedImplementedInterface(UMLType implementedInterface) {
		this.addedImplementedInterfaces.add(implementedInterface);
	}

	private void reportRemovedImplementedInterface(UMLType implementedInterface) {
		this.removedImplementedInterfaces.add(implementedInterface);
	}

	public void reportAddedAnonymousClass(UMLAnonymousClass umlClass) {
		this.addedAnonymousClasses.add(umlClass);
	}

	public void reportRemovedAnonymousClass(UMLAnonymousClass umlClass) {
		this.removedAnonymousClasses.add(umlClass);
	}

	private void setVisibilityChanged(boolean visibilityChanged) {
		this.visibilityChanged = visibilityChanged;
	}

	private void setOldVisibility(String oldVisibility) {
		this.oldVisibility = oldVisibility;
	}

	private void setNewVisibility(String newVisibility) {
		this.newVisibility = newVisibility;
	}

	private void setAbstractionChanged(boolean abstractionChanged) {
		this.abstractionChanged = abstractionChanged;
	}

	private void setOldAbstraction(boolean oldAbstraction) {
		this.oldAbstraction = oldAbstraction;
	}

	private void setNewAbstraction(boolean newAbstraction) {
		this.newAbstraction = newAbstraction;
	}

	private void setSuperclassChanged(boolean superclassChanged) {
		this.superclassChanged = superclassChanged;
	}

	private void setOldSuperclass(UMLType oldSuperclass) {
		this.oldSuperclass = oldSuperclass;
	}

	private void setNewSuperclass(UMLType newSuperclass) {
		this.newSuperclass = newSuperclass;
	}

	public UMLType getSuperclass() {
		if(!superclassChanged && oldSuperclass != null && newSuperclass != null)
			return oldSuperclass;
		return null;
	}

	public UMLType getOldSuperclass() {
		return oldSuperclass;
	}

	public UMLType getNewSuperclass() {
		return newSuperclass;
	}

	public List<UMLType> getAddedImplementedInterfaces() {
		return addedImplementedInterfaces;
	}

	public List<UMLType> getRemovedImplementedInterfaces() {
		return removedImplementedInterfaces;
	}

	public List<UMLAnonymousClass> getAddedAnonymousClasses() {
		return addedAnonymousClasses;
	}

	public List<UMLAnonymousClass> getRemovedAnonymousClasses() {
		return removedAnonymousClasses;
	}

	public Set<CandidateAttributeRefactoring> getCandidateAttributeRenames() {
		return candidateAttributeRenames;
	}

	public Set<CandidateMergeVariableRefactoring> getCandidateAttributeMerges() {
		return candidateAttributeMerges;
	}

	public Set<CandidateSplitVariableRefactoring> getCandidateAttributeSplits() {
		return candidateAttributeSplits;
	}

	public boolean containsOperationWithTheSameSignatureInOriginalClass(UMLOperation operation) {
		for(UMLOperation originalOperation : originalClass.getOperations()) {
			if(originalOperation.equalSignature(operation))
				return true;
		}
		return false;
	}

	public boolean containsOperationWithTheSameSignatureInNextClass(UMLOperation operation) {
		for(UMLOperation originalOperation : nextClass.getOperations()) {
			if(originalOperation.equalSignature(operation))
				return true;
		}
		return false;
	}

	public UMLOperation containsRemovedOperationWithTheSameSignature(UMLOperation operation) {
		for(UMLOperation removedOperation : removedOperations) {
			if(removedOperation.equalSignature(operation))
				return removedOperation;
		}
		return null;
	}

	public UMLAttribute containsRemovedAttributeWithTheSameSignature(UMLAttribute attribute) {
		for(UMLAttribute removedAttribute : removedAttributes) {
			if(removedAttribute.equalsIgnoringChangedVisibility(attribute))
				return removedAttribute;
		}
		return null;
	}

	private void processInheritance() {
		if(!originalClass.getVisibility().equals(nextClass.getVisibility())) {
			setVisibilityChanged(true);
			setOldVisibility(originalClass.getVisibility());
			setNewVisibility(nextClass.getVisibility());
		}
		if(!originalClass.isInterface() && !nextClass.isInterface()) {
			if(originalClass.isAbstract() != nextClass.isAbstract()) {
				setAbstractionChanged(true);
				setOldAbstraction(originalClass.isAbstract());
				setNewAbstraction(nextClass.isAbstract());
			}
		}
		if(originalClass.getSuperclass() != null && nextClass.getSuperclass() != null) {
			if(!originalClass.getSuperclass().equals(nextClass.getSuperclass())) {
				setSuperclassChanged(true);
			}
			setOldSuperclass(originalClass.getSuperclass());
			setNewSuperclass(nextClass.getSuperclass());
		}
		else if(originalClass.getSuperclass() != null && nextClass.getSuperclass() == null) {
			setSuperclassChanged(true);
			setOldSuperclass(originalClass.getSuperclass());
			setNewSuperclass(nextClass.getSuperclass());
		}
		else if(originalClass.getSuperclass() == null && nextClass.getSuperclass() != null) {
			setSuperclassChanged(true);
			setOldSuperclass(originalClass.getSuperclass());
			setNewSuperclass(nextClass.getSuperclass());
		}
		for(UMLType implementedInterface : originalClass.getImplementedInterfaces()) {
			if(!nextClass.getImplementedInterfaces().contains(implementedInterface))
				reportRemovedImplementedInterface(implementedInterface);
		}
		for(UMLType implementedInterface : nextClass.getImplementedInterfaces()) {
			if(!originalClass.getImplementedInterfaces().contains(implementedInterface))
				reportAddedImplementedInterface(implementedInterface);
		}
	}

	public void addOperationBodyMapper(UMLOperationBodyMapper operationBodyMapper) {
		this.operationBodyMapperList.add(operationBodyMapper);
	}

	public List<Refactoring> getRefactoringsBeforePostProcessing() {
		return refactorings;
	}

	public List<Refactoring> getRefactorings() {
		List<Refactoring> refactorings = new ArrayList<Refactoring>(this.refactorings);
		for(UMLOperationBodyMapper mapper : operationBodyMapperList) {
			UMLOperationDiff operationSignatureDiff = new UMLOperationDiff(mapper.getOperation1(), mapper.getOperation2(), mapper.getMappings());
			refactorings.addAll(operationSignatureDiff.getRefactorings());
			processMapperRefactorings(mapper, refactorings);
		}
		refactorings.addAll(inferAttributeMergesAndSplits(renameMap, refactorings));
		for(MergeVariableReplacement merge : mergeMap.keySet()) {
			Set<UMLAttribute> mergedAttributes = new LinkedHashSet<UMLAttribute>();
			Set<VariableDeclaration> mergedVariables = new LinkedHashSet<VariableDeclaration>();
			for(String mergedVariable : merge.getMergedVariables()) {
				UMLAttribute a1 = findAttributeInOriginalClass(mergedVariable);
				if(a1 != null) {
					mergedAttributes.add(a1);
					mergedVariables.add(a1.getVariableDeclaration());
				}
			}
			UMLAttribute a2 = findAttributeInNextClass(merge.getAfter());
			Set<CandidateMergeVariableRefactoring> set = mergeMap.get(merge);
			for(CandidateMergeVariableRefactoring candidate : set) {
				if(mergedVariables.size() > 1 && mergedVariables.size() == merge.getMergedVariables().size() && a2 != null) {
					MergeAttributeRefactoring ref = new MergeAttributeRefactoring(mergedVariables, a2.getVariableDeclaration(), getOriginalClassName(), getNextClassName(), set);
					if(!refactorings.contains(ref)) {
						refactorings.add(ref);
						break;//it's not necessary to repeat the same process for all candidates in the set
					}
				}
				else {
					candidate.setMergedAttributes(mergedAttributes);
					candidate.setNewAttribute(a2);
					candidateAttributeMerges.add(candidate);
				}
			}
		}
		for(SplitVariableReplacement split : splitMap.keySet()) {
			Set<UMLAttribute> splitAttributes = new LinkedHashSet<UMLAttribute>();
			Set<VariableDeclaration> splitVariables = new LinkedHashSet<VariableDeclaration>();
			for(String splitVariable : split.getSplitVariables()) {
				UMLAttribute a2 = findAttributeInNextClass(splitVariable);
				if(a2 != null) {
					splitAttributes.add(a2);
					splitVariables.add(a2.getVariableDeclaration());
				}
			}
			UMLAttribute a1 = findAttributeInOriginalClass(split.getBefore());
			Set<CandidateSplitVariableRefactoring> set = splitMap.get(split);
			for(CandidateSplitVariableRefactoring candidate : set) {
				if(splitVariables.size() > 1 && splitVariables.size() == split.getSplitVariables().size() && a1 != null) {
					SplitAttributeRefactoring ref = new SplitAttributeRefactoring(a1.getVariableDeclaration(), splitVariables, getOriginalClassName(), getNextClassName(), set);
					if(!refactorings.contains(ref)) {
						refactorings.add(ref);
						break;//it's not necessary to repeat the same process for all candidates in the set
					}
				}
				else {
					candidate.setSplitAttributes(splitAttributes);
					candidate.setOldAttribute(a1);
					candidateAttributeSplits.add(candidate);
				}
			}
		}
		Set<Replacement> renames = renameMap.keySet();
		Set<Replacement> allConsistentRenames = new LinkedHashSet<Replacement>();
		Set<Replacement> allInconsistentRenames = new LinkedHashSet<Replacement>();
		Map<String, Set<String>> aliasedAttributesInOriginalClass = originalClass.aliasedAttributes();
		Map<String, Set<String>> aliasedAttributesInNextClass = nextClass.aliasedAttributes();
		ConsistentReplacementDetector.updateRenames(allConsistentRenames, allInconsistentRenames, renames,
				aliasedAttributesInOriginalClass, aliasedAttributesInNextClass);
		allConsistentRenames.removeAll(allInconsistentRenames);
		for(Replacement pattern : allConsistentRenames) {
			UMLAttribute a1 = findAttributeInOriginalClass(pattern.getBefore());
			UMLAttribute a2 = findAttributeInNextClass(pattern.getAfter());
			Set<CandidateAttributeRefactoring> set = renameMap.get(pattern);
			for(CandidateAttributeRefactoring candidate : set) {
				if(candidate.getOriginalVariableDeclaration() == null && candidate.getRenamedVariableDeclaration() == null) {
					if(a1 != null && a2 != null) {
						if((!originalClass.containsAttributeWithName(pattern.getAfter()) || cyclicRename(renameMap, pattern)) &&
								(!nextClass.containsAttributeWithName(pattern.getBefore()) || cyclicRename(renameMap, pattern)) &&
								!inconsistentAttributeRename(pattern, aliasedAttributesInOriginalClass, aliasedAttributesInNextClass) &&
								!attributeMerged(a1, a2, refactorings) && !attributeSplit(a1, a2, refactorings)) {
							RenameAttributeRefactoring ref = new RenameAttributeRefactoring(a1.getVariableDeclaration(), a2.getVariableDeclaration(),
									getOriginalClassName(), getNextClassName(), set);
							if(!refactorings.contains(ref)) {
								refactorings.add(ref);
								if(!a1.getVariableDeclaration().getType().equals(a2.getVariableDeclaration().getType()) || !a1.getVariableDeclaration().getType().equalsQualified(a2.getVariableDeclaration().getType())) {
									ChangeAttributeTypeRefactoring refactoring = new ChangeAttributeTypeRefactoring(a1.getVariableDeclaration(), a2.getVariableDeclaration(),
											getOriginalClassName(), getNextClassName(),
											VariableReferenceExtractor.findReferences(a1.getVariableDeclaration(), a2.getVariableDeclaration(), operationBodyMapperList),  originalClass.getFieldTypeMap(), nextClass.getFieldTypeMap(), a1.getVisibility());
									refactorings.add(refactoring);
								}
								break;//it's not necessary to repeat the same process for all candidates in the set
							}
						}
					}
					else {
						candidate.setOriginalAttribute(a1);
						candidate.setRenamedAttribute(a2);
						if(a1 != null)
							candidate.setOriginalVariableDeclaration(a1.getVariableDeclaration());
						if(a2 != null)
							candidate.setRenamedVariableDeclaration(a2.getVariableDeclaration());
						candidateAttributeRenames.add(candidate);
					}
				}
				else if(candidate.getOriginalVariableDeclaration() != null) {
					if(a2 != null) {
						RenameVariableRefactoring ref = new RenameVariableRefactoring(
								candidate.getOriginalVariableDeclaration(), a2.getVariableDeclaration(),
								candidate.getOperationBefore(), candidate.getOperationAfter(), candidate.getAttributeReferences());
						if(!refactorings.contains(ref)) {
							refactorings.add(ref);
							if(!candidate.getOriginalVariableDeclaration().getType().equals(a2.getVariableDeclaration().getType()) ||
									!candidate.getOriginalVariableDeclaration().getType().equalsQualified(a2.getVariableDeclaration().getType())) {
								ChangeVariableTypeRefactoring refactoring = new ChangeVariableTypeRefactoring(candidate.getOriginalVariableDeclaration(), a2.getVariableDeclaration(),
										candidate.getOperationBefore(), candidate.getOperationAfter(), candidate.getAttributeReferences());
								refactorings.add(refactoring);
							}
						}
					}
					else {
						//field is declared in a superclass or outer class
						candidateAttributeRenames.add(candidate);
					}
				}
				else if(candidate.getRenamedVariableDeclaration() != null) {
					//inline field
				}
			}
		}
		return refactorings;
	}

	private void processMapperRefactorings(UMLOperationBodyMapper mapper, List<Refactoring> refactorings) {
		for(Refactoring refactoring : mapper.getRefactorings()) {
			if(refactorings.contains(refactoring)) {
				//special handling for replacing rename variable refactorings having statement mapping information
				int index = refactorings.indexOf(refactoring);
				refactorings.remove(index);
				refactorings.add(index, refactoring);
			}
			else {
				refactorings.add(refactoring);
			}
		}
		for(CandidateAttributeRefactoring candidate : mapper.getCandidateAttributeRenames()) {
			if(!multipleExtractedMethodInvocationsWithDifferentAttributesAsArguments(candidate, refactorings)) {
				String before = PrefixSuffixUtils.normalize(candidate.getOriginalVariableName());
				String after = PrefixSuffixUtils.normalize(candidate.getRenamedVariableName());
				if(before.contains(".") && after.contains(".")) {
					String prefix1 = before.substring(0, before.lastIndexOf(".") + 1);
					String prefix2 = after.substring(0, after.lastIndexOf(".") + 1);
					if(prefix1.equals(prefix2)) {
						before = before.substring(prefix1.length(), before.length());
						after = after.substring(prefix2.length(), after.length());
					}
				}
				Replacement renamePattern = new Replacement(before, after, ReplacementType.VARIABLE_NAME);
				if(renameMap.containsKey(renamePattern)) {
					renameMap.get(renamePattern).add(candidate);
				}
				else {
					Set<CandidateAttributeRefactoring> set = new LinkedHashSet<CandidateAttributeRefactoring>();
					set.add(candidate);
					renameMap.put(renamePattern, set);
				}
			}
		}
		for(CandidateMergeVariableRefactoring candidate : mapper.getCandidateAttributeMerges()) {
			Set<String> before = new LinkedHashSet<String>();
			for(String mergedVariable : candidate.getMergedVariables()) {
				before.add(PrefixSuffixUtils.normalize(mergedVariable));
			}
			String after = PrefixSuffixUtils.normalize(candidate.getNewVariable());
			MergeVariableReplacement merge = new MergeVariableReplacement(before, after);
			processMerge(mergeMap, merge, candidate);
		}
		for(CandidateSplitVariableRefactoring candidate : mapper.getCandidateAttributeSplits()) {
			Set<String> after = new LinkedHashSet<String>();
			for(String splitVariable : candidate.getSplitVariables()) {
				after.add(PrefixSuffixUtils.normalize(splitVariable));
			}
			String before = PrefixSuffixUtils.normalize(candidate.getOldVariable());
			SplitVariableReplacement split = new SplitVariableReplacement(before, after);
			processSplit(splitMap, split, candidate);
		}
	}

	private boolean multipleExtractedMethodInvocationsWithDifferentAttributesAsArguments(CandidateAttributeRefactoring candidate, List<Refactoring> refactorings) {
		for(Refactoring refactoring : refactorings) {
			if(refactoring instanceof ExtractOperationRefactoring) {
				ExtractOperationRefactoring extractRefactoring = (ExtractOperationRefactoring)refactoring;
				if(extractRefactoring.getExtractedOperation().equals(candidate.getOperationAfter())) {
					List<OperationInvocation> extractedInvocations = extractRefactoring.getExtractedOperationInvocations();
					if(extractedInvocations.size() > 1) {
						Set<VariableDeclaration> attributesMatchedWithArguments = new LinkedHashSet<VariableDeclaration>();
						Set<String> attributeNamesMatchedWithArguments = new LinkedHashSet<String>();
						for(OperationInvocation extractedInvocation : extractedInvocations) {
							for(String argument : extractedInvocation.getArguments()) {
								for(UMLAttribute attribute : originalClass.getAttributes()) {
									if(attribute.getName().equals(argument)) {
										attributesMatchedWithArguments.add(attribute.getVariableDeclaration());
										attributeNamesMatchedWithArguments.add(attribute.getName());
										break;
									}
								}
							}
						}
						if((attributeNamesMatchedWithArguments.contains(candidate.getOriginalVariableName()) ||
								attributeNamesMatchedWithArguments.contains(candidate.getRenamedVariableName())) &&
								attributesMatchedWithArguments.size() > 1) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private Set<Refactoring> inferAttributeMergesAndSplits(Map<Replacement, Set<CandidateAttributeRefactoring>> map, List<Refactoring> refactorings) {
		Set<Refactoring> newRefactorings = new LinkedHashSet<Refactoring>();
		for(Replacement replacement : map.keySet()) {
			Set<CandidateAttributeRefactoring> candidates = map.get(replacement);
			for(CandidateAttributeRefactoring candidate : candidates) {
				String originalAttributeName = PrefixSuffixUtils.normalize(candidate.getOriginalVariableName());
				String renamedAttributeName = PrefixSuffixUtils.normalize(candidate.getRenamedVariableName());
				UMLOperationBodyMapper candidateMapper = null;
				for(UMLOperationBodyMapper mapper : operationBodyMapperList) {
					if(mapper.getMappings().containsAll(candidate.getAttributeReferences())) {
						candidateMapper = mapper;
						break;
					}
					for(UMLOperationBodyMapper nestedMapper : mapper.getChildMappers()) {
						if(nestedMapper.getMappings().containsAll(candidate.getAttributeReferences())) {
							candidateMapper = nestedMapper;
							break;
						}
					}
				}
				for(Refactoring refactoring : refactorings) {
					if(refactoring instanceof MergeVariableRefactoring) {
						MergeVariableRefactoring merge = (MergeVariableRefactoring)refactoring;
						Set<String> nonMatchingVariableNames = new LinkedHashSet<String>();
						String matchingVariableName = null;
						for(VariableDeclaration variableDeclaration : merge.getMergedVariables()) {
							if(originalAttributeName.equals(variableDeclaration.getVariableName())) {
								matchingVariableName = variableDeclaration.getVariableName();
							}
							else {
								for(StatementObject statement : candidateMapper.getNonMappedLeavesT1()) {
									if(statement.getString().startsWith(variableDeclaration.getVariableName() + "=") ||
											statement.getString().startsWith("this." + variableDeclaration.getVariableName() + "=")) {
										nonMatchingVariableNames.add(variableDeclaration.getVariableName());
										break;
									}
								}
							}
						}
						if(matchingVariableName != null && renamedAttributeName.equals(merge.getNewVariable().getVariableName()) && nonMatchingVariableNames.size() > 0) {
							Set<UMLAttribute> mergedAttributes = new LinkedHashSet<UMLAttribute>();
							Set<VariableDeclaration> mergedVariables = new LinkedHashSet<VariableDeclaration>();
							Set<String> allMatchingVariables = new LinkedHashSet<String>();
							if(merge.getMergedVariables().iterator().next().getVariableName().equals(matchingVariableName)) {
								allMatchingVariables.add(matchingVariableName);
								allMatchingVariables.addAll(nonMatchingVariableNames);
							}
							else {
								allMatchingVariables.addAll(nonMatchingVariableNames);
								allMatchingVariables.add(matchingVariableName);
							}
							for(String mergedVariable : allMatchingVariables) {
								UMLAttribute a1 = findAttributeInOriginalClass(mergedVariable);
								if(a1 != null) {
									mergedAttributes.add(a1);
									mergedVariables.add(a1.getVariableDeclaration());
								}
							}
							UMLAttribute a2 = findAttributeInNextClass(renamedAttributeName);
							if(mergedVariables.size() > 1 && mergedVariables.size() == merge.getMergedVariables().size() && a2 != null) {
								MergeAttributeRefactoring ref = new MergeAttributeRefactoring(mergedVariables, a2.getVariableDeclaration(), getOriginalClassName(), getNextClassName(), new LinkedHashSet<CandidateMergeVariableRefactoring>());
								if(!refactorings.contains(ref)) {
									newRefactorings.add(ref);
								}
							}
						}
					}
					else if(refactoring instanceof SplitVariableRefactoring) {
						SplitVariableRefactoring split = (SplitVariableRefactoring)refactoring;
						Set<String> nonMatchingVariableNames = new LinkedHashSet<String>();
						String matchingVariableName = null;
						for(VariableDeclaration variableDeclaration : split.getSplitVariables()) {
							if(renamedAttributeName.equals(variableDeclaration.getVariableName())) {
								matchingVariableName = variableDeclaration.getVariableName();
							}
							else {
								for(StatementObject statement : candidateMapper.getNonMappedLeavesT2()) {
									if(statement.getString().startsWith(variableDeclaration.getVariableName() + "=") ||
											statement.getString().startsWith("this." + variableDeclaration.getVariableName() + "=")) {
										nonMatchingVariableNames.add(variableDeclaration.getVariableName());
										break;
									}
								}
							}
						}
						if(matchingVariableName != null && originalAttributeName.equals(split.getOldVariable().getVariableName()) && nonMatchingVariableNames.size() > 0) {
							Set<UMLAttribute> splitAttributes = new LinkedHashSet<UMLAttribute>();
							Set<VariableDeclaration> splitVariables = new LinkedHashSet<VariableDeclaration>();
							Set<String> allMatchingVariables = new LinkedHashSet<String>();
							if(split.getSplitVariables().iterator().next().getVariableName().equals(matchingVariableName)) {
								allMatchingVariables.add(matchingVariableName);
								allMatchingVariables.addAll(nonMatchingVariableNames);
							}
							else {
								allMatchingVariables.addAll(nonMatchingVariableNames);
								allMatchingVariables.add(matchingVariableName);
							}
							for(String splitVariable : allMatchingVariables) {
								UMLAttribute a2 = findAttributeInNextClass(splitVariable);
								if(a2 != null) {
									splitAttributes.add(a2);
									splitVariables.add(a2.getVariableDeclaration());
								}
							}
							UMLAttribute a1 = findAttributeInOriginalClass(originalAttributeName);
							if(splitVariables.size() > 1 && splitVariables.size() == split.getSplitVariables().size() && a1 != null) {
								SplitAttributeRefactoring ref = new SplitAttributeRefactoring(a1.getVariableDeclaration(), splitVariables, getOriginalClassName(), getNextClassName(), new LinkedHashSet<CandidateSplitVariableRefactoring>());
								if(!refactorings.contains(ref)) {
									newRefactorings.add(ref);
								}
							}
						}
					}
				}
			}
		}
		return newRefactorings;
	}

	private boolean attributeMerged(UMLAttribute a1, UMLAttribute a2, List<Refactoring> refactorings) {
		for(Refactoring refactoring : refactorings) {
			if(refactoring instanceof MergeAttributeRefactoring) {
				MergeAttributeRefactoring merge = (MergeAttributeRefactoring)refactoring;
				if(merge.getMergedAttributes().contains(a1.getVariableDeclaration()) && merge.getNewAttribute().equals(a2.getVariableDeclaration())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean attributeSplit(UMLAttribute a1, UMLAttribute a2, List<Refactoring> refactorings) {
		for(Refactoring refactoring : refactorings) {
			if(refactoring instanceof SplitAttributeRefactoring) {
				SplitAttributeRefactoring split = (SplitAttributeRefactoring)refactoring;
				if(split.getSplitAttributes().contains(a2.getVariableDeclaration()) && split.getOldAttribute().equals(a1.getVariableDeclaration())) {
					return true;
				}
			}
		}
		return false;
	}

	private void processMerge(Map<MergeVariableReplacement, Set<CandidateMergeVariableRefactoring>> mergeMap,
			MergeVariableReplacement newMerge, CandidateMergeVariableRefactoring candidate) {
		MergeVariableReplacement mergeToBeRemoved = null;
		for(MergeVariableReplacement merge : mergeMap.keySet()) {
			if(merge.subsumes(newMerge)) {
				mergeMap.get(merge).add(candidate);
				return;
			}
			else if(merge.equal(newMerge)) {
				mergeMap.get(merge).add(candidate);
				return;
			}
			else if(merge.commonAfter(newMerge)) {
				mergeToBeRemoved = merge;
				Set<String> mergedVariables = new LinkedHashSet<String>();
				mergedVariables.addAll(merge.getMergedVariables());
				mergedVariables.addAll(newMerge.getMergedVariables());
				MergeVariableReplacement replacement = new MergeVariableReplacement(mergedVariables, merge.getAfter());
				Set<CandidateMergeVariableRefactoring> candidates = mergeMap.get(mergeToBeRemoved);
				candidates.add(candidate);
				mergeMap.put(replacement, candidates);
				break;
			}
			else if(newMerge.subsumes(merge)) {
				mergeToBeRemoved = merge;
				Set<CandidateMergeVariableRefactoring> candidates = mergeMap.get(mergeToBeRemoved);
				candidates.add(candidate);
				mergeMap.put(newMerge, candidates);
				break;
			}
		}
		if(mergeToBeRemoved != null) {
			mergeMap.remove(mergeToBeRemoved);
			return;
		}
		Set<CandidateMergeVariableRefactoring> set = new LinkedHashSet<CandidateMergeVariableRefactoring>();
		set.add(candidate);
		mergeMap.put(newMerge, set);
	}

	private void processSplit(Map<SplitVariableReplacement, Set<CandidateSplitVariableRefactoring>> splitMap,
			SplitVariableReplacement newSplit, CandidateSplitVariableRefactoring candidate) {
		SplitVariableReplacement splitToBeRemoved = null;
		for(SplitVariableReplacement split : splitMap.keySet()) {
			if(split.subsumes(newSplit)) {
				splitMap.get(split).add(candidate);
				return;
			}
			else if(split.equal(newSplit)) {
				splitMap.get(split).add(candidate);
				return;
			}
			else if(split.commonBefore(newSplit)) {
				splitToBeRemoved = split;
				Set<String> splitVariables = new LinkedHashSet<String>();
				splitVariables.addAll(split.getSplitVariables());
				splitVariables.addAll(newSplit.getSplitVariables());
				SplitVariableReplacement replacement = new SplitVariableReplacement(split.getBefore(), splitVariables);
				Set<CandidateSplitVariableRefactoring> candidates = splitMap.get(splitToBeRemoved);
				candidates.add(candidate);
				splitMap.put(replacement, candidates);
				break;
			}
			else if(newSplit.subsumes(split)) {
				splitToBeRemoved = split;
				Set<CandidateSplitVariableRefactoring> candidates = splitMap.get(splitToBeRemoved);
				candidates.add(candidate);
				splitMap.put(newSplit, candidates);
				break;
			}
		}
		if(splitToBeRemoved != null) {
			splitMap.remove(splitToBeRemoved);
			return;
		}
		Set<CandidateSplitVariableRefactoring> set = new LinkedHashSet<CandidateSplitVariableRefactoring>();
		set.add(candidate);
		splitMap.put(newSplit, set);
	}

	public UMLAttribute findAttributeInOriginalClass(String attributeName) {
		for(UMLAttribute attribute : originalClass.getAttributes()) {
			if(attribute.getName().equals(attributeName)) {
				return attribute;
			}
		}
		return null;
	}

	public UMLAttribute findAttributeInNextClass(String attributeName) {
		for(UMLAttribute attribute : nextClass.getAttributes()) {
			if(attribute.getName().equals(attributeName)) {
				return attribute;
			}
		}
		return null;
	}

	private boolean inconsistentAttributeRename(Replacement pattern,
			Map<String, Set<String>> aliasedAttributesInOriginalClass,
			Map<String, Set<String>> aliasedAttributesInNextClass) {
		for(String key : aliasedAttributesInOriginalClass.keySet()) {
			if(aliasedAttributesInOriginalClass.get(key).contains(pattern.getBefore())) {
				return false;
			}
		}
		for(String key : aliasedAttributesInNextClass.keySet()) {
			if(aliasedAttributesInNextClass.get(key).contains(pattern.getAfter())) {
				return false;
			}
		}
		int counter = 0;
		int allCases = 0;
		for(UMLOperationBodyMapper mapper : this.operationBodyMapperList) {
			List<String> allVariables1 = mapper.getOperation1().getAllVariables();
			List<String> allVariables2 = mapper.getOperation2().getAllVariables();
			for(UMLOperationBodyMapper nestedMapper : mapper.getChildMappers()) {
				allVariables1.addAll(nestedMapper.getOperation1().getAllVariables());
				allVariables2.addAll(nestedMapper.getOperation2().getAllVariables());
			}
			boolean variables1contains = (allVariables1.contains(pattern.getBefore()) &&
					!mapper.getOperation1().getParameterNameList().contains(pattern.getBefore())) ||
					allVariables1.contains("this."+pattern.getBefore());
			boolean variables2Contains = (allVariables2.contains(pattern.getAfter()) &&
					!mapper.getOperation2().getParameterNameList().contains(pattern.getAfter())) ||
					allVariables2.contains("this."+pattern.getAfter());
			if(variables1contains && !variables2Contains) {	
				counter++;
			}
			if(variables2Contains && !variables1contains) {
				counter++;
			}
			if(variables1contains || variables2Contains) {
				allCases++;
			}
		}
		double percentage = (double)counter/(double)allCases;
		if(percentage > 0.5)
			return true;
		return false;
	}

	private static boolean cyclicRename(Map<Replacement, Set<CandidateAttributeRefactoring>> renames, Replacement rename) {
		for(Replacement r : renames.keySet()) {
			if((rename.getAfter().equals(r.getBefore()) || rename.getBefore().equals(r.getAfter())) &&
					(totalOccurrences(renames.get(rename)) > 1 || totalOccurrences(renames.get(r)) > 1))
			return true;
		}
		return false;
	}

	private static int totalOccurrences(Set<CandidateAttributeRefactoring> candidates) {
		int totalCount = 0;
		for(CandidateAttributeRefactoring candidate : candidates) {
			totalCount += candidate.getOccurrences();
		}
		return totalCount;
	}

	private int computeAbsoluteDifferenceInPositionWithinClass(UMLOperation removedOperation, UMLOperation addedOperation) {
		int index1 = originalClass.getOperations().indexOf(removedOperation);
		int index2 = nextClass.getOperations().indexOf(addedOperation);
		return Math.abs(index1-index2);
	}

	private void checkForOperationSignatureChanges() throws RefactoringMinerTimedOutException {
		consistentMethodInvocationRenames = findConsistentMethodInvocationRenames();
		if(removedOperations.size() <= addedOperations.size()) {
			for(Iterator<UMLOperation> removedOperationIterator = removedOperations.iterator(); removedOperationIterator.hasNext();) {
				UMLOperation removedOperation = removedOperationIterator.next();
				TreeSet<UMLOperationBodyMapper> mapperSet = new TreeSet<UMLOperationBodyMapper>();
				for(Iterator<UMLOperation> addedOperationIterator = addedOperations.iterator(); addedOperationIterator.hasNext();) {
					UMLOperation addedOperation = addedOperationIterator.next();
					int maxDifferenceInPosition;
					if(removedOperation.hasTestAnnotation() && addedOperation.hasTestAnnotation()) {
						maxDifferenceInPosition = Math.abs(removedOperations.size() - addedOperations.size());
					}
					else {
						maxDifferenceInPosition = Math.max(removedOperations.size(), addedOperations.size());
					}
					updateMapperSet(mapperSet, removedOperation, addedOperation, maxDifferenceInPosition);
					List<UMLOperation> operationsInsideAnonymousClass = addedOperation.getOperationsInsideAnonymousClass(this.addedAnonymousClasses);
					for(UMLOperation operationInsideAnonymousClass : operationsInsideAnonymousClass) {
						updateMapperSet(mapperSet, removedOperation, operationInsideAnonymousClass, addedOperation, maxDifferenceInPosition);
					}
				}
				if(!mapperSet.isEmpty()) {
					UMLOperationBodyMapper bestMapper = findBestMapper(mapperSet);
					if(bestMapper != null) {
						removedOperation = bestMapper.getOperation1();
						UMLOperation addedOperation = bestMapper.getOperation2();
						addedOperations.remove(addedOperation);
						removedOperationIterator.remove();
	
						UMLOperationDiff operationSignatureDiff = new UMLOperationDiff(removedOperation, addedOperation, bestMapper.getMappings());
						operationDiffList.add(operationSignatureDiff);
						refactorings.addAll(operationSignatureDiff.getRefactorings());
						if(!removedOperation.getName().equals(addedOperation.getName()) &&
								!(removedOperation.isConstructor() && addedOperation.isConstructor())) {
							RenameOperationRefactoring rename = new RenameOperationRefactoring(bestMapper);
							refactorings.add(rename);
						}
						this.addOperationBodyMapper(bestMapper);
					}
				}
			}
		}
		else {
			for(Iterator<UMLOperation> addedOperationIterator = addedOperations.iterator(); addedOperationIterator.hasNext();) {
				UMLOperation addedOperation = addedOperationIterator.next();
				TreeSet<UMLOperationBodyMapper> mapperSet = new TreeSet<UMLOperationBodyMapper>();
				for(Iterator<UMLOperation> removedOperationIterator = removedOperations.iterator(); removedOperationIterator.hasNext();) {
					UMLOperation removedOperation = removedOperationIterator.next();
					int maxDifferenceInPosition;
					if(removedOperation.hasTestAnnotation() && addedOperation.hasTestAnnotation()) {
						maxDifferenceInPosition = Math.abs(removedOperations.size() - addedOperations.size());
					}
					else {
						maxDifferenceInPosition = Math.max(removedOperations.size(), addedOperations.size());
					}
					updateMapperSet(mapperSet, removedOperation, addedOperation, maxDifferenceInPosition);
					List<UMLOperation> operationsInsideAnonymousClass = addedOperation.getOperationsInsideAnonymousClass(this.addedAnonymousClasses);
					for(UMLOperation operationInsideAnonymousClass : operationsInsideAnonymousClass) {
						updateMapperSet(mapperSet, removedOperation, operationInsideAnonymousClass, addedOperation, maxDifferenceInPosition);
					}
				}
				if(!mapperSet.isEmpty()) {
					UMLOperationBodyMapper bestMapper = findBestMapper(mapperSet);
					if(bestMapper != null) {
						UMLOperation removedOperation = bestMapper.getOperation1();
						addedOperation = bestMapper.getOperation2();
						removedOperations.remove(removedOperation);
						addedOperationIterator.remove();
	
						UMLOperationDiff operationSignatureDiff = new UMLOperationDiff(removedOperation, addedOperation, bestMapper.getMappings());
						operationDiffList.add(operationSignatureDiff);
						refactorings.addAll(operationSignatureDiff.getRefactorings());
						if(!removedOperation.getName().equals(addedOperation.getName()) &&
								!(removedOperation.isConstructor() && addedOperation.isConstructor())) {
							RenameOperationRefactoring rename = new RenameOperationRefactoring(bestMapper);
							refactorings.add(rename);
						}
						this.addOperationBodyMapper(bestMapper);
					}
				}
			}
		}
	}

	private Set<MethodInvocationReplacement> findConsistentMethodInvocationRenames() {
		Set<MethodInvocationReplacement> allConsistentMethodInvocationRenames = new LinkedHashSet<MethodInvocationReplacement>();
		Set<MethodInvocationReplacement> allInconsistentMethodInvocationRenames = new LinkedHashSet<MethodInvocationReplacement>();
		for(UMLOperationBodyMapper bodyMapper : operationBodyMapperList) {
			Set<MethodInvocationReplacement> methodInvocationRenames = bodyMapper.getMethodInvocationRenameReplacements();
			ConsistentReplacementDetector.updateRenames(allConsistentMethodInvocationRenames, allInconsistentMethodInvocationRenames,
					methodInvocationRenames);
		}
		allConsistentMethodInvocationRenames.removeAll(allInconsistentMethodInvocationRenames);
		return allConsistentMethodInvocationRenames;
	}

	private void updateMapperSet(TreeSet<UMLOperationBodyMapper> mapperSet, UMLOperation removedOperation, UMLOperation addedOperation, int differenceInPosition) throws RefactoringMinerTimedOutException {
		UMLOperationBodyMapper operationBodyMapper = new UMLOperationBodyMapper(removedOperation, addedOperation, this);
		List<AbstractCodeMapping> totalMappings = new ArrayList<AbstractCodeMapping>(operationBodyMapper.getMappings());
		int mappings = operationBodyMapper.mappingsWithoutBlocks();
		if(mappings > 0) {
			int absoluteDifferenceInPosition = computeAbsoluteDifferenceInPositionWithinClass(removedOperation, addedOperation);
			if(exactMappings(operationBodyMapper, mappings)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT1AndT2(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					compatibleSignatures(removedOperation, addedOperation, absoluteDifferenceInPosition)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT2(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					isPartOfMethodExtracted(removedOperation, addedOperation)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT1(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					isPartOfMethodInlined(removedOperation, addedOperation)) {
				mapperSet.add(operationBodyMapper);
			}
		}
		else {
			for(MethodInvocationReplacement replacement : consistentMethodInvocationRenames) {
				if(replacement.getInvokedOperationBefore().matchesOperation(removedOperation) &&
						replacement.getInvokedOperationAfter().matchesOperation(addedOperation)) {
					mapperSet.add(operationBodyMapper);
					break;
				}
			}
		}
		if(totalMappings.size() > 0) {
			int absoluteDifferenceInPosition = computeAbsoluteDifferenceInPositionWithinClass(removedOperation, addedOperation);
			if(singleUnmatchedStatementCallsAddedOperation(operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					compatibleSignatures(removedOperation, addedOperation, absoluteDifferenceInPosition)) {
				mapperSet.add(operationBodyMapper);
			}
		}
	}

	private void updateMapperSet(TreeSet<UMLOperationBodyMapper> mapperSet, UMLOperation removedOperation, UMLOperation operationInsideAnonymousClass, UMLOperation addedOperation, int differenceInPosition) throws RefactoringMinerTimedOutException {
		UMLOperationBodyMapper operationBodyMapper = new UMLOperationBodyMapper(removedOperation, operationInsideAnonymousClass, this);
		int mappings = operationBodyMapper.mappingsWithoutBlocks();
		if(mappings > 0) {
			int absoluteDifferenceInPosition = computeAbsoluteDifferenceInPositionWithinClass(removedOperation, addedOperation);
			if(exactMappings(operationBodyMapper, mappings)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT1AndT2(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					compatibleSignatures(removedOperation, addedOperation, absoluteDifferenceInPosition)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT2(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					isPartOfMethodExtracted(removedOperation, addedOperation)) {
				mapperSet.add(operationBodyMapper);
			}
			else if(mappedElementsMoreThanNonMappedT1(mappings, operationBodyMapper) &&
					absoluteDifferenceInPosition <= differenceInPosition &&
					isPartOfMethodInlined(removedOperation, addedOperation)) {
				mapperSet.add(operationBodyMapper);
			}
		}
	}

	private boolean exactMappings(UMLOperationBodyMapper operationBodyMapper, int mappings) {
		if(allMappingsAreExactMatches(operationBodyMapper, mappings)) {
			if(operationBodyMapper.nonMappedElementsT1() == 0 && operationBodyMapper.nonMappedElementsT2() == 0)
				return true;
			else if(operationBodyMapper.nonMappedElementsT1() > 0 && operationBodyMapper.getNonMappedInnerNodesT1().size() == 0 && operationBodyMapper.nonMappedElementsT2() == 0) {
				int countableStatements = 0;
				int parameterizedVariableDeclarationStatements = 0;
				UMLOperation addedOperation = operationBodyMapper.getOperation2();
				List<String> nonMappedLeavesT1 = new ArrayList<String>();
				for(StatementObject statement : operationBodyMapper.getNonMappedLeavesT1()) {
					if(statement.countableStatement()) {
						nonMappedLeavesT1.add(statement.getString());
						for(String parameterName : addedOperation.getParameterNameList()) {
							if(statement.getVariableDeclaration(parameterName) != null) {
								parameterizedVariableDeclarationStatements++;
								break;
							}
						}
						countableStatements++;
					}
				}
				int nonMappedLeavesExactlyMatchedInTheBodyOfAddedOperation = 0;
				for(UMLOperation operation : addedOperations) {
					if(!operation.equals(addedOperation) && operation.getBody() != null) {
						for(StatementObject statement : operation.getBody().getCompositeStatement().getLeaves()) {
							if(nonMappedLeavesT1.contains(statement.getString())) {
								nonMappedLeavesExactlyMatchedInTheBodyOfAddedOperation++;
							}
						}
					}
				}
				return (countableStatements == parameterizedVariableDeclarationStatements || countableStatements == nonMappedLeavesExactlyMatchedInTheBodyOfAddedOperation + parameterizedVariableDeclarationStatements) && countableStatements > 0;
			}
			else if(operationBodyMapper.nonMappedElementsT1() == 0 && operationBodyMapper.nonMappedElementsT2() > 0 && operationBodyMapper.getNonMappedInnerNodesT2().size() == 0) {
				int countableStatements = 0;
				int parameterizedVariableDeclarationStatements = 0;
				UMLOperation removedOperation = operationBodyMapper.getOperation1();
				for(StatementObject statement : operationBodyMapper.getNonMappedLeavesT2()) {
					if(statement.countableStatement()) {
						for(String parameterName : removedOperation.getParameterNameList()) {
							if(statement.getVariableDeclaration(parameterName) != null) {
								parameterizedVariableDeclarationStatements++;
								break;
							}
						}
						countableStatements++;
					}
				}
				return countableStatements == parameterizedVariableDeclarationStatements && countableStatements > 0;
			}
			else if((operationBodyMapper.nonMappedElementsT1() == 1 || operationBodyMapper.nonMappedElementsT2() == 1) &&
					operationBodyMapper.getNonMappedInnerNodesT1().size() == 0 && operationBodyMapper.getNonMappedInnerNodesT2().size() == 0) {
				StatementObject statementUsingParameterAsInvoker1 = null;
				UMLOperation removedOperation = operationBodyMapper.getOperation1();
				for(StatementObject statement : operationBodyMapper.getNonMappedLeavesT1()) {
					if(statement.countableStatement()) {
						for(String parameterName : removedOperation.getParameterNameList()) {
							OperationInvocation invocation = statement.invocationCoveringEntireFragment();
							if(invocation != null && invocation.getExpression() != null && invocation.getExpression().equals(parameterName)) {
								statementUsingParameterAsInvoker1 = statement;
								break;
							}
						}
					}
				}
				StatementObject statementUsingParameterAsInvoker2 = null;
				UMLOperation addedOperation = operationBodyMapper.getOperation2();
				for(StatementObject statement : operationBodyMapper.getNonMappedLeavesT2()) {
					if(statement.countableStatement()) {
						for(String parameterName : addedOperation.getParameterNameList()) {
							OperationInvocation invocation = statement.invocationCoveringEntireFragment();
							if(invocation != null && invocation.getExpression() != null && invocation.getExpression().equals(parameterName)) {
								statementUsingParameterAsInvoker2 = statement;
								break;
							}
						}
					}
				}
				if(statementUsingParameterAsInvoker1 != null && statementUsingParameterAsInvoker2 != null) {
					for(AbstractCodeMapping mapping : operationBodyMapper.getMappings()) {
						if(mapping.getFragment1() instanceof CompositeStatementObject && mapping.getFragment2() instanceof CompositeStatementObject) {
							CompositeStatementObject parent1 = (CompositeStatementObject)mapping.getFragment1();
							CompositeStatementObject parent2 = (CompositeStatementObject)mapping.getFragment2();
							if(parent1.getLeaves().contains(statementUsingParameterAsInvoker1) && parent2.getLeaves().contains(statementUsingParameterAsInvoker2)) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}

	private boolean mappedElementsMoreThanNonMappedT1AndT2(int mappings, UMLOperationBodyMapper operationBodyMapper) {
		int nonMappedElementsT1 = operationBodyMapper.nonMappedElementsT1();
		int nonMappedElementsT2 = operationBodyMapper.nonMappedElementsT2();
		return (mappings > nonMappedElementsT1 && mappings > nonMappedElementsT2) ||
				(nonMappedElementsT1 == 0 && mappings > Math.floor(nonMappedElementsT2/2.0)) ||
				(mappings == 1 && nonMappedElementsT1 + nonMappedElementsT2 == 1 && operationBodyMapper.getOperation1().getName().equals(operationBodyMapper.getOperation2().getName()));
	}

	private boolean mappedElementsMoreThanNonMappedT2(int mappings, UMLOperationBodyMapper operationBodyMapper) {
		int nonMappedElementsT2 = operationBodyMapper.nonMappedElementsT2();
		int nonMappedElementsT2CallingAddedOperation = operationBodyMapper.nonMappedElementsT2CallingAddedOperation(addedOperations);
		int nonMappedElementsT2WithoutThoseCallingAddedOperation = nonMappedElementsT2 - nonMappedElementsT2CallingAddedOperation;
		return mappings > nonMappedElementsT2 || (mappings >= nonMappedElementsT2WithoutThoseCallingAddedOperation &&
				nonMappedElementsT2CallingAddedOperation >= nonMappedElementsT2WithoutThoseCallingAddedOperation);
	}

	private boolean mappedElementsMoreThanNonMappedT1(int mappings, UMLOperationBodyMapper operationBodyMapper) {
		int nonMappedElementsT1 = operationBodyMapper.nonMappedElementsT1();
		int nonMappedElementsT1CallingRemovedOperation = operationBodyMapper.nonMappedElementsT1CallingRemovedOperation(removedOperations);
		int nonMappedElementsT1WithoutThoseCallingRemovedOperation = nonMappedElementsT1 - nonMappedElementsT1CallingRemovedOperation;
		return mappings > nonMappedElementsT1 || (mappings >= nonMappedElementsT1WithoutThoseCallingRemovedOperation &&
				nonMappedElementsT1CallingRemovedOperation >= nonMappedElementsT1WithoutThoseCallingRemovedOperation);
	}

	private UMLOperationBodyMapper findBestMapper(TreeSet<UMLOperationBodyMapper> mapperSet) {
		List<UMLOperationBodyMapper> mapperList = new ArrayList<UMLOperationBodyMapper>(mapperSet);
		UMLOperationBodyMapper bestMapper = mapperSet.first();
		UMLOperation bestMapperOperation1 = bestMapper.getOperation1();
		UMLOperation bestMapperOperation2 = bestMapper.getOperation2();
		if(bestMapperOperation1.equalReturnParameter(bestMapperOperation2) &&
				bestMapperOperation1.getName().equals(bestMapperOperation2.getName()) &&
				bestMapperOperation1.commonParameterTypes(bestMapperOperation2).size() > 0) {
			return bestMapper;
		}
		for(int i=1; i<mapperList.size(); i++) {
			UMLOperationBodyMapper mapper = mapperList.get(i);
			UMLOperation operation2 = mapper.getOperation2();
			List<OperationInvocation> operationInvocations2 = operation2.getAllOperationInvocations();
			boolean anotherMapperCallsOperation2OfTheBestMapper = false;
			for(OperationInvocation invocation : operationInvocations2) {
				if(invocation.matchesOperation(bestMapper.getOperation2(), operation2.variableTypeMap(), modelDiff) && !invocation.matchesOperation(bestMapper.getOperation1(), operation2.variableTypeMap(), modelDiff) &&
						!operationContainsMethodInvocationWithTheSameNameAndCommonArguments(invocation, removedOperations)) {
					anotherMapperCallsOperation2OfTheBestMapper = true;
					break;
				}
			}
			UMLOperation operation1 = mapper.getOperation1();
			List<OperationInvocation> operationInvocations1 = operation1.getAllOperationInvocations();
			boolean anotherMapperCallsOperation1OfTheBestMapper = false;
			for(OperationInvocation invocation : operationInvocations1) {
				if(invocation.matchesOperation(bestMapper.getOperation1(), operation1.variableTypeMap(), modelDiff) && !invocation.matchesOperation(bestMapper.getOperation2(), operation1.variableTypeMap(), modelDiff) &&
						!operationContainsMethodInvocationWithTheSameNameAndCommonArguments(invocation, addedOperations)) {
					anotherMapperCallsOperation1OfTheBestMapper = true;
					break;
				}
			}
			boolean nextMapperMatchesConsistentRename = matchesConsistentMethodInvocationRename(mapper, consistentMethodInvocationRenames);
			boolean bestMapperMismatchesConsistentRename = mismatchesConsistentMethodInvocationRename(bestMapper, consistentMethodInvocationRenames);
			if(bestMapperMismatchesConsistentRename && nextMapperMatchesConsistentRename) {
				bestMapper = mapper;
				break;
			}
			if(anotherMapperCallsOperation2OfTheBestMapper || anotherMapperCallsOperation1OfTheBestMapper) {
				bestMapper = mapper;
				break;
			}
		}
		if(mismatchesConsistentMethodInvocationRename(bestMapper, consistentMethodInvocationRenames)) {
			return null;
		}
		return bestMapper;
	}

	private boolean matchesConsistentMethodInvocationRename(UMLOperationBodyMapper mapper, Set<MethodInvocationReplacement> consistentMethodInvocationRenames) {
		for(MethodInvocationReplacement rename : consistentMethodInvocationRenames) {
			if(mapper.getOperation1().getName().equals(rename.getBefore()) && mapper.getOperation2().getName().equals(rename.getAfter())) {
				return true;
			}
		}
		return false;
	}

	private boolean mismatchesConsistentMethodInvocationRename(UMLOperationBodyMapper mapper, Set<MethodInvocationReplacement> consistentMethodInvocationRenames) {
		for(MethodInvocationReplacement rename : consistentMethodInvocationRenames) {
			if(mapper.getOperation1().getName().equals(rename.getBefore()) && !mapper.getOperation2().getName().equals(rename.getAfter())) {
				return true;
			}
			else if(!mapper.getOperation1().getName().equals(rename.getBefore()) && mapper.getOperation2().getName().equals(rename.getAfter())) {
				return true;
			}
		}
		return false;
	}

	private boolean operationContainsMethodInvocationWithTheSameNameAndCommonArguments(OperationInvocation invocation, List<UMLOperation> operations) {
		for(UMLOperation operation : operations) {
			List<OperationInvocation> operationInvocations = operation.getAllOperationInvocations();
			for(OperationInvocation operationInvocation : operationInvocations) {
				Set<String> argumentIntersection = new LinkedHashSet<String>(operationInvocation.getArguments());
				argumentIntersection.retainAll(invocation.getArguments());
				if(operationInvocation.getMethodName().equals(invocation.getMethodName()) && !argumentIntersection.isEmpty()) {
					return true;
				}
				else if(argumentIntersection.size() > 0 && argumentIntersection.size() == invocation.getArguments().size()) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean singleUnmatchedStatementCallsAddedOperation(UMLOperationBodyMapper operationBodyMapper) {
		List<StatementObject> nonMappedLeavesT1 = operationBodyMapper.getNonMappedLeavesT1();
		List<StatementObject> nonMappedLeavesT2 = operationBodyMapper.getNonMappedLeavesT2();
		if(nonMappedLeavesT1.size() == 1 && nonMappedLeavesT2.size() == 1) {
			StatementObject statementT2 = nonMappedLeavesT2.get(0);
			OperationInvocation invocationT2 = statementT2.invocationCoveringEntireFragment();
			if(invocationT2 != null) {
				for(UMLOperation addedOperation : addedOperations) {
					if(invocationT2.matchesOperation(addedOperation, operationBodyMapper.getOperation2().variableTypeMap(), modelDiff)) {
						StatementObject statementT1 = nonMappedLeavesT1.get(0);
						OperationInvocation invocationT1 = statementT1.invocationCoveringEntireFragment();
						if(invocationT1 != null && addedOperation.getAllOperationInvocations().contains(invocationT1)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean isPartOfMethodExtracted(UMLOperation removedOperation, UMLOperation addedOperation) {
		List<OperationInvocation> removedOperationInvocations = removedOperation.getAllOperationInvocations();
		List<OperationInvocation> addedOperationInvocations = addedOperation.getAllOperationInvocations();
		Set<OperationInvocation> intersection = new LinkedHashSet<OperationInvocation>(removedOperationInvocations);
		intersection.retainAll(addedOperationInvocations);
		int numberOfInvocationsMissingFromRemovedOperation = new LinkedHashSet<OperationInvocation>(removedOperationInvocations).size() - intersection.size();
		
		Set<OperationInvocation> operationInvocationsInMethodsCalledByAddedOperation = new LinkedHashSet<OperationInvocation>();
		for(OperationInvocation addedOperationInvocation : addedOperationInvocations) {
			if(!intersection.contains(addedOperationInvocation)) {
				for(UMLOperation operation : addedOperations) {
					if(!operation.equals(addedOperation) && operation.getBody() != null) {
						if(addedOperationInvocation.matchesOperation(operation, addedOperation.variableTypeMap(), modelDiff)) {
							//addedOperation calls another added method
							operationInvocationsInMethodsCalledByAddedOperation.addAll(operation.getAllOperationInvocations());
						}
					}
				}
			}
		}
		Set<OperationInvocation> newIntersection = new LinkedHashSet<OperationInvocation>(removedOperationInvocations);
		newIntersection.retainAll(operationInvocationsInMethodsCalledByAddedOperation);
		
		Set<OperationInvocation> removedOperationInvocationsWithIntersectionsAndGetterInvocationsSubtracted = new LinkedHashSet<OperationInvocation>(removedOperationInvocations);
		removedOperationInvocationsWithIntersectionsAndGetterInvocationsSubtracted.removeAll(intersection);
		removedOperationInvocationsWithIntersectionsAndGetterInvocationsSubtracted.removeAll(newIntersection);
		for(Iterator<OperationInvocation> operationInvocationIterator = removedOperationInvocationsWithIntersectionsAndGetterInvocationsSubtracted.iterator(); operationInvocationIterator.hasNext();) {
			OperationInvocation invocation = operationInvocationIterator.next();
			if(invocation.getMethodName().startsWith("get")) {
				operationInvocationIterator.remove();
			}
		}
		int numberOfInvocationsOriginallyCalledByRemovedOperationFoundInOtherAddedOperations = newIntersection.size();
		int numberOfInvocationsMissingFromRemovedOperationWithoutThoseFoundInOtherAddedOperations = numberOfInvocationsMissingFromRemovedOperation - numberOfInvocationsOriginallyCalledByRemovedOperationFoundInOtherAddedOperations;
		return numberOfInvocationsOriginallyCalledByRemovedOperationFoundInOtherAddedOperations > numberOfInvocationsMissingFromRemovedOperationWithoutThoseFoundInOtherAddedOperations ||
				numberOfInvocationsOriginallyCalledByRemovedOperationFoundInOtherAddedOperations > removedOperationInvocationsWithIntersectionsAndGetterInvocationsSubtracted.size();
	}

	private boolean isPartOfMethodInlined(UMLOperation removedOperation, UMLOperation addedOperation) {
		List<OperationInvocation> removedOperationInvocations = removedOperation.getAllOperationInvocations();
		List<OperationInvocation> addedOperationInvocations = addedOperation.getAllOperationInvocations();
		Set<OperationInvocation> intersection = new LinkedHashSet<OperationInvocation>(removedOperationInvocations);
		intersection.retainAll(addedOperationInvocations);
		int numberOfInvocationsMissingFromAddedOperation = new LinkedHashSet<OperationInvocation>(addedOperationInvocations).size() - intersection.size();
		
		Set<OperationInvocation> operationInvocationsInMethodsCalledByRemovedOperation = new LinkedHashSet<OperationInvocation>();
		for(OperationInvocation removedOperationInvocation : removedOperationInvocations) {
			if(!intersection.contains(removedOperationInvocation)) {
				for(UMLOperation operation : removedOperations) {
					if(!operation.equals(removedOperation) && operation.getBody() != null) {
						if(removedOperationInvocation.matchesOperation(operation, removedOperation.variableTypeMap(), modelDiff)) {
							//removedOperation calls another removed method
							operationInvocationsInMethodsCalledByRemovedOperation.addAll(operation.getAllOperationInvocations());
						}
					}
				}
			}
		}
		Set<OperationInvocation> newIntersection = new LinkedHashSet<OperationInvocation>(addedOperationInvocations);
		newIntersection.retainAll(operationInvocationsInMethodsCalledByRemovedOperation);
		
		int numberOfInvocationsCalledByAddedOperationFoundInOtherRemovedOperations = newIntersection.size();
		int numberOfInvocationsMissingFromAddedOperationWithoutThoseFoundInOtherRemovedOperations = numberOfInvocationsMissingFromAddedOperation - numberOfInvocationsCalledByAddedOperationFoundInOtherRemovedOperations;
		return numberOfInvocationsCalledByAddedOperationFoundInOtherRemovedOperations > numberOfInvocationsMissingFromAddedOperationWithoutThoseFoundInOtherRemovedOperations;
	}

	private boolean allMappingsAreExactMatches(UMLOperationBodyMapper operationBodyMapper, int mappings) {
		int tryMappings = 0;
		int mappingsWithTypeReplacement = 0;
		for(AbstractCodeMapping mapping : operationBodyMapper.getMappings()) {
			if(mapping.getFragment1().getString().equals("try") && mapping.getFragment2().getString().equals("try")) {
				tryMappings++;
			}
			if(mapping.containsReplacement(ReplacementType.TYPE)) {
				mappingsWithTypeReplacement++;
			}
		}
		if(mappings == operationBodyMapper.exactMatches() + tryMappings) {
			return true;
		}
		if(mappings == operationBodyMapper.exactMatches() + tryMappings + mappingsWithTypeReplacement && mappings > mappingsWithTypeReplacement) {
			return true;
		}
		return false;
	}

	private boolean compatibleSignatures(UMLOperation removedOperation, UMLOperation addedOperation, int absoluteDifferenceInPosition) {
		return addedOperation.compatibleSignature(removedOperation) ||
		(
		(absoluteDifferenceInPosition == 0 || operationsBeforeAndAfterMatch(removedOperation, addedOperation)) &&
		!gettersWithDifferentReturnType(removedOperation, addedOperation) &&
		(addedOperation.getParameterTypeList().equals(removedOperation.getParameterTypeList()) || addedOperation.normalizedNameDistance(removedOperation) <= MAX_OPERATION_NAME_DISTANCE)
		);
	}

	private boolean gettersWithDifferentReturnType(UMLOperation removedOperation, UMLOperation addedOperation) {
		if(removedOperation.isGetter() && addedOperation.isGetter()) {
			UMLType type1 = removedOperation.getReturnParameter().getType();
			UMLType type2 = addedOperation.getReturnParameter().getType();
			if(!removedOperation.equalReturnParameter(addedOperation) && !type1.compatibleTypes(type2)) {
				return true;
			}
		}
		return false;
	}

	private boolean operationsBeforeAndAfterMatch(UMLOperation removedOperation, UMLOperation addedOperation) {
		UMLOperation operationBefore1 = null;
		UMLOperation operationAfter1 = null;
		List<UMLOperation> originalClassOperations = originalClass.getOperations();
		for(int i=0; i<originalClassOperations.size(); i++) {
			UMLOperation current = originalClassOperations.get(i);
			if(current.equals(removedOperation)) {
				if(i>0) {
					operationBefore1 = originalClassOperations.get(i-1);
				}
				if(i<originalClassOperations.size()-1) {
					operationAfter1 = originalClassOperations.get(i+1);
				}
			}
		}
		
		UMLOperation operationBefore2 = null;
		UMLOperation operationAfter2 = null;
		List<UMLOperation> nextClassOperations = nextClass.getOperations();
		for(int i=0; i<nextClassOperations.size(); i++) {
			UMLOperation current = nextClassOperations.get(i);
			if(current.equals(addedOperation)) {
				if(i>0) {
					operationBefore2 = nextClassOperations.get(i-1);
				}
				if(i<nextClassOperations.size()-1) {
					operationAfter2 = nextClassOperations.get(i+1);
				}
			}
		}
		
		boolean operationsBeforeMatch = false;
		if(operationBefore1 != null && operationBefore2 != null) {
			operationsBeforeMatch = operationBefore1.equalParameterTypes(operationBefore2) && operationBefore1.getName().equals(operationBefore2.getName());
		}
		
		boolean operationsAfterMatch = false;
		if(operationAfter1 != null && operationAfter2 != null) {
			operationsAfterMatch = operationAfter1.equalParameterTypes(operationAfter2) && operationAfter1.getName().equals(operationAfter2.getName());
		}
		
		return operationsBeforeMatch || operationsAfterMatch;
	}

	private void checkForInlinedOperations() throws RefactoringMinerTimedOutException {
		List<UMLOperation> operationsToBeRemoved = new ArrayList<UMLOperation>();
		for(Iterator<UMLOperation> removedOperationIterator = removedOperations.iterator(); removedOperationIterator.hasNext();) {
			UMLOperation removedOperation = removedOperationIterator.next();
			for(UMLOperationBodyMapper mapper : getOperationBodyMapperList()) {
				if(!mapper.getNonMappedLeavesT2().isEmpty() || !mapper.getNonMappedInnerNodesT2().isEmpty() ||
					!mapper.getReplacementsInvolvingMethodInvocation().isEmpty()) {
					List<OperationInvocation> operationInvocations = getInvocationsInTargetOperationBeforeInline(mapper);
					List<OperationInvocation> removedOperationInvocations = matchingInvocations(removedOperation, operationInvocations, mapper.getOperation1().variableTypeMap());
					if(removedOperationInvocations.size() > 0 && !invocationMatchesWithAddedOperation(removedOperationInvocations.get(0), mapper.getOperation1().variableTypeMap(), mapper.getOperation2().getAllOperationInvocations())) {
						OperationInvocation removedOperationInvocation = removedOperationInvocations.get(0);
						List<String> arguments = removedOperationInvocation.getArguments();
						List<String> parameters = removedOperation.getParameterNameList();
						Map<String, String> parameterToArgumentMap = new LinkedHashMap<String, String>();
						//special handling for methods with varargs parameter for which no argument is passed in the matching invocation
						int size = Math.min(arguments.size(), parameters.size());
						for(int i=0; i<size; i++) {
							parameterToArgumentMap.put(parameters.get(i), arguments.get(i));
						}
						UMLOperationBodyMapper operationBodyMapper = new UMLOperationBodyMapper(removedOperation, mapper, parameterToArgumentMap, this);
						if(inlineMatchCondition(operationBodyMapper)) {
							InlineOperationRefactoring inlineOperationRefactoring =	new InlineOperationRefactoring(operationBodyMapper, mapper.getOperation1(), removedOperationInvocations);
							refactorings.add(inlineOperationRefactoring);
							processMapperRefactorings(operationBodyMapper, refactorings);
							mapper.addChildMapper(operationBodyMapper);
							operationsToBeRemoved.add(removedOperation);
						}
					}
				}
			}
		}
		removedOperations.removeAll(operationsToBeRemoved);
	}

	private List<OperationInvocation> getInvocationsInTargetOperationBeforeInline(UMLOperationBodyMapper mapper) {
		List<OperationInvocation> operationInvocations = mapper.getOperation1().getAllOperationInvocations();
		for(StatementObject statement : mapper.getNonMappedLeavesT1()) {
			ExtractOperationDetection.addStatementInvocations(operationInvocations, statement);
			for(UMLAnonymousClass anonymousClass : removedAnonymousClasses) {
				if(statement.getLocationInfo().subsumes(anonymousClass.getLocationInfo())) {
					for(UMLOperation anonymousOperation : anonymousClass.getOperations()) {
						for(OperationInvocation anonymousInvocation : anonymousOperation.getAllOperationInvocations()) {
							if(!ExtractOperationDetection.containsInvocation(operationInvocations, anonymousInvocation)) {
								operationInvocations.add(anonymousInvocation);
							}
						}
					}
				}
			}
		}
		return operationInvocations;
	}

	private boolean inlineMatchCondition(UMLOperationBodyMapper operationBodyMapper) {
		int mappings = operationBodyMapper.mappingsWithoutBlocks();
		int nonMappedElementsT1 = operationBodyMapper.nonMappedElementsT1();
		List<AbstractCodeMapping> exactMatchList = operationBodyMapper.getExactMatches();
		int exactMatches = exactMatchList.size();
		return mappings > 0 && (mappings > nonMappedElementsT1 ||
				(exactMatches == 1 && !exactMatchList.get(0).getFragment1().throwsNewException() && nonMappedElementsT1-exactMatches < 10) ||
				(exactMatches > 1 && nonMappedElementsT1-exactMatches < 20));
	}

	private boolean invocationMatchesWithAddedOperation(OperationInvocation removedOperationInvocation, Map<String, UMLType> variableTypeMap, List<OperationInvocation> operationInvocationsInNewMethod) {
		if(operationInvocationsInNewMethod.contains(removedOperationInvocation)) {
			for(UMLOperation addedOperation : addedOperations) {
				if(removedOperationInvocation.matchesOperation(addedOperation, variableTypeMap, modelDiff)) {
					return true;
				}
			}
		}
		return false;
	}

	private void checkForExtractedOperations() throws RefactoringMinerTimedOutException {
		List<UMLOperation> operationsToBeRemoved = new ArrayList<UMLOperation>();
		for(Iterator<UMLOperation> addedOperationIterator = addedOperations.iterator(); addedOperationIterator.hasNext();) {
			UMLOperation addedOperation = addedOperationIterator.next();
			for(UMLOperationBodyMapper mapper : getOperationBodyMapperList()) {
				ExtractOperationDetection detection = new ExtractOperationDetection(mapper, addedOperations, this, modelDiff);
				List<ExtractOperationRefactoring> refs = detection.check(addedOperation);
				for(ExtractOperationRefactoring refactoring : refs) {
					refactorings.add(refactoring);
					UMLOperationBodyMapper operationBodyMapper = refactoring.getBodyMapper();
					processMapperRefactorings(operationBodyMapper, refactorings);
					mapper.addChildMapper(operationBodyMapper);
					operationsToBeRemoved.add(addedOperation);
				}
			}
		}
		addedOperations.removeAll(operationsToBeRemoved);
	}

	private List<OperationInvocation> matchingInvocations(UMLOperation operation,
			List<OperationInvocation> operationInvocations, Map<String, UMLType> variableTypeMap) {
		List<OperationInvocation> addedOperationInvocations = new ArrayList<OperationInvocation>();
		for(OperationInvocation invocation : operationInvocations) {
			if(invocation.matchesOperation(operation, variableTypeMap, modelDiff)) {
				addedOperationInvocations.add(invocation);
			}
		}
		return addedOperationInvocations;
	}

	public boolean isEmpty() {
		return addedOperations.isEmpty() && removedOperations.isEmpty() &&
			addedAttributes.isEmpty() && removedAttributes.isEmpty() &&
			operationDiffList.isEmpty() && attributeDiffList.isEmpty() &&
			operationBodyMapperList.isEmpty() &&
			!visibilityChanged && !abstractionChanged;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		if(!isEmpty())
			sb.append(originalClass.getName()).append(":").append("\n");
		if(visibilityChanged) {
			sb.append("\t").append("visibility changed from " + oldVisibility + " to " + newVisibility).append("\n");
		}
		if(abstractionChanged) {
			sb.append("\t").append("abstraction changed from " + (oldAbstraction ? "abstract" : "concrete") + " to " +
					(newAbstraction ? "abstract" : "concrete")).append("\n");
		}
		Collections.sort(removedOperations);
		for(UMLOperation umlOperation : removedOperations) {
			sb.append("operation " + umlOperation + " removed").append("\n");
		}
		Collections.sort(addedOperations);
		for(UMLOperation umlOperation : addedOperations) {
			sb.append("operation " + umlOperation + " added").append("\n");
		}
		Collections.sort(removedAttributes);
		for(UMLAttribute umlAttribute : removedAttributes) {
			sb.append("attribute " + umlAttribute + " removed").append("\n");
		}
		Collections.sort(addedAttributes);
		for(UMLAttribute umlAttribute : addedAttributes) {
			sb.append("attribute " + umlAttribute + " added").append("\n");
		}
		for(UMLOperationDiff operationDiff : operationDiffList) {
			sb.append(operationDiff);
		}
		for(UMLAttributeDiff attributeDiff : attributeDiffList) {
			sb.append(attributeDiff);
		}
		Collections.sort(operationBodyMapperList);
		for(UMLOperationBodyMapper operationBodyMapper : operationBodyMapperList) {
			sb.append(operationBodyMapper);
		}
		return sb.toString();
	}

	public int compareTo(UMLClassBaseDiff other) {
		return this.originalClass.getName().compareTo(other.originalClass.getName());
	}

	public boolean containsExtractOperationRefactoring(UMLOperation sourceOperationBeforeExtraction, UMLOperation extractedOperation) {
		for(Refactoring ref : refactorings) {
			if(ref instanceof ExtractOperationRefactoring) {
				ExtractOperationRefactoring extractRef = (ExtractOperationRefactoring)ref;
				if(extractRef.getSourceOperationBeforeExtraction().equals(sourceOperationBeforeExtraction) &&
						extractRef.getExtractedOperation().equalSignature(extractedOperation)) {
					return true;
				}
			}
		}
		return false;
	}

	public UMLModelDiff getModelDiff() {
		return modelDiff;
	}
}