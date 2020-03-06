package gr.uom.java.xmi;

import gr.uom.java.xmi.TypeFactMiner.TypFct.Context;
import gr.uom.java.xmi.LocationInfo.CodeElementType;
import gr.uom.java.xmi.decomposition.OperationBody;
import gr.uom.java.xmi.decomposition.VariableDeclaration;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

import static gr.uom.java.xmi.TypeFactMiner.TypeGraphUtil.getTypeGraph;
import static java.util.stream.Collectors.*;

public class UMLModelASTReader {
	public static final String systemFileSeparator = Matcher.quoteReplacement(File.separator);
	private UMLModel umlModel;
	private String projectRoot;
	private ASTParser parser;

	// for GitHub API
	public UMLModelASTReader(Map<String, String> javaFileContents, Set<String> repositoryDirectories) {
		this.umlModel = new UMLModel(repositoryDirectories);
		this.parser = ASTParser.newParser(AST.JLS11);
		for(String filePath : javaFileContents.keySet()) {
			Map<String, String> options = JavaCore.getOptions();
			options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
			parser.setCompilerOptions(options);
			parser.setResolveBindings(false);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setStatementsRecovery(true);
			parser.setSource(javaFileContents.get(filePath).toCharArray());
			CompilationUnit compilationUnit = (CompilationUnit)parser.createAST(null);
			processCompilationUnit(filePath, compilationUnit);
		}
	}

	// for .git
	public UMLModelASTReader(Map<String, String> javaFileContents, Set<String> repositoryDirectories, Repository repo, RevCommit commit) {
		this.umlModel = new UMLModel(repositoryDirectories, repo, commit);
		this.parser = ASTParser.newParser(AST.JLS11);
		for(String filePath : javaFileContents.keySet()) {
			Map<String, String> options = JavaCore.getOptions();
			options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
			options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
			parser.setCompilerOptions(options);
			parser.setResolveBindings(false);
			parser.setKind(ASTParser.K_COMPILATION_UNIT);
			parser.setStatementsRecovery(true);
			parser.setSource(javaFileContents.get(filePath).toCharArray());
			CompilationUnit compilationUnit = (CompilationUnit)parser.createAST(null);
			processCompilationUnit(filePath, compilationUnit);
		}
	}
	// for download and analyze
	public UMLModelASTReader(File rootFolder, List<String> javaFiles, Set<String> repositoryDirectories) {
		this(rootFolder, buildAstParser(rootFolder), javaFiles, repositoryDirectories);
	}

	public UMLModelASTReader(File rootFolder, ASTParser parser, List<String> javaFiles, Set<String> repositoryDirectories) {
		this.umlModel = new UMLModel(repositoryDirectories);
		this.projectRoot = rootFolder.getPath();
		this.parser = parser;
		final String[] emptyArray = new String[0];
		
		String[] filesArray = new String[javaFiles.size()];
		for (int i = 0; i < filesArray.length; i++) {
			filesArray[i] = rootFolder + File.separator + javaFiles.get(i).replaceAll("/", systemFileSeparator);
		}

		FileASTRequestor fileASTRequestor = new FileASTRequestor() { 
			@Override
			public void acceptAST(String sourceFilePath, CompilationUnit ast) {
				String relativePath = sourceFilePath.substring(projectRoot.length() + 1).replaceAll(systemFileSeparator, "/");
				processCompilationUnit(relativePath, ast);
			}
		};
		this.parser.createASTs((String[]) filesArray, null, emptyArray, fileASTRequestor, null);
	}

	private static ASTParser buildAstParser(File srcFolder) {
		ASTParser parser = ASTParser.newParser(AST.JLS11);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setResolveBindings(false);
		parser.setEnvironment(new String[0], new String[]{srcFolder.getPath()}, null, false);
		return parser;
	}

	public UMLModel getUmlModel() {
		return this.umlModel;
	}

	protected void processCompilationUnit(String sourceFilePath, CompilationUnit compilationUnit) {
		PackageDeclaration packageDeclaration = compilationUnit.getPackage();
		String packageName = null;
		if(packageDeclaration != null)
			packageName = packageDeclaration.getName().getFullyQualifiedName();
		else
			packageName = "";
		
		List<ImportDeclaration> imports = compilationUnit.imports();
		Map<Boolean, List<String>> importedTypes = imports.stream().collect(groupingBy(x -> x.isOnDemand(),
				collectingAndThen(toList(), l -> l.stream().map(x -> x.getName().getFullyQualifiedName()).collect(toList()))));

		List<AbstractTypeDeclaration> topLevelTypeDeclarations = compilationUnit.types();
        for(AbstractTypeDeclaration abstractTypeDeclaration : topLevelTypeDeclarations) {
        	if(abstractTypeDeclaration instanceof TypeDeclaration) {
        		TypeDeclaration topLevelTypeDeclaration = (TypeDeclaration)abstractTypeDeclaration;
        		processTypeDeclaration(compilationUnit, topLevelTypeDeclaration, packageName, sourceFilePath, importedTypes);
        	}
        }
	}

