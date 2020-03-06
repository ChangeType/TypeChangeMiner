package ca.concordia.jaranalyzer.Models;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MethodInfo {
	private String name;
	private ClassInfo classInfo;
	private Type[] argumentTypes;
	private Type returnType;
	private List<String> thrownInternalClassNames;
	private boolean isPublic;
	private boolean isPrivate;
	private boolean isProtected;
	private boolean isAbstract;
	private boolean isStatic;
	private boolean isSynchronized;
	private boolean isConstructor;
	private String qualifiedName;

	@SuppressWarnings("unchecked")
	public MethodInfo(MethodNode methodNode, ClassInfo classInfo) {
		this.name = methodNode.name;
		if (name.equals("<init>")) {
			isConstructor = true;
			name = classInfo.getName();
		}

		this.classInfo = classInfo;
		this.returnType = Type.getReturnType(methodNode.desc);
		if (isConstructor && name.contains("$")) {
			List<Type> types = new ArrayList<Type>();
			for (Type type : Type.getArgumentTypes(methodNode.desc)) {
				if (!classInfo.getQualifiedName().startsWith(type.getClassName() + "$")) {
					types.add(type);
				}
			}
			this.argumentTypes = new Type[types.size()];
			this.argumentTypes = types.toArray(this.argumentTypes);
		}
		else {
			this.argumentTypes = Type.getArgumentTypes(methodNode.desc);
		}
		
		this.thrownInternalClassNames = methodNode.exceptions;

		if ((methodNode.access & Opcodes.ACC_PUBLIC) != 0) {
			isPublic = true;
		} else if ((methodNode.access & Opcodes.ACC_PROTECTED) != 0) {
			isProtected = true;
		} else if ((methodNode.access & Opcodes.ACC_PRIVATE) != 0) {
			isPrivate = true;
		}
		this.qualifiedName = classInfo.getQualifiedName();
		if ((methodNode.access & Opcodes.ACC_STATIC) != 0) {
			isStatic = true;
			this.qualifiedName = qualifiedName + name;
		}

		if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
			isAbstract = true;
		}

		if ((methodNode.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
			isSynchronized = true;
		}
	}

	public String toString() {
		StringBuilder methodDescription = new StringBuilder();
		if (isPublic) {
			methodDescription.append("public ");
		} else if (isProtected) {
			methodDescription.append("protected ");
		} else if (isPrivate) {
			methodDescription.append("private ");
		}

		if (isStatic) {
			methodDescription.append("static ");
		}

		if (isAbstract) {
			methodDescription.append("abstract ");
		}

		if (isSynchronized) {
			methodDescription.append("synchronized ");
		}

		methodDescription.append(returnType.getClassName());
		methodDescription.append(" ");
		methodDescription.append(this.name);

		methodDescription.append("(");
		for (int i = 0; i < argumentTypes.length; i++) {
			Type argumentType = argumentTypes[i];
			if (i > 0) {
				methodDescription.append(", ");
			}
			methodDescription.append(argumentType.getClassName());
		}
		methodDescription.append(")");

		if (!thrownInternalClassNames.isEmpty()) {
			methodDescription.append(" throws ");
			int i = 0;
			for (String thrownInternalClassName : thrownInternalClassNames) {
				if (i > 0) {
					methodDescription.append(", ");
				}
				methodDescription.append(Type.getObjectType(
						thrownInternalClassName).getClassName());
				i++;
			}
		}

		return methodDescription.toString();
	}

	public String getName() {
		return name;
	}







	public ClassInfo getClassInfo() {
		return classInfo;
	}

	public String getQualifiedClassName() {
		return classInfo.getQualifiedName();
	}

	public String getClassName() {
		return classInfo.getName();
	}

	public String getPackageName() {
		return classInfo.getPackageName();
	}

	public Type[] getArgumentTypes() {
		return argumentTypes;
	}

	public boolean isConstructor() {
		return isConstructor;
	}

	public String getParameterTypes() {
		String parameters = "";
		return Arrays.stream(argumentTypes).map(x->x.getClassName()).collect(Collectors.joining("&"));
	}

	public String getReturnType() {
		return returnType.getClassName();// .substring(returnType.getClassName().lastIndexOf('.')
											// + 1);
	}

	public Type getReturnTypeAsType() {
		return returnType;// .substring(returnType.getClassName().lastIndexOf('.')
		// + 1);
	}

	public List<String> getThrownInternalClassNames() {
		return thrownInternalClassNames;
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

	public boolean isStatic() {
		return isStatic;
	}

	public boolean isSynchronized() {
		return isSynchronized;
	}

	public boolean matches(String methodName, int numberOfParameters) {
		if (argumentTypes.length != numberOfParameters)
			return false;

		if (name.replace('$', '.').equals(methodName)) {
			return true;
		} else if ((getClassName() + "." + name).replace('$', '.').equals(methodName)) {
			return true;
		} else if ((getQualifiedClassName() + "." + name).replace('$', '.').equals(
				methodName)) {
			return true;
		} else if (isConstructor) {
			if (getQualifiedClassName().replace('$', '.').equals(methodName)) {
				return true;
			}
			if(name.contains("$") && name.endsWith("$" + methodName)) {
				return true;
			}
		}
		return false;
	}


//	public Identification getID(Identification owner){
//		TypeSignature typeSign = TypeSignature.newBuilder()
//				.setMthdSign(MethodSign.newBuilder()
//						.setReturnType(getTypeInfo(returnType))
//						.addAllParam(Arrays.stream(argumentTypes).map(this::getTypeInfo).collect(toList())))
//				.build();
//
//		return Identification.newBuilder()
//				.setName(getName())
//				.setType(typeSign)
//				.setKind(isConstructor ? "CONSTRUCTOR" : "METHOD")
//				.setOwner(owner).build();
//	}
//
//
//
//	public TypeFactGraph<Identification> getTFG(Identification owner) {
//		Identification mid = getID(owner);
//		Identification am = Identification.newBuilder()
//				.setName(isPrivate ? "PRIVATE" : (isPublic ? "PUBLIC" : (isProtected ? "PROTECTED" : "DEFAULT")))
//				.setKind("MODIFIER").setOwner(mid).build();
//		return TypeFactGraph.<Identification>emptyTFG()
//				.map(addNode(mid))
//				.map(u_v(mid, am, "MODIFIER"));
//	}
}
