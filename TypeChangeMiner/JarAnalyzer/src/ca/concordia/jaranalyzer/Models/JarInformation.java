package ca.concordia.jaranalyzer.Models;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import ca.concordia.jaranalyzer.util.Utility;

public class JarInformation {
	private String name;
	private String groupId;
	private String artifactId;
	private String version;

	private Map<String, PackageInfo> packages;


	public JarInformation(JarFile jarFile, String groupId, String artifactId,
						  String version) {
		this.artifactId = artifactId;
		this.groupId = groupId;
		this.version = version;
		this.name = Utility.getJarName(jarFile.getName());
		this.packages = new HashMap<>();

		Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {

			JarEntry entry = entries.nextElement();
			String entryName = entry.getName();
			if (entryName.endsWith(".class")) {
				ClassNode classNode = new ClassNode();
				InputStream classFileInputStream;
				try {
					classFileInputStream = jarFile.getInputStream(entry);
					try {
						ClassReader classReader = new ClassReader(
								classFileInputStream);
						classReader.accept(classNode, 0);
					} catch (Exception e) {
						System.out.println("Could not read class file");
						e.printStackTrace();
					} finally {
						classFileInputStream.close();
					}
				} catch (Exception e) {
					System.out.println("Could not read class file");
					e.printStackTrace();
				}
				ClassInfo newClass = new ClassInfo(classNode);
				if(newClass.getQualifiedName()!=null) {
					String packageName = newClass.getQualifiedName().substring(0,
							newClass.getQualifiedName().lastIndexOf('.'));
					PackageInfo packageInfo = getPackageInfo(packageName);
					packageInfo.addClass(newClass);
				}

			}
		}

		for (ClassInfo classInfo : getClasses()) {
			if (!classInfo.getSuperClassName().equals("java.lang.Object")) {
				for (ClassInfo cls : getClasses()) {
					if (cls.getQualifiedName().equals(
							classInfo.getSuperClassName())) {
						classInfo.setSuperClassInfo(cls);
					}
				}
			}
			for (String superInterface : classInfo.getSuperInterfaceNames()) {
				for (ClassInfo cls : getClasses()) {
					if (cls.getQualifiedName().equals(
							superInterface)) {
						classInfo.putSuperInterfaceInfo(superInterface, cls);
					}
				}
			}
		}

	}


	private PackageInfo getPackageInfo(String packageName) {
		if (packages.containsKey(packageName)) {
			return packages.get(packageName);
		}
		PackageInfo packageInfo = new PackageInfo(packageName);
		packages.put(packageName, packageInfo);
		return packageInfo;
	}

	public String toString() {
		StringBuilder jarString = new StringBuilder(name);
		for (PackageInfo packageInfo : packages.values()) {
			jarString.append("\n\n").append(packageInfo.toString());
		}
		return jarString.toString();
	}

	public ArrayList<ClassInfo> getClasses() {
		ArrayList<ClassInfo> classes = new ArrayList<ClassInfo>();
		for (PackageInfo packageInfo : packages.values()) {
			classes.addAll(packageInfo.getClasses());
		}
		return classes;
	}

	public Collection<PackageInfo> getPackages() {
		return packages.values();
	}


	public String getName() {
		return name;
	}



	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public ArrayList<ClassInfo> getClasses(String importedPackage) {
		ArrayList<ClassInfo> matchedClasses = new ArrayList<ClassInfo>();
		for (PackageInfo packageInfo : packages.values()) {
			if (packageInfo.matchesImportStatement(importedPackage)) {
				if (packageInfo.getName().equals(importedPackage)) {
					matchedClasses.addAll(packageInfo.getClasses());
				}
				else {
					for (ClassInfo classInfo : packageInfo.getClasses()) {
						if (classInfo.matchesImportStatement(importedPackage)) {
							matchedClasses.add(classInfo);
						}
					}
				}
			}
		}
		return matchedClasses;
	}

	@Override
	public boolean equals(Object o){
		if(o instanceof JarInformation){
			JarInformation j = (JarInformation) o;
			return j.getArtifactId().equals(artifactId)
					&& j.getVersion().equals(version)
					&& j.getGroupId().equals(groupId);
		}
		return false;
	}
}
