package ca.concordia.jaranalyzer.Models;


import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassInfo  {

	private String qualifiedName;
	private String name;
	private String packageName;
	private Type type;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isInterface;
	private List<MethodInfo> methods;
	private List<FieldInfo> fields;
	private String superClassName;
	private ClassInfo superClassInfo;
	private Map<String, ClassInfo> superInterfaceMap;
	private boolean isEnum;

	public ClassInfo(ClassNode classNode) {
		try {
			this.methods = new ArrayList<>();
			this.fields = new ArrayList<>();
			this.superInterfaceMap = new LinkedHashMap<>();
			this.qualifiedName = classNode.name.replace('/', '.');
			this.qualifiedName = qualifiedName.contains("$") ? qualifiedName.replace("$",".") : qualifiedName;
			if (!classNode.name.contains("/")) {
				this.name = classNode.name;
				this.packageName = "";
			} else {
				this.name = classNode.name.substring(
						classNode.name.lastIndexOf('/') + 1);
				this.packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/')).replace('/', '.');
			}

			if (classNode.superName != null) {
				this.superClassName = classNode.superName.replace('/', '.').replace("$",".");
			} else {
				this.superClassName = "";
			}

			this.type = Type.getObjectType(classNode.name);

			if ((classNode.access & Opcodes.ACC_PUBLIC) != 0) {
				isPublic = true;
			} else if ((classNode.access & Opcodes.ACC_PROTECTED) != 0) {
				isProtected = true;
			} else if ((classNode.access & Opcodes.ACC_PRIVATE) != 0) {
				isPrivate = true;
			}

			if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
				isAbstract = true;
			}

			if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
				isInterface = true;
			}

			if ((classNode.access & Opcodes.ACC_ENUM) != 0) {
				isEnum = true;
			}

			@SuppressWarnings("unchecked")
			List<String> implementedInterfaces = classNode.interfaces;
			for (String interfaceName : implementedInterfaces) {
				superInterfaceMap.put(interfaceName.replace('/', '.').replace("$","."), null);
			}
			
			@SuppressWarnings("unchecked")
			List<MethodNode> methodNodes = classNode.methods;
			for (MethodNode methodNode : methodNodes) {
				methods.add(new MethodInfo(methodNode, this));
			}

			@SuppressWarnings("unchecked")
			List<FieldNode> fieldNodes = classNode.fields;
			for (FieldNode fieldNode : fieldNodes) {
				fields.add(new FieldInfo(fieldNode, this));
			}

		} catch (Exception e) {
			System.out.println(e);
		}
	}

	public String getQualifiedName() {
		return qualifiedName;
	}


	public String getName() {
		return name;
	}

	public String getPackageName() {
		return packageName;
	}

	public Type getType() {
		return type;
	}





	public boolean isPublic() {
		return isPublic;
	}

	public boolean isPrivate() {
		return isPrivate;
	}

	public boolean isProtected() {
		return isProtected;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public boolean isInterface() {
		return isInterface;
	}

	public List<MethodInfo> getMethods() {
		return methods;
	}

	public String getSuperClassName() {
		return superClassName;
	}

	public ClassInfo getSuperClassInfo() {
		return superClassInfo;
	}

	public void setSuperClassInfo(ClassInfo superClassInfo) {
		this.superClassInfo = superClassInfo;
	}

	public Set<String> getSuperInterfaceNames() {
		return superInterfaceMap.keySet();
	}

	public void putSuperInterfaceInfo(String interfaceName, ClassInfo interfaceInfo) {
		interfaceName = interfaceName.replace("$",".");
		superInterfaceMap.put(interfaceName, interfaceInfo);
	}

	public ArrayList<MethodInfo> getPublicMethods() {
		ArrayList<MethodInfo> publicMethods = new ArrayList<>();
		for (MethodInfo methodInfo : getMethods()) {
			if (methodInfo.isPublic())
				publicMethods.add(methodInfo);
		}
		return publicMethods;
	}

	public String toString() {
		StringBuilder classDescription = new StringBuilder();

		classDescription.append(getSignature());

		/*for (MethodInfo method : methods) {
			classDescription.append("\n\t" + method.toString());
		}*/

		return classDescription.toString();
	}

	public String getSignature() {
		StringBuilder classDescription = new StringBuilder();
		if (isPublic) {
			classDescription.append("public ");
		} else if (isProtected) {
			classDescription.append("protected ");
		} else if (isPrivate) {
			classDescription.append("private ");
		}

		if (isAbstract) {
			classDescription.append("abstract ");
		}

		if (isInterface) {
			classDescription.append("interface ");
		} else {
			classDescription.append("class ");
		}

		classDescription.append(type.getClassName());

		return classDescription.toString();
	}

	public List<FieldInfo> getFieldsByName(String fieldName) {
		List<FieldInfo> matchedFields = new ArrayList<>();
		for (FieldInfo fieldInfo : fields) {
			if (fieldInfo.getName().equals(fieldName)) {
				matchedFields.add(fieldInfo);
			}
		}

		if (matchedFields.size() == 0 && superClassInfo != null) {
			matchedFields.addAll(superClassInfo.getFieldsByName(fieldName));
		}

		if (matchedFields.size() == 0) {
			for (String superInterfaceName : superInterfaceMap.keySet()) {
				ClassInfo interfaceInfo = superInterfaceMap.get(superInterfaceName);
				if (interfaceInfo != null) {
					matchedFields.addAll(interfaceInfo.getFieldsByName(fieldName));
				}
			}
		}
		return matchedFields;
	}


	public List<FieldInfo> getFields(){
		return fields;
	}

	public List<MethodInfo> getMethods(String methodName,
			int numberOfParameters) {
		List<MethodInfo> matchedMethods = new ArrayList<>();

		for (MethodInfo method : getMethods()) {
			if (method.matches(methodName, numberOfParameters)) {
				matchedMethods.add(method);
			}
		}

		if (matchedMethods.size() == 0 && superClassInfo != null) {
			matchedMethods.addAll(superClassInfo.getMethods(methodName, numberOfParameters));
		}
		
		if (matchedMethods.size() == 0) {
			for (String superInterfaceName : superInterfaceMap.keySet()) {
				ClassInfo interfaceInfo = superInterfaceMap.get(superInterfaceName);
				if (interfaceInfo != null) {
					matchedMethods.addAll(interfaceInfo.getMethods(methodName, numberOfParameters));
				}
			}
		}
		return matchedMethods;
	}

	public boolean matchesImportStatement(String importedPackage) {
		if(qualifiedName.replace('$', '.').startsWith(importedPackage)){
			return true;
		} else {
			return false;
		}
		
	}
	public String getKind() {
		return "CLASS";
	}


	public boolean isEnum() {
		return isEnum;
	}
}
