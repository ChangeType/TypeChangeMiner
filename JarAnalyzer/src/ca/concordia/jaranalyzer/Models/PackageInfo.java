package ca.concordia.jaranalyzer.Models;

import java.util.ArrayList;

public class PackageInfo {
	private int id;
	private String name;
	private int jarId;
	private ArrayList<ClassInfo> classes;

	public PackageInfo(String packageName) {
		this.classes = new ArrayList<>();
		this.name = packageName;
	}

	public int getId() {
		return id;
	}


	public String getName() {
		return name;
	}



	public int getJarId() {
		return jarId;
	}

	public ArrayList<ClassInfo> getClasses() {
		return classes;
	}
	
	public boolean addClass(ClassInfo classInfo){
		classes.add(classInfo);
		return true;
	}

	public boolean matchesImportStatement(String importedPackage) {
		return importedPackage.startsWith(name);
	}

	public String toString() {
		String packageString = "PACKAGE: " + name;
		for (ClassInfo classFile : classes) {
			packageString += "\n" + classFile.toString();
		}
		return packageString;
	}

//	public Identification getID(){
//		return Identification.newBuilder()
//				.setName(getName())
//				.setType(TypeSignatureOuterClass.TypeSignature.newBuilder().setNoType(true).build())
//				.setKind("PACKAGE").build();
//	}
//
//
//	public TypeFactGraph<Identification> getTFG(Identification owner) {
//
//		Identification pkgId = getID();
//		TypeFactGraph<Identification> tfg = TypeFactGraph.<Identification>emptyTFG()
//				.mergeMap(classes.parallelStream()
//						.map(c -> u_v(pkgId, c.getID(pkgId), "DECLARES"))
//						.collect(toList()));
//		return mergeGraphs(tfg, classes.stream()
//				.map(c -> c.getTFG(pkgId))
//				.reduce(TypeFactGraph.emptyTFG(), TypeFactGraph::mergeGraphs));
//	}
}