	private UMLJavadoc generateJavadoc(BodyDeclaration bodyDeclaration) {
		UMLJavadoc doc = null;
		Javadoc javaDoc = bodyDeclaration.getJavadoc();
		if(javaDoc != null) {
			doc = new UMLJavadoc();
			List<TagElement> tags = javaDoc.tags();
			for(TagElement tag : tags) {
				UMLTagElement tagElement = new UMLTagElement(tag.getTagName());
				List<IDocElement> fragments = tag.fragments();
				for(IDocElement docElement : fragments) {
					tagElement.addFragment(docElement.toString());
				}
				doc.addTag(tagElement);
			}
		}
		return doc;
	}


	private void processTypeDeclaration(CompilationUnit cu, TypeDeclaration typeDeclaration, String packageName, String sourceFile,
										Map<Boolean, List<String>> importedTypes) {
		UMLJavadoc javadoc = generateJavadoc(typeDeclaration);
		if(javadoc != null && javadoc.contains("Source code generated using FreeMarker template")) {
			return;
		}
		String className = typeDeclaration.getName().getFullyQualifiedName();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, typeDeclaration, CodeElementType.TYPE_DECLARATION);
		UMLClass umlClass = new UMLClass(packageName, className, locationInfo, typeDeclaration.isPackageMemberTypeDeclaration(), importedTypes, new Context(cu, typeDeclaration.getMethods()));
		umlClass.setJavadoc(javadoc);
		
		if(typeDeclaration.isInterface()) {
			umlClass.setInterface(true);
    	}
    	
    	int modifiers = typeDeclaration.getModifiers();
    	if((modifiers & Modifier.ABSTRACT) != 0)
    		umlClass.setAbstract(true);
    	
    	if((modifiers & Modifier.PUBLIC) != 0)
    		umlClass.setVisibility("public");
    	else if((modifiers & Modifier.PROTECTED) != 0)
    		umlClass.setVisibility("protected");
    	else if((modifiers & Modifier.PRIVATE) != 0)
    		umlClass.setVisibility("private");
    	else
    		umlClass.setVisibility("package");
		
    	List<TypeParameter> typeParameters = typeDeclaration.typeParameters();
		for(TypeParameter typeParameter : typeParameters) {
			UMLTypeParameter umlTypeParameter = new UMLTypeParameter(typeParameter.getName().getFullyQualifiedName());
			List<Type> typeBounds = typeParameter.typeBounds();
			for(Type type : typeBounds) {
				umlTypeParameter.addTypeBound(UMLType.extractTypeObject(type.toString(),
						generateLocationInfo(cu, sourceFile, type, CodeElementType.TYPE)));
			}
    		umlClass.addTypeParameter(umlTypeParameter);
    	}
    	
    	Type superclassType = typeDeclaration.getSuperclassType();
    	if(superclassType != null) {
    		UMLType umlType = UMLType.extractTypeObject(UMLType.getTypeName(superclassType, 0),
    				generateLocationInfo(cu, sourceFile, superclassType, CodeElementType.TYPE));
    		UMLGeneralization umlGeneralization = new UMLGeneralization(umlClass, umlType.getClassType());
    		umlClass.setSuperclass(umlType);
    		getUmlModel().addGeneralization(umlGeneralization);
    	}
    	
    	List<Type> superInterfaceTypes = typeDeclaration.superInterfaceTypes();
    	for(Type interfaceType : superInterfaceTypes) {
    		UMLType umlType = UMLType.extractTypeObject(UMLType.getTypeName(interfaceType, 0),
    				generateLocationInfo(cu, sourceFile, interfaceType, CodeElementType.TYPE));
    		UMLRealization umlRealization = new UMLRealization(umlClass, umlType.getClassType());
    		umlClass.addImplementedInterface(umlType);
    		getUmlModel().addRealization(umlRealization);
    	}
    	
    	FieldDeclaration[] fieldDeclarations = typeDeclaration.getFields();
    	for(FieldDeclaration fieldDeclaration : fieldDeclarations) {
    		List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, umlClass.isInterface(), sourceFile);
    		for(UMLAttribute attribute : attributes) {
    			attribute.setClassName(umlClass.getName());
    			umlClass.addAttribute(attribute);
    		}
    	}
    	
    	MethodDeclaration[] methodDeclarations = typeDeclaration.getMethods();
    	for(MethodDeclaration methodDeclaration : methodDeclarations) {
    		UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, sourceFile, umlClass);
    		operation.setClassName(umlClass.getName());
    		umlClass.addOperation(operation);
    	}
    	
    	AnonymousClassDeclarationVisitor visitor = new AnonymousClassDeclarationVisitor();
    	typeDeclaration.accept(visitor);
    	Set<AnonymousClassDeclaration> anonymousClassDeclarations = visitor.getAnonymousClassDeclarations();
    	
    	DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    	for(AnonymousClassDeclaration anonymous : anonymousClassDeclarations) {
    		insertNode(anonymous, root);
    	}
    	
    	List<UMLAnonymousClass> createdAnonymousClasses = new ArrayList<UMLAnonymousClass>();
    	Enumeration enumeration = root.preorderEnumeration();
    	while(enumeration.hasMoreElements()) {
    		DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
    		if(node.getUserObject() != null) {
    			AnonymousClassDeclaration anonymous = (AnonymousClassDeclaration)node.getUserObject();
    			String anonymousBinaryName = getAnonymousBinaryName(node);
    			String anonymousCodePath = getAnonymousCodePath(node);
    			UMLAnonymousClass anonymousClass = processAnonymousClassDeclaration(cu, anonymous, packageName + "." + className, anonymousBinaryName, anonymousCodePath, sourceFile);
    			umlClass.addAnonymousClass(anonymousClass);
    			for(UMLOperation operation : umlClass.getOperations()) {
    				if(operation.getLocationInfo().subsumes(anonymousClass.getLocationInfo())) {
    					operation.addAnonymousClass(anonymousClass);
    				}
    			}
    			for(UMLAnonymousClass createdAnonymousClass : createdAnonymousClasses) {
    				for(UMLOperation operation : createdAnonymousClass.getOperations()) {
        				if(operation.getLocationInfo().subsumes(anonymousClass.getLocationInfo())) {
        					operation.addAnonymousClass(anonymousClass);
        				}
        			}
    			}
    			createdAnonymousClasses.add(anonymousClass);
    		}
    	}
    	
    	this.getUmlModel().addClass(umlClass);
		
		TypeDeclaration[] types = typeDeclaration.getTypes();
		for(TypeDeclaration type : types) {
			processTypeDeclaration(cu, type, umlClass.getName(), sourceFile, importedTypes);
		}
	}

	private static boolean isInterface(UMLAbstractClass umlCls){
		return umlCls instanceof UMLClass && ((UMLClass) umlCls).isInterface();
	}

	private UMLOperation processMethodDeclaration(CompilationUnit cu, MethodDeclaration methodDeclaration, String packageName, String sourceFile, UMLAbstractClass umlCls) {
		UMLJavadoc javadoc = generateJavadoc(methodDeclaration);
		String methodName = methodDeclaration.getName().getFullyQualifiedName();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, methodDeclaration, CodeElementType.METHOD_DECLARATION);
		UMLOperation umlOperation = new UMLOperation(methodName, locationInfo, umlCls, new Context(cu, Optional.of(methodDeclaration)));
		umlOperation.setJavadoc(javadoc);
		
		if(methodDeclaration.isConstructor())
			umlOperation.setConstructor(true);
		
		int methodModifiers = methodDeclaration.getModifiers();
		if((methodModifiers & Modifier.PUBLIC) != 0)
			umlOperation.setVisibility("public");
		else if((methodModifiers & Modifier.PROTECTED) != 0)
			umlOperation.setVisibility("protected");
		else if((methodModifiers & Modifier.PRIVATE) != 0)
			umlOperation.setVisibility("private");
		else if(isInterface(umlCls))
			umlOperation.setVisibility("public");
		else
			umlOperation.setVisibility("package");
		
		if((methodModifiers & Modifier.ABSTRACT) != 0)
			umlOperation.setAbstract(true);
		
		if((methodModifiers & Modifier.FINAL) != 0)
			umlOperation.setFinal(true);
		
		if((methodModifiers & Modifier.STATIC) != 0)
			umlOperation.setStatic(true);
		
		List<IExtendedModifier> extendedModifiers = methodDeclaration.modifiers();
		for(IExtendedModifier extendedModifier : extendedModifiers) {
			if(extendedModifier.isAnnotation()) {
				Annotation annotation = (Annotation)extendedModifier;
				if(annotation.getTypeName().getFullyQualifiedName().equals("Test")) {
					umlOperation.setTestAnnotation(true);
					break;
				}
			}
		}
		
		List<TypeParameter> typeParameters = methodDeclaration.typeParameters();
		for(TypeParameter typeParameter : typeParameters) {
			UMLTypeParameter umlTypeParameter = new UMLTypeParameter(typeParameter.getName().getFullyQualifiedName());
			List<Type> typeBounds = typeParameter.typeBounds();
			for(Type type : typeBounds) {
				umlTypeParameter.addTypeBound(UMLType.extractTypeObject(type.toString(),
						generateLocationInfo(cu, sourceFile, type, CodeElementType.TYPE)));
			}
			umlOperation.addTypeParameter(umlTypeParameter);
		}
		
		Block block = methodDeclaration.getBody();
		if(block != null) {
			OperationBody body = new OperationBody(cu, sourceFile, block);
			umlOperation.setBody(body);
			if(block.statements().size() == 0) {
				umlOperation.setEmptyBody(true);
			}
		}
		else {
			umlOperation.setBody(null);
		}
		
		Type returnType = methodDeclaration.getReturnType2();
		if(returnType != null) {
			UMLType type = UMLType.extractTypeObject(UMLType.getTypeName(returnType, 0),
					generateLocationInfo(cu, sourceFile, returnType, CodeElementType.TYPE));
			UMLParameter returnParameter = new UMLParameter("return", type, "return", false, getTypeGraph(returnType));
			umlOperation.addParameter(returnParameter);
		}
		List<SingleVariableDeclaration> parameters = methodDeclaration.parameters();
		for(SingleVariableDeclaration parameter : parameters) {
			Type parameterType = parameter.getType();
			String parameterName = parameter.getName().getFullyQualifiedName();
			String typeName = UMLType.getTypeName(parameterType, parameter.getExtraDimensions());
			if (parameter.isVarargs()) {
				typeName = typeName + "[]";
			}
			UMLType type = UMLType.extractTypeObject(typeName,
					generateLocationInfo(cu, sourceFile, parameterType, CodeElementType.TYPE));
			UMLParameter umlParameter = new UMLParameter(parameterName, type, "in", parameter.isVarargs(), getTypeGraph(parameterType));
			VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, parameter, parameter.isVarargs());
			variableDeclaration.setParameter(true);
			umlParameter.setVariableDeclaration(variableDeclaration);
			umlOperation.addParameter(umlParameter);
		}
		return umlOperation;
	}


	private List<UMLAttribute> processFieldDeclaration(CompilationUnit cu, FieldDeclaration fieldDeclaration, boolean isInterfaceField, String sourceFile) {
		UMLJavadoc javadoc = generateJavadoc(fieldDeclaration);
		List<UMLAttribute> attributes = new ArrayList<UMLAttribute>();
		Type fieldType = fieldDeclaration.getType();
		List<VariableDeclarationFragment> fragments = fieldDeclaration.fragments();
		for(VariableDeclarationFragment fragment : fragments) {
			UMLType type = UMLType.extractTypeObject(UMLType.getTypeName(fieldType, fragment.getExtraDimensions()),
					generateLocationInfo(cu, sourceFile, fieldType, CodeElementType.TYPE));
			String fieldName = fragment.getName().getFullyQualifiedName();
			LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, fragment, CodeElementType.FIELD_DECLARATION);
			UMLAttribute umlAttribute = new UMLAttribute(fieldName, type, locationInfo, getTypeGraph(fieldType));
			VariableDeclaration variableDeclaration = new VariableDeclaration(cu, sourceFile, fragment);
			variableDeclaration.setAttribute(true);
			umlAttribute.setVariableDeclaration(variableDeclaration);
			umlAttribute.setJavadoc(javadoc);
			
			int fieldModifiers = fieldDeclaration.getModifiers();
			if((fieldModifiers & Modifier.PUBLIC) != 0)
				umlAttribute.setVisibility("public");
			else if((fieldModifiers & Modifier.PROTECTED) != 0)
				umlAttribute.setVisibility("protected");
			else if((fieldModifiers & Modifier.PRIVATE) != 0)
				umlAttribute.setVisibility("private");
			else if(isInterfaceField)
				umlAttribute.setVisibility("public");
			else
				umlAttribute.setVisibility("package");
			
			if((fieldModifiers & Modifier.FINAL) != 0)
				umlAttribute.setFinal(true);
			
			if((fieldModifiers & Modifier.STATIC) != 0)
				umlAttribute.setStatic(true);
			
			attributes.add(umlAttribute);
		}
		return attributes;
	}
	
	private UMLAnonymousClass processAnonymousClassDeclaration(CompilationUnit cu, AnonymousClassDeclaration anonymous, String packageName, String binaryName, String codePath, String sourceFile) {
		List<BodyDeclaration> bodyDeclarations = anonymous.bodyDeclarations();
		LocationInfo locationInfo = generateLocationInfo(cu, sourceFile, anonymous, CodeElementType.ANONYMOUS_CLASS_DECLARATION);
		UMLAnonymousClass anonymousClass = new UMLAnonymousClass(packageName, binaryName, codePath, locationInfo, new Context(cu, Optional.empty()));
		
		for(BodyDeclaration bodyDeclaration : bodyDeclarations) {
			if(bodyDeclaration instanceof FieldDeclaration) {
				FieldDeclaration fieldDeclaration = (FieldDeclaration)bodyDeclaration;
				List<UMLAttribute> attributes = processFieldDeclaration(cu, fieldDeclaration, false, sourceFile);
	    		for(UMLAttribute attribute : attributes) {
	    			attribute.setClassName(anonymousClass.getCodePath());
	    			anonymousClass.addAttribute(attribute);
	    		}
			}
			else if(bodyDeclaration instanceof MethodDeclaration) {
				MethodDeclaration methodDeclaration = (MethodDeclaration)bodyDeclaration;
				UMLOperation operation = processMethodDeclaration(cu, methodDeclaration, packageName, sourceFile, anonymousClass);
				operation.setClassName(anonymousClass.getCodePath());
				anonymousClass.addOperation(operation);
			}
		}
		
		return anonymousClass;
	}
	
	private void insertNode(AnonymousClassDeclaration childAnonymous, DefaultMutableTreeNode root) {
		Enumeration enumeration = root.postorderEnumeration();
		DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(childAnonymous);
		
		DefaultMutableTreeNode parentNode = root;
		while(enumeration.hasMoreElements()) {
			DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode)enumeration.nextElement();
			AnonymousClassDeclaration currentAnonymous = (AnonymousClassDeclaration)currentNode.getUserObject();
			if(currentAnonymous != null && isParent(childAnonymous, currentAnonymous)) {
				parentNode = currentNode;
				break;
			}
		}
		parentNode.add(childNode);
	}

	private String getAnonymousCodePath(DefaultMutableTreeNode node) {
		AnonymousClassDeclaration anonymous = (AnonymousClassDeclaration)node.getUserObject();
		String name = "";
		ASTNode parent = anonymous.getParent();
		while(parent != null) {
			if(parent instanceof MethodDeclaration) {
				String methodName = ((MethodDeclaration)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = methodName;
				}
				else {
					name = methodName + "." + name;
				}
			}
			else if(parent instanceof VariableDeclarationFragment &&
					(parent.getParent() instanceof FieldDeclaration ||
					parent.getParent() instanceof VariableDeclarationStatement)) {
				String fieldName = ((VariableDeclarationFragment)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = fieldName;
				}
				else {
					name = fieldName + "." + name;
				}
			}
			else if(parent instanceof MethodInvocation) {
				String invocationName = ((MethodInvocation)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = invocationName;
				}
				else {
					name = invocationName + "." + name;
				}
			}
			else if(parent instanceof SuperMethodInvocation) {
				String invocationName = ((SuperMethodInvocation)parent).getName().getIdentifier();
				if(name.isEmpty()) {
					name = invocationName;
				}
				else {
					name = invocationName + "." + name;
				}
			}
			parent = parent.getParent();
		}
		return name.toString();
	}

	private String getAnonymousBinaryName(DefaultMutableTreeNode node) {
		StringBuilder name = new StringBuilder();
		TreeNode[] path = node.getPath();
		for(int i=0; i<path.length; i++) {
			DefaultMutableTreeNode tmp = (DefaultMutableTreeNode)path[i];
			if(tmp.getUserObject() != null) {
				DefaultMutableTreeNode parent = (DefaultMutableTreeNode)tmp.getParent();
				int index = parent.getIndex(tmp);
				name.append(index+1);
				if(i < path.length-1)
					name.append(".");
			}
		}
		return name.toString();
	}
	
	private boolean isParent(ASTNode child, ASTNode parent) {
		ASTNode current = child;
		while(current.getParent() != null) {
			if(current.getParent().equals(parent))
				return true;
			current = current.getParent();
		}
		return false;
	}

	private LocationInfo generateLocationInfo(CompilationUnit cu, String sourceFile, ASTNode node, CodeElementType codeElementType) {
		return new LocationInfo(cu, sourceFile, node, codeElementType);
	}
}
